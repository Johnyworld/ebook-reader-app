package com.rotein.ebookreader.reader

import android.annotation.SuppressLint
import android.util.Base64
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.rotein.ebookreader.CenteredMessage
import com.rotein.ebookreader.LoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile

@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun MobiViewer(path: String, onCenterTap: () -> Unit) {
    var html by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            html = withContext(Dispatchers.IO) { extractMobiHtml(path) }
            if (html == null) error = true
        } catch (_: Exception) {
            error = true
        }
    }

    when {
        error -> CenteredMessage("MOBI 파일을 읽을 수 없습니다.")
        html == null -> LoadingIndicator()
        else -> AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                if (webView.tag != path) {
                    webView.tag = path
                    val encoded = Base64.encodeToString(
                        html!!.toByteArray(Charsets.UTF_8), Base64.NO_PADDING
                    )
                    webView.loadData(encoded, "text/html", "base64")
                }
                webView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val x = event.x
                        val width = v.width.toFloat()
                        if (x > width / 3f && x < width * 2f / 3f) onCenterTap()
                        v.performClick()
                    }
                    false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun extractMobiHtml(path: String): String? {
    RandomAccessFile(path, "r").use { raf ->
        val fileSize = raf.length()
        if (fileSize < 78 + 8) return null

        val palmHeader = ByteArray(78)
        raf.readFully(palmHeader)
        val numRecords = mobiUShort(palmHeader, 76)
        if (numRecords == 0) return null

        val recordOffsets = LongArray(numRecords)
        repeat(numRecords) { i ->
            val entry = ByteArray(8)
            raf.readFully(entry)
            recordOffsets[i] = mobiUInt(entry, 0)
        }

        fun recordBytes(i: Int): ByteArray {
            val end = if (i + 1 < numRecords) recordOffsets[i + 1] else fileSize
            val size = (end - recordOffsets[i]).coerceAtMost(65536L).toInt()
            return ByteArray(size).also { buf ->
                raf.seek(recordOffsets[i])
                raf.readFully(buf)
            }
        }

        val record0 = recordBytes(0)
        if (record0.size < 32) return null

        val compression = mobiUShort(record0, 0)
        val textRecordCount = mobiUShort(record0, 8)

        if (record0.copyOfRange(16, 20).toString(Charsets.ISO_8859_1) != "MOBI") return null
        val encoding = mobiInt(record0, 28)
        val charset = if (encoding == 65001) Charsets.UTF_8 else Charsets.ISO_8859_1

        val sb = StringBuilder()
        for (i in 1..textRecordCount) {
            if (i >= numRecords) break
            val rec = recordBytes(i)
            val decoded = when (compression) {
                1 -> rec
                2 -> palmDocDecompress(rec)
                else -> return null // HUFF/CDIC not supported
            }
            sb.append(decoded.toString(charset))
        }

        val content = sb.toString()
        return if (content.trimStart().startsWith("<")) {
            content
        } else {
            "<!DOCTYPE html><html><body><p>${content.replace("\n", "<br/>")}</p></body></html>"
        }
    }
}

private fun palmDocDecompress(data: ByteArray): ByteArray {
    val out = mutableListOf<Byte>()
    var i = 0
    while (i < data.size) {
        val b = data[i++].toInt() and 0xFF
        when {
            b == 0x00 -> out.add(0)
            b in 0x01..0x08 -> repeat(b) { if (i < data.size) out.add(data[i++]) }
            b in 0x09..0x7F -> out.add(b.toByte())
            b in 0x80..0xBF -> if (i < data.size) {
                val b2 = data[i++].toInt() and 0xFF
                val distance = ((b and 0x3F) shl 5) or (b2 ushr 3)
                val length = (b2 and 0x07) + 3
                val pos = out.size - distance
                if (pos >= 0) repeat(length) { j -> out.add(out[pos + j]) }
            }
            else -> { // 0xC0..0xFF
                out.add(' '.code.toByte())
                out.add((b xor 0x80).toByte())
            }
        }
    }
    return out.toByteArray()
}

private fun mobiUShort(b: ByteArray, off: Int) =
    ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

private fun mobiUInt(b: ByteArray, off: Int) =
    ((b[off].toLong() and 0xFF) shl 24) or
    ((b[off + 1].toLong() and 0xFF) shl 16) or
    ((b[off + 2].toLong() and 0xFF) shl 8) or
    (b[off + 3].toLong() and 0xFF)

private fun mobiInt(b: ByteArray, off: Int) =
    ((b[off].toInt() and 0xFF) shl 24) or
    ((b[off + 1].toInt() and 0xFF) shl 16) or
    ((b[off + 2].toInt() and 0xFF) shl 8) or
    (b[off + 3].toInt() and 0xFF)
