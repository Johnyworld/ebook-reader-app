package com.rotein.ebookreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(book: BookFile, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onClose() }
    var showMenu by remember { mutableStateOf(false) }
    val onCenterTap = { showMenu = !showMenu }

    Box(modifier = modifier.fillMaxSize()) {
        when (book.extension.lowercase()) {
            "txt"  -> TxtViewer(book.path, onCenterTap)
            "epub" -> EpubViewer(book.path, onCenterTap)
            "pdf"  -> PdfViewer(book.path, onCenterTap)
            "mobi" -> MobiViewer(book.path, onCenterTap)
            else   -> CenteredMessage("지원하지 않는 형식입니다.")
        }

        // 투명 스크림 - 메뉴 외 영역 탭시 닫기
        if (showMenu) {
            Box(modifier = Modifier.fillMaxSize().clickable { showMenu = false })
        }

        // 바텀 시트 (스크림보다 위, 헤더보다 아래)
        if (showMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures {} }
            ) {
                HorizontalDivider(color = Color.Black)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 진행 상황
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("15 / 320 페이지", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "4% 읽음",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { 0.04f }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "1장. 시작하며",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = Color.Black)

                        ReaderMenuItem(Icons.Default.Search, "본문 검색")
                        ReaderMenuItem(Icons.Default.Star, "하이라이트")
                        ReaderMenuItem(Icons.Default.Edit, "메모")
                        ReaderMenuItem(Icons.Default.Bookmark, "북마크")
                        ReaderMenuItem(Icons.Default.Settings, "설정")

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        // 헤더 (최상위 레이어)
        if (showMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                        }
                        Text(
                            text = book.metadata?.title ?: book.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "북마크")
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
            }
        }
    }
}

@Composable
private fun ReaderMenuItem(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ─── TXT ─────────────────────────────────────────────────────────────────────

@Composable
private fun TxtViewer(path: String, onCenterTap: () -> Unit) {
    var text by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            text = withContext(Dispatchers.IO) { File(path).readText() }
        } catch (_: Exception) {
            error = true
        }
    }

    when {
        error -> CenteredMessage("파일을 읽을 수 없습니다.")
        text == null -> LoadingIndicator()
        else -> Text(
            text = text!!,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onCenterTap) {
                    detectTapGestures { offset ->
                        if (offset.x > size.width / 3f && offset.x < size.width * 2f / 3f) {
                            onCenterTap()
                        }
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}

// ─── EPUB (epub.js) ───────────────────────────────────────────────────────────
// 사전 준비: epub.min.js 를 app/src/main/assets/epub.min.js 에 배치해야 합니다.
// 다운로드: https://github.com/futurepress/epub.js/releases

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Composable
private fun EpubViewer(path: String, onCenterTap: () -> Unit) {
    val context = LocalContext.current
    var bookDir by remember(path) { mutableStateOf<String?>(null) }
    var opfPath by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            val result = withContext(Dispatchers.IO) { extractEpub(context, path) }
            if (result != null) { bookDir = result.first; opfPath = result.second }
            else error = true
        } catch (_: Exception) { error = true }
    }

    when {
        error -> CenteredMessage("EPUB 파일을 읽을 수 없습니다.")
        bookDir == null -> LoadingIndicator()
        else -> AndroidView(
            factory = { ctx ->
                // iframe 내 터치도 가로채기 위해 WebView 위에 투명 overlay View 를 배치
                FrameLayout(ctx).apply {
                    val webView = WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = true
                        settings.useWideViewPort = false
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = false
                        webViewClient = WebViewClient()
                    }
                    val overlay = android.view.View(ctx).apply {
                        setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                val x = event.x
                                val w = v.width.toFloat()
                                when {
                                    x < w / 3f  -> webView.evaluateJavascript("window._prev()", null)
                                    x > w * 2f / 3f -> webView.evaluateJavascript("window._next()", null)
                                    else -> onCenterTap()
                                }
                                v.performClick()
                            }
                            true
                        }
                    }
                    addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            update = { frameLayout ->
                if (frameLayout.tag != path) {
                    frameLayout.tag = path
                    val webView = frameLayout.getChildAt(0) as WebView
                    webView.loadDataWithBaseURL(
                        "file://${bookDir}/",
                        buildEpubJsHtml(opfPath!!),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** EPUB ZIP 을 캐시 디렉토리에 압축 해제하고 epub.js 를 복사한다. */
private fun extractEpub(context: Context, epubPath: String): Pair<String, String>? {
    val hash = epubPath.hashCode().toString()
    val outDir = File(context.cacheDir, "epub/$hash")
    val opfMarker = File(outDir, ".opf_path")

    if (outDir.exists() && opfMarker.exists() && File(outDir, "epub.min.js").exists()) {
        return Pair(outDir.absolutePath, opfMarker.readText())
    }

    outDir.mkdirs()

    ZipFile(epubPath).use { zip ->
        for (entry in zip.entries()) {
            if (entry.isDirectory) continue
            val outFile = File(outDir, entry.name).canonicalFile
            if (!outFile.path.startsWith(outDir.canonicalPath)) continue // 경로 탈출 방지
            outFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { it.copyTo(outFile.outputStream()) }
        }
    }

    // assets 에서 epub.min.js 복사
    context.assets.open("epub.min.js").use { it.copyTo(File(outDir, "epub.min.js").outputStream()) }

    val opfPath = findOpfPath(outDir) ?: return null
    opfMarker.writeText(opfPath)
    return Pair(outDir.absolutePath, opfPath)
}

private fun findOpfPath(dir: File): String? {
    val containerXml = File(dir, "META-INF/container.xml")
    if (!containerXml.exists()) return null
    val parser = Xml.newPullParser()
    parser.setInput(containerXml.inputStream(), null)
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG && parser.name == "rootfile")
            return parser.getAttributeValue(null, "full-path")
        ev = parser.next()
    }
    return null
}

private fun buildEpubJsHtml(opfPath: String) = """<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'/>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 100%; height: 100%; overflow: hidden; background: #fff; }
#viewer { width: 100%; height: 100%; }
</style>
</head>
<body>
<div id="viewer"></div>
<script src="epub.min.js"></script>
<script>
var book = ePub("$opfPath");
var rendition = book.renderTo("viewer", {
    width: window.innerWidth,
    height: window.innerHeight,
    spread: "none",
    flow: "paginated"
});
rendition.display();
window._prev = function() { rendition.prev(); };
window._next = function() { rendition.next(); };
</script>
</body>
</html>"""

// ─── PDF ─────────────────────────────────────────────────────────────────────

@Composable
private fun PdfViewer(path: String, onCenterTap: () -> Unit) {
    val renderer = remember(path) {
        try {
            val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd)
        } catch (_: Exception) { null }
    }

    DisposableEffect(path) {
        onDispose { renderer?.close() }
    }

    if (renderer == null) {
        CenteredMessage("PDF 파일을 읽을 수 없습니다.")
        return
    }

    val pageCount = renderer.pageCount

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pageCount) { index ->
                PdfPageItem(renderer = renderer, pageIndex = index)
                if (index < pageCount - 1) HorizontalDivider(thickness = 4.dp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onCenterTap) {
                    detectTapGestures { offset ->
                        if (offset.x > size.width / 3f && offset.x < size.width * 2f / 3f) {
                            onCenterTap()
                        }
                    }
                }
        )
    }
}

@Composable
private fun PdfPageItem(renderer: PdfRenderer, pageIndex: Int) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val scale = 2f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

// ─── MOBI ─────────────────────────────────────────────────────────────────────

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MobiViewer(path: String, onCenterTap: () -> Unit) {
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

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
