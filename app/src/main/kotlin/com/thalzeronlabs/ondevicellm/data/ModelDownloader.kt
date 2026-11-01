package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** A model file published at a fixed URL, pinned by its exact size and SHA-256. */
data class ModelSpec(
    val displayName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    companion object {
        /** Qwen2.5-0.5B-Instruct, 4-bit (Q4_K_M), from the official Qwen repository on Hugging Face. */
        val QWEN_2_5_0_5B_Q4_K_M = ModelSpec(
            displayName = "Qwen2.5 0.5B Instruct (Q4_K_M)",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 491_400_032L,
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        )
    }
}

/** What the downloader reports while it works. */
sealed interface DownloadProgress {
    val bytes: Long
    val total: Long
    val fraction: Float get() = if (total <= 0) 0f else (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f)

    /** Bytes are arriving. [resumedFrom] > 0 means an earlier partial download is being continued. */
    data class Transferring(override val bytes: Long, override val total: Long, val resumedFrom: Long) : DownloadProgress

    /** The whole file is on disk and its SHA-256 is being checked. */
    data class Verifying(override val bytes: Long, override val total: Long) : DownloadProgress
}

/** A failure that retrying will not fix. */
open class FatalDownloadException(message: String) : IOException(message)

class ChecksumMismatchException(expected: String, actual: String) : FatalDownloadException(
    "The downloaded file is corrupted (SHA-256 ${actual.take(12)}…, expected ${expected.take(12)}…). " +
        "It was deleted, please try again."
)

class NotEnoughSpaceException(val neededBytes: Long, val freeBytes: Long) : FatalDownloadException(
    "Not enough free space: the model needs ${neededBytes.mb()} MB, the phone has ${freeBytes.mb()} MB free."
)

class HttpStatusException(val code: Int) : IOException("The server answered HTTP $code") {
    /** Server errors, timeouts and rate limits are worth another try; other 4xx are not. */
    val retryable: Boolean get() = code >= 500 || code == 408 || code == 429
}

/** One HTTP GET, possibly starting at an offset. Swapped for a fake in unit tests. */
fun interface HttpTransport {
    fun get(url: String, fromByte: Long): HttpResponse
}

class HttpResponse(
    val code: Int,
    private val headers: (String) -> String?,
    val body: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    fun header(name: String): String? = headers(name)
    override fun close() {
        try { body.close() } finally { onClose() }
    }
}

/** The real transport, on top of [HttpURLConnection] (OkHttp under the hood on Android). */
class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : HttpTransport {
    override fun get(url: String, fromByte: Long): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty("User-Agent", "android-on-device-llm")
        // A compressed body would break byte offsets, so ask for the raw bytes.
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (fromByte > 0) connection.setRequestProperty("Range", "bytes=$fromByte-")
        val code = connection.responseCode
        val body = if (code in 200..299) connection.inputStream
        else connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
        return HttpResponse(code, connection::getHeaderField, body) { connection.disconnect() }
    }
}

/**
 * Downloads [spec] to [target], the way a 500 MB file on a phone needs to be downloaded:
 *
 * - **Resumable.** Bytes go to `<target>.part` and stay there when the download is cancelled, the app is killed or
 *   the network drops; the next call continues from where it stopped with an HTTP `Range` request.
 * - **Verified.** The file is only moved to [target] after its size and SHA-256 match [spec]. A corrupted file is
 *   deleted, never loaded.
 * - **Retried.** Network errors and 5xx answers are retried with exponential backoff before giving up.
 * - **Space-aware.** It refuses to start when the disk cannot hold the rest of the file.
 */
class ModelDownloader(
    private val spec: ModelSpec,
    val target: File,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val freeBytes: () -> Long = { target.absoluteFile.parentFile?.usableSpace ?: Long.MAX_VALUE },
    private val maxAttempts: Int = 4,
    private val retryDelayMillis: Long = 2_000,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val partial = File(target.path + ".part")

    /** Bytes already downloaded by an earlier, unfinished run. */
    fun partialBytes(): Long = if (partial.isFile) partial.length() else 0L

    fun download(): Flow<DownloadProgress> = flow {
        target.absoluteFile.parentFile?.mkdirs()
        emitAll(
            flow { transfer() }.retryWhen { cause, attempt ->
                val retry = cause.isRetryable() && attempt + 1 < maxAttempts
                if (retry) delay(retryDelayMillis shl attempt.toInt())
                retry
            }
        )
        verifyAndInstall()
    }.flowOn(ioDispatcher)

    private suspend fun FlowCollector<DownloadProgress>.transfer() {
        var offset = partialBytes()
        if (offset > spec.sizeBytes) {
            partial.delete()
            offset = 0
        }
        if (offset == spec.sizeBytes) return

        val needed = spec.sizeBytes - offset
        val free = freeBytes()
        if (free < needed + SPACE_MARGIN_BYTES) throw NotEnoughSpaceException(needed + SPACE_MARGIN_BYTES, free)

        transport.get(spec.url, offset).use { response ->
            when (response.code) {
                206 -> if (contentRangeStart(response.header("Content-Range")) != offset) {
                    partial.delete()
                    throw IOException("The server resumed at the wrong offset; starting over")
                }
                in 200..299 -> offset = 0 // the server ignored the Range header: start over
                416 -> {
                    partial.delete()
                    throw IOException("The server could not resume the download; starting over")
                }
                else -> throw HttpStatusException(response.code)
            }

            emit(DownloadProgress.Transferring(offset, spec.sizeBytes, resumedFrom = offset))
            FileOutputStream(partial, offset > 0).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var written = offset
                var lastStep = -1L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = response.body.read(buffer)
                    if (read < 0) break
                    if (written + read > spec.sizeBytes) {
                        output.close()
                        partial.delete()
                        throw FatalDownloadException("The server sent more data than expected; the file has changed")
                    }
                    output.write(buffer, 0, read)
                    written += read
                    val step = written * PROGRESS_STEPS / spec.sizeBytes
                    if (step != lastStep) {
                        lastStep = step
                        emit(DownloadProgress.Transferring(written, spec.sizeBytes, resumedFrom = offset))
                    }
                }
                if (written < spec.sizeBytes) throw IOException("The connection closed early")
            }
        }
    }

    private suspend fun FlowCollector<DownloadProgress>.verifyAndInstall() {
        val size = partialBytes()
        if (size != spec.sizeBytes) {
            partial.delete()
            throw FatalDownloadException("Downloaded $size bytes, expected ${spec.sizeBytes}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var hashed = 0L
            var lastStep = -1L
            emit(DownloadProgress.Verifying(0, size))
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                hashed += read
                val step = hashed * PROGRESS_STEPS / size
                if (step != lastStep) {
                    lastStep = step
                    emit(DownloadProgress.Verifying(hashed, size))
                }
            }
        }
        val actual = digest.digest().toHex()
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            partial.delete()
            throw ChecksumMismatchException(spec.sha256, actual)
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw IOException("Could not move the downloaded model into place")
    }

    private fun Throwable.isRetryable(): Boolean = when (this) {
        is FatalDownloadException -> false
        is HttpStatusException -> retryable
        is IOException -> true
        else -> false
    }

    companion object {
        /** Room kept free on top of the file itself, so the phone is not left with a full disk. */
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
        private const val BUFFER_BYTES = 1 shl 16
        private const val PROGRESS_STEPS = 1_000L

        /** `bytes 100-199/200` → 100. */
        internal fun contentRangeStart(header: String?): Long? =
            header?.let { Regex("""bytes\s+(\d+)-""").find(it) }?.groupValues?.get(1)?.toLongOrNull()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.US, it) }

private fun Long.mb(): Long = this / 1_000_000
