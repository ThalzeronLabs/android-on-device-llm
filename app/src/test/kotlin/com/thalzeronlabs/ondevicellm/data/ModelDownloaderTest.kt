package com.thalzeronlabs.ondevicellm.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random

class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val data = Random(42).nextBytes(200_000)
    private val spec = ModelSpec("Test", "https://example.test/model.gguf", data.size.toLong(), sha256(data))

    /** An in-memory HTTP server that honours `Range` and can misbehave on demand. */
    private inner class FakeServer : HttpTransport {
        val requestedOffsets = mutableListOf<Long>()
        val statuses = ArrayDeque<Int>()
        var ignoreRange = false
        var reportedRangeStart: Long? = null
        var dropConnectionAfter: Int? = null
        var body: ByteArray = data

        override fun get(url: String, fromByte: Long): HttpResponse {
            requestedOffsets += fromByte
            statuses.removeFirstOrNull()?.let { return HttpResponse(it, { null }, ByteArrayInputStream(ByteArray(0))) }
            if (fromByte > body.size) return HttpResponse(416, { null }, ByteArrayInputStream(ByteArray(0)))
            val start = if (ignoreRange) 0 else fromByte.toInt()
            val code = if (fromByte > 0 && !ignoreRange) 206 else 200
            val headers = mapOf("Content-Range" to "bytes ${reportedRangeStart ?: start}-${body.size - 1}/${body.size}")
            var stream: InputStream = ByteArrayInputStream(body, start, body.size - start)
            dropConnectionAfter?.let { limit ->
                dropConnectionAfter = null
                stream = DroppingStream(stream, limit)
            }
            return HttpResponse(code, { headers[it] }, stream)
        }
    }

    /** Delivers [limit] bytes, then fails like a dropped connection. */
    private class DroppingStream(private val inner: InputStream, private var limit: Int) : InputStream() {
        override fun read(): Int = if (limit-- <= 0) throw IOException("connection reset") else inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (limit <= 0) throw IOException("connection reset")
            val n = inner.read(b, off, minOf(len, limit))
            if (n > 0) limit -= n
            return n
        }
    }

    private val target get() = File(tmp.root, "model.gguf")

    private fun downloader(
        server: HttpTransport,
        spec: ModelSpec = this.spec,
        freeBytes: Long = Long.MAX_VALUE,
        maxAttempts: Int = 3,
        io: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = ModelDownloader(
        spec = spec,
        target = target,
        transport = server,
        freeBytes = { freeBytes },
        maxAttempts = maxAttempts,
        retryDelayMillis = 0,
        ioDispatcher = io,
    )

    @Test
    fun `downloads, verifies and installs the file`() = runTest {
        val d = downloader(FakeServer())

        val events = d.download().toList()

        assertArrayEquals(data, target.readBytes())
        assertFalse(d.partial.exists())
        assertEquals(DownloadProgress.Verifying(data.size.toLong(), data.size.toLong()), events.last())
        val transferred = events.filterIsInstance<DownloadProgress.Transferring>().map { it.bytes }
        assertEquals(transferred.sorted(), transferred)
        assertEquals(data.size.toLong(), transferred.last())
    }

    @Test
    fun `a dropped connection is retried from where it stopped`() = runTest {
        val server = FakeServer().apply { dropConnectionAfter = 3_000 }

        downloader(server).download().toList()

        assertEquals(listOf(0L, 3_000L), server.requestedOffsets)
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun `a failed run keeps its bytes and the next run resumes them`() = runTest {
        val server = FakeServer().apply { dropConnectionAfter = 70_000 }
        val d = downloader(server, maxAttempts = 1)

        try {
            d.download().toList()
            fail("expected the dropped connection to surface")
        } catch (e: IOException) {
            assertEquals("connection reset", e.message)
        }
        assertEquals(70_000L, d.partialBytes())
        assertFalse(target.exists())

        val events = d.download().toList()

        assertEquals(listOf(0L, 70_000L), server.requestedOffsets)
        assertEquals(70_000L, (events.first() as DownloadProgress.Transferring).resumedFrom)
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun `cancelling keeps the partial file`() = runTest {
        // Same dispatcher as the collector, so no buffer lets the download run ahead of the cancellation.
        val d = downloader(FakeServer(), io = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)

        d.download().first { it is DownloadProgress.Transferring && it.bytes > 0 }

        assertTrue(d.partialBytes() > 0)
        assertTrue(d.partialBytes() < data.size)
        assertFalse(target.exists())
    }

    @Test
    fun `a server that ignores Range makes it start over cleanly`() = runTest {
        File(target.path + ".part").writeBytes(data.copyOf(5_000))
        val server = FakeServer().apply { ignoreRange = true }

        downloader(server).download().toList()

        assertEquals(listOf(5_000L), server.requestedOffsets)
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun `a resume at the wrong offset is discarded and restarted`() = runTest {
        File(target.path + ".part").writeBytes(data.copyOf(5_000))
        val server = FakeServer().apply { reportedRangeStart = 4_000 }

        downloader(server).download().toList()

        assertEquals(listOf(5_000L, 0L), server.requestedOffsets)
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun `a corrupted file is rejected, deleted and not retried`() = runTest {
        val server = FakeServer().apply { body = data.copyOf().also { it[123] = (it[123] + 1).toByte() } }
        val d = downloader(server)

        try {
            d.download().toList()
            fail("expected a checksum error")
        } catch (e: ChecksumMismatchException) {
            assertTrue(e.message!!.contains("corrupted"))
        }
        assertEquals(1, server.requestedOffsets.size)
        assertFalse(d.partial.exists())
        assertFalse(target.exists())
    }

    @Test
    fun `refuses to start without enough free space`() = runTest {
        val server = FakeServer()
        val d = downloader(server, freeBytes = data.size.toLong())

        try {
            d.download().toList()
            fail("expected a free-space error")
        } catch (e: NotEnoughSpaceException) {
            assertEquals(data.size + ModelDownloader.SPACE_MARGIN_BYTES, e.neededBytes)
        }
        assertTrue(server.requestedOffsets.isEmpty())
    }

    @Test
    fun `server errors are retried, client errors are not`() = runTest {
        val flaky = FakeServer().apply { statuses += listOf(503, 500) }
        downloader(flaky).download().toList()
        assertEquals(3, flaky.requestedOffsets.size)
        assertArrayEquals(data, target.readBytes())

        target.delete()
        val missing = FakeServer().apply { statuses += 404 }
        try {
            downloader(missing).download().toList()
            fail("expected HTTP 404")
        } catch (e: HttpStatusException) {
            assertEquals(404, e.code)
        }
        assertEquals(1, missing.requestedOffsets.size)
    }

    @Test
    fun `gives up after the last attempt`() = runTest {
        val server = FakeServer().apply { statuses += listOf(503, 503, 503) }
        try {
            downloader(server, maxAttempts = 3).download().toList()
            fail("expected HTTP 503")
        } catch (e: HttpStatusException) {
            assertEquals(503, e.code)
        }
        assertEquals(3, server.requestedOffsets.size)
    }

    @Test
    fun `a complete partial file is verified without touching the network`() = runTest {
        File(target.path + ".part").writeBytes(data)
        val server = FakeServer()

        downloader(server).download().toList()

        assertTrue(server.requestedOffsets.isEmpty())
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun `an oversized leftover is thrown away`() = runTest {
        File(target.path + ".part").writeBytes(data + ByteArray(10))
        val server = FakeServer()

        downloader(server).download().toList()

        assertEquals(listOf(0L), server.requestedOffsets)
        assertArrayEquals(data, target.readBytes())
    }

    @Test
    fun `parses the start of a Content-Range header`() {
        assertEquals(100L, ModelDownloader.contentRangeStart("bytes 100-199/200"))
        assertEquals(0L, ModelDownloader.contentRangeStart("bytes 0-0/1"))
        assertEquals(null, ModelDownloader.contentRangeStart("bytes */200"))
        assertEquals(null, ModelDownloader.contentRangeStart(null))
    }

    @Test
    fun `the default model is pinned to the published Hugging Face file`() {
        val qwen = ModelSpec.QWEN_2_5_0_5B_Q4_K_M
        assertTrue(qwen.url.startsWith("https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/"))
        assertEquals(491_400_032L, qwen.sizeBytes)
        assertTrue(qwen.sha256.startsWith("74a4da8c"))
        assertEquals(64, qwen.sha256.length)
    }

    @Test
    fun `the real transport sends a Range header and resumes over HTTP`() = runTest {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val seenRanges = mutableListOf<String?>()
        val worker = thread {
            repeat(2) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    val range = headers.firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                    seenRanges += range
                    val start = range?.substringAfter("bytes=")?.substringBefore("-")?.toInt() ?: 0
                    // First request: cut the body short, as a dropped connection would.
                    val end = if (it == 0) 50_000 else data.size
                    val status = if (start > 0) "206 Partial Content" else "200 OK"
                    val head = "HTTP/1.1 $status\r\nContent-Length: ${data.size - start}\r\n" +
                        "Content-Range: bytes $start-${data.size - 1}/${data.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(head.toByteArray(Charsets.ISO_8859_1))
                        write(data, start, end - start)
                        flush()
                    }
                }
            }
        }
        val local = spec.copy(url = "http://127.0.0.1:${server.localPort}/model.gguf")
        val d = ModelDownloader(
            local, target, UrlConnectionTransport(), freeBytes = { Long.MAX_VALUE },
            maxAttempts = 2, retryDelayMillis = 0, ioDispatcher = Dispatchers.IO,
        )

        d.download().toList()
        worker.join()
        server.close()

        assertEquals(null, seenRanges[0])
        assertEquals("bytes=50000-", seenRanges[1]?.substringAfter(":")?.trim())
        assertArrayEquals(data, target.readBytes())
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}
