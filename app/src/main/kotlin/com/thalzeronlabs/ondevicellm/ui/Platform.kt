package com.thalzeronlabs.ondevicellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/** Puts [text] on the clipboard. Android 13+ confirms it on its own; older versions get a short toast. */
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

/** A share sheet for a conversation exported as Markdown. Plain text, so every messaging app accepts it. */
fun shareMarkdownIntent(title: String, markdown: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    return Intent.createChooser(send, "Share conversation")
}
