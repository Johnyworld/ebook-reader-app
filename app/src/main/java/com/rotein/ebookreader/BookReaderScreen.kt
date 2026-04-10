package com.rotein.ebookreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import android.os.SystemClock
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

data class TocItem(
    val label: String,
    val href: String,
    val depth: Int,
    val page: Int = 0,
    val subitems: List<TocItem> = emptyList()
)

data class SearchResultItem(
    val cfi: String,
    val excerpt: String,
    val chapter: String = "",
    val page: Int = 0,
    val spineIndex: Int = -1,
    val charPos: Int = -1
)

private fun parseSearchResults(json: JSONArray): List<SearchResultItem> {
    val items = mutableListOf<SearchResultItem>()
    for (i in 0 until json.length()) {
        val obj = json.getJSONObject(i)
        items.add(SearchResultItem(
            cfi = obj.optString("cfi", ""),
            excerpt = obj.optString("excerpt", ""),
            chapter = obj.optString("chapter", ""),
            page = obj.optInt("page", 0),
            spineIndex = obj.optInt("spineIndex", -1),
            charPos = obj.optInt("charPos", -1)
        ))
    }
    return items
}

private fun recalcSearchPages(
    results: List<SearchResultItem>,
    spinePageOffsets: Map<Int, Int>,
    charPageBreaksJson: String
): List<SearchResultItem> {
    if (results.isEmpty() || charPageBreaksJson.isEmpty()) return results
    val breaksMap = try {
        val obj = org.json.JSONObject(charPageBreaksJson)
        val map = mutableMapOf<Int, List<Int>>()
        obj.keys().forEach { key ->
            val arr = obj.getJSONArray(key)
            val list = mutableListOf<Int>()
            for (i in 0 until arr.length()) list.add(arr.getInt(i))
            map[key.toInt()] = list
        }
        map
    } catch (_: Exception) { return results }

    return results.map { r ->
        if (r.spineIndex < 0 || r.charPos < 0) return@map r
        val breaks = breaksMap[r.spineIndex]
        val baseOffset = spinePageOffsets[r.spineIndex] ?: 0
        val pageWithin = if (breaks != null && breaks.size > 1) {
            var pw = 0
            for (bi in breaks.indices.reversed()) {
                if (r.charPos >= breaks[bi]) { pw = bi; break }
            }
            pw
        } else 0
        r.copy(page = baseOffset + pageWithin + 1)
    }
}

private fun parseTocJson(json: JSONArray, depth: Int = 0): List<TocItem> {
    val items = mutableListOf<TocItem>()
    for (i in 0 until json.length()) {
        val obj = json.getJSONObject(i)
        val subitems = if (obj.has("subitems")) parseTocJson(obj.getJSONArray("subitems"), depth + 1) else emptyList()
        items.add(TocItem(
            label = obj.getString("label"),
            href = obj.getString("href"),
            depth = depth,
            page = if (obj.has("page")) obj.getInt("page") else 0,
            subitems = subitems
        ))
    }
    return items
}

private fun flattenToc(items: List<TocItem>): List<TocItem> {
    val result = mutableListOf<TocItem>()
    for (item in items) {
        result.add(item)
        result.addAll(flattenToc(item.subitems))
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(book: BookFile, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    val onCenterTap = { showMenu = !showMenu }

    val context = LocalContext.current
    val dao = remember { BookDatabase.getInstance(context).bookReadRecordDao() }
    val bookmarkDao = remember { BookDatabase.getInstance(context).bookmarkDao() }
    val highlightDao = remember { BookDatabase.getInstance(context).highlightDao() }
    val scope = rememberCoroutineScope()

    var readingProgress by remember(book.path) { mutableStateOf(0f) }
    var chapterTitle by remember(book.path) { mutableStateOf("") }
    var savedCfi by remember(book.path) { mutableStateOf<String?>(null) }
    var isLoading by remember(book.path) { mutableStateOf(book.extension.lowercase() == "epub") }
    var locationsReady by remember(book.path) { mutableStateOf(false) }
    var tocItems by remember(book.path) { mutableStateOf<List<TocItem>>(emptyList()) }
    var showTocPopup by remember { mutableStateOf(false) }
    var showSearchPopup by remember { mutableStateOf(false) }
    var showBookmarkPopup by remember { mutableStateOf(false) }
    var showHighlightPopup by remember { mutableStateOf(false) }
    data class HighlightActionState(val id: Long, val x: Float, val y: Float, val bottom: Float)
    var highlightActionState by remember { mutableStateOf<HighlightActionState?>(null) }
    val memoDao = remember { BookDatabase.getInstance(context).memoDao() }
    var memos by remember(book.path) { mutableStateOf<List<Memo>>(emptyList()) }
    var showMemoListPopup by remember { mutableStateOf(false) }
    var showMemoEditor by remember { mutableStateOf(false) }
    var editingMemo by remember { mutableStateOf<Memo?>(null) }
    var pendingMemoText by remember { mutableStateOf("") }
    var pendingMemoCfi by remember { mutableStateOf("") }
    data class MemoActionState(val id: Long, val x: Float, val y: Float, val bottom: Float)
    var memoActionState by remember { mutableStateOf<MemoActionState?>(null) }
    data class CombinedAnnotationState(val highlightId: Long, val memoId: Long, val x: Float, val y: Float, val bottom: Float)
    var combinedAnnotationState by remember { mutableStateOf<CombinedAnnotationState?>(null) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val epubWebView = remember { mutableStateOf<WebView?>(null) }
    var currentPage by remember(book.path) { mutableStateOf(0) }
    var totalPages by remember(book.path) { mutableStateOf(0) }
    var currentCfi by remember(book.path) { mutableStateOf("") }
    var isCurrentPageBookmarked by remember(book.path) { mutableStateOf(false) }
    var bookmarks by remember(book.path) { mutableStateOf<List<Bookmark>>(emptyList()) }
    var highlights by remember(book.path) { mutableStateOf<List<Highlight>>(emptyList()) }
    var isContentRendered by remember(book.path) { mutableStateOf(false) }
    var isScanning by remember(book.path) { mutableStateOf(false) }
    var spinePageOffsets by remember(book.path) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var cfiPageMap by remember(book.path) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var scanCacheValid by remember(book.path) { mutableStateOf(false) }
    var spineCharPageBreaksJson by remember(book.path) { mutableStateOf("") }
    var prevProgress by remember(book.path) { mutableStateOf(-1f) }
    var debugSpineIndex by remember(book.path) { mutableStateOf(-1) }
    var debugDisplayedPage by remember(book.path) { mutableStateOf(-1) }
    var debugDisplayedTotal by remember(book.path) { mutableStateOf(0) }
    var debugScrollX by remember(book.path) { mutableStateOf(0f) }
    var debugScrollWidth by remember(book.path) { mutableStateOf(0f) }
    var debugDeltaX by remember(book.path) { mutableStateOf(0f) }
    var showSettingsPopup by remember { mutableStateOf(false) }
    var showFontPopup by remember { mutableStateOf(false) }
    var readerSettings by remember { mutableStateOf(ReaderSettingsStore.load(context)) }
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        while (true) {
            currentTime = sdf.format(java.util.Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    val activity = LocalContext.current as? MainActivity
    DisposableEffect(epubWebView.value) {
        activity?.currentEpubWebView = epubWebView.value
        onDispose { activity?.currentEpubWebView = null }
    }

    BackHandler { onClose() }
    BackHandler(enabled = showMenu) { showMenu = false }
    BackHandler(enabled = showTocPopup) { showTocPopup = false }
    BackHandler(enabled = showSearchPopup) { showSearchPopup = false }
    BackHandler(enabled = showBookmarkPopup) { showBookmarkPopup = false }
    BackHandler(enabled = showHighlightPopup) { showHighlightPopup = false }
    BackHandler(enabled = highlightActionState != null) { highlightActionState = null }
    BackHandler(enabled = showMemoListPopup) { showMemoListPopup = false }
    BackHandler(enabled = showMemoEditor) { showMemoEditor = false }
    BackHandler(enabled = memoActionState != null) { memoActionState = null }
    BackHandler(enabled = combinedAnnotationState != null) { combinedAnnotationState = null }
    BackHandler(enabled = showSettingsPopup) { showSettingsPopup = false }
    BackHandler(enabled = showFontPopup) { showFontPopup = false }

    LaunchedEffect(readerSettings) {
        withContext(Dispatchers.IO) { ReaderSettingsStore.save(context, readerSettings) }
    }

    LaunchedEffect(showMenu) {
        if (showMenu) {
            epubWebView.value?.evaluateJavascript("window._currentCfi || ''") { result ->
                val cfi = result?.removeSurrounding("\"")?.trim().orEmpty()
                if (cfi.isNotEmpty()) currentCfi = cfi
            }
        }
    }

    LaunchedEffect(book.path) {
        val record = withContext(Dispatchers.IO) { dao.getByPath(book.path) }
        savedCfi = record?.lastCfi ?: ""
        val cachedToc = record?.tocJson.orEmpty()
        if (cachedToc.isNotEmpty()) {
            try {
                tocItems = parseTocJson(JSONArray(cachedToc))
                locationsReady = true
            } catch (_: Exception) {}
        }
        val currentFingerprint = readerSettings.layoutFingerprint()
        if (record != null && record.cachedSettingsFingerprint == currentFingerprint && record.cachedTotalPages > 0) {
            totalPages = record.cachedTotalPages
            try {
                val obj = org.json.JSONObject(record.cachedSpinePageOffsetsJson)
                val map = mutableMapOf<Int, Int>()
                obj.keys().forEach { key -> map[key.toInt()] = obj.getInt(key) }
                spinePageOffsets = map
            } catch (_: Exception) {}
            scanCacheValid = true
            spineCharPageBreaksJson = record.cachedSpineCharPageBreaksJson
        }
        bookmarks = withContext(Dispatchers.IO) { bookmarkDao.getByBook(book.path) }
        highlights = withContext(Dispatchers.IO) { highlightDao.getByBook(book.path) }
        memos = withContext(Dispatchers.IO) { memoDao.getByBook(book.path) }
    }

    LaunchedEffect(isContentRendered) {
        if (!isContentRendered) return@LaunchedEffect
        if (highlights.isNotEmpty()) {
            val json = highlights.joinToString(",", "[", "]") {
                """{"id":${it.id},"cfi":"${it.cfi.replace("\\", "\\\\").replace("\"", "\\\"")}"}"""
            }
            epubWebView.value?.evaluateJavascript("window._applyHighlights('$json')", null)
        }
        if (memos.isNotEmpty()) {
            val json = memos.joinToString(",", "[", "]") {
                """{"id":${it.id},"cfi":"${it.cfi.replace("\\", "\\\\").replace("\"", "\\\"")}"}"""
            }
            epubWebView.value?.evaluateJavascript("window._applyMemos('$json')", null)
        }
    }

    LaunchedEffect(currentCfi, bookmarks, isContentRendered) {
        if (!isContentRendered) return@LaunchedEffect
        val wv = epubWebView.value ?: return@LaunchedEffect
        if (currentCfi.isEmpty() || bookmarks.isEmpty()) {
            isCurrentPageBookmarked = false
            wv.evaluateJavascript("window._showBookmarkRibbon(false)", null)
            return@LaunchedEffect
        }
        val cfiListJson = bookmarks.map { it.cfi }.filter { it.isNotEmpty() }
            .joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
        wv.evaluateJavascript("window._isBookmarkedInRange('${cfiListJson.replace("'", "\\'")}')") { result ->
            val matched = result?.removeSurrounding("\"")?.trim() == "true"
            isCurrentPageBookmarked = matched
            wv.evaluateJavascript("window._showBookmarkRibbon($matched)", null)
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        when (book.extension.lowercase()) {
            "txt"  -> TxtViewer(book.path, onCenterTap)
            "epub" -> EpubViewer(
                path = book.path,
                savedCfi = savedCfi,
                onCenterTap = onCenterTap,
                onLocationUpdate = { progress, cfi, chapter ->
                    locationsReady = true
                    currentCfi = cfi
                    chapterTitle = chapter
                    if (prevProgress >= 0f && currentPage > 0 && totalPages > 0) {
                        if (progress > prevProgress) {
                            currentPage = (currentPage + 1).coerceAtMost(totalPages)
                        } else if (progress < prevProgress) {
                            currentPage = (currentPage - 1).coerceAtLeast(1)
                        }
                        readingProgress = currentPage.toFloat() / totalPages.toFloat()
                    }
                    prevProgress = progress
                    scope.launch(Dispatchers.IO) { dao.upsertCfi(book.path, cfi) }
                },
                onTocLoaded = { tocJson ->
                    if (!locationsReady) {
                        try { tocItems = parseTocJson(JSONArray(tocJson)) } catch (_: Exception) {}
                    }
                },
                onTocReady = { tocJson ->
                    try {
                        val newItems = parseTocJson(JSONArray(tocJson))
                        val hasPageData = flattenToc(newItems).any { it.page > 0 }
                        if (hasPageData || flattenToc(tocItems).none { it.page > 0 }) {
                            tocItems = newItems
                            locationsReady = true
                        }
                        if (hasPageData) {
                            scope.launch(Dispatchers.IO) { dao.upsertTocJson(book.path, tocJson) }
                        }
                    } catch (_: Exception) {}
                },
                onContentRendered = {
                    isLoading = false
                    isContentRendered = true
                    if (scanCacheValid && spinePageOffsets.isNotEmpty()) {
                        val jsonObj = org.json.JSONObject()
                        spinePageOffsets.forEach { (k, v) -> jsonObj.put(k.toString(), v) }
                        val charBreaksJs = if (spineCharPageBreaksJson.isNotEmpty()) "_spineCharPageBreaks=$spineCharPageBreaksJson;" else ""
                        val js = "_spinePageOffsets=$jsonObj;_totalVisualPages=$totalPages;$charBreaksJs" +
                            "if(_pendingLocation){reportLocation(_pendingLocation);_pendingLocation=null;}" +
                            "else{var l=rendition.currentLocation();if(l&&l.start)reportLocation(l);}"
                        epubWebView.value?.evaluateJavascript(js, null)
                    } else {
                        isScanning = true
                    }
                    val allCfis = (bookmarks.map { it.cfi } + highlights.map { it.cfi } + memos.map { it.cfi })
                        .filter { it.isNotEmpty() }.distinct()
                    if (allCfis.isNotEmpty()) {
                        val cfiArray = org.json.JSONArray(allCfis)
                        epubWebView.value?.evaluateJavascript("window._setCfiList('${cfiArray.toString().replace("'", "\\'")}')", null)
                    }
                },
                onHighlight = { text, cfi ->
                    if (cfi.isNotEmpty()) {
                        val chapterSnapshot = chapterTitle
                        if (currentPage > 0) cfiPageMap = cfiPageMap + (cfi to currentPage)
                        scope.launch {
                            val highlight = Highlight(
                                bookPath = book.path,
                                cfi = cfi,
                                text = text,
                                chapterTitle = chapterSnapshot,
                                page = currentPage,
                                createdAt = System.currentTimeMillis()
                            )
                            val id = withContext(Dispatchers.IO) { highlightDao.insert(highlight) }
                            val saved = highlight.copy(id = id)
                            highlights = highlights + saved
                            val escapedCfi = cfi.replace("\\", "\\\\").replace("\"", "\\\"")
                            epubWebView.value?.evaluateJavascript("window._addHighlight(\"$escapedCfi\", $id)", null)
                        }
                    }
                },
                onChapterChanged = { chapter -> chapterTitle = chapter },
                onMemo = { text, cfi ->
                    pendingMemoText = text
                    pendingMemoCfi = cfi
                    editingMemo = memos.find { it.cfi == cfi }
                    showMemoEditor = true
                },
                onHighlightLongPress = { id, x, y, bottom ->
                    val cfi = highlights.find { it.id == id }?.cfi
                    val overlappingMemo = if (cfi != null) memos.find { it.cfi == cfi } else null
                    if (overlappingMemo != null) {
                        combinedAnnotationState = CombinedAnnotationState(id, overlappingMemo.id, x, y, bottom)
                    } else {
                        highlightActionState = HighlightActionState(id, x, y, bottom)
                    }
                },
                onMemoLongPress = { id, x, y, bottom ->
                    val cfi = memos.find { it.id == id }?.cfi
                    val overlappingHighlight = if (cfi != null) highlights.find { it.cfi == cfi } else null
                    if (overlappingHighlight != null) {
                        combinedAnnotationState = CombinedAnnotationState(overlappingHighlight.id, id, x, y, bottom)
                    } else {
                        memoActionState = MemoActionState(id, x, y, bottom)
                    }
                },
                onWebViewCreated = { webView -> epubWebView.value = webView },
                onPageInfoChanged = { page, total ->
                    currentPage = page
                    if (total > 0) readingProgress = page.toFloat() / total.toFloat()
                },
                onDebugInfo = { spineIdx, dispPage, dispTotal, scrollX, scrollW, deltaX ->
                    debugSpineIndex = spineIdx
                    debugDisplayedPage = dispPage
                    debugDisplayedTotal = dispTotal
                    debugScrollX = scrollX
                    debugScrollWidth = scrollW
                    debugDeltaX = deltaX
                },
                onScanStart = { if (!scanCacheValid) isScanning = true },
                onScanComplete = { scannedTotal, spinePageOffsetsJson, cfiPageMapJson, charPageBreaksJson ->
                    if (scannedTotal != totalPages) {
                        totalPages = scannedTotal
                    }
                    isScanning = false
                    try {
                        val obj = org.json.JSONObject(spinePageOffsetsJson)
                        val map = mutableMapOf<Int, Int>()
                        obj.keys().forEach { key -> map[key.toInt()] = obj.getInt(key) }
                        spinePageOffsets = map
                        val fingerprint = readerSettings.layoutFingerprint()
                        scope.launch(Dispatchers.IO) {
                            dao.upsertPageScanCache(book.path, scannedTotal, spinePageOffsetsJson, charPageBreaksJson, fingerprint)
                        }
                    } catch (_: Exception) {}
                    spineCharPageBreaksJson = charPageBreaksJson
                    try {
                        val obj = org.json.JSONObject(cfiPageMapJson)
                        val map = mutableMapOf<String, Int>()
                        obj.keys().forEach { key -> map[key] = obj.getInt(key) }
                        cfiPageMap = map
                        val newSpineOffsets = spinePageOffsets
                        scope.launch(Dispatchers.IO) {
                            bookmarks = bookmarks.map { bm ->
                                val newPage = cfiToPage(bm.cfi, newSpineOffsets, map)
                                if (newPage > 0 && newPage != bm.page) {
                                    bookmarkDao.updatePage(bm.id, newPage)
                                    bm.copy(page = newPage)
                                } else bm
                            }
                            highlights = highlights.map { hl ->
                                val newPage = cfiToPage(hl.cfi, newSpineOffsets, map)
                                if (newPage > 0 && newPage != hl.page) {
                                    highlightDao.updatePage(hl.id, newPage)
                                    hl.copy(page = newPage)
                                } else hl
                            }
                            memos = memos.map { m ->
                                val newPage = cfiToPage(m.cfi, newSpineOffsets, map)
                                if (newPage > 0 && newPage != m.page) {
                                    memoDao.updatePage(m.id, newPage)
                                    m.copy(page = newPage)
                                } else m
                            }
                        }
                    } catch (_: Exception) {}
                    if (!searchResults.isNullOrEmpty()) {
                        searchResults = recalcSearchPages(searchResults!!, spinePageOffsets, charPageBreaksJson)
                    }
                },
                onSearchResultsPartial = { json ->
                    try {
                        val partial = parseSearchResults(JSONArray(json))
                        searchResults = (searchResults ?: emptyList()) + partial
                    } catch (_: Exception) {}
                },
                onSearchComplete = { isSearching = false },
                readerSettings = readerSettings
            )
            "pdf"  -> PdfViewer(book.path, onCenterTap)
            "mobi" -> MobiViewer(book.path, onCenterTap)
            else   -> CenteredMessage("지원하지 않는 형식입니다.")
        }

        // 디버그 정보 오버레이 (필요 시 주석 해제)
        // if (isContentRendered && debugSpineIndex >= 0) {
        //     Text(
        //         text = "si=$debugSpineIndex p=$debugDisplayedPage/$debugDisplayedTotal s=${"%.3f".format(debugScrollX)}/${"%.3f".format(debugScrollWidth)} d=${"%.3f".format(debugDeltaX)}",
        //         modifier = Modifier
        //             .align(Alignment.TopCenter)
        //             .padding(top = 4.dp)
        //             .background(Color.White),
        //         style = MaterialTheme.typography.bodySmall,
        //         color = Color.Black
        //     )
        // }

        // 북마크 리본은 WebView 내부 HTML로 렌더링 (글자 하위 레이어)

        // 하단 정보 오버레이
        if (!showMenu && isContentRendered) {
            val leftText = readerBottomInfoText(readerSettings.leftInfo, book, chapterTitle, currentPage, totalPages, readingProgress, currentTime)
            val rightText = readerBottomInfoText(readerSettings.rightInfo, book, chapterTitle, currentPage, totalPages, readingProgress, currentTime)
            if (leftText != null || rightText != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = maxOf(readerSettings.paddingHorizontal, 4).dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(leftText ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(rightText ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        // 로딩 오버레이
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.Black)
            }
        }

        // 투명 스크림 - 클릭 이벤트는 아래 레이어로 통과 (가운데 탭으로 닫기)
        if (showMenu) {
            Box(modifier = Modifier.fillMaxSize())
        }

        // 바텀 시트 (스크림보다 위, 헤더보다 아래)
        if (showMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                        .pointerInput(Unit) { detectTapGestures {} },
                    color = Color.White
                ) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("도서 정보를 읽고 있습니다.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 진행 상황
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isLoading) "도서 불러오는 중..." else "${(readingProgress * 100).toInt()}% 읽음",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (totalPages > 0) {
                                    Text(
                                        "$currentPage / $totalPages",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            if (!isLoading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { readingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Black,
                                trackColor = Color(0xFFCCCCCC),
                                strokeCap = StrokeCap.Square,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { showTocPopup = true }
                                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    chapterTitle.ifEmpty { "목차" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = Color.Black)

                        ReaderMenuItem(Icons.Default.Search, "본문 검색", onClick = {
                            showSearchPopup = true
                        })
                        HorizontalDivider(color = Color(0xFFCCCCCC))
                        ReaderMenuItem(Icons.Default.Star, "하이라이트", onClick = {
                            showHighlightPopup = true
                        })
                        HorizontalDivider(color = Color(0xFFCCCCCC))
                        ReaderMenuItem(Icons.Default.Edit, "메모", onClick = {
                            showMemoListPopup = true
                        })
                        HorizontalDivider(color = Color(0xFFCCCCCC))
                        ReaderMenuItem(Icons.Default.Bookmark, "북마크", onClick = {
                            showBookmarkPopup = true
                        })
                        HorizontalDivider(color = Color(0xFFCCCCCC))
                        ReaderMenuItem(Icons.Default.Settings, "설정", onClick = {
                            showSettingsPopup = true
                            showMenu = false
                        })

                        Spacer(Modifier.height(2.dp))
                    }
                    } // else
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
                        IconButton(onClick = {
                            if (currentCfi.isEmpty()) return@IconButton
                            if (isCurrentPageBookmarked) {
                                scope.launch {
                                    val wv = epubWebView.value
                                    if (wv != null) {
                                        val cfiListJson = bookmarks.map { it.cfi }.filter { it.isNotEmpty() }
                                            .joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
                                        wv.evaluateJavascript(
                                            "(function(){try{var cfis=${cfiListJson};var startCfi=window._currentCfi;var endCfi=window._currentEndCfi;var epubcfi=new ePub.CFI();var matched=[];for(var i=0;i<cfis.length;i++){var c=cfis[i];if(c===startCfi||(endCfi&&epubcfi.compare(c,startCfi)>=0&&epubcfi.compare(c,endCfi)<=0)){matched.push(c);}}return JSON.stringify(matched);}catch(e){return '[]';}})()"
                                        ) { result ->
                                            val matchedCfis = try {
                                                val arr = org.json.JSONArray(result?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "[]")
                                                (0 until arr.length()).map { arr.getString(it) }.toSet()
                                            } catch (_: Exception) { emptySet() }
                                            scope.launch {
                                                for (cfi in matchedCfis) {
                                                    withContext(Dispatchers.IO) { bookmarkDao.deleteByCfi(book.path, cfi) }
                                                }
                                                bookmarks = bookmarks.filter { it.cfi !in matchedCfis }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val wv = epubWebView.value
                                val saveBookmark = { cfi: String, excerpt: String ->
                                    if (cfi.isNotEmpty() && currentPage > 0) cfiPageMap = cfiPageMap + (cfi to currentPage)
                                    scope.launch {
                                        val bookmark = Bookmark(
                                            bookPath = book.path,
                                            cfi = cfi,
                                            chapterTitle = chapterTitle,
                                            excerpt = excerpt,
                                            page = currentPage,
                                            createdAt = System.currentTimeMillis()
                                        )
                                        withContext(Dispatchers.IO) { bookmarkDao.insert(bookmark) }
                                        bookmarks = bookmarks + bookmark
                                    }
                                }
                                if (wv != null) {
                                    wv.evaluateJavascript(
                                        "(function(){try{var loc=rendition.currentLocation();var cfi=(loc&&loc.start&&loc.start.cfi)?loc.start.cfi:'';var excerpt='';if(cfi){var r=rendition.getRange(cfi);if(r){var doc=r.startContainer.ownerDocument;var er=doc.createRange();er.setStart(r.startContainer,r.startOffset);er.setEnd(doc.body,doc.body.childNodes.length);excerpt=er.toString().trim().replace(/\\s+/g,' ').substring(0,150);}}if(!excerpt){var c=rendition.getContents();if(c&&c[0]){excerpt=(c[0].document.body.innerText||'').trim().replace(/\\s+/g,' ').substring(0,150);}}return JSON.stringify({cfi:cfi,excerpt:excerpt});}catch(e){return JSON.stringify({cfi:'',excerpt:''});}})()"
                                    ) { result ->
                                        try {
                                            val json = org.json.JSONObject(
                                                result?.removeSurrounding("\"")
                                                    ?.replace("\\\"", "\"")
                                                    ?.replace("\\\\", "\\")
                                                    ?: "{}"
                                            )
                                            val cfi = json.optString("cfi", "")
                                            val excerpt = json.optString("excerpt", "")
                                                .replace("\\n", " ").replace("\\r", "").trim()
                                            saveBookmark(cfi, excerpt)
                                        } catch (e: Exception) {
                                            saveBookmark("", "")
                                        }
                                    }
                                } else {
                                    saveBookmark("", "")
                                }
                            }
                        }) {
                            Icon(
                                if (isCurrentPageBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "북마크",
                                tint = Color.Black
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
            }
        }

        // 목차 팝업 (헤더보다 위, 최상위 레이어)
        if (showTocPopup) {
            TocPopup(
                tocItems = tocItems,
                bookTitle = book.metadata?.title ?: book.name,
                currentChapterTitle = chapterTitle,
                totalBookPages = totalPages,
                onNavigate = { href ->
                    epubWebView.value?.post {
                        epubWebView.value?.evaluateJavascript(
                            "window._displayHref(\"${href.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\")",
                            null
                        )
                    }
                    showTocPopup = false
                    showMenu = false
                },
                onDismiss = { showTocPopup = false }
            )
        }

        // 검색 팝업
        if (showSearchPopup) {
            SearchPopup(
                searchResults = searchResults,
                isSearching = isSearching,
                tocItems = tocItems,
                initialQuery = searchQuery,
                onSearch = { query ->
                    searchQuery = query
                    isSearching = true
                    searchResults = emptyList()
                    val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    epubWebView.value?.post {
                        epubWebView.value?.evaluateJavascript("window._setSearchHighlight(\"$escaped\")", null)
                        epubWebView.value?.evaluateJavascript("window._search(\"$escaped\")", null)
                    }
                },
                onNavigate = { cfi, page ->
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                    showSearchPopup = false
                    showMenu = false
                },
                onClear = {
                    searchQuery = ""
                    searchResults = null
                    isSearching = false
                    epubWebView.value?.post {
                        epubWebView.value?.evaluateJavascript("window._clearSearchHighlight()", null)
                    }
                },
                onDismiss = { showSearchPopup = false }
            )
        }

        // 하이라이트 팝업
        if (showHighlightPopup) {
            HighlightPopup(
                highlights = highlights,
                spinePageOffsets = spinePageOffsets,
                cfiPageMap = cfiPageMap,
                onNavigate = { cfi ->
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                    showHighlightPopup = false
                    showMenu = false
                },
                onDelete = { highlight ->
                    scope.launch {
                        withContext(Dispatchers.IO) { highlightDao.deleteById(highlight.id) }
                        highlights = highlights.filter { it.id != highlight.id }
                        epubWebView.value?.evaluateJavascript("window._removeHighlight(${highlight.id})", null)
                    }
                },
                onDismiss = { showHighlightPopup = false }
            )
        }

        highlightActionState?.let { state ->
            HighlightActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                onDismiss = { highlightActionState = null },
                onDelete = {
                    val id = state.id
                    highlightActionState = null
                    scope.launch {
                        withContext(Dispatchers.IO) { highlightDao.deleteById(id) }
                        epubWebView.value?.evaluateJavascript("window._removeHighlight($id)", null)
                        highlights = highlights.filter { it.id != id }
                    }
                },
                onMemo = {
                    val hl = highlights.find { it.id == state.id }
                    pendingMemoText = hl?.text ?: ""
                    pendingMemoCfi = hl?.cfi ?: ""
                    editingMemo = memos.find { it.cfi == pendingMemoCfi }
                    showMemoEditor = true
                    highlightActionState = null
                },
                onShare = {
                    val text = highlights.find { it.id == state.id }?.text ?: ""
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    highlightActionState = null
                }
            )
        }

        combinedAnnotationState?.let { state ->
            CombinedAnnotationPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                onDeleteHighlight = {
                    val hid = state.highlightId
                    combinedAnnotationState = null
                    scope.launch {
                        withContext(Dispatchers.IO) { highlightDao.deleteById(hid) }
                        epubWebView.value?.evaluateJavascript("window._removeHighlight($hid)", null)
                        highlights = highlights.filter { it.id != hid }
                    }
                },
                onEditMemo = {
                    val memo = memos.find { it.id == state.memoId }
                    combinedAnnotationState = null
                    if (memo != null) {
                        editingMemo = memo
                        pendingMemoText = memo.text
                        pendingMemoCfi = memo.cfi
                        showMemoEditor = true
                    }
                },
                onDeleteMemo = {
                    val mid = state.memoId
                    combinedAnnotationState = null
                    scope.launch {
                        withContext(Dispatchers.IO) { memoDao.deleteById(mid) }
                        epubWebView.value?.evaluateJavascript("window._removeMemo($mid)", null)
                        memos = memos.filter { it.id != mid }
                    }
                },
                onShare = {
                    val hl = highlights.find { it.id == state.highlightId }
                    val memo = memos.find { it.id == state.memoId }
                    val text = buildString {
                        hl?.text?.let { append(it) }
                        memo?.note?.takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    combinedAnnotationState = null
                },
                onDismiss = { combinedAnnotationState = null }
            )
        }

        memoActionState?.let { state ->
            val memo = memos.find { it.id == state.id }
            MemoActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                onHighlight = {
                    memoActionState = null
                    if (memo != null) {
                        val chapterSnapshot = chapterTitle
                        scope.launch {
                            val highlight = Highlight(
                                bookPath = book.path,
                                cfi = memo.cfi,
                                text = memo.text,
                                chapterTitle = chapterSnapshot,
                                page = memo.page.takeIf { it > 0 } ?: cfiToPage(memo.cfi, spinePageOffsets, cfiPageMap),
                                createdAt = System.currentTimeMillis()
                            )
                            val id = withContext(Dispatchers.IO) { highlightDao.insert(highlight) }
                            val saved = highlight.copy(id = id)
                            highlights = highlights + saved
                            val escapedCfi = memo.cfi.replace("\\", "\\\\").replace("\"", "\\\"")
                            epubWebView.value?.evaluateJavascript("window._addHighlight(\"$escapedCfi\", $id)", null)
                        }
                    }
                },
                onEdit = {
                    memoActionState = null
                    if (memo != null) {
                        editingMemo = memo
                        pendingMemoText = memo.text
                        pendingMemoCfi = memo.cfi
                        showMemoEditor = true
                    }
                },
                onDelete = {
                    val id = state.id
                    memoActionState = null
                    scope.launch {
                        withContext(Dispatchers.IO) { memoDao.deleteById(id) }
                        epubWebView.value?.evaluateJavascript("window._removeMemo($id)", null)
                        memos = memos.filter { it.id != id }
                    }
                },
                onShare = {
                    val text = memo?.let { "${it.text}\n\n${it.note}" } ?: ""
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    memoActionState = null
                },
                onDismiss = { memoActionState = null }
            )
        }

        if (showMemoListPopup) {
            MemoListPopup(
                memos = memos,
                spinePageOffsets = spinePageOffsets,
                cfiPageMap = cfiPageMap,
                onNavigate = { memo ->
                    epubWebView.value?.post {
                        val escaped = memo.cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                    showMemoListPopup = false
                    showMenu = false
                },
                onEdit = { memo ->
                    editingMemo = memo
                    pendingMemoText = memo.text
                    pendingMemoCfi = memo.cfi
                    showMemoEditor = true
                },
                onDelete = { memo ->
                    scope.launch {
                        withContext(Dispatchers.IO) { memoDao.deleteById(memo.id) }
                        epubWebView.value?.evaluateJavascript("window._removeMemo(${memo.id})", null)
                        memos = memos.filter { it.id != memo.id }
                    }
                },
                onDismiss = { showMemoListPopup = false }
            )
        }

        if (showMemoEditor) {
            MemoEditorScreen(
                selectedText = editingMemo?.text ?: pendingMemoText,
                initialNote = editingMemo?.note ?: "",
                onSave = { note ->
                    val existing = editingMemo
                    if (existing != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) { memoDao.updateNote(existing.id, note) }
                            memos = memos.map { if (it.id == existing.id) it.copy(note = note) else it }
                            showMemoEditor = false
                            editingMemo = null
                        }
                    } else if (pendingMemoCfi.isNotEmpty()) {
                        val chapterSnapshot = chapterTitle
                        if (currentPage > 0) cfiPageMap = cfiPageMap + (pendingMemoCfi to currentPage)
                        scope.launch {
                            val newMemo = Memo(
                                bookPath = book.path,
                                cfi = pendingMemoCfi,
                                text = pendingMemoText,
                                note = note,
                                chapterTitle = chapterSnapshot,
                                page = currentPage,
                                createdAt = System.currentTimeMillis()
                            )
                            val id = withContext(Dispatchers.IO) { memoDao.insert(newMemo) }
                            val saved = newMemo.copy(id = id)
                            memos = memos + saved
                            val escapedCfi = pendingMemoCfi.replace("\\", "\\\\").replace("\"", "\\\"")
                            epubWebView.value?.evaluateJavascript("window._addMemo(\"$escapedCfi\", $id)", null)
                            showMemoEditor = false
                        }
                    } else {
                        showMemoEditor = false
                    }
                },
                onCancel = { showMemoEditor = false; editingMemo = null },
                onDelete = if (editingMemo != null) ({
                    val id = editingMemo!!.id
                    showMemoEditor = false
                    editingMemo = null
                    scope.launch {
                        withContext(Dispatchers.IO) { memoDao.deleteById(id) }
                        epubWebView.value?.evaluateJavascript("window._removeMemo($id)", null)
                        memos = memos.filter { it.id != id }
                    }
                }) else null,
                onNavigate = if (editingMemo != null) ({
                    val cfi = editingMemo!!.cfi
                    showMemoEditor = false
                    showMenu = false
                    editingMemo = null
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                }) else null
            )
        }

        // 설정 바텀시트
        if (showSettingsPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { showSettingsPopup = false } }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                        .pointerInput(Unit) { detectTapGestures {} },
                    color = Color.White
                ) {
                    ReaderSettingsBottomSheet(
                        settings = readerSettings,
                        onSettingsChange = { readerSettings = it },
                        onDismiss = { showSettingsPopup = false },
                        onOpenFontPopup = { showFontPopup = true }
                    )
                }
            }
        }

        // 글꼴 팝업
        if (showFontPopup) {
            FontLayerPopup(
                currentFontName = readerSettings.fontName,
                onSelect = { readerSettings = readerSettings.copy(fontName = it); showFontPopup = false },
                onFontChanged = { readerSettings = readerSettings.copy(fontName = it) },
                onFontImported = {},
                onDismiss = { showFontPopup = false }
            )
        }

        // 북마크 팝업
        if (showBookmarkPopup) {
            BookmarkPopup(
                bookmarks = bookmarks,
                spinePageOffsets = spinePageOffsets,
                cfiPageMap = cfiPageMap,
                onNavigate = { cfi ->
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                    showBookmarkPopup = false
                    showMenu = false
                },
                onDelete = { bookmark ->
                    scope.launch {
                        withContext(Dispatchers.IO) { bookmarkDao.deleteByCfi(bookmark.bookPath, bookmark.cfi) }
                        bookmarks = bookmarks.filter { it.id != bookmark.id }
                    }
                },
                onDismiss = { showBookmarkPopup = false }
            )
        }
    }
}

@Composable
private fun ReaderMenuItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
}

@Composable
private fun TocPopup(
    tocItems: List<TocItem>,
    bookTitle: String,
    currentChapterTitle: String,
    totalBookPages: Int,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val flatItems = remember(tocItems) { flattenToc(tocItems) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val density = LocalDensity.current
    val itemHeightDp = 49 // top(12) + bottom(12) padding + bodyLarge line height
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp().value.toInt() }
    val itemsPerPage = maxOf(1, (screenHeightDp - 56 - bottomBarHeightDp) / itemHeightDp)
    val totalPages = maxOf(1, (flatItems.size + itemsPerPage - 1) / itemsPerPage)
    var currentPage by remember { mutableStateOf(0) }
    val pageItems = flatItems.drop(currentPage * itemsPerPage).take(itemsPerPage)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text(
                    "목차: $bookTitle",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = Color.Black)
            Column(modifier = Modifier.weight(1f)) {
                if (flatItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("목차를 불러오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    pageItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(item.href) }
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 12.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.width(40.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (item.page > 0) {
                                    Text(
                                        "${item.page}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFBBBBBB)
                                    )
                                }
                            }
                            Text(
                                item.label,
                                style = if (item.depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                color = if (item.label == currentChapterTitle) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = (item.depth * 16).dp)
                            )
                        }
                        HorizontalDivider(color = Color(0xFFCCCCCC))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .onSizeChanged { bottomBarHeightPx = it.height },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable(enabled = currentPage > 0) { currentPage-- }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "이전",
                        modifier = Modifier.height(16.dp),
                        tint = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC)
                    )
                    Text(
                        "이전",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC)
                    )
                }
                Text(
                    "${currentPage + 1}/$totalPages (${flatItems.size}건)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .clickable(enabled = currentPage < totalPages - 1) { currentPage++ }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "다음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "다음",
                        modifier = Modifier.height(16.dp),
                        tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPopup(
    searchResults: List<SearchResultItem>?,
    isSearching: Boolean,
    tocItems: List<TocItem>,
    initialQuery: String,
    onSearch: (String) -> Unit,
    onNavigate: (cfi: String, page: Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val tocPageMap = remember(tocItems) {
        flattenToc(tocItems).filter { it.page > 0 }.associate { it.label to it.page }
    }
    val sortedResults = remember(searchResults, tocPageMap) {
        searchResults?.sortedBy { r ->
            if (r.page > 0) r.page else (tocPageMap[r.chapter] ?: Int.MAX_VALUE)
        }
    }
    var query by remember { mutableStateOf(initialQuery) }
    var searchedQuery by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    var currentPage by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 80
    val headerHeightDp = 56
    val paginationHeightDp = 56
    val searchBarHeightDp = 45
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp - searchBarHeightDp) / itemHeightDp)
    val resultList = sortedResults ?: emptyList()
    val totalPages = maxOf(1, (resultList.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = resultList.drop(currentPage * itemsPerPage).take(itemsPerPage)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(searchedQuery) { currentPage = 0 }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text(
                    "본문 검색",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = Color.Black)

            Box(modifier = Modifier.weight(1f)) {
                when {
                    searchResults == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("검색어를 입력하세요.", style = MaterialTheme.typography.bodyMedium)
                    }
                    isSearching && searchResults.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.Black)
                    }
                    searchResults.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("검색 결과가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, result ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFCCCCCC))
                            val effectivePage = if (result.page > 0) result.page else (tocPageMap[result.chapter] ?: 0)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clickable { onNavigate(result.cfi, effectivePage) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                if (result.chapter.isNotEmpty() || effectivePage > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            result.chapter,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFBBBBBB),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (result.page > 0) {
                                            Text(
                                                "p.$effectivePage",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFBBBBBB)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = buildAnnotatedString {
                                        val excerpt = result.excerpt.trim()
                                        val lowerExcerpt = excerpt.lowercase()
                                        val lowerQuery = searchedQuery.lowercase()
                                        var cursor = 0
                                        if (lowerQuery.isNotEmpty()) {
                                            var idx = lowerExcerpt.indexOf(lowerQuery)
                                            while (idx != -1) {
                                                append(excerpt.substring(cursor, idx))
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                    append(excerpt.substring(idx, idx + lowerQuery.length))
                                                }
                                                cursor = idx + lowerQuery.length
                                                idx = lowerExcerpt.indexOf(lowerQuery, cursor)
                                            }
                                        }
                                        append(excerpt.substring(cursor))
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Column {
                if (!searchResults.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable(enabled = currentPage > 0) { currentPage-- }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "이전",
                                modifier = Modifier.height(16.dp),
                                tint = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC)
                            )
                            Text(
                                "이전",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC)
                            )
                        }
                        Text(
                            "${currentPage + 1}/$totalPages (${resultList.size}건)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier
                                .clickable(enabled = currentPage < totalPages - 1) { currentPage++ }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "다음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "다음",
                                modifier = Modifier.height(16.dp),
                                tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.length >= 2) { val q = query.trim().replace(Regex("\\s+"), " "); searchedQuery = q; onSearch(q) }
                        }),
                        decorationBox = { innerTextField ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        "검색어를 두 글자 이상 입력하세요.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = ""; searchedQuery = ""; onClear() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "검색어 지우기",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                            .clickable { if (query.length >= 2) { val q = query.trim().replace(Regex("\\s+"), " "); searchedQuery = q; onSearch(q) } }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("검색", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkPopup(
    bookmarks: List<Bookmark>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (cfi: String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 88
    val headerHeightDp = 56
    val paginationHeightDp = 56
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(BookmarkSortStore.load(context)) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val sortedBookmarks = remember(bookmarks, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.CREATED_ASC -> bookmarks.sortedBy { it.createdAt }
            BookmarkSortOrder.CREATED_DESC -> bookmarks.sortedByDescending { it.createdAt }
            BookmarkSortOrder.PAGE_ASC -> bookmarks.sortedBy { it.page.takeIf { p -> p > 0 } ?: cfiToPage(it.cfi, spinePageOffsets, cfiPageMap) }
        }
    }
    val totalPages = maxOf(1, (sortedBookmarks.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = sortedBookmarks.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(sortOrder) { BookmarkSortStore.save(context, sortOrder) }
    LaunchedEffect(sortedBookmarks.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text(
                    "북마크",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.onGloballyPositioned { anchorHeight = it.size.height }) {
                    TextButton(onClick = { dropdownExpanded = true }) {
                        Text(
                            text = sortOrder.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (dropdownExpanded) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, anchorHeight),
                            onDismissRequest = { dropdownExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(IntrinsicSize.Max)
                                    .background(Color.White)
                                    .border(1.dp, Color.Black)
                            ) {
                                BookmarkSortOrder.entries.forEachIndexed { index, order ->
                                    val isSelected = sortOrder == order
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                sortOrder = order
                                                dropdownExpanded = false
                                            }
                                            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = order.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Black,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (index < BookmarkSortOrder.entries.lastIndex) {
                                        HorizontalDivider(color = Color(0xFFE0E0E0))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.Black)

            Box(modifier = Modifier.weight(1f)) {
                if (bookmarks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("북마크가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, bookmark ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFCCCCCC))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(88.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { onNavigate(bookmark.cfi) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val bookmarkPage = bookmark.page.takeIf { it > 0 } ?: cfiToPage(bookmark.cfi, spinePageOffsets, cfiPageMap)
                                        if (bookmarkPage > 0) {
                                            Text(
                                                "p.$bookmarkPage",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.Black
                                            )
                                        }
                                        Text(
                                            bookmark.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFBBBBBB),
                                            modifier = Modifier.weight(1f).padding(start = if (bookmarkPage > 0) 8.dp else 0.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                    Text(
                                        bookmark.excerpt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        dateFormat.format(Date(bookmark.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFBBBBBB)
                                    )
                                }
                                IconButton(onClick = { onDelete(bookmark) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column {
                if (sortedBookmarks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable(enabled = currentPage > 0) { currentPage-- }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "이전",
                                modifier = Modifier.height(16.dp),
                                tint = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC)
                            )
                            Text(
                                "이전",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC)
                            )
                        }
                        Text(
                            "${currentPage + 1}/$totalPages (${sortedBookmarks.size}건)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier
                                .clickable(enabled = currentPage < totalPages - 1) { currentPage++ }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "다음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "다음",
                                modifier = Modifier.height(16.dp),
                                tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
            }
        }
    }
}

@Composable
private fun HighlightPopup(
    highlights: List<Highlight>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (cfi: String) -> Unit,
    onDelete: (Highlight) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 88
    val headerHeightDp = 56
    val paginationHeightDp = 56
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(HighlightSortStore.load(context)) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val sortedHighlights = remember(highlights, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.CREATED_ASC -> highlights.sortedBy { it.createdAt }
            BookmarkSortOrder.CREATED_DESC -> highlights.sortedByDescending { it.createdAt }
            BookmarkSortOrder.PAGE_ASC -> highlights.sortedBy { it.page.takeIf { p -> p > 0 } ?: cfiToPage(it.cfi, spinePageOffsets, cfiPageMap) }
        }
    }
    val totalPages = maxOf(1, (sortedHighlights.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = sortedHighlights.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(sortOrder) { HighlightSortStore.save(context, sortOrder) }
    LaunchedEffect(sortedHighlights.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text("하이라이트", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.onGloballyPositioned { anchorHeight = it.size.height }) {
                    TextButton(onClick = { dropdownExpanded = true }) {
                        Text(sortOrder.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (dropdownExpanded) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, anchorHeight),
                            onDismissRequest = { dropdownExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(modifier = Modifier.width(IntrinsicSize.Max).background(Color.White).border(1.dp, Color.Black)) {
                                BookmarkSortOrder.entries.forEachIndexed { index, order ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { sortOrder = order; dropdownExpanded = false }
                                            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                                    ) {
                                        Text(order.label, style = MaterialTheme.typography.bodyMedium, color = Color.Black, modifier = Modifier.weight(1f))
                                        if (sortOrder == order) {
                                            Spacer(Modifier.width(16.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (index < BookmarkSortOrder.entries.lastIndex) HorizontalDivider(color = Color(0xFFE0E0E0))
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.Black)

            Box(modifier = Modifier.weight(1f)) {
                if (highlights.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("하이라이트가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, highlight ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFCCCCCC))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(88.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f).fillMaxHeight()
                                        .clickable { onNavigate(highlight.cfi) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val highlightPage = highlight.page.takeIf { it > 0 } ?: cfiToPage(highlight.cfi, spinePageOffsets, cfiPageMap)
                                        if (highlightPage > 0) {
                                            Text("p.$highlightPage", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                                        }
                                        Text(
                                            highlight.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFBBBBBB),
                                            modifier = Modifier.weight(1f).padding(start = if (highlightPage > 0) 8.dp else 0.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                                        )
                                    }
                                    Text(
                                        highlight.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(dateFormat.format(Date(highlight.createdAt)), style = MaterialTheme.typography.labelSmall, color = Color(0xFFBBBBBB))
                                }
                                IconButton(onClick = { onDelete(highlight) }) {
                                    Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Column {
                if (sortedHighlights.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable(enabled = currentPage > 0) { currentPage-- }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전", modifier = Modifier.height(16.dp), tint = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC))
                            Text("이전", style = MaterialTheme.typography.bodyMedium, color = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC))
                        }
                        Text("${currentPage + 1}/$totalPages (${sortedHighlights.size}건)", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.clickable(enabled = currentPage < totalPages - 1) { currentPage++ }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("다음", style = MaterialTheme.typography.bodyMedium, color = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음", modifier = Modifier.height(16.dp), tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC))
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
            }
        }
    }
}

@Composable
private fun MemoEditorScreen(
    selectedText: String,
    initialNote: String,
    onSave: (note: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null
) {
    var note by remember { mutableStateOf(initialNote) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "취소")
                }
                Text("메모", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSave(note) }) {
                    Text("저장", color = Color.Black)
                }
            }
            HorizontalDivider(color = Color.Black)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedText.isNotEmpty()) {
                    Text(
                        selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    )
                }
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) {
                                Text("메모를 입력하세요.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFAAAAAA))
                            }
                            inner()
                        }
                    }
                )
            }
            if (onDelete != null || onNavigate != null) {
                HorizontalDivider(color = Color.Black)
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Text("삭제", color = Color.Black)
                        }
                    }
                    if (onDelete != null && onNavigate != null) {
                        Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
                    }
                    if (onNavigate != null) {
                        TextButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                            Text("페이지로 이동", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoListPopup(
    memos: List<Memo>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (Memo) -> Unit,
    onEdit: (Memo) -> Unit,
    onDelete: (Memo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 112
    val headerHeightDp = 56
    val paginationHeightDp = 56
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(MemoSortStore.load(context)) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val sortedMemos = remember(memos, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.CREATED_ASC -> memos.sortedBy { it.createdAt }
            BookmarkSortOrder.CREATED_DESC -> memos.sortedByDescending { it.createdAt }
            BookmarkSortOrder.PAGE_ASC -> memos.sortedBy { it.page.takeIf { p -> p > 0 } ?: cfiToPage(it.cfi, spinePageOffsets, cfiPageMap) }
        }
    }
    val totalPages = maxOf(1, (sortedMemos.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = sortedMemos.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault()) }

    LaunchedEffect(sortOrder) { MemoSortStore.save(context, sortOrder) }
    LaunchedEffect(sortedMemos.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text("메모", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.onGloballyPositioned { anchorHeight = it.size.height }) {
                    TextButton(onClick = { dropdownExpanded = true }) {
                        Text(sortOrder.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (dropdownExpanded) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, anchorHeight),
                            onDismissRequest = { dropdownExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(modifier = Modifier.width(IntrinsicSize.Max).background(Color.White).border(1.dp, Color.Black)) {
                                BookmarkSortOrder.entries.forEachIndexed { index, order ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { sortOrder = order; dropdownExpanded = false }
                                            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                                    ) {
                                        Text(order.label, style = MaterialTheme.typography.bodyMedium, color = Color.Black, modifier = Modifier.weight(1f))
                                        if (sortOrder == order) {
                                            Spacer(Modifier.width(16.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (index < BookmarkSortOrder.entries.lastIndex) HorizontalDivider(color = Color(0xFFE0E0E0))
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.Black)

            Box(modifier = Modifier.weight(1f)) {
                if (memos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("메모가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, memo ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFCCCCCC))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(112.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f).fillMaxHeight()
                                        .clickable { onNavigate(memo); onEdit(memo) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val memoPage = memo.page.takeIf { it > 0 } ?: cfiToPage(memo.cfi, spinePageOffsets, cfiPageMap)
                                        if (memoPage > 0) {
                                            Text("p.$memoPage", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                                        }
                                        Text(
                                            memo.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFBBBBBB),
                                            modifier = Modifier.weight(1f).padding(start = if (memoPage > 0) 8.dp else 0.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                                        )
                                    }
                                    Text(
                                        memo.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    if (memo.note.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Icon(Icons.Default.ModeComment, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF888888))
                                            Text(memo.note, style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text(dateFormat.format(Date(memo.createdAt)), style = MaterialTheme.typography.labelSmall, color = Color(0xFFBBBBBB))
                                }
                                IconButton(onClick = { onDelete(memo) }) {
                                    Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Column {
                if (sortedMemos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable(enabled = currentPage > 0) { currentPage-- }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전", modifier = Modifier.height(16.dp), tint = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC))
                            Text("이전", style = MaterialTheme.typography.bodyMedium, color = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC))
                        }
                        Text("${currentPage + 1}/$totalPages (${sortedMemos.size}건)", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.clickable(enabled = currentPage < totalPages - 1) { currentPage++ }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("다음", style = MaterialTheme.typography.bodyMedium, color = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음", modifier = Modifier.height(16.dp), tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC))
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
            }
        }
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
private fun EpubViewer(
    path: String,
    savedCfi: String?,
    onCenterTap: () -> Unit,
    onLocationUpdate: (progress: Float, cfi: String, chapterTitle: String) -> Unit = { _, _, _ -> },
    onTocLoaded: (tocJson: String) -> Unit = {},
    onContentRendered: () -> Unit = {},
    onChapterChanged: (chapter: String) -> Unit = {},
    onTocReady: (tocJson: String) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {},
    onPageInfoChanged: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    onDebugInfo: (spineIndex: Int, displayedPage: Int, displayedTotal: Int, scrollX: Float, scrollWidth: Float, deltaX: Float) -> Unit = { _, _, _, _, _, _ -> },
    onScanStart: () -> Unit = {},
    onScanComplete: (totalPages: Int, spinePageOffsetsJson: String, cfiPageMapJson: String, spineCharPageBreaksJson: String) -> Unit = { _, _, _, _ -> },
    onSearchResultsPartial: (resultsJson: String) -> Unit = {},
    onSearchComplete: () -> Unit = {},
    onTextSelected: (text: String, x: Float, y: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
    onHighlight: (text: String, cfi: String) -> Unit = { _, _ -> },
    onMemo: (text: String, cfi: String) -> Unit = { _, _ -> },
    onHighlightLongPress: (id: Long, x: Float, y: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
    onMemoLongPress: (id: Long, x: Float, y: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
    readerSettings: ReaderSettings = ReaderSettings()
) {
    val context = LocalContext.current
    var bookDir by remember(path) { mutableStateOf<String?>(null) }
    var opfPath by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf(false) }
    data class SelectionState(val text: String, val x: Float, val y: Float, val bottom: Float, val cfi: String = "", val isAtPageEnd: Boolean = false)
    var selectionState by remember { mutableStateOf<SelectionState?>(null) }
    val selectionActive = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val webViewRef = remember { java.util.concurrent.atomic.AtomicReference<android.webkit.WebView?>(null) }
    val onHighlightLongPressRef = remember { java.util.concurrent.atomic.AtomicReference<((Long, Float, Float, Float) -> Unit)?>(null) }
    onHighlightLongPressRef.set(onHighlightLongPress)
    val onMemoLongPressRef = remember { java.util.concurrent.atomic.AtomicReference<((Long, Float, Float, Float) -> Unit)?>(null) }
    onMemoLongPressRef.set(onMemoLongPress)
    val pageFlipRef = remember { java.util.concurrent.atomic.AtomicReference(readerSettings.pageFlip) }
    pageFlipRef.set(readerSettings.pageFlip)
    val clearSelection: () -> Unit = {
        selectionActive.set(false)
        selectionState = null
        webViewRef.get()?.evaluateJavascript("window._clearSelection()", null)
    }
    val selectionOnTextSelected: (String, Float, Float, Float) -> Unit = { text, x, y, bottom ->
        onTextSelected(text, x, y, bottom)
        if (text.isNotEmpty()) {
            selectionActive.set(true)
        } else {
            selectionActive.set(false)
            selectionState = null
        }
    }
    val selectionOnSelectionTapped: (String, Float, Float, Float, String, Boolean) -> Unit = { text, x, y, bottom, cfi, isAtPageEnd ->
        if (text.isNotEmpty()) selectionState = SelectionState(text, x, y, bottom, cfi, isAtPageEnd)
    }

    LaunchedEffect(readerSettings) {
        val fontFamily = fontFamilyForJs(readerSettings.fontName)
        val fontFilePath = ImportedFontStore.load(context).find { it.name == readerSettings.fontName }?.filePath ?: ""
        val js = """window._applyReaderSettings(
            "$fontFamily",
            "$fontFilePath",
            ${readerSettings.fontSize},
            "${readerSettings.textAlign.name.lowercase()}",
            ${readerSettings.lineHeight},
            ${readerSettings.paragraphSpacing},
            ${readerSettings.paddingVertical},
            ${readerSettings.paddingHorizontal},
            ${readerSettings.dualPage}
        )"""
        webViewRef.get()?.evaluateJavascript(js, null)
    }


    LaunchedEffect(path) {
        try {
            val result = withContext(Dispatchers.IO) { extractEpub(context, path) }
            if (result != null) { bookDir = result.first; opfPath = result.second }
            else error = true
        } catch (_: Exception) { error = true }
    }

    Box(Modifier.fillMaxSize()) {
    when {
        error -> CenteredMessage("EPUB 파일을 읽을 수 없습니다.")
        bookDir == null || savedCfi == null -> LoadingIndicator()
        else -> AndroidView(
            factory = { ctx ->
                // iframe 내 터치도 가로채기 위해 WebView 위에 투명 overlay View 를 배치
                FrameLayout(ctx).apply {
                    val emptyActionModeCallback = object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            mode.hide(Long.MAX_VALUE) // 시각적으로 숨기되 ActionMode는 살아있음 → 선택 유지
                            return true
                        }
                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
                        override fun onDestroyActionMode(mode: ActionMode) {}
                    }
                    val webView = object : WebView(ctx) {
                        override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
                            super.startActionMode(emptyActionModeCallback)
                        override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
                            super.startActionMode(emptyActionModeCallback, type)
                        override fun startActionModeForChild(originalView: View?, callback: ActionMode.Callback?, type: Int): ActionMode? =
                            super.startActionModeForChild(originalView, emptyActionModeCallback, type)
                    }.apply {
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
                        addJavascriptInterface(EpubBridge(onLocationUpdate, onTocLoaded, onContentRendered, onChapterChanged, onTocReady, onPageInfoChanged, onDebugInfo, onScanStart, onScanComplete, onSearchResultsPartial, onSearchComplete, selectionOnTextSelected, selectionOnSelectionTapped), "Android")
                    }
                    webViewRef.set(webView)
                    onWebViewCreated(webView)
                    val overlay = android.view.View(ctx).apply {
                        var isLongPress = false
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                if (selectionActive.get()) {
                                    val density = ctx.resources.displayMetrics.density
                                    val cssX = e.x / density
                                    val cssY = e.y / density
                                    webView.evaluateJavascript("""
                                        (function() {
                                            try {
                                                var iframe = document.querySelector('iframe');
                                                if (!iframe || !iframe.contentDocument) return;
                                                var doc = iframe.contentDocument;
                                                var sel = doc.getSelection();
                                                if (!sel || sel.toString().trim().length === 0) return;
                                                var range = sel.getRangeAt(0);
                                                var iframeRect = iframe.getBoundingClientRect();
                                                var docX = $cssX - iframeRect.left;
                                                var docY = $cssY - iframeRect.top;
                                                var rects = range.getClientRects();
                                                for (var i = 0; i < rects.length; i++) {
                                                    var r = rects[i];
                                                    if (docX >= r.left && docX <= r.right && docY >= r.top && docY <= r.bottom) {
                                                        var boundRect = range.getBoundingClientRect();
                                                        var cfi = typeof window._getCfiFromSelection === 'function' ? (window._getCfiFromSelection() || '') : '';
                                                        var isAtPageEnd = typeof window._isSelectionAtPageEnd === 'function' ? window._isSelectionAtPageEnd() : false;
                                                        Android.onSelectionTapped(
                                                            sel.toString().trim(),
                                                            boundRect.left + boundRect.width/2 + iframeRect.left,
                                                            boundRect.top + iframeRect.top,
                                                            boundRect.bottom + iframeRect.top,
                                                            cfi,
                                                            isAtPageEnd
                                                        );
                                                        return;
                                                    }
                                                }
                                                sel.removeAllRanges();
                                                Android.onTextSelected('', 0, 0, 0);
                                            } catch(err) {}
                                        })()
                                    """.trimIndent(), null)
                                    this@apply.performClick()
                                    return true
                                }
                                val x = e.x
                                val y = e.y
                                val w = this@apply.width.toFloat()
                                val h = this@apply.height.toFloat()
                                when (pageFlipRef.get()) {
                                    ReaderPageFlip.LR_PREV_NEXT -> when {
                                        x < w / 3f -> webView.evaluateJavascript("window._prev()", null)
                                        x > w * 2f / 3f -> webView.evaluateJavascript("window._next()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.LR_NEXT_PREV -> when {
                                        x < w / 3f -> webView.evaluateJavascript("window._next()", null)
                                        x > w * 2f / 3f -> webView.evaluateJavascript("window._prev()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_PREV_NEXT -> when {
                                        y < h / 3f -> webView.evaluateJavascript("window._prev()", null)
                                        y > h * 2f / 3f -> webView.evaluateJavascript("window._next()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_NEXT_PREV -> when {
                                        y < h / 3f -> webView.evaluateJavascript("window._next()", null)
                                        y > h * 2f / 3f -> webView.evaluateJavascript("window._prev()", null)
                                        else -> onCenterTap()
                                    }
                                }
                                this@apply.performClick()
                                return true
                            }

                            override fun onLongPress(e: MotionEvent) {
                                val density = ctx.resources.displayMetrics.density
                                val cssX = e.x / density
                                val cssY = e.y / density
                                val ex = e.x; val ey = e.y; val eDownTime = e.downTime
                                webView.evaluateJavascript("window._getAnnotationAtPoint($cssX, $cssY)") { result ->
                                    val cleaned = result?.trim()?.let { r ->
                                        if (r == "null" || r == "\"null\"") null
                                        else r.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                                    }
                                    val hitData = try {
                                        if (cleaned != null) JSONObject(cleaned) else null
                                    } catch (_: Exception) { null }
                                    if (hitData != null) {
                                        val id = hitData.getLong("id")
                                        val cx = hitData.getDouble("cx").toFloat()
                                        val y = hitData.getDouble("y").toFloat()
                                        val bottom = hitData.getDouble("bottom").toFloat()
                                        when (hitData.getString("type")) {
                                            "highlight" -> onHighlightLongPressRef.get()?.invoke(id, cx, y, bottom)
                                            "memo" -> onMemoLongPressRef.get()?.invoke(id, cx, y, bottom)
                                        }
                                    } else {
                                        isLongPress = true
                                        val downEvent = MotionEvent.obtain(eDownTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, ex, ey, 0)
                                        webView.dispatchTouchEvent(downEvent)
                                        downEvent.recycle()
                                    }
                                }
                            }
                        })

                        setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
                            if (isLongPress) {
                                when (event.action) {
                                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                                        webView.dispatchTouchEvent(event)
                                        if (event.action == MotionEvent.ACTION_UP) isLongPress = false
                                    }
                                }
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
                    val fontFilePath = ImportedFontStore.load(context)
                        .find { it.name == readerSettings.fontName }?.filePath ?: ""
                    webView.loadDataWithBaseURL(
                        "file://${bookDir}/",
                        buildEpubJsHtml(opfPath!!, savedCfi, readerSettings, fontFilePath),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    selectionState?.let { sel ->
        SelectionPopup(
            selectionY = sel.y,
            selectionBottom = sel.bottom,
            selectionCx = sel.x,
            onHighlight = { onHighlight(sel.text, sel.cfi); clearSelection() },
            onMemo = { onMemo(sel.text, sel.cfi); clearSelection() },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, sel.text)
                }
                context.startActivity(Intent.createChooser(intent, null))
                clearSelection()
            },
            onDismiss = { clearSelection() }
        )
    }
    } // Box
}

@Composable
private fun SelectionPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    onHighlight: () -> Unit,
    onMemo: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val popupHeightDp = 48.dp
    val marginDp = 8.dp
    val yDp = selectionY.dp
    val bottomDp = selectionBottom.dp
    val cxDp = selectionCx.dp
    val showAbove = yDp > popupHeightDp + marginDp
    val offsetY = if (showAbove) yDp - popupHeightDp - marginDp else bottomDp + marginDp

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var popupWidthDp by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Row(
            modifier = Modifier
                .onSizeChanged { popupWidthDp = with(density) { it.width.toDp() } }
                .alpha(if (popupWidthDp == 0.dp) 0f else 1f)
                .offset(
                    x = (cxDp - popupWidthDp / 2).coerceIn(marginDp, screenWidthDp - popupWidthDp - marginDp),
                    y = offsetY
                )
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onHighlight) {
                Text("하이라이트", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onMemo) {
                Text("메모", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onShare) {
                Text("공유", color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun HighlightActionPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    onDelete: () -> Unit,
    onMemo: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val popupHeightDp = 48.dp
    val marginDp = 8.dp
    val yDp = selectionY.dp
    val bottomDp = selectionBottom.dp
    val cxDp = selectionCx.dp
    val showAbove = yDp > popupHeightDp + marginDp
    val offsetY = if (showAbove) yDp - popupHeightDp - marginDp else bottomDp + marginDp

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var popupWidthDp by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Row(
            modifier = Modifier
                .onSizeChanged { popupWidthDp = with(density) { it.width.toDp() } }
                .alpha(if (popupWidthDp == 0.dp) 0f else 1f)
                .offset(
                    x = (cxDp - popupWidthDp / 2).coerceIn(marginDp, screenWidthDp - popupWidthDp - marginDp),
                    y = offsetY
                )
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDelete) {
                Text("하이라이트 삭제", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onMemo) {
                Text("메모", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onShare) {
                Text("공유", color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MemoActionPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    onHighlight: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val popupHeightDp = 48.dp
    val marginDp = 8.dp
    val yDp = selectionY.dp
    val bottomDp = selectionBottom.dp
    val cxDp = selectionCx.dp
    val showAbove = yDp > popupHeightDp + marginDp
    val offsetY = if (showAbove) yDp - popupHeightDp - marginDp else bottomDp + marginDp

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var popupWidthDp by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Row(
            modifier = Modifier
                .onSizeChanged { popupWidthDp = with(density) { it.width.toDp() } }
                .alpha(if (popupWidthDp == 0.dp) 0f else 1f)
                .offset(
                    x = (cxDp - popupWidthDp / 2).coerceIn(marginDp, screenWidthDp - popupWidthDp - marginDp),
                    y = offsetY
                )
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onHighlight) {
                Text("하이라이트", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onEdit) {
                Text("메모 편집", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onDelete) {
                Text("메모 삭제", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onShare) {
                Text("공유", color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CombinedAnnotationPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    onDeleteHighlight: () -> Unit,
    onEditMemo: () -> Unit,
    onDeleteMemo: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val popupHeightDp = 48.dp
    val marginDp = 8.dp
    val yDp = selectionY.dp
    val bottomDp = selectionBottom.dp
    val cxDp = selectionCx.dp
    val showAbove = yDp > popupHeightDp + marginDp
    val offsetY = if (showAbove) yDp - popupHeightDp - marginDp else bottomDp + marginDp

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var popupWidthDp by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Row(
            modifier = Modifier
                .onSizeChanged { popupWidthDp = with(density) { it.width.toDp() } }
                .alpha(if (popupWidthDp == 0.dp) 0f else 1f)
                .offset(
                    x = (cxDp - popupWidthDp / 2).coerceIn(marginDp, screenWidthDp - popupWidthDp - marginDp),
                    y = offsetY
                )
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDeleteHighlight) {
                Text("하이라이트 삭제", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onEditMemo) {
                Text("메모 편집", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onDeleteMemo) {
                Text("메모 삭제", color = Color.Black, fontSize = 14.sp)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
            TextButton(onClick = onShare) {
                Text("공유", color = Color.Black, fontSize = 14.sp)
            }
        }
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

private fun readerBottomInfoText(
    info: ReaderBottomInfo,
    book: BookFile,
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    readingProgress: Float,
    currentTime: String
): String? = when (info) {
    ReaderBottomInfo.NONE -> null
    ReaderBottomInfo.BOOK_TITLE -> book.metadata?.title ?: book.name
    ReaderBottomInfo.CHAPTER_TITLE -> chapterTitle.ifEmpty { null }
    ReaderBottomInfo.PAGE -> if (totalPages > 0) "$currentPage / $totalPages" else null
    ReaderBottomInfo.CLOCK -> currentTime.ifEmpty { null }
    ReaderBottomInfo.PROGRESS -> "${(readingProgress * 100).toInt()}%"
}

private fun fontFamilyForJs(fontName: String): String = when (fontName) {
    FONT_EPUB_ORIGINAL -> FONT_EPUB_ORIGINAL
    FONT_SYSTEM -> ""
    else -> fontName
}

private fun buildEpubJsHtml(opfPath: String, savedCfi: String, settings: ReaderSettings, fontFilePath: String = "") = """<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'/>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 100%; height: 100%; overflow: hidden; background: #fff; }
#viewer { position: absolute; top: ${settings.paddingVertical}px; left: ${settings.paddingHorizontal}px; right: ${settings.paddingHorizontal}px; bottom: ${settings.paddingVertical + 16}px; }
.epub-view svg { mix-blend-mode: multiply; }
</style>
</head>
<body>
<div id="bookmark-ribbon" style="display:none;position:absolute;top:-12px;right:16px;width:16px;height:40px;z-index:0;pointer-events:none;">
<svg width="16" height="40" viewBox="0 0 16 40" xmlns="http://www.w3.org/2000/svg">
<path d="M0,0 L16,0 L16,40 L8,30 L0,40 Z" fill="#ccc"/>
</svg>
</div>
<div id="viewer"></div>
<script>
(function() {
    var _RO = window.ResizeObserver;
    if (!_RO) return;
    window.ResizeObserver = function(callback) {
        return new _RO(function(entries, observer) {
            requestAnimationFrame(function() { callback(entries, observer); });
        });
    };
    window.ResizeObserver.prototype = _RO.prototype;
})();
</script>
<script src="epub.min.js"></script>
<script>
window._showBookmarkRibbon = function(show) {
    var el = document.getElementById('bookmark-ribbon');
    if (el) el.style.display = show ? 'block' : 'none';
};
var _dualPage = ${settings.dualPage};
function _getSpreadMode() {
    return (_dualPage && window.innerWidth > window.innerHeight) ? "always" : "none";
}
var book = ePub("$opfPath");
var _viewerEl = document.getElementById('viewer');
var _viewerRect = _viewerEl ? _viewerEl.getBoundingClientRect() : null;
var _viewerW = (_viewerRect && _viewerRect.width > 0) ? _viewerRect.width : (window.innerWidth - ${settings.paddingHorizontal * 2});
var _viewerH = (_viewerRect && _viewerRect.height > 0) ? _viewerRect.height : (window.innerHeight - ${settings.paddingVertical * 2 + 16});
var rendition = book.renderTo("viewer", {
    width: _viewerW,
    height: _viewerH,
    spread: _getSpreadMode(),
    flow: "paginated",
    manager: "default",
    minSpreadWidth: 0
});
window.addEventListener('resize', function() {
    if (!window._readerSettings) return;
    var s = window._readerSettings;
    rendition.spread(_getSpreadMode(), 0);
    rendition.resize(window.innerWidth - s.paddingHorizontal * 2, window.innerHeight - s.paddingVertical * 2 - 16);
    var viewer = document.getElementById('viewer');
    if (viewer) {
        viewer.style.top = s.paddingVertical + 'px';
        viewer.style.left = s.paddingHorizontal + 'px';
        viewer.style.right = s.paddingHorizontal + 'px';
        viewer.style.bottom = (s.paddingVertical + 16) + 'px';
    }
});

function findTocEntry(href, items) {
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        var itemHref = item.href.split('#')[0];
        if (href === itemHref || href.endsWith('/' + itemHref) || itemHref.endsWith('/' + href)) {
            return item.label.trim();
        }
        if (item.subitems && item.subitems.length > 0) {
            var found = findTocEntry(href, item.subitems);
            if (found) return found;
        }
    }
    return "";
}

function reportLocation(location) {
    try {
        var percentage = (location.start && location.start.percentage) ? location.start.percentage : 0;
        if (percentage === 0 && location.start && book.spine && book.spine.items.length > 0) {
            percentage = location.start.index / book.spine.items.length;
        }
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        var href = (location.start && location.start.href) ? location.start.href : "";
        var chapter = "";
        if (book.navigation && book.navigation.toc) {
            chapter = findTocEntry(href, book.navigation.toc);
        }
        Android.onLocationChanged(percentage, cfi, chapter);
        if (_totalVisualPages > 0 && location.start) {
            var idx = location.start.index !== undefined ? location.start.index : 0;
            var pg = location.start.displayed ? location.start.displayed.page : 1;
            var total = location.start.displayed ? location.start.displayed.total : 0;
            try {
                var delta = rendition.manager && rendition.manager.layout ? rendition.manager.layout.delta : 0;
                if (delta > 0 && rendition.manager.container) {
                    var scrollLeft = rendition.manager.container.scrollLeft;
                    pg = Math.round(scrollLeft / delta) + 1;
                }
            } catch(e) {}
            Android.onPageInfoChanged((_spinePageOffsets[idx] || 0) + pg, _totalVisualPages);
            var _scrollX = 0, _scrollW = 0, _deltaX = 0;
            try {
                if (rendition.manager && rendition.manager.container) {
                    _scrollX = rendition.manager.container.scrollLeft;
                    _scrollW = rendition.manager.container.scrollWidth;
                }
                if (rendition.manager && rendition.manager.layout) {
                    _deltaX = rendition.manager.layout.delta;
                }
            } catch(e) {}
            Android.onDebugInfo(idx, pg, total, _scrollX, _scrollW, _deltaX);
        }
    } catch(e) {}
}

var _totalLocations = 0;
var _spinePageCounts = {};
var _spinePageOffsets = {};
var _totalVisualPages = 0;
var _spineCharPageBreaks = {};
var _locationsReady = false;
var _rendered = false;
var _pendingLocation = null;
var _pendingCfiList = [];
var _cfiPageMap = {};
function _getSpineIndexFromCfi(cfi) {
    var m = cfi.match(/\/6\/(\d+)/);
    return m ? (parseInt(m[1]) / 2) - 1 : -1;
}
window._setCfiList = function(jsonStr) {
    try {
        var cfis = JSON.parse(jsonStr);
        _pendingCfiList = [];
        _cfiPageMap = {};
        for (var ci = 0; ci < cfis.length; ci++) {
            var si = _getSpineIndexFromCfi(cfis[ci]);
            if (si >= 0) _pendingCfiList.push({cfi: cfis[ci], spineIndex: si});
        }
    } catch(e) {}
};
window._currentCfi = "";
window._currentEndCfi = "";
window._isBookmarkedInRange = function(cfiListJson) {
    try {
        var cfis = JSON.parse(cfiListJson);
        if (!cfis.length || !window._currentCfi) return false;
        var startCfi = window._currentCfi;
        var endCfi = window._currentEndCfi;
        var epubcfi = new ePub.CFI();
        for (var i = 0; i < cfis.length; i++) {
            var c = cfis[i];
            if (c === startCfi) return true;
            if (endCfi) {
                var afterStart = epubcfi.compare(c, startCfi) >= 0;
                var beforeEnd = epubcfi.compare(c, endCfi) <= 0;
                if (afterStart && beforeEnd) return true;
            }
        }
    } catch(e) {}
    return false;
};
var _waitingForFonts = false;
rendition.on("relocated", function(location) {
    if (!_rendered || _waitingForFonts) {
        _rendered = true;
        if (!_waitingForFonts) {
            try {
                var iframe = document.querySelector('iframe');
                var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
                if (iDoc && iDoc.fonts && iDoc.fonts.status === 'loading' && _savedCfi) {
                    _waitingForFonts = true;
                    iDoc.fonts.ready.then(function() {
                        rendition.display(_savedCfi);
                    });
                    return;
                }
            } catch(e) {}
        }
        _waitingForFonts = false;
        Android.onContentRendered();
    }
    try {
        var href = (location.start && location.start.href) ? location.start.href : "";
        if (href && book.navigation && book.navigation.toc) {
            var chapter = findTocEntry(href, book.navigation.toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
    try {
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        if (cfi) {
            window._currentCfi = cfi;
            var endCfi = (location.end && location.end.cfi) ? location.end.cfi : "";
            window._currentEndCfi = endCfi;
            Android.onLocationChanged(0, cfi, "");
        }
    } catch(e) {}
    if (_locationsReady || _totalVisualPages > 0) {
        reportLocation(location);
    } else {
        _pendingLocation = location;
    }
    if (_searchHighlightQuery) { setTimeout(_applySearchHighlights, 50); }
});

book.loaded.navigation.then(function(nav) {
    try {
        var toc = nav.toc || [];
        function buildToc(items, depth) {
            var result = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                result.push({
                    label: item.label.trim(),
                    href: item.href,
                    depth: depth,
                    subitems: item.subitems ? buildToc(item.subitems, depth + 1) : []
                });
            }
            return result;
        }
        Android.onTocLoaded(JSON.stringify(buildToc(toc, 0)));
        var loc = rendition.currentLocation();
        if (loc && loc.start && loc.start.href) {
            var chapter = findTocEntry(loc.start.href, toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
});

function computeVisualPages() {
    try { Android.onScanStart(); } catch(e) {}
    var s = window._readerSettings;
    var scanW = window.innerWidth - s.paddingHorizontal * 2;
    var scanH = window.innerHeight - s.paddingVertical * 2 - 16;
    var scanDiv = document.createElement('div');
    scanDiv.style.cssText = 'position:fixed;left:-' + (scanW + 10) + 'px;top:0;width:' + scanW + 'px;height:' + scanH + 'px;overflow:hidden;';
    document.body.appendChild(scanDiv);
    var scanBook = ePub("$opfPath");
    var scanRendition = scanBook.renderTo(scanDiv, {
        width: scanW,
        height: scanH,
        spread: "none",
        flow: "paginated",
        manager: "default",
        minSpreadWidth: 9999
    });
    scanRendition.hooks.content.register(function(contents) {
        _injectReaderStyle(contents.document);
    });
    scanBook.ready.then(function() {
        var items = scanBook.spine ? (scanBook.spine.items || []) : [];
        if (items.length === 0) {
            setTimeout(function() {
                try { scanRendition.destroy(); } catch(e) {}
                try { scanBook.destroy(); } catch(e) {}
                document.body.removeChild(scanDiv);
            }, 100);
            return;
        }
        var i = 0;
        var _runningOffset = 0;
        function _resolveCfisInSpine(cfis, idx, done) {
            if (idx >= cfis.length) { done(); return; }
            scanRendition.display(cfis[idx]).then(function() {
                try {
                    var cfiLoc = scanRendition.currentLocation();
                    var pg = (cfiLoc && cfiLoc.start && cfiLoc.start.displayed) ? cfiLoc.start.displayed.page : 1;
                    try {
                        var sDelta = scanRendition.manager && scanRendition.manager.layout ? scanRendition.manager.layout.delta : 0;
                        if (sDelta > 0 && scanRendition.manager.container) {
                            var sScrollLeft = scanRendition.manager.container.scrollLeft;
                            pg = Math.round(sScrollLeft / sDelta) + 1;
                        }
                    } catch(e2) {}
                    _cfiPageMap[cfis[idx]] = _spinePageOffsets[i] + pg;
                } catch(e) {}
                _resolveCfisInSpine(cfis, idx + 1, done);
            }).catch(function() { _resolveCfisInSpine(cfis, idx + 1, done); });
        }
        function next() {
            if (i >= items.length) {
                _totalVisualPages = _runningOffset;
                try {
                    var tocNav = book.navigation ? (book.navigation.toc || []) : [];
                    var spineItemsForToc = book.spine ? (book.spine.items || []) : [];
                    function buildTocWithPages(tocItems, depth) {
                        var result = [];
                        for (var ti = 0; ti < tocItems.length; ti++) {
                            var tocItem = tocItems[ti];
                            var hrefBase = tocItem.href.split('#')[0];
                            var page = 0;
                            for (var si = 0; si < spineItemsForToc.length; si++) {
                                var siHref = (spineItemsForToc[si].href || '').split('?')[0];
                                if (siHref === hrefBase || siHref.endsWith('/' + hrefBase) || hrefBase.endsWith('/' + siHref)) {
                                    page = (_spinePageOffsets[si] || 0) + 1;
                                    break;
                                }
                            }
                            result.push({
                                label: tocItem.label.trim(),
                                href: tocItem.href,
                                page: page,
                                depth: depth,
                                subitems: tocItem.subitems ? buildTocWithPages(tocItem.subitems, depth + 1) : []
                            });
                        }
                        return result;
                    }
                    Android.onTocReady(JSON.stringify(buildTocWithPages(tocNav, 0)));
                } catch(e) {}
                _pendingLocation = null;
                var _scanCurrentPage = 0;
                var loc = rendition.currentLocation();
                if (loc && loc.start) {
                    var idx = loc.start.index !== undefined ? loc.start.index : 0;
                    var pg = loc.start.displayed ? loc.start.displayed.page : 1;
                    try {
                        var delta = rendition.manager && rendition.manager.layout ? rendition.manager.layout.delta : 0;
                        if (delta > 0 && rendition.manager.container) {
                            var scrollLeft = rendition.manager.container.scrollLeft;
                            pg = Math.round(scrollLeft / delta) + 1;
                        }
                    } catch(e) {}
                    _scanCurrentPage = (_spinePageOffsets[idx] || 0) + pg;
                }
                Android.onScanComplete(_totalVisualPages, _scanCurrentPage, JSON.stringify(_spinePageOffsets), JSON.stringify(_cfiPageMap), JSON.stringify(_spineCharPageBreaks));
                setTimeout(function() {
                    try { scanRendition.destroy(); } catch(e) {}
                    try { scanBook.destroy(); } catch(e) {}
                    document.body.removeChild(scanDiv);
                }, 100);
                return;
            }
            scanRendition.display(items[i].href).then(function() {
                var loc = scanRendition.currentLocation();
                _spinePageCounts[i] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
                _spinePageOffsets[i] = _runningOffset;
                try {
                    var pageBreaks = [0];
                    var sTotal = _spinePageCounts[i];
                    if (sTotal > 1) {
                        var sDelta = scanRendition.manager && scanRendition.manager.layout ? scanRendition.manager.layout.delta : 0;
                        if (sDelta > 0) {
                            var sIframe = scanDiv.querySelector('iframe');
                            var sDoc = sIframe && (sIframe.contentDocument || (sIframe.contentWindow && sIframe.contentWindow.document));
                            if (sDoc) {
                                var sBody = sDoc.body || sDoc.querySelector('body') || sDoc.documentElement;
                                if (sBody) {
                                    var sWalker = sDoc.createTreeWalker(sBody, NodeFilter.SHOW_TEXT, null, false);
                                    var sNd, charOffset = 0;
                                    var lastPage = 0;
                                    while ((sNd = sWalker.nextNode())) {
                                        var len = sNd.textContent.length;
                                        if (len === 0) continue;
                                        try {
                                            var range = sDoc.createRange();
                                            range.selectNodeContents(sNd);
                                            var rect = range.getBoundingClientRect();
                                            if (rect.width > 0) {
                                                var nodePage = Math.floor(rect.left / sDelta);
                                                if (nodePage > lastPage) {
                                                    for (var np = lastPage + 1; np <= nodePage; np++) {
                                                        pageBreaks.push(charOffset);
                                                    }
                                                    lastPage = nodePage;
                                                }
                                            }
                                        } catch(re) {}
                                        charOffset += len;
                                    }
                                }
                            }
                        }
                    }
                    _spineCharPageBreaks[i] = pageBreaks;
                } catch(pe) {
                    _spineCharPageBreaks[i] = [0];
                }
                var cfisForSpine = [];
                for (var ci = 0; ci < _pendingCfiList.length; ci++) {
                    if (_pendingCfiList[ci].spineIndex === i) cfisForSpine.push(_pendingCfiList[ci].cfi);
                }
                _resolveCfisInSpine(cfisForSpine, 0, function() {
                    _runningOffset += _spinePageCounts[i];
                    i++;
                    next();
                });
            });
        }
        next();
    });
}

book.ready.then(function() {
    return book.locations.generate(150);
}).then(function() {
    try {
        var locs = book.locations._locations || [];
        _totalLocations = locs.length || 1;
        var toc = book.navigation ? (book.navigation.toc || []) : [];
        function buildTocSimple(items, depth) {
            var result = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                result.push({
                    label: item.label.trim(),
                    href: item.href,
                    depth: depth,
                    subitems: item.subitems ? buildTocSimple(item.subitems, depth + 1) : []
                });
            }
            return result;
        }
        Android.onTocReady(JSON.stringify(buildTocSimple(toc, 0)));
    } catch(e) {}

    _locationsReady = true;
    clearTimeout(_rescanTimer);
    computeVisualPages();
});

rendition.hooks.content.register(function(contents) {
    var doc = contents.document;
    _injectReaderStyle(doc);
    if (doc.body) { void doc.body.offsetHeight; }
    var debounceTimer = null;
    var lastSelectionTime = 0;

    // 선택 드래그 중 CSS columns 스크롤 방지
    // iframe 내부 + 외부 컨테이너 모두 잠금
    var _selLocked = false;
    var _savedScrolls = [];
    function _getAllScrollTargets() {
        var targets = [];
        if (doc.body) targets.push(doc.body);
        if (doc.documentElement) targets.push(doc.documentElement);
        try {
            var outer = document; // 외부 문서
            var epubView = outer.querySelector('.epub-view');
            var epubContainer = outer.querySelector('.epub-container');
            if (epubView) targets.push(epubView);
            if (epubContainer) targets.push(epubContainer);
            if (outer.querySelector('#viewer')) targets.push(outer.querySelector('#viewer'));
        } catch(e) {}
        return targets;
    }
    function _captureAll() {
        _savedScrolls = _getAllScrollTargets().map(function(el) {
            return { el: el, left: el.scrollLeft };
        });
    }
    function _restoreAll() {
        _savedScrolls.forEach(function(s) {
            if (s.el.scrollLeft !== s.left) s.el.scrollLeft = s.left;
        });
    }
    var _rafId = 0;
    function _lockLoop() {
        if (!_selLocked) return;
        _restoreAll();
        _rafId = (doc.defaultView || window).requestAnimationFrame(_lockLoop);
    }
    _captureAll();
    doc.addEventListener('selectionchange', function() {
        var sel = doc.getSelection();
        var hasSelection = sel && sel.toString().trim().length > 0;
        if (hasSelection && !_selLocked) {
            _captureAll();
            _selLocked = true;
            _rafId = (doc.defaultView || window).requestAnimationFrame(_lockLoop);
        } else if (!hasSelection && _selLocked) {
            _selLocked = false;
            if (_rafId) (doc.defaultView || window).cancelAnimationFrame(_rafId);
        }
    });
    _getAllScrollTargets().forEach(function(el) {
        el.addEventListener('scroll', function() {
            if (_selLocked) _restoreAll();
            else _captureAll();
        });
    });

    window._getCfiFromSelection = function() {
        try {
            var sel = doc.getSelection();
            if (!sel || sel.rangeCount === 0) return '';
            return contents.cfiFromRange(sel.getRangeAt(0)) || '';
        } catch(e) { return ''; }
    };
    doc.addEventListener('selectionchange', function() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function() {
            try {
                var sel = doc.getSelection();
                var text = sel ? sel.toString().trim() : '';
                if (text.length > 0) {
                    lastSelectionTime = Date.now();
                    Android.onTextSelected(text, 0, 0, 0);
                } else if (Date.now() - lastSelectionTime > 500) {
                    Android.onTextSelected('', 0, 0, 0);
                }
            } catch(e) {}
        }, 200);
    });
});

window._clearSelection = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (iframe && iframe.contentDocument) {
            iframe.contentDocument.getSelection().removeAllRanges();
        }
    } catch(e) {}
};

var _searchHighlightQuery = '';
function _removeSearchHighlights() {
    try {
        var iframe = document.querySelector('iframe');
        var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
        if (!iDoc) return;
        var marks = iDoc.querySelectorAll('.search-hl');
        for (var i = marks.length - 1; i >= 0; i--) {
            var mark = marks[i];
            var parent = mark.parentNode;
            parent.replaceChild(iDoc.createTextNode(mark.textContent), mark);
            parent.normalize();
        }
    } catch(e) {}
}
function _applySearchHighlights() {
    if (!_searchHighlightQuery) return;
    _removeSearchHighlights();
    try {
        var iframe = document.querySelector('iframe');
        var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
        if (!iDoc || !iDoc.body) return;
        var lowerQuery = _searchHighlightQuery.toLowerCase();
        var walker = iDoc.createTreeWalker(iDoc.body, NodeFilter.SHOW_TEXT, null, false);
        var textNodes = [];
        var nd;
        while ((nd = walker.nextNode())) textNodes.push(nd);
        for (var i = 0; i < textNodes.length; i++) {
            var tn = textNodes[i];
            var text = tn.textContent;
            var lower = text.toLowerCase();
            var idx = lower.indexOf(lowerQuery);
            if (idx === -1) continue;
            var frag = iDoc.createDocumentFragment();
            var cursor = 0;
            while (idx !== -1) {
                if (idx > cursor) frag.appendChild(iDoc.createTextNode(text.substring(cursor, idx)));
                var span = iDoc.createElement('span');
                span.className = 'search-hl';
                span.textContent = text.substring(idx, idx + lowerQuery.length);
                frag.appendChild(span);
                cursor = idx + lowerQuery.length;
                idx = lower.indexOf(lowerQuery, cursor);
            }
            if (cursor < text.length) frag.appendChild(iDoc.createTextNode(text.substring(cursor)));
            tn.parentNode.replaceChild(frag, tn);
        }
    } catch(e) {}
}
window._setSearchHighlight = function(query) {
    _searchHighlightQuery = query;
    _applySearchHighlights();
};
window._clearSearchHighlight = function() {
    _searchHighlightQuery = '';
    _removeSearchHighlights();
};

var _highlightCfiMap = {};
window._addHighlight = function(cfi, id) {
    try {
        _highlightCfiMap[id] = cfi;
        rendition.annotations.add("highlight", cfi, {id: id}, null, "epub-hl-" + id, {
            "background": "#d0d0d0",
            "border": "none"
        });
    } catch(e) {}
};
window._removeHighlight = function(id) {
    try {
        var cfi = _highlightCfiMap[id];
        if (cfi) {
            rendition.annotations.remove(cfi, "highlight");
            delete _highlightCfiMap[id];
        }
    } catch(e) {}
};
window._applyHighlights = function(json) {
    try {
        var hl = JSON.parse(json);
        for (var i = 0; i < hl.length; i++) {
            window._addHighlight(hl[i].cfi, hl[i].id);
        }
    } catch(e) {}
};
var _memoCfiMap = {};
window._addMemo = function(cfi, id) {
    try {
        _memoCfiMap[id] = cfi;
        rendition.annotations.add("underline", cfi, {id: id}, null, "epub-memo-" + id, {
            "fill": "#000000",
            "stroke": "none"
        });
    } catch(e) {}
};
window._removeMemo = function(id) {
    try {
        var cfi = _memoCfiMap[id];
        if (cfi) { rendition.annotations.remove(cfi, "underline"); delete _memoCfiMap[id]; }
    } catch(e) {}
};
window._applyMemos = function(json) {
    try {
        var items = JSON.parse(json);
        for (var i = 0; i < items.length; i++) { window._addMemo(items[i].cfi, items[i].id); }
    } catch(e) {}
};
window._getAnnotationAtPoint = function(x, y) {
    try {
        var hlGroups = document.querySelectorAll('[class^="epub-hl-"]');
        for (var i = 0; i < hlGroups.length; i++) {
            var g = hlGroups[i];
            var rects = g.querySelectorAll('rect');
            for (var j = 0; j < rects.length; j++) {
                var br = rects[j].getBoundingClientRect();
                if (x >= br.left && x <= br.right && y >= br.top && y <= br.bottom) {
                    var match = (g.getAttribute('class') || '').match(/epub-hl-(\d+)/);
                    if (match) {
                        var gbr = g.getBoundingClientRect();
                        return JSON.stringify({type: 'highlight', id: parseInt(match[1]), cx: gbr.left + gbr.width/2, y: gbr.top, bottom: gbr.bottom});
                    }
                }
            }
        }
        var memoGroups = document.querySelectorAll('[class^="epub-memo-"]');
        for (var i = 0; i < memoGroups.length; i++) {
            var g = memoGroups[i];
            var gbr = g.getBoundingClientRect();
            if (x >= gbr.left && x <= gbr.right && y >= gbr.top - 20 && y <= gbr.bottom + 10) {
                var match = (g.getAttribute('class') || '').match(/epub-memo-(\d+)/);
                if (match) {
                    return JSON.stringify({type: 'memo', id: parseInt(match[1]), cx: gbr.left + gbr.width/2, y: gbr.top, bottom: gbr.bottom});
                }
            }
        }
    } catch(e) {}
    return 'null';
};

window._isSelectionAtPageEnd = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return false;
        var doc = iframe.contentDocument;
        var sel = doc.getSelection();
        if (!sel || sel.rangeCount === 0 || sel.toString().trim().length === 0) return false;
        var range = sel.getRangeAt(0);

        var manager = rendition.manager;
        if (!manager || !manager.container) return false;
        var scrollLeft = manager.container.scrollLeft;
        var pageWidth = manager.layout ? manager.layout.delta : manager.container.offsetWidth;
        var rightEdge = scrollLeft + pageWidth;

        var endRange = doc.createRange();
        endRange.setStart(range.endContainer, range.endOffset);
        endRange.setEnd(range.endContainer, range.endOffset);

        var walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT, null, false);
        var lastVisibleTextNode = null;
        var node;
        while (node = walker.nextNode()) {
            if (node.textContent.trim().length === 0) continue;
            var r = doc.createRange();
            r.selectNodeContents(node);
            var rects = r.getClientRects();
            for (var i = 0; i < rects.length; i++) {
                var rect = rects[i];
                if (rect.right > scrollLeft && rect.left < rightEdge) {
                    lastVisibleTextNode = node;
                }
            }
        }

        if (!lastVisibleTextNode) return false;

        return range.endContainer === lastVisibleTextNode &&
               range.endOffset === lastVisibleTextNode.textContent.length;
    } catch(e) { return false; }
};

window._selectFirstCharOfPage = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return;
        var doc = iframe.contentDocument;

        var manager = rendition.manager;
        if (!manager || !manager.container) return;
        var scrollLeft = manager.container.scrollLeft;
        var pageWidth = manager.layout ? manager.layout.delta : manager.container.offsetWidth;
        var rightEdge = scrollLeft + pageWidth;

        var walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT, null, false);
        var firstVisibleTextNode = null;
        var node;
        while (node = walker.nextNode()) {
            if (node.textContent.trim().length === 0) continue;
            var r = doc.createRange();
            r.selectNodeContents(node);
            var rects = r.getClientRects();
            for (var i = 0; i < rects.length; i++) {
                var rect = rects[i];
                if (rect.right > scrollLeft && rect.left < rightEdge) {
                    firstVisibleTextNode = node;
                    break;
                }
            }
            if (firstVisibleTextNode) break;
        }

        if (!firstVisibleTextNode) return;

        var range = doc.createRange();
        range.setStart(firstVisibleTextNode, 0);
        range.setEnd(firstVisibleTextNode, Math.min(1, firstVisibleTextNode.textContent.length));
        var sel = doc.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
    } catch(e) {}
};

window._mergeCfi = function(startCfi, endCfi) {
    try {
        var sMatch = startCfi.match(/^(epubcfi\([^,]+),([^,]+),([^)]+)\)$/);
        var eMatch = endCfi.match(/^(epubcfi\([^,]+),([^,]+),([^)]+)\)$/);
        if (sMatch && eMatch) {
            return sMatch[1] + ',' + sMatch[2] + ',' + eMatch[3] + ')';
        }
        return endCfi;
    } catch(e) { return endCfi; }
};

var _savedCfi = "${savedCfi.replace("\"", "\\\"")}";
rendition.display(_savedCfi.length > 0 ? _savedCfi : undefined);
window._prev = function() { rendition.prev(); };
window._next = function() {
    // epub.js는 scrollLeft + offsetWidth + delta <= scrollWidth 로 같은 챕터 내 다음 페이지 존재 여부를 판단한다.
    // 브라우저가 scrollLeft를 물리 픽셀에 스냅하면서 서브픽셀 오차가 누적되어,
    // 실제로 마지막 페이지가 남아있는데도 이 조건이 false가 되어 챕터를 건너뛰는 문제가 있다.
    // tolerance(delta/2)를 두어 오차 범위 내일 때는 직접 스크롤하여 마지막 페이지를 보여준다.
    var manager = rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var offsetWidth = manager.container.offsetWidth;
        var scrollWidth = manager.container.scrollWidth;
        var delta = manager.layout.delta;
        var tolerance = delta * 0.5;
        var scrollEnd = scrollLeft + offsetWidth + delta;
        if (scrollEnd > scrollWidth && scrollEnd <= scrollWidth + tolerance) {
            manager.scrollBy(delta, 0, true);
            rendition.reportLocation();
            return;
        }
    }
    rendition.next();
};

window._displayHref = function(href) { rendition.display(href); };
window._displayCfi = function(cfi) {
    var prevIndex = -1;
    try {
        var loc = rendition.currentLocation();
        if (loc && loc.start) prevIndex = loc.start.index;
    } catch(e) {}
    rendition.display(cfi).then(function() {
        var newIndex = -1;
        try {
            var loc2 = rendition.currentLocation();
            if (loc2 && loc2.start) newIndex = loc2.start.index;
        } catch(e) {}
        if (prevIndex !== newIndex) {
            setTimeout(function() { rendition.display(cfi); }, 0);
        }
    });
};
window._displayPageNum = function(pageNum) {
    try {
        var items = book.spine ? (book.spine.items || []) : [];
        var targetIdx = 0;
        var pageWithin = 0;
        for (var i = 0; i < items.length; i++) {
            var offset = _spinePageOffsets[i] || 0;
            var count = _spinePageCounts[i] || 1;
            if (pageNum - 1 < offset + count) {
                targetIdx = i;
                pageWithin = Math.max(0, pageNum - 1 - offset);
                break;
            }
        }
        var capturedTargetIdx = targetIdx;
        rendition.display(items[targetIdx].href).then(function() {
            var remaining = pageWithin;
            function step() {
                if (remaining <= 0) { return; }
                remaining--;
                rendition.next().then(function() {
                    var loc = rendition.currentLocation();
                    if (loc && loc.start && loc.start.index !== undefined && loc.start.index !== capturedTargetIdx) {
                        rendition.prev();
                    } else {
                        step();
                    }
                }).catch(function() {});
            }
            step();
        }).catch(function() {});
    } catch(e) {}
};
window._search = function(query) {
    var items = book.spine ? (book.spine.items || []) : [];
    var lowerQuery = query.toLowerCase();
    var nextIdx = 0;
    var activeChains = 0;
    var CONCURRENCY = 3;

    function next() {
        if (nextIdx >= items.length) {
            if (--activeChains === 0) Android.onSearchComplete();
            return;
        }
        var spineIndex = nextIdx;
        var section = items[nextIdx++];
        var href = section.href;
        var chapter = '';
        try {
            if (book.navigation && book.navigation.toc) {
                chapter = findTocEntry(href, book.navigation.toc);
            }
        } catch(e) {}
        try {
        var sectionHref = typeof section.url === 'string' ? section.url : (section.canonical || section.href || href);
        book.load(sectionHref)
            .then(function(doc) {
                try {
                    var body = doc.body || doc.querySelector('body') || doc.documentElement;
                    // 텍스트 노드별 문서 내 시작 offset 수집
                    var nodeMap = [];
                    var walker = doc.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
                    var nd, offset = 0;
                    while ((nd = walker.nextNode())) {
                        nodeMap.push({ node: nd, start: offset });
                        offset += nd.textContent.length;
                    }
                    var fullText = body ? body.textContent : '';
                    var lowerText = fullText.toLowerCase();
                    var positions = [];
                    var p = lowerText.indexOf(lowerQuery);
                    while (p !== -1 && positions.length < 5) { positions.push(p); p = lowerText.indexOf(lowerQuery, p + 1); }
                    if (positions.length > 0) {
                        var found = [];
                        for (var i = 0; i < positions.length; i++) {
                            var pos = positions[i];
                            var matchPage = 0;
                            if (_totalVisualPages > 0) {
                                var breaks = _spineCharPageBreaks[spineIndex];
                                if (breaks && breaks.length > 1) {
                                    var pageWithin = 0;
                                    for (var bi = breaks.length - 1; bi >= 0; bi--) {
                                        if (pos >= breaks[bi]) { pageWithin = bi; break; }
                                    }
                                    matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
                                } else {
                                    var sectionPages = _spinePageCounts[spineIndex] || 1;
                                    var pageWithin = Math.floor((pos / Math.max(fullText.length, 1)) * sectionPages);
                                    matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
                                }
                            }
                            var s = Math.max(0, pos - 60);
                            var e = Math.min(fullText.length, pos + query.length + 60);
                            var cfi = href;
                            try {
                                for (var j = 0; j < nodeMap.length; j++) {
                                    var nm = nodeMap[j];
                                    var nodeEnd = nm.start + nm.node.textContent.length;
                                    if (nm.start <= pos && pos < nodeEnd) {
                                        var charOffset = pos - nm.start;
                                        var steps = [];
                                        var cur = nm.node;
                                        // text node: position among all childNodes (1-indexed)
                                        var nodeIdx = Array.prototype.indexOf.call(cur.parentNode.childNodes, cur) + 1;
                                        steps.unshift('/' + nodeIdx + ':' + charOffset);
                                        cur = cur.parentNode;
                                        // walk up to body (case-insensitive for xhtml)
                                        while (cur && cur.parentNode) {
                                            var n = cur.nodeName.toUpperCase();
                                            if (n === 'BODY' || n === 'HTML' || n === '#DOCUMENT') break;
                                            var idx = 1;
                                            var s2 = cur.previousSibling;
                                            while (s2) { if (s2.nodeType === 1) idx++; s2 = s2.previousSibling; }
                                            steps.unshift('/' + (idx * 2));
                                            cur = cur.parentNode;
                                        }
                                        var cfiBase = section.cfiBase || '';
                                        if (cfiBase) {
                                            cfi = 'epubcfi(' + cfiBase + '!/4' + steps.join('') + ')';
                                        }
                                        break;
                                    }
                                }
                            } catch(e2) {
                                var cfiBase = section.cfiBase || '';
                                if (cfiBase) { cfi = 'epubcfi(' + cfiBase + ')'; }
                            }
                            found.push({ cfi: cfi, excerpt: fullText.substring(s, e).replace(/\s+/g, ' ').trim(), chapter: chapter, page: matchPage, spineIndex: spineIndex, charPos: pos });
                        }
                        if (found.length) Android.onSearchResultsPartial(JSON.stringify(found));
                    }
                } catch(e4) {}
                next();
            })
            .catch(function() { next(); });
        } catch(e5) { next(); }
    }

    var count = Math.min(CONCURRENCY, items.length);
    if (count === 0) { Android.onSearchComplete(); return; }
    activeChains = count;
    for (var c = 0; c < count; c++) next();
};

window._readerSettings = {
    fontFamily: "${fontFamilyForJs(settings.fontName)}",
    fontFilePath: "$fontFilePath",
    fontSize: ${settings.fontSize},
    textAlign: "${settings.textAlign.name.lowercase()}",
    lineHeight: ${settings.lineHeight},
    paragraphSpacing: ${settings.paragraphSpacing},
    paddingVertical: ${settings.paddingVertical},
    paddingHorizontal: ${settings.paddingHorizontal}
};

function _buildReaderCss(s) {
    var baseRule = 'font-size: ' + s.fontSize + 'px !important; line-height: ' + s.lineHeight + ' !important;';
    var paragraphRule = s.paragraphSpacing > 0 ? 'p { margin-bottom: ' + s.paragraphSpacing + 'px !important; }' : '';
    if (s.fontFamily === 'epub_original') {
        return 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6 {' + baseRule + '}' + paragraphRule;
    }
    var fontFaceDecl = '';
    var ff;
    if (s.fontFilePath) {
        fontFaceDecl = '@font-face { font-family: "_imported_"; src: url("' + s.fontFilePath + '"); }';
        ff = "'_imported_', sans-serif";
    } else {
        ff = s.fontFamily ? ("'" + s.fontFamily + "', sans-serif") : 'sans-serif';
    }
    return fontFaceDecl + 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6 {font-family: ' + ff + ' !important; ' + baseRule + '}' + paragraphRule;
}

function _injectReaderStyle(doc) {
    try {
        var style = doc.getElementById('_rs');
        if (!style) {
            style = doc.createElement('style');
            style.id = '_rs';
            doc.head.appendChild(style);
        }
        style.textContent = _buildReaderCss(window._readerSettings) + ' .search-hl { background: #e8a230; color: #fff; }';
    } catch(e) {}
}

var _rescanTimer = null;
window._applyReaderSettings = function(fontFamily, fontFilePath, fontSize, textAlign, lineHeight, paragraphSpacing, paddingVertical, paddingHorizontal, dualPage) {
    window._readerSettings = { fontFamily: fontFamily, fontFilePath: fontFilePath, fontSize: fontSize, textAlign: textAlign, lineHeight: lineHeight, paragraphSpacing: paragraphSpacing, paddingVertical: paddingVertical, paddingHorizontal: paddingHorizontal };
    _dualPage = !!dualPage;
    try {
        var viewer = document.getElementById('viewer');
        viewer.style.top = paddingVertical + 'px';
        viewer.style.left = paddingHorizontal + 'px';
        viewer.style.right = paddingHorizontal + 'px';
        viewer.style.bottom = (paddingVertical + 16) + 'px';
        rendition.spread(_getSpreadMode(), 0);
        rendition.resize(window.innerWidth - paddingHorizontal * 2, window.innerHeight - paddingVertical * 2 - 16);
    } catch(e) {}
    try {
        rendition.getContents().forEach(function(c) { _injectReaderStyle(c.document); });
    } catch(e) {}
    clearTimeout(_rescanTimer);
    if (_locationsReady) {
        _rescanTimer = setTimeout(function() { computeVisualPages(); }, 500);
    }
};
</script>
</body>
</html>"""

private class EpubBridge(
    private val onUpdate: (progress: Float, cfi: String, chapterTitle: String) -> Unit,
    private val onTocLoadedCallback: (tocJson: String) -> Unit = {},
    private val onRenderedCallback: () -> Unit = {},
    private val onChapterChangedCallback: (chapter: String) -> Unit = {},
    private val onTocReadyCallback: (tocJson: String) -> Unit = {},
    private val onPageInfoChangedCallback: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    private val onDebugInfoCallback: (spineIndex: Int, displayedPage: Int, displayedTotal: Int, scrollX: Float, scrollWidth: Float, deltaX: Float) -> Unit = { _, _, _, _, _, _ -> },
    private val onScanStartCallback: () -> Unit = {},
    private val onScanCompleteCallback: (totalPages: Int, spinePageOffsetsJson: String, cfiPageMapJson: String, spineCharPageBreaksJson: String) -> Unit = { _, _, _, _ -> },
    private val onSearchResultsPartialCallback: (resultsJson: String) -> Unit = {},
    private val onSearchCompleteCallback: () -> Unit = {},
    private val onTextSelectedCallback: (text: String, x: Float, y: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
    private val onSelectionTappedCallback: (text: String, x: Float, y: Float, bottom: Float, cfi: String, isAtPageEnd: Boolean) -> Unit = { _, _, _, _, _, _ -> }
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onLocationChanged(progress: Float, cfi: String, chapterTitle: String) {
        mainHandler.post { onUpdate(progress, cfi, chapterTitle) }
    }

    @android.webkit.JavascriptInterface
    fun onTocLoaded(tocJson: String) {
        mainHandler.post { onTocLoadedCallback(tocJson) }
    }

    @android.webkit.JavascriptInterface
    fun onContentRendered() {
        mainHandler.post { onRenderedCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onChapterChanged(chapter: String) {
        mainHandler.post { onChapterChangedCallback(chapter) }
    }

    @android.webkit.JavascriptInterface
    fun onTocReady(tocJson: String) {
        mainHandler.post { onTocReadyCallback(tocJson) }
    }

    @android.webkit.JavascriptInterface
    fun onPageInfoChanged(currentPage: Int, totalPages: Int) {
        mainHandler.post { onPageInfoChangedCallback(currentPage, totalPages) }
    }

    @android.webkit.JavascriptInterface
    fun onDebugInfo(spineIndex: Int, displayedPage: Int, displayedTotal: Int, scrollX: Float, scrollWidth: Float, deltaX: Float) {
        mainHandler.post { onDebugInfoCallback(spineIndex, displayedPage, displayedTotal, scrollX, scrollWidth, deltaX) }
    }

    @android.webkit.JavascriptInterface
    fun onScanStart() {
        mainHandler.post { onScanStartCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onScanComplete(totalPages: Int, currentPage: Int, spinePageOffsetsJson: String, cfiPageMapJson: String, spineCharPageBreaksJson: String) {
        mainHandler.post {
            onPageInfoChangedCallback(currentPage, totalPages)
            onScanCompleteCallback(totalPages, spinePageOffsetsJson, cfiPageMapJson, spineCharPageBreaksJson)
        }
    }

    @android.webkit.JavascriptInterface
    fun onSearchResultsPartial(resultsJson: String) {
        mainHandler.post { onSearchResultsPartialCallback(resultsJson) }
    }

    @android.webkit.JavascriptInterface
    fun onSearchComplete() {
        mainHandler.post { onSearchCompleteCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onTextSelected(text: String, x: Float, y: Float, bottom: Float) {
        mainHandler.post { onTextSelectedCallback(text, x, y, bottom) }
    }

    @android.webkit.JavascriptInterface
    fun onSelectionTapped(text: String, x: Float, y: Float, bottom: Float, cfi: String, isAtPageEnd: Boolean) {
        mainHandler.post { onSelectionTappedCallback(text, x, y, bottom, cfi, isAtPageEnd) }
    }

}

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
private fun ReaderSettingsBottomSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenFontPopup: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabLabels = listOf("글자", "여백", "뷰어")

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabLabels.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                tabLabels.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (selectedTab == index) Color.Black else Color.Transparent)
                    )
                }
                Spacer(modifier = Modifier.width(56.dp))
            }
        }
        HorizontalDivider(color = Color.Black)

        Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
            when (selectedTab) {
                0 -> ReaderGlyphTab(settings, onSettingsChange, onOpenFontPopup)
                1 -> ReaderMarginTab(settings, onSettingsChange)
                2 -> ReaderViewerTab(settings, onSettingsChange)
            }
        }
    }
}

@Composable
private fun ReaderGlyphTab(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onOpenFontPopup: () -> Unit = {}
) {
    val context = LocalContext.current
    val importedFonts = remember { ImportedFontStore.load(context) }
    val allFonts = remember(importedFonts) {
        listOf(FONT_EPUB_ORIGINAL, FONT_SYSTEM) + READER_BUILTIN_FONT_NAMES + importedFonts.map { it.name }
    }
    val currentIndex = allFonts.indexOf(settings.fontName).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingRow("글꼴") {
            Row(
                modifier = Modifier.width(160.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSettingsChange(settings.copy(fontName = allFonts[(currentIndex - 1 + allFonts.size) % allFonts.size])) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(
                    fontDisplayName(settings.fontName),
                    modifier = Modifier.weight(1f).clickable { onOpenFontPopup() },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { onSettingsChange(settings.copy(fontName = allFonts[(currentIndex + 1) % allFonts.size])) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        ReaderSettingRow("글자 크기") {
            ReaderStepperField(
                value = settings.fontSize.toString(),
                onDecrement = { if (settings.fontSize > 8) onSettingsChange(settings.copy(fontSize = settings.fontSize - 1)) },
                onIncrement = { if (settings.fontSize < 32) onSettingsChange(settings.copy(fontSize = settings.fontSize + 1)) }
            )
        }
    }
}

@Composable
private fun ReaderMarginTab(settings: ReaderSettings, onSettingsChange: (ReaderSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingRow("줄 간격") {
            ReaderStepperField(
                value = "%.1f".format(settings.lineHeight),
                onDecrement = { if (settings.lineHeight > 1.0f) onSettingsChange(settings.copy(lineHeight = (Math.round(settings.lineHeight * 10) - 1) / 10f)) },
                onIncrement = { if (settings.lineHeight < 3.0f) onSettingsChange(settings.copy(lineHeight = (Math.round(settings.lineHeight * 10) + 1) / 10f)) }
            )
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        ReaderSettingRow("문단 간격") {
            ReaderStepperField(
                value = settings.paragraphSpacing.toString(),
                onDecrement = { if (settings.paragraphSpacing > 0) onSettingsChange(settings.copy(paragraphSpacing = settings.paragraphSpacing - 1)) },
                onIncrement = { if (settings.paragraphSpacing < 40) onSettingsChange(settings.copy(paragraphSpacing = settings.paragraphSpacing + 1)) }
            )
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        ReaderSettingRow("상하 여백") {
            ReaderStepperField(
                value = settings.paddingVertical.toString(),
                onDecrement = { if (settings.paddingVertical > -16) onSettingsChange(settings.copy(paddingVertical = settings.paddingVertical - 2)) },
                onIncrement = { if (settings.paddingVertical < 80) onSettingsChange(settings.copy(paddingVertical = settings.paddingVertical + 2)) }
            )
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        ReaderSettingRow("좌우 여백") {
            ReaderStepperField(
                value = settings.paddingHorizontal.toString(),
                onDecrement = { if (settings.paddingHorizontal > -16) onSettingsChange(settings.copy(paddingHorizontal = settings.paddingHorizontal - 2)) },
                onIncrement = { if (settings.paddingHorizontal < 80) onSettingsChange(settings.copy(paddingHorizontal = settings.paddingHorizontal + 2)) }
            )
        }
    }
}

@Composable
private fun ReaderViewerTab(settings: ReaderSettings, onSettingsChange: (ReaderSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingRow("페이지 넘김") {
            ReaderCycleSelectorField(
                options = ReaderPageFlip.entries,
                selected = settings.pageFlip,
                onSelect = { onSettingsChange(settings.copy(pageFlip = it)) },
                labelFor = { it.label },
                forceAbove = true
            )
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        ReaderSettingRow("왼쪽 하단 정보") {
            ReaderCycleSelectorField(
                options = ReaderBottomInfo.entries,
                selected = settings.leftInfo,
                onSelect = { onSettingsChange(settings.copy(leftInfo = it)) },
                labelFor = { it.label }
            )
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        ReaderSettingRow("오른쪽 하단 정보") {
            ReaderCycleSelectorField(
                options = ReaderBottomInfo.entries,
                selected = settings.rightInfo,
                onSelect = { onSettingsChange(settings.copy(rightInfo = it)) },
                labelFor = { it.label }
            )
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "가로모드 두 쪽 보기",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.dualPage,
                onCheckedChange = { onSettingsChange(settings.copy(dualPage = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.Black,
                    uncheckedThumbColor = Color.Black,
                    uncheckedTrackColor = Color.White,
                    uncheckedBorderColor = Color.Black
                )
            )
        }
    }
}

@Composable
private fun ReaderSettingRow(label: String, field: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        field()
    }
}

@Composable
private fun ReaderStepperField(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.width(160.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDecrement, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun <T> ReaderCycleSelectorField(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String,
    forceAbove: Boolean = false
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    val currentIndex = options.indexOf(selected)
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var buttonPositionY by remember { mutableStateOf(0f) }
    var buttonHeightPx by remember { mutableStateOf(0f) }
    var dropdownHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .width(160.dp)
            .onGloballyPositioned { coords ->
                buttonPositionY = coords.positionInRoot().y
                buttonHeightPx = coords.size.height.toFloat()
            }
    ) {
        Row(
            modifier = Modifier.width(160.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onSelect(options[(currentIndex - 1 + options.size) % options.size]) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                labelFor(selected),
                modifier = Modifier
                    .weight(1f)
                    .clickable { dropdownOpen = true; currentPage = 0 },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { onSelect(options[(currentIndex + 1) % options.size]) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (dropdownOpen) {
            val itemHeightPx = with(density) { 44.dp.toPx() }
            val paginationHeightPx = with(density) { 44.dp.toPx() }
            val maxDropdownHeightPx = screenHeightPx * 0.6f
            val needsPagination = options.size * itemHeightPx > maxDropdownHeightPx
            val itemsPerPage = if (needsPagination) {
                ((maxDropdownHeightPx - paginationHeightPx) / itemHeightPx).toInt().coerceAtLeast(1)
            } else {
                options.size
            }
            val totalPages = if (needsPagination) {
                (options.size + itemsPerPage - 1) / itemsPerPage
            } else 1
            val pageStart = currentPage * itemsPerPage
            val pageEnd = minOf(pageStart + itemsPerPage, options.size)
            val visibleOptions = options.subList(pageStart, pageEnd)

            val estimatedDropdownHeightPx = if (needsPagination) maxDropdownHeightPx.toInt() else (options.size * itemHeightPx).toInt()
            val spaceBelow = screenHeightPx - buttonPositionY - buttonHeightPx
            val showAbove = forceAbove || spaceBelow < estimatedDropdownHeightPx
            val offsetY = if (showAbove) {
                -(if (dropdownHeightPx > 0) dropdownHeightPx else estimatedDropdownHeightPx)
            } else {
                buttonHeightPx.toInt()
            }

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, offsetY),
                onDismissRequest = { dropdownOpen = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Black)
                        .onGloballyPositioned { dropdownHeightPx = it.size.height }
                ) {
                    visibleOptions.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option); dropdownOpen = false }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Text(
                                labelFor(option),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                                modifier = Modifier.weight(1f)
                            )
                            if (option == selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (index < visibleOptions.lastIndex || needsPagination) {
                            HorizontalDivider(color = Color(0xFFE0E0E0))
                        }
                    }
                    if (needsPagination) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                modifier = Modifier.size(44.dp),
                                enabled = currentPage > 0
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (currentPage > 0) Color.Black else Color(0xFFBBBBBB)
                                )
                            }
                            Text(
                                "${currentPage + 1} / $totalPages",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black
                            )
                            IconButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                modifier = Modifier.size(44.dp),
                                enabled = currentPage < totalPages - 1
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFBBBBBB)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontLayerPopup(
    currentFontName: String,
    onSelect: (String) -> Unit,
    onFontChanged: (String) -> Unit = {},
    onFontImported: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    var selectedTab by remember { mutableStateOf(0) }
    var fontSortOrder by remember { mutableStateOf(FontSortOrder.NAME_ASC) }
    var systemFontSortOrder by remember { mutableStateOf(SystemFontSortOrder.NAME_ASC) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var sortAnchorHeight by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var confirmImportFont by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDeleteFont by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember { mutableStateOf(ImportedFontStore.load(context)) }

    val itemHeightDp = 56
    val headerHeightDp = 56 + 44 + 2 // header(56) + tabs(44) + tab underline(2)
    val paginationHeightDp = 56
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)

    val pinnedFonts = listOf(FONT_EPUB_ORIGINAL, FONT_SYSTEM)
    val appFonts = remember(fontSortOrder, importedFonts) {
        val all = READER_BUILTIN_FONT_NAMES + importedFonts.map { it.name }
        when (fontSortOrder) {
            FontSortOrder.NAME_ASC -> all.sortedBy { it }
            FontSortOrder.NAME_DESC -> all.sortedByDescending { it }
            FontSortOrder.CREATED_DESC -> (READER_BUILTIN_FONT_NAMES + importedFonts.reversed().map { it.name })
            FontSortOrder.IMPORTED -> all
        }
    }

    val systemFontFileMap = remember { getSystemFontFileMap() }
    val systemFonts = remember(systemFontSortOrder, importedFonts) {
        val importedNames = importedFonts.map { it.name }.toSet()
        systemFontFileMap.keys.filter { it !in importedNames }.let { keys ->
            when (systemFontSortOrder) {
                SystemFontSortOrder.NAME_ASC -> keys.sortedBy { it }
                SystemFontSortOrder.NAME_DESC -> keys.sortedByDescending { it }
            }
        }
    }

    val currentItems: List<String> = if (selectedTab == 0) pinnedFonts + appFonts else systemFonts
    val totalPages = maxOf(1, (currentItems.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = currentItems.drop(currentPage * itemsPerPage).take(itemsPerPage)

    LaunchedEffect(selectedTab) { currentPage = 0 }
    LaunchedEffect(currentItems.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    confirmImportFont?.let { (fontName, filePath) ->
        AlertDialog(
            onDismissRequest = { confirmImportFont = null },
            title = { Text("글꼴 가져오기") },
            text = { Text("${fontName} 글꼴을 가져올까요?") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val srcFile = File(filePath)
                        val destFile = File(ImportedFontStore.getDir(context), srcFile.name)
                        if (!destFile.exists()) srcFile.copyTo(destFile)
                        ImportedFontStore.add(context, fontName, destFile.absolutePath)
                        importedFonts = ImportedFontStore.load(context)
                        onFontImported()
                    } catch (e: Exception) { /* ignore */ }
                    confirmImportFont = null
                }) { Text("예") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportFont = null }) { Text("아니오") }
            }
        )
    }

    confirmDeleteFont?.let { fontName ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFont = null },
            title = { Text("글꼴 삭제") },
            text = { Text("${fontName} 글꼴을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    ImportedFontStore.remove(context, fontName)
                    importedFonts = ImportedFontStore.load(context)
                    onFontImported()
                    if (fontName == currentFontName) onFontChanged(FONT_EPUB_ORIGINAL)
                    confirmDeleteFont = null
                }) { Text("예") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFont = null }) { Text("아니오") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text("글꼴", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.onGloballyPositioned { sortAnchorHeight = it.size.height }) {
                    TextButton(onClick = { sortDropdownExpanded = true }) {
                        Text(
                            if (selectedTab == 0) fontSortOrder.label else systemFontSortOrder.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (sortDropdownExpanded) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, sortAnchorHeight),
                            onDismissRequest = { sortDropdownExpanded = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(modifier = Modifier.width(IntrinsicSize.Max).background(Color.White).border(1.dp, Color.Black)) {
                                if (selectedTab == 0) {
                                    FontSortOrder.entries.forEachIndexed { i, order ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { fontSortOrder = order; sortDropdownExpanded = false }
                                                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                                        ) {
                                            Text(order.label, style = MaterialTheme.typography.bodyMedium, color = Color.Black, modifier = Modifier.weight(1f))
                                            if (fontSortOrder == order) {
                                                Spacer(Modifier.width(16.dp))
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        if (i < FontSortOrder.entries.lastIndex) HorizontalDivider(color = Color(0xFFE0E0E0))
                                    }
                                } else {
                                    SystemFontSortOrder.entries.forEachIndexed { i, order ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { systemFontSortOrder = order; sortDropdownExpanded = false }
                                                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                                        ) {
                                            Text(order.label, style = MaterialTheme.typography.bodyMedium, color = Color.Black, modifier = Modifier.weight(1f))
                                            if (systemFontSortOrder == order) {
                                                Spacer(Modifier.width(16.dp))
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        if (i < SystemFontSortOrder.entries.lastIndex) HorizontalDivider(color = Color(0xFFE0E0E0))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Tabs
            Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                listOf("글꼴", "글꼴 가져오기").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            // Tab underline
            Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (selectedTab == 0) Color.Black else Color.Transparent))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (selectedTab == 1) Color.Black else Color.Transparent))
            }
            HorizontalDivider(color = Color.Black)

            // Content
            Box(modifier = Modifier.weight(1f)) {
                if (currentItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedTab == 0) "글꼴이 없습니다." else "기기에 글꼴이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, fontName ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFCCCCCC))
                            val isImported = selectedTab == 0 && importedFonts.any { it.name == fontName }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickable {
                                        if (selectedTab == 0) {
                                            onSelect(fontName)
                                        } else {
                                            val filePath = systemFontFileMap[fontName] ?: ""
                                            if (filePath.isNotEmpty()) confirmImportFont = Pair(fontName, filePath)
                                        }
                                    }
                                    .padding(start = 16.dp, end = if (isImported) 4.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    fontDisplayName(fontName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedTab == 0 && fontName == currentFontName) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                if (isImported) {
                                    IconButton(
                                        onClick = { confirmDeleteFont = fontName },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            Column {
                if (currentItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable(enabled = currentPage > 0) { currentPage-- }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전", modifier = Modifier.height(16.dp), tint = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC))
                            Text("이전", style = MaterialTheme.typography.bodyMedium, color = if (currentPage > 0) Color.Black else Color(0xFFCCCCCC))
                        }
                        Text("${currentPage + 1}/$totalPages (${currentItems.size}개)", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.clickable(enabled = currentPage < totalPages - 1) { currentPage++ }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("다음", style = MaterialTheme.typography.bodyMedium, color = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음", modifier = Modifier.height(16.dp), tint = if (currentPage < totalPages - 1) Color.Black else Color(0xFFCCCCCC))
                        }
                    }
                }
                HorizontalDivider(color = Color.Black)
            }
        }
    }
}

private fun cfiToPage(cfi: String, spinePageOffsets: Map<Int, Int>, cfiPageMap: Map<String, Int> = emptyMap()): Int {
    if (cfi.isEmpty()) return 0
    cfiPageMap[cfi]?.let { return it }
    if (spinePageOffsets.isEmpty()) return 0
    val step = Regex("/6/(\\d+)").find(cfi)?.groupValues?.get(1)?.toIntOrNull() ?: return 0
    val spineIndex = (step / 2 - 1).coerceAtLeast(0)
    return (spinePageOffsets[spineIndex] ?: 0) + 1
}

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
