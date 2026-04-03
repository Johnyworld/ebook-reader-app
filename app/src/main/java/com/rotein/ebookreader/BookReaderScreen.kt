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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FormatListBulleted
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile
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
    val page: Int = 0
)

private fun parseSearchResults(json: JSONArray): List<SearchResultItem> {
    val items = mutableListOf<SearchResultItem>()
    for (i in 0 until json.length()) {
        val obj = json.getJSONObject(i)
        items.add(SearchResultItem(
            cfi = obj.optString("cfi", ""),
            excerpt = obj.optString("excerpt", ""),
            chapter = obj.optString("chapter", ""),
            page = obj.optInt("page", 0)
        ))
    }
    return items
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
    val scope = rememberCoroutineScope()

    var readingProgress by remember(book.path) { mutableStateOf(0f) }
    var chapterTitle by remember(book.path) { mutableStateOf("") }
    var savedCfi by remember(book.path) { mutableStateOf<String?>(null) }
    var isLoading by remember(book.path) { mutableStateOf(book.extension.lowercase() == "epub") }
    var locationsReady by remember(book.path) { mutableStateOf(false) }
    var tocItems by remember(book.path) { mutableStateOf<List<TocItem>>(emptyList()) }
    var showTocPopup by remember { mutableStateOf(false) }
    var showSearchPopup by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val epubWebView = remember { mutableStateOf<WebView?>(null) }
    var currentPage by remember(book.path) { mutableStateOf(0) }
    var totalPages by remember(book.path) { mutableStateOf(0) }

    BackHandler { onClose() }
    BackHandler(enabled = showMenu) { showMenu = false }
    BackHandler(enabled = showTocPopup) { showTocPopup = false }
    BackHandler(enabled = showSearchPopup) { showSearchPopup = false }

    LaunchedEffect(book.path) {
        val record = withContext(Dispatchers.IO) { dao.getByPath(book.path) }
        savedCfi = record?.lastCfi ?: ""
        totalPages = record?.totalPages ?: 0
        val cachedToc = record?.tocJson.orEmpty()
        if (cachedToc.isNotEmpty()) {
            try {
                tocItems = parseTocJson(JSONArray(cachedToc))
                locationsReady = true
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (book.extension.lowercase()) {
            "txt"  -> TxtViewer(book.path, onCenterTap)
            "epub" -> EpubViewer(
                path = book.path,
                savedCfi = savedCfi,
                onCenterTap = onCenterTap,
                onLocationUpdate = { progress, cfi, chapter ->
                    locationsReady = true
                    if (progress > 0f) readingProgress = progress
                    if (chapter.isNotEmpty()) chapterTitle = chapter
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
                onContentRendered = { isLoading = false },
                onChapterChanged = { chapter -> chapterTitle = chapter },
                onWebViewCreated = { webView -> epubWebView.value = webView },
                onPageInfoChanged = { page, _ ->
                    currentPage = page
                },
                onScanComplete = { scannedTotal ->
                    if (scannedTotal != totalPages) {
                        totalPages = scannedTotal
                    }
                    scope.launch(Dispatchers.IO) { dao.upsertTotalPages(book.path, scannedTotal) }
                },
                onSearchResultsPartial = { json ->
                    try {
                        val partial = parseSearchResults(JSONArray(json))
                        searchResults = (searchResults ?: emptyList()) + partial
                    } catch (_: Exception) {}
                },
                onSearchComplete = { isSearching = false }
            )
            "pdf"  -> PdfViewer(book.path, onCenterTap)
            "mobi" -> MobiViewer(book.path, onCenterTap)
            else   -> CenteredMessage("지원하지 않는 형식입니다.")
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
                            if (chapterTitle.isNotEmpty()) {
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
                                        chapterTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.Black)

                        ReaderMenuItem(Icons.Default.Search, "본문 검색", onClick = {
                            searchResults = null
                            isSearching = false
                            showSearchPopup = true
                        })
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
                onSearch = { query ->
                    isSearching = true
                    searchResults = emptyList()
                    val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    epubWebView.value?.post {
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
                onDismiss = { showSearchPopup = false }
            )
        }
    }
}

@Composable
private fun ReaderMenuItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
    onSearch: (String) -> Unit,
    onNavigate: (cfi: String, page: Int) -> Unit,
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
    var query by remember { mutableStateOf("") }
    var searchedQuery by remember { mutableStateOf("") }
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
    onScanComplete: (totalPages: Int) -> Unit = {},
    onSearchResultsPartial: (resultsJson: String) -> Unit = {},
    onSearchComplete: () -> Unit = {}
) {
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
        bookDir == null || savedCfi == null -> LoadingIndicator()
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
                        addJavascriptInterface(EpubBridge(onLocationUpdate, onTocLoaded, onContentRendered, onChapterChanged, onTocReady, onPageInfoChanged, onScanComplete, onSearchResultsPartial, onSearchComplete), "Android")
                    }
                    onWebViewCreated(webView)
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
                        buildEpubJsHtml(opfPath!!, savedCfi),
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

private fun buildEpubJsHtml(opfPath: String, savedCfi: String) = """<!DOCTYPE html>
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
    flow: "paginated",
    manager: "default",
    minSpreadWidth: 9999
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
            Android.onPageInfoChanged((_spinePageOffsets[idx] || 0) + pg, _totalVisualPages);
        }
    } catch(e) {}
}

var _totalLocations = 0;
var _spinePageCounts = {};
var _spinePageOffsets = {};
var _totalVisualPages = 0;
var _locationsReady = false;
var _rendered = false;
rendition.on("relocated", function(location) {
    if (!_rendered) { _rendered = true; Android.onContentRendered(); }
    try {
        var href = (location.start && location.start.href) ? location.start.href : "";
        if (href && book.navigation && book.navigation.toc) {
            var chapter = findTocEntry(href, book.navigation.toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
    try {
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        if (cfi) Android.onLocationChanged(0, cfi, "");
    } catch(e) {}
    if (_locationsReady) reportLocation(location);
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
    var items = book.spine ? (book.spine.items || []) : [];
    if (items.length === 0) return;
    var scanDiv = document.createElement('div');
    scanDiv.style.cssText = 'position:fixed;left:-' + (window.innerWidth + 10) + 'px;top:0;width:' + window.innerWidth + 'px;height:' + window.innerHeight + 'px;overflow:hidden;';
    document.body.appendChild(scanDiv);
    var scanRendition = book.renderTo(scanDiv, {
        width: window.innerWidth,
        height: window.innerHeight,
        spread: "none",
        flow: "paginated",
        manager: "default",
        minSpreadWidth: 9999
    });
    var i = 0;
    function next() {
        if (i >= items.length) {
            var offset = 0;
            for (var j = 0; j < items.length; j++) {
                _spinePageOffsets[j] = offset;
                offset += (_spinePageCounts[j] || 1);
            }
            _totalVisualPages = offset;
            document.body.removeChild(scanDiv);
            Android.onScanComplete(_totalVisualPages);
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
            var loc = rendition.currentLocation();
            if (loc && loc.start) {
                var idx = loc.start.index !== undefined ? loc.start.index : 0;
                var pg = loc.start.displayed ? loc.start.displayed.page : 1;
                Android.onPageInfoChanged((_spinePageOffsets[idx] || 0) + pg, _totalVisualPages);
            }
            return;
        }
        scanRendition.display(items[i].href).then(function() {
            var loc = scanRendition.currentLocation();
            _spinePageCounts[i] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
            i++;
            next();
        });
    }
    next();
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
    var loc = rendition.currentLocation();
    if (loc && loc.start) {
        reportLocation(loc);
    }
    computeVisualPages();
});

var _savedCfi = "${savedCfi.replace("\"", "\\\"")}";
rendition.display(_savedCfi.length > 0 ? _savedCfi : undefined);
window._prev = function() { rendition.prev(); };
window._next = function() { rendition.next(); };
window._displayHref = function(href) { rendition.display(href); };
window._displayCfi = function(cfi) { rendition.display(cfi); };
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
                                var sectionPages = _spinePageCounts[spineIndex] || 1;
                                var pageWithin = Math.floor((pos / Math.max(fullText.length, 1)) * sectionPages);
                                matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
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
                            found.push({ cfi: cfi, excerpt: fullText.substring(s, e).replace(/\s+/g, ' ').trim(), chapter: chapter, page: matchPage });
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
    private val onScanCompleteCallback: (totalPages: Int) -> Unit = {},
    private val onSearchResultsPartialCallback: (resultsJson: String) -> Unit = {},
    private val onSearchCompleteCallback: () -> Unit = {}
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
    fun onScanComplete(totalPages: Int) {
        mainHandler.post { onScanCompleteCallback(totalPages) }
    }

    @android.webkit.JavascriptInterface
    fun onSearchResultsPartial(resultsJson: String) {
        mainHandler.post { onSearchResultsPartialCallback(resultsJson) }
    }

    @android.webkit.JavascriptInterface
    fun onSearchComplete() {
        mainHandler.post { onSearchCompleteCallback() }
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
