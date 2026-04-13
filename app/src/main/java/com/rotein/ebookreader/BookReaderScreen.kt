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
import androidx.compose.foundation.layout.RowScope
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
import com.rotein.ebookreader.ui.components.PaginationBar
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
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.components.ActionPopup
import com.rotein.ebookreader.ui.components.EreaderTabBar
import com.rotein.ebookreader.ui.components.ReaderCycleSelectorField
import com.rotein.ebookreader.ui.components.ReaderSettingRow
import com.rotein.ebookreader.ui.components.ReaderStepperField
import com.rotein.ebookreader.ui.components.ActionItem
import com.rotein.ebookreader.reader.TxtViewer
import com.rotein.ebookreader.reader.PdfViewer
import com.rotein.ebookreader.reader.MobiViewer
import com.rotein.ebookreader.reader.EpubBridge
import com.rotein.ebookreader.reader.EpubViewer
import com.rotein.ebookreader.reader.buildEpubJsHtml
import com.rotein.ebookreader.reader.TocPopup
import com.rotein.ebookreader.reader.SearchPopup
import com.rotein.ebookreader.reader.BookmarkPopup
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing
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
import androidx.compose.ui.zIndex
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
    val onNavigationCompleteRef = remember { mutableStateOf<(() -> Unit)?>(null) }
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
                    if (savedCfi.isNullOrEmpty()) isLoading = false
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
                onNavigationComplete = {
                    isLoading = false
                    onNavigationCompleteRef.value?.invoke()
                    onNavigationCompleteRef.value = null
                },
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
        //             .background(EreaderColors.White),
        //         style = MaterialTheme.typography.bodySmall,
        //         color = EreaderColors.Black
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
                        .padding(horizontal = maxOf(readerSettings.paddingHorizontal, 4).dp, vertical = EreaderSpacing.XS),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(leftText ?: "", style = MaterialTheme.typography.bodySmall, color = EreaderColors.DarkGray)
                    Text(rightText ?: "", style = MaterialTheme.typography.bodySmall, color = EreaderColors.DarkGray)
                }
            }
        }

        // 로딩 오버레이
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(EreaderColors.White).clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EreaderColors.Black)
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
                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.L)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EreaderColors.Black)
                        .pointerInput(Unit) { detectTapGestures {} },
                    color = EreaderColors.White
                ) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.L),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("도서 정보를 읽고 있습니다.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 진행 상황
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)) {
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
                            Spacer(Modifier.height(EreaderSpacing.S))
                            LinearProgressIndicator(
                                progress = { readingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = EreaderColors.Black,
                                trackColor = EreaderColors.Gray,
                                strokeCap = StrokeCap.Square,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                            }
                            Spacer(Modifier.height(EreaderSpacing.M))
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { showTocPopup = true }
                                    .border(1.dp, EreaderColors.Black, RoundedCornerShape(4.dp))
                                    .padding(horizontal = EreaderSpacing.S, vertical = EreaderSpacing.XS),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = EreaderColors.Black
                                )
                                Text(
                                    chapterTitle.ifEmpty { "목차" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                    color = EreaderColors.Black
                                )
                            }
                        }

                        HorizontalDivider(color = EreaderColors.Black)

                        ReaderMenuItem(Icons.Default.Search, "본문 검색", onClick = {
                            showSearchPopup = true
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        ReaderMenuItem(Icons.Default.Star, "하이라이트", onClick = {
                            showHighlightPopup = true
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        ReaderMenuItem(Icons.Default.Edit, "메모", onClick = {
                            showMemoListPopup = true
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        ReaderMenuItem(Icons.Default.Bookmark, "북마크", onClick = {
                            showBookmarkPopup = true
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        ReaderMenuItem(Icons.Default.Settings, "설정", onClick = {
                            showSettingsPopup = true
                            showMenu = false
                        })

                        Spacer(Modifier.height(EreaderSpacing.XXS))
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
                    color = EreaderColors.White
                ) {
                    PopupHeaderBar(
                        title = book.metadata?.title ?: book.name,
                        onBack = onClose
                    ) {
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
                                tint = EreaderColors.Black
                            )
                        }
                    }
                }
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
                    if (onNavigationCompleteRef.value != null) return@TocPopup
                    onNavigationCompleteRef.value = { showTocPopup = false; showMenu = false }
                    epubWebView.value?.post {
                        epubWebView.value?.evaluateJavascript(
                            "window._displayHref(\"${href.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\")",
                            null
                        )
                    }
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
                    if (onNavigationCompleteRef.value != null) return@SearchPopup
                    onNavigationCompleteRef.value = { showSearchPopup = false; showMenu = false }
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
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
                    if (onNavigationCompleteRef.value != null) return@HighlightPopup
                    onNavigationCompleteRef.value = { showHighlightPopup = false; showMenu = false }
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
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
            ActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                actions = listOf(
                    ActionItem("하이라이트 삭제") {
                        val id = state.id
                        highlightActionState = null
                        scope.launch {
                            withContext(Dispatchers.IO) { highlightDao.deleteById(id) }
                            epubWebView.value?.evaluateJavascript("window._removeHighlight($id)", null)
                            highlights = highlights.filter { it.id != id }
                        }
                    },
                    ActionItem("메모") {
                        val hl = highlights.find { it.id == state.id }
                        pendingMemoText = hl?.text ?: ""
                        pendingMemoCfi = hl?.cfi ?: ""
                        editingMemo = memos.find { it.cfi == pendingMemoCfi }
                        showMemoEditor = true
                        highlightActionState = null
                    },
                    ActionItem("공유") {
                        val text = highlights.find { it.id == state.id }?.text ?: ""
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        highlightActionState = null
                    },
                ),
                onDismiss = { highlightActionState = null },
            )
        }

        combinedAnnotationState?.let { state ->
            ActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                actions = listOf(
                    ActionItem("하이라이트 삭제") {
                        val hid = state.highlightId
                        combinedAnnotationState = null
                        scope.launch {
                            withContext(Dispatchers.IO) { highlightDao.deleteById(hid) }
                            epubWebView.value?.evaluateJavascript("window._removeHighlight($hid)", null)
                            highlights = highlights.filter { it.id != hid }
                        }
                    },
                    ActionItem("메모 편집") {
                        val memo = memos.find { it.id == state.memoId }
                        combinedAnnotationState = null
                        if (memo != null) {
                            editingMemo = memo
                            pendingMemoText = memo.text
                            pendingMemoCfi = memo.cfi
                            showMemoEditor = true
                        }
                    },
                    ActionItem("메모 삭제") {
                        val mid = state.memoId
                        combinedAnnotationState = null
                        scope.launch {
                            withContext(Dispatchers.IO) { memoDao.deleteById(mid) }
                            epubWebView.value?.evaluateJavascript("window._removeMemo($mid)", null)
                            memos = memos.filter { it.id != mid }
                        }
                    },
                    ActionItem("공유") {
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
                ),
                onDismiss = { combinedAnnotationState = null },
            )
        }

        memoActionState?.let { state ->
            val memo = memos.find { it.id == state.id }
            ActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                actions = listOf(
                    ActionItem("하이라이트") {
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
                    ActionItem("메모 편집") {
                        memoActionState = null
                        if (memo != null) {
                            editingMemo = memo
                            pendingMemoText = memo.text
                            pendingMemoCfi = memo.cfi
                            showMemoEditor = true
                        }
                    },
                    ActionItem("메모 삭제") {
                        val id = state.id
                        memoActionState = null
                        scope.launch {
                            withContext(Dispatchers.IO) { memoDao.deleteById(id) }
                            epubWebView.value?.evaluateJavascript("window._removeMemo($id)", null)
                            memos = memos.filter { it.id != id }
                        }
                    },
                    ActionItem("공유") {
                        val text = memo?.let { "${it.text}\n\n${it.note}" } ?: ""
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        memoActionState = null
                    },
                ),
                onDismiss = { memoActionState = null },
            )
        }

        if (showMemoListPopup) {
            MemoListPopup(
                memos = memos,
                spinePageOffsets = spinePageOffsets,
                cfiPageMap = cfiPageMap,
                onNavigate = { memo ->
                    if (onNavigationCompleteRef.value != null) return@MemoListPopup
                    onNavigationCompleteRef.value = { showMemoListPopup = false; showMenu = false }
                    epubWebView.value?.post {
                        val escaped = memo.cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
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
                    if (onNavigationCompleteRef.value != null) return@MemoEditorScreen
                    val cfi = editingMemo!!.cfi
                    onNavigationCompleteRef.value = { showMemoEditor = false; showMenu = false; editingMemo = null }
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
                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.L)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EreaderColors.Black)
                        .pointerInput(Unit) { detectTapGestures {} },
                    color = EreaderColors.White
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
                    if (onNavigationCompleteRef.value != null) return@BookmarkPopup
                    onNavigationCompleteRef.value = { showBookmarkPopup = false; showMenu = false }
                    epubWebView.value?.post {
                        val escaped = cfi.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        epubWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
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
            .padding(horizontal = EreaderSpacing.L),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
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
    val paginationHeightDp = 72
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(HighlightSortStore.load(context)) }
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

    FullScreenPopup {
            PopupHeaderBar(title = "하이라이트", onBack = onDismiss) {
                EreaderDropdownMenu(
                    items = BookmarkSortOrder.entries.toList(),
                    selectedItem = sortOrder,
                    onSelect = { sortOrder = it },
                    label = { it.label },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (highlights.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("하이라이트가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, highlight ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(88.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f).fillMaxHeight()
                                        .clickable { onNavigate(highlight.cfi) }
                                        .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = EreaderSpacing.XS),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val highlightPage = highlight.page.takeIf { it > 0 } ?: cfiToPage(highlight.cfi, spinePageOffsets, cfiPageMap)
                                        if (highlightPage > 0) {
                                            Text("p.$highlightPage", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = EreaderColors.Black)
                                        }
                                        Text(
                                            highlight.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EreaderColors.DarkGray,
                                            modifier = Modifier.weight(1f).padding(start = if (highlightPage > 0) EreaderSpacing.S else 0.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                                        )
                                    }
                                    Text(
                                        highlight.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                                    )
                                    Text(dateFormat.format(Date(highlight.createdAt)), style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray)
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
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = "${currentPage + 1}/$totalPages (${sortedHighlights.size}건)",
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
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

    FullScreenPopup {
            PopupHeaderBar(title = "메모", onBack = onCancel) {
                TextButton(onClick = { onSave(note) }) {
                    Text("저장", color = EreaderColors.Black)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(EreaderSpacing.L),
                verticalArrangement = Arrangement.spacedBy(EreaderSpacing.L)
            ) {
                if (selectedText.isNotEmpty()) {
                    Text(
                        selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EreaderColors.Gray, RoundedCornerShape(4.dp))
                            .padding(EreaderSpacing.M)
                    )
                }
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, EreaderColors.Gray, RoundedCornerShape(4.dp))
                        .padding(EreaderSpacing.M),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = EreaderColors.Black),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) {
                                Text("메모를 입력하세요.", style = MaterialTheme.typography.bodyMedium, color = EreaderColors.Gray)
                            }
                            inner()
                        }
                    }
                )
            }
            if (onDelete != null || onNavigate != null) {
                HorizontalDivider(color = EreaderColors.Black)
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Text("삭제", color = EreaderColors.Black)
                        }
                    }
                    if (onDelete != null && onNavigate != null) {
                        Box(Modifier.width(1.dp).height(20.dp).background(EreaderColors.Black))
                    }
                    if (onNavigate != null) {
                        TextButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                            Text("페이지로 이동", color = EreaderColors.Black)
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
    val paginationHeightDp = 72
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(MemoSortStore.load(context)) }
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

    FullScreenPopup {
            PopupHeaderBar(title = "메모", onBack = onDismiss) {
                EreaderDropdownMenu(
                    items = BookmarkSortOrder.entries.toList(),
                    selectedItem = sortOrder,
                    onSelect = { sortOrder = it },
                    label = { it.label },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (memos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("메모가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, memo ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(112.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f).fillMaxHeight()
                                        .clickable { onNavigate(memo); onEdit(memo) }
                                        .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = EreaderSpacing.XS),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val memoPage = memo.page.takeIf { it > 0 } ?: cfiToPage(memo.cfi, spinePageOffsets, cfiPageMap)
                                        if (memoPage > 0) {
                                            Text("p.$memoPage", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = EreaderColors.Black)
                                        }
                                        Text(
                                            memo.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EreaderColors.DarkGray,
                                            modifier = Modifier.weight(1f).padding(start = if (memoPage > 0) EreaderSpacing.S else 0.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                                        )
                                    }
                                    Text(
                                        memo.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                                    )
                                    if (memo.note.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.XS),
                                            modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                                        ) {
                                            Icon(Icons.Default.ModeComment, contentDescription = null, modifier = Modifier.size(12.dp), tint = EreaderColors.DarkGray)
                                            Text(memo.note, style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text(dateFormat.format(Date(memo.createdAt)), style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray)
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
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = "${currentPage + 1}/$totalPages (${sortedMemos.size}건)",
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
                }
            }
    }
}

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
            EreaderTabBar(
                tabs = tabLabels,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                trailingContent = {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
            )
        }
        HorizontalDivider(color = EreaderColors.Black)

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
        HorizontalDivider(color = EreaderColors.Gray)
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
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow("문단 간격") {
            ReaderStepperField(
                value = settings.paragraphSpacing.toString(),
                onDecrement = { if (settings.paragraphSpacing > 0) onSettingsChange(settings.copy(paragraphSpacing = settings.paragraphSpacing - 1)) },
                onIncrement = { if (settings.paragraphSpacing < 40) onSettingsChange(settings.copy(paragraphSpacing = settings.paragraphSpacing + 1)) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow("상하 여백") {
            ReaderStepperField(
                value = settings.paddingVertical.toString(),
                onDecrement = { if (settings.paddingVertical > -16) onSettingsChange(settings.copy(paddingVertical = settings.paddingVertical - 2)) },
                onIncrement = { if (settings.paddingVertical < 80) onSettingsChange(settings.copy(paddingVertical = settings.paddingVertical + 2)) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
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
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow("왼쪽 하단 정보") {
            ReaderCycleSelectorField(
                options = ReaderBottomInfo.entries,
                selected = settings.leftInfo,
                onSelect = { onSettingsChange(settings.copy(leftInfo = it)) },
                labelFor = { it.label }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow("오른쪽 하단 정보") {
            ReaderCycleSelectorField(
                options = ReaderBottomInfo.entries,
                selected = settings.rightInfo,
                onSelect = { onSettingsChange(settings.copy(rightInfo = it)) },
                labelFor = { it.label }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.S),
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
                    checkedThumbColor = EreaderColors.White,
                    checkedTrackColor = EreaderColors.Black,
                    uncheckedThumbColor = EreaderColors.Black,
                    uncheckedTrackColor = EreaderColors.White,
                    uncheckedBorderColor = EreaderColors.Black
                )
            )
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
    var currentPage by remember { mutableStateOf(0) }
    var confirmImportFont by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDeleteFont by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember { mutableStateOf(ImportedFontStore.load(context)) }

    val itemHeightDp = 56
    val headerHeightDp = 56 + 48 + 2 // header(56) + tabs(48) + tab underline(2)
    val paginationHeightDp = 72
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

    FullScreenPopup {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = EreaderSpacing.XS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text("글꼴", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selectedTab == 0) {
                    EreaderDropdownMenu(
                        items = FontSortOrder.entries.toList(),
                        selectedItem = fontSortOrder,
                        onSelect = { fontSortOrder = it },
                        label = { it.label },
                    )
                } else {
                    EreaderDropdownMenu(
                        items = SystemFontSortOrder.entries.toList(),
                        selectedItem = systemFontSortOrder,
                        onSelect = { systemFontSortOrder = it },
                        label = { it.label },
                    )
                }
            }
            EreaderTabBar(
                tabs = listOf("글꼴", "글꼴 가져오기"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            HorizontalDivider(color = EreaderColors.Black)

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
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
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
                                    .padding(start = EreaderSpacing.L, end = if (isImported) EreaderSpacing.XS else EreaderSpacing.L),
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
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = "${currentPage + 1}/$totalPages (${currentItems.size}개)",
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
                }
            }
    }
}

