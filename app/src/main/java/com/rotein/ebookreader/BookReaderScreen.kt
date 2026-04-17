package com.rotein.ebookreader

import android.content.Intent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotein.ebookreader.reader.BookmarkPopup
import com.rotein.ebookreader.reader.EpubViewer
import com.rotein.ebookreader.reader.FontLayerPopup
import com.rotein.ebookreader.reader.HighlightPopup
import com.rotein.ebookreader.reader.MemoEditorScreen
import com.rotein.ebookreader.reader.MemoListPopup
import com.rotein.ebookreader.reader.MobiViewer
import com.rotein.ebookreader.reader.PdfViewer
import com.rotein.ebookreader.reader.ReaderMenuItem
import com.rotein.ebookreader.reader.ReaderSettingsBottomSheet
import com.rotein.ebookreader.reader.SearchPopup
import com.rotein.ebookreader.reader.TocPopup
import com.rotein.ebookreader.reader.TxtViewer
import com.rotein.ebookreader.ui.components.ActionItem
import com.rotein.ebookreader.ui.components.ActionPopup
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(book: BookFile, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val vm: BookReaderViewModel = viewModel()
    val popupState by vm.popupState.collectAsState()
    val readingState by vm.readingState.collectAsState()
    val contentState by vm.contentState.collectAsState()
    val readerSettings by vm.readerSettings.collectAsState()
    val currentTime by vm.currentTime.collectAsState()
    val onCenterTap = { vm.toggleMenu() }

    val isPdf = book.extension.lowercase() == "pdf"
    val isEpub = book.extension.lowercase() == "epub"

    val tocItems by vm.tocItems.collectAsState()
    val pageCalcState by vm.pageCalcState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val annotationState by vm.annotationState.collectAsState()
    val annotationUiState by vm.annotationUiState.collectAsState()
    val searchState by vm.searchState.collectAsState()
    val viewerWebView = remember { mutableStateOf<WebView?>(null) }
    val onNavigationCompleteRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    val activity = LocalContext.current as? MainActivity
    DisposableEffect(viewerWebView.value) {
        activity?.currentEpubWebView = viewerWebView.value
        onDispose { activity?.currentEpubWebView = null }
    }

    BackHandler {
        vm.setShowMenu(false)
        onClose()
    }
    BackHandler(enabled = popupState.showMenu) { vm.setShowMenu(false) }
    BackHandler(enabled = popupState.showTocPopup) { vm.setShowTocPopup(false) }
    BackHandler(enabled = popupState.showSearchPopup) { vm.setShowSearchPopup(false) }
    BackHandler(enabled = popupState.showBookmarkPopup) { vm.setShowBookmarkPopup(false) }
    BackHandler(enabled = popupState.showHighlightPopup) { vm.setShowHighlightPopup(false) }
    BackHandler(enabled = annotationUiState.highlightActionState != null) { vm.setHighlightAction(null) }
    BackHandler(enabled = popupState.showMemoListPopup) { vm.setShowMemoListPopup(false) }
    BackHandler(enabled = popupState.showMemoEditor) { vm.closeMemoEditor() }
    BackHandler(enabled = annotationUiState.memoActionState != null) { vm.setMemoAction(null) }
    BackHandler(enabled = annotationUiState.combinedAnnotationState != null) { vm.setCombinedAnnotation(null) }
    BackHandler(enabled = popupState.showSettingsPopup) { vm.setShowSettingsPopup(false) }
    BackHandler(enabled = popupState.showFontPopup) { vm.setShowFontPopup(false) }

    LaunchedEffect(popupState.showMenu) {
        if (popupState.showMenu && isEpub) {
            viewerWebView.value?.evaluateJavascript("window._currentCfi || ''") { result ->
                val cfi = result?.removeSurrounding("\"")?.trim().orEmpty()
                if (cfi.isNotEmpty()) vm.updateCurrentCfi(cfi)
            }
        }
    }

    LaunchedEffect(book.path) {
        vm.loadBook(book.path, book.extension.lowercase() == "epub")
    }

    LaunchedEffect(contentState.isContentRendered) {
        if (!contentState.isContentRendered) return@LaunchedEffect
        if (isPdf) return@LaunchedEffect
        if (annotationState.highlights.isNotEmpty()) {
            val json = annotationState.highlights.joinToString(",", "[", "]") {
                """{"id":${it.id},"cfi":"${it.cfi.escapeCfiForJs()}"}"""
            }
            viewerWebView.value?.evaluateJavascript("window._applyHighlights('$json')", null)
        }
        if (annotationState.memos.isNotEmpty()) {
            val json = annotationState.memos.joinToString(",", "[", "]") {
                """{"id":${it.id},"cfi":"${it.cfi.escapeCfiForJs()}"}"""
            }
            viewerWebView.value?.evaluateJavascript("window._applyMemos('$json')", null)
        }
    }

    LaunchedEffect(readingState.currentCfi, annotationState.bookmarks, contentState.isContentRendered) {
        if (!contentState.isContentRendered) return@LaunchedEffect
        if (isPdf) {
            val currentPageCfi = readingState.currentCfi
            val isBookmarked = annotationState.bookmarks.any { it.cfi == currentPageCfi }
            vm.setCurrentPageBookmarked(isBookmarked)
            viewerWebView.value?.evaluateJavascript("window._showBookmarkRibbon($isBookmarked)", null)
            return@LaunchedEffect
        }
        val wv = viewerWebView.value ?: return@LaunchedEffect
        if (readingState.currentCfi.isEmpty() || annotationState.bookmarks.isEmpty()) {
            vm.setCurrentPageBookmarked(false)
            wv.evaluateJavascript("window._showBookmarkRibbon(false)", null)
            return@LaunchedEffect
        }
        val cfiListJson = annotationState.bookmarks.map { it.cfi }.filter { it.isNotEmpty() }
            .joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
        wv.evaluateJavascript("window._isBookmarkedInRange('${cfiListJson.replace("'", "\\'")}')") { result ->
            val matched = result?.removeSurrounding("\"")?.trim() == "true"
            vm.setCurrentPageBookmarked(matched)
            wv.evaluateJavascript("window._showBookmarkRibbon($matched)", null)
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds().testTag("bookReaderScreen")) {
        when (book.extension.lowercase()) {
            "txt"  -> TxtViewer(book.path, onCenterTap)
            "epub" -> EpubViewer(
                path = book.path,
                savedCfi = readingState.savedCfi,
                onCenterTap = onCenterTap,
                onLocationUpdate = { progress, cfi, chapter ->
                    vm.updateLocation(progress, cfi, chapter)
                    vm.saveCfi(cfi)
                },
                onTocLoaded = { tocJson -> vm.onTocLoaded(tocJson) },
                onTocReady = { tocJson -> vm.onTocReady(tocJson) },
                onContentRendered = {
                    if (readingState.savedCfi.isNullOrEmpty()) vm.setLoading(false)
                    vm.setContentRendered(true)
                    if (contentState.scanCacheValid && pageCalcState.spinePageOffsets.isNotEmpty()) {
                        val jsonObj = org.json.JSONObject()
                        pageCalcState.spinePageOffsets.forEach { (k, v) -> jsonObj.put(k.toString(), v) }
                        val charBreaksJs = if (pageCalcState.spineCharPageBreaksJson.isNotEmpty()) "_epub.spineCharPageBreaks=${pageCalcState.spineCharPageBreaksJson};" else ""
                        val js = "_epub.spinePageOffsets=$jsonObj;_epub.totalVisualPages=${readingState.totalPages};$charBreaksJs" +
                            "if(_epub.pendingLocation){reportLocation(_epub.pendingLocation);_epub.pendingLocation=null;}" +
                            "else{var l=_epub.rendition.currentLocation();if(l&&l.start)reportLocation(l);}"
                        viewerWebView.value?.evaluateJavascript(js, null)
                    } else {
                        vm.setScanning(true)
                    }
                    val allCfis = (annotationState.bookmarks.map { it.cfi } + annotationState.highlights.map { it.cfi } + annotationState.memos.map { it.cfi })
                        .filter { it.isNotEmpty() }.distinct()
                    if (allCfis.isNotEmpty()) {
                        val cfiArray = org.json.JSONArray(allCfis)
                        viewerWebView.value?.evaluateJavascript("window._setCfiList('${cfiArray.toString().replace("'", "\\'")}')", null)
                    }
                },
                onHighlight = { text, cfi ->
                    if (cfi.isNotEmpty()) {
                        scope.launch {
                            val saved = vm.addHighlight(cfi, text)
                            val escapedCfi = cfi.escapeCfiForJs()
                            viewerWebView.value?.evaluateJavascript("window._addHighlight(\"$escapedCfi\", ${saved.id})", null)
                        }
                    }
                },
                onChapterChanged = { chapter -> vm.updateChapterTitle(chapter) },
                onMemo = { text, cfi -> vm.onMemoRequest(text, cfi) },
                onHighlightLongPress = { id, x, y, bottom -> vm.onHighlightLongPress(id, x, y, bottom) },
                onMemoLongPress = { id, x, y, bottom -> vm.onMemoLongPress(id, x, y, bottom) },
                onWebViewCreated = { webView -> viewerWebView.value = webView },
                onPageInfoChanged = { page, total ->
                    vm.updatePageInfo(page, total)
                },
                onDebugInfo = { _, _, _, _, _, _ -> },
                onScanStart = { if (!contentState.scanCacheValid) vm.setScanning(true) },
                onScanComplete = { scannedTotal, spinePageOffsetsJson, cfiPageMapJson, charPageBreaksJson ->
                    vm.onScanComplete(scannedTotal, spinePageOffsetsJson, cfiPageMapJson, charPageBreaksJson)
                },
                onSearchResultsPartial = { json -> vm.onSearchResultsPartial(json) },
                onSearchComplete = { vm.onSearchComplete() },
                onNavigationComplete = {
                    vm.setLoading(false)
                    onNavigationCompleteRef.value?.invoke()
                    onNavigationCompleteRef.value = null
                },
                readerSettings = readerSettings
            )
            "pdf"  -> if (readingState.savedCfi == null) LoadingIndicator() else PdfViewer(
                path = book.path,
                savedPage = readingState.savedCfi
                    ?.removePrefix("pdf-page:")?.toIntOrNull() ?: 1,
                pageFlip = readerSettings.pageFlip,
                onCenterTap = onCenterTap,
                onPageChanged = { page, total ->
                    vm.updatePageInfo(page, total)
                },
                onLocationUpdate = { _, pageNum ->
                    val cfi = "pdf-page:$pageNum"
                    vm.updateCurrentCfi(cfi)
                    vm.saveCfi(cfi)
                },
                onContentLoaded = {
                    vm.setLoading(false)
                    vm.setContentRendered(true)
                },
                onTocLoaded = { tocJson -> vm.onTocLoaded(tocJson) },
                onSearchResultsPartial = { json -> vm.onSearchResultsPartial(json) },
                onSearchComplete = { vm.onSearchComplete() },
                onNavigationComplete = {
                    onNavigationCompleteRef.value?.invoke()
                    onNavigationCompleteRef.value = null
                },
                onWebViewCreated = { webView -> viewerWebView.value = webView }
            )
            "mobi" -> MobiViewer(book.path, onCenterTap)
            else   -> CenteredMessage("지원하지 않는 형식입니다.")
        }

        // 북마크 리본은 WebView 내부 HTML로 렌더링 (글자 하위 레이어)

        // 하단 정보 오버레이
        if (!popupState.showMenu && contentState.isContentRendered) {
            val leftText = readerBottomInfoText(readerSettings.leftInfo, book, readingState.chapterTitle, readingState.currentPage, readingState.totalPages, readingState.readingProgress, currentTime)
            val rightText = readerBottomInfoText(readerSettings.rightInfo, book, readingState.chapterTitle, readingState.currentPage, readingState.totalPages, readingState.readingProgress, currentTime)
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
        if (contentState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(EreaderColors.White).clickable(enabled = false) {}.testTag("readerLoading"),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EreaderColors.Black)
            }
        }

        // 투명 스크림 - 클릭 이벤트는 아래 레이어로 통과 (가운데 탭으로 닫기)
        if (popupState.showMenu) {
            Box(modifier = Modifier.fillMaxSize())
        }

        // 바텀 시트 (스크림보다 위, 헤더보다 아래)
        if (popupState.showMenu) {
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
                    if (contentState.isScanning) {
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
                                    "${(readingState.readingProgress * 100).toInt()}% 읽음",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${readingState.currentPage} / ${readingState.totalPages}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(EreaderSpacing.S))
                            LinearProgressIndicator(
                                progress = { readingState.readingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = EreaderColors.Black,
                                trackColor = EreaderColors.Gray,
                                strokeCap = StrokeCap.Square,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                            Spacer(Modifier.height(EreaderSpacing.M))
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { vm.setShowTocPopup(true) }
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
                                    readingState.chapterTitle.ifEmpty { "목차" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                    color = EreaderColors.Black,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(color = EreaderColors.Black)

                        ReaderMenuItem(Icons.Default.Search, "본문 검색", onClick = {
                            vm.setShowSearchPopup(true)
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        if (!isPdf) {
                        ReaderMenuItem(Icons.Default.Star, "하이라이트", onClick = {
                            vm.setShowHighlightPopup(true)
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        ReaderMenuItem(Icons.Default.Edit, "메모", onClick = {
                            vm.setShowMemoListPopup(true)
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        }
                        ReaderMenuItem(Icons.Default.Bookmark, "북마크", onClick = {
                            vm.setShowBookmarkPopup(true)
                        })
                        HorizontalDivider(color = EreaderColors.Gray)
                        ReaderMenuItem(Icons.Default.Settings, "설정", onClick = {
                            vm.setShowSettingsPopup(true)
                            vm.setShowMenu(false)
                        })

                        Spacer(Modifier.height(EreaderSpacing.XXS))
                    }
                    } // else
                }
            }
        }

        // 헤더 (최상위 레이어)
        if (popupState.showMenu) {
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
                        onBack = {
                            vm.setShowMenu(false)
                            onClose()
                        }
                    ) {
                        IconButton(onClick = {
                            if (readingState.currentCfi.isEmpty()) return@IconButton
                            if (annotationState.isCurrentPageBookmarked) {
                                if (isPdf) {
                                    vm.removeBookmarksByCfis(setOf(readingState.currentCfi))
                                } else {
                                scope.launch {
                                    val wv = viewerWebView.value
                                    if (wv != null) {
                                        val cfiListJson = annotationState.bookmarks.map { it.cfi }.filter { it.isNotEmpty() }
                                            .joinToString(",", "[", "]") { "\"${it.replace("\"", "\\\"")}\"" }
                                        wv.evaluateJavascript(
                                            "(function(){try{var cfis=${cfiListJson};var startCfi=window._currentCfi;var endCfi=window._currentEndCfi;var epubcfi=new ePub.CFI();var matched=[];for(var i=0;i<cfis.length;i++){var c=cfis[i];if(c===startCfi||(endCfi&&epubcfi.compare(c,startCfi)>=0&&epubcfi.compare(c,endCfi)<=0)){matched.push(c);}}return JSON.stringify(matched);}catch(e){return '[]';}})()"
                                        ) { result ->
                                            val matchedCfis = try {
                                                val arr = org.json.JSONArray(result?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "[]")
                                                (0 until arr.length()).map { arr.getString(it) }.toSet()
                                            } catch (_: Exception) { emptySet() }
                                            vm.removeBookmarksByCfis(matchedCfis)
                                        }
                                    }
                                }
                                }
                            } else {
                                if (isPdf) {
                                    val pageNum = readingState.currentCfi.removePrefix("pdf-page:").toIntOrNull() ?: 0
                                    val bookmark = Bookmark(
                                        bookPath = book.path,
                                        cfi = readingState.currentCfi,
                                        chapterTitle = "",
                                        excerpt = "${pageNum}페이지",
                                        page = pageNum,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    vm.addBookmark(bookmark)
                                } else {
                                val wv = viewerWebView.value
                                val saveBookmark = { cfi: String, excerpt: String ->
                                    val bookmark = Bookmark(
                                        bookPath = book.path,
                                        cfi = cfi,
                                        chapterTitle = readingState.chapterTitle,
                                        excerpt = excerpt,
                                        page = readingState.currentPage,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    vm.addBookmark(bookmark)
                                }
                                if (wv != null) {
                                    wv.evaluateJavascript(
                                        "(function(){try{var loc=_epub.rendition.currentLocation();var cfi=(loc&&loc.start&&loc.start.cfi)?loc.start.cfi:'';var excerpt='';if(cfi){var r=_epub.rendition.getRange(cfi);if(r){var doc=r.startContainer.ownerDocument;var er=doc.createRange();er.setStart(r.startContainer,r.startOffset);er.setEnd(doc.body,doc.body.childNodes.length);excerpt=er.toString().trim().replace(/\\s+/g,' ').substring(0,150);}}if(!excerpt){var c=_epub.rendition.getContents();if(c&&c[0]){excerpt=(c[0].document.body.innerText||'').trim().replace(/\\s+/g,' ').substring(0,150);}}return JSON.stringify({cfi:cfi,excerpt:excerpt});}catch(e){return JSON.stringify({cfi:'',excerpt:''});}})()"
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
                            }
                        }) {
                            Icon(
                                if (annotationState.isCurrentPageBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "북마크",
                                tint = EreaderColors.Black
                            )
                        }
                    }
                }
            }
        }

        // 목차 팝업 (헤더보다 위, 최상위 레이어)
        if (popupState.showTocPopup) {
            TocPopup(
                tocItems = tocItems,
                bookTitle = book.metadata?.title ?: book.name,
                currentChapterTitle = readingState.chapterTitle,
                totalBookPages = readingState.totalPages,
                onNavigate = { href ->
                    if (isPdf) {
                        if (onNavigationCompleteRef.value != null) return@TocPopup
                        onNavigationCompleteRef.value = { vm.setShowTocPopup(false); vm.setShowMenu(false) }
                        val pageNum = href.removePrefix("pdf-page:").toIntOrNull() ?: 1
                        viewerWebView.value?.evaluateJavascript("window._goToPage($pageNum)", null)
                    } else {
                        if (onNavigationCompleteRef.value != null) return@TocPopup
                        onNavigationCompleteRef.value = { vm.setShowTocPopup(false); vm.setShowMenu(false) }
                        viewerWebView.value?.post {
                            viewerWebView.value?.evaluateJavascript(
                                "window._displayHref(\"${href.escapeCfiForJs()}\")",
                                null
                            )
                        }
                    }
                },
                onDismiss = { vm.setShowTocPopup(false); onNavigationCompleteRef.value = null }
            )
        }

        // 검색 팝업
        if (popupState.showSearchPopup) {
            SearchPopup(
                searchResults = searchState.results,
                isSearching = searchState.isSearching,
                tocItems = tocItems,
                initialQuery = searchState.query,
                onSearch = { query ->
                    vm.startSearch(query)
                    val escaped = query.escapeCfiForJs()
                    viewerWebView.value?.post {
                        viewerWebView.value?.evaluateJavascript("window._setSearchHighlight(\"$escaped\")", null)
                        viewerWebView.value?.evaluateJavascript("window._search(\"$escaped\")", null)
                    }
                },
                onNavigate = { cfi, page ->
                    if (isPdf) {
                        if (onNavigationCompleteRef.value != null) return@SearchPopup
                        onNavigationCompleteRef.value = { vm.setShowSearchPopup(false); vm.setShowMenu(false) }
                        val pageNum = cfi.removePrefix("pdf-page:").toIntOrNull() ?: page
                        viewerWebView.value?.evaluateJavascript("window._goToPage($pageNum)", null)
                    } else {
                        if (onNavigationCompleteRef.value != null) return@SearchPopup
                        onNavigationCompleteRef.value = { vm.setShowSearchPopup(false); vm.setShowMenu(false) }
                        viewerWebView.value?.post {
                            val escaped = cfi.escapeCfiForJs()
                            viewerWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                        }
                    }
                },
                onClear = {
                    vm.clearSearch()
                    viewerWebView.value?.post {
                        viewerWebView.value?.evaluateJavascript("window._clearSearchHighlight()", null)
                    }
                },
                onDismiss = { vm.setShowSearchPopup(false); onNavigationCompleteRef.value = null }
            )
        }

        // 하이라이트 팝업
        if (popupState.showHighlightPopup) {
            HighlightPopup(
                highlights = annotationState.highlights,
                spinePageOffsets = pageCalcState.spinePageOffsets,
                cfiPageMap = pageCalcState.cfiPageMap,
                onNavigate = { cfi ->
                    if (onNavigationCompleteRef.value != null) return@HighlightPopup
                    onNavigationCompleteRef.value = { vm.setShowHighlightPopup(false); vm.setShowMenu(false) }
                    viewerWebView.value?.post {
                        val escaped = cfi.escapeCfiForJs()
                        viewerWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                },
                onDelete = { highlight ->
                    vm.removeHighlight(highlight.id)
                    viewerWebView.value?.evaluateJavascript("window._removeHighlight(${highlight.id})", null)
                },
                onDismiss = { vm.setShowHighlightPopup(false); onNavigationCompleteRef.value = null }
            )
        }

        annotationUiState.highlightActionState?.let { state ->
            ActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                actions = listOf(
                    ActionItem("하이라이트 삭제") {
                        val id = state.id
                        vm.setHighlightAction(null)
                        vm.removeHighlight(id)
                        viewerWebView.value?.evaluateJavascript("window._removeHighlight($id)", null)
                    },
                    ActionItem("메모") {
                        val hl = annotationState.highlights.find { it.id == state.id }
                        vm.openMemoEditor(hl?.text ?: "", hl?.cfi ?: "", annotationState.memos.find { it.cfi == hl?.cfi })
                        vm.setHighlightAction(null)
                    },
                    ActionItem("공유") {
                        val text = annotationState.highlights.find { it.id == state.id }?.text ?: ""
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        vm.setHighlightAction(null)
                    },
                ),
                onDismiss = { vm.setHighlightAction(null) },
            )
        }

        annotationUiState.combinedAnnotationState?.let { state ->
            ActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                actions = listOf(
                    ActionItem("하이라이트 삭제") {
                        val hid = state.highlightId
                        vm.setCombinedAnnotation(null)
                        vm.removeHighlight(hid)
                        viewerWebView.value?.evaluateJavascript("window._removeHighlight($hid)", null)
                    },
                    ActionItem("메모 편집") {
                        val memo = annotationState.memos.find { it.id == state.memoId }
                        vm.setCombinedAnnotation(null)
                        if (memo != null) {
                            vm.openMemoEditor(memo.text, memo.cfi, memo)
                        }
                    },
                    ActionItem("메모 삭제") {
                        val mid = state.memoId
                        vm.setCombinedAnnotation(null)
                        vm.removeMemo(mid)
                        viewerWebView.value?.evaluateJavascript("window._removeMemo($mid)", null)
                    },
                    ActionItem("공유") {
                        val hl = annotationState.highlights.find { it.id == state.highlightId }
                        val memo = annotationState.memos.find { it.id == state.memoId }
                        val text = buildString {
                            hl?.text?.let { append(it) }
                            memo?.note?.takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        vm.setCombinedAnnotation(null)
                    },
                ),
                onDismiss = { vm.setCombinedAnnotation(null) },
            )
        }

        annotationUiState.memoActionState?.let { state ->
            val memo = annotationState.memos.find { it.id == state.id }
            ActionPopup(
                selectionY = state.y,
                selectionBottom = state.bottom,
                selectionCx = state.x,
                actions = listOf(
                    ActionItem("하이라이트") {
                        vm.setMemoAction(null)
                        if (memo != null) {
                            scope.launch {
                                val saved = vm.addHighlightFromMemo(memo)
                                val escapedCfi = memo.cfi.escapeCfiForJs()
                                viewerWebView.value?.evaluateJavascript("window._addHighlight(\"$escapedCfi\", ${saved.id})", null)
                            }
                        }
                    },
                    ActionItem("메모 편집") {
                        vm.setMemoAction(null)
                        if (memo != null) {
                            vm.openMemoEditor(memo.text, memo.cfi, memo)
                        }
                    },
                    ActionItem("메모 삭제") {
                        val id = state.id
                        vm.setMemoAction(null)
                        vm.removeMemo(id)
                        viewerWebView.value?.evaluateJavascript("window._removeMemo($id)", null)
                    },
                    ActionItem("공유") {
                        val text = memo?.let { "${it.text}\n\n${it.note}" } ?: ""
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        vm.setMemoAction(null)
                    },
                ),
                onDismiss = { vm.setMemoAction(null) },
            )
        }

        if (popupState.showMemoListPopup) {
            MemoListPopup(
                memos = annotationState.memos,
                spinePageOffsets = pageCalcState.spinePageOffsets,
                cfiPageMap = pageCalcState.cfiPageMap,
                onNavigate = { memo ->
                    if (onNavigationCompleteRef.value != null) return@MemoListPopup
                    onNavigationCompleteRef.value = { vm.setShowMemoListPopup(false); vm.setShowMenu(false) }
                    viewerWebView.value?.post {
                        val escaped = memo.cfi.escapeCfiForJs()
                        viewerWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                },
                onEdit = { memo ->
                    vm.openMemoEditor(memo.text, memo.cfi, memo)
                },
                onDelete = { memo ->
                    vm.removeMemo(memo.id)
                    viewerWebView.value?.evaluateJavascript("window._removeMemo(${memo.id})", null)
                },
                onDismiss = { vm.setShowMemoListPopup(false); onNavigationCompleteRef.value = null }
            )
        }

        if (popupState.showMemoEditor) {
            MemoEditorScreen(
                selectedText = annotationUiState.editingMemo?.text ?: annotationUiState.pendingMemoText,
                initialNote = annotationUiState.editingMemo?.note ?: "",
                onSave = { note ->
                    scope.launch {
                        val saved = vm.saveMemo(note, annotationUiState.editingMemo, annotationUiState.pendingMemoText, annotationUiState.pendingMemoCfi)
                        if (saved != null && annotationUiState.editingMemo == null && annotationUiState.pendingMemoCfi.isNotEmpty()) {
                            val escapedCfi = annotationUiState.pendingMemoCfi.escapeCfiForJs()
                            viewerWebView.value?.evaluateJavascript("window._addMemo(\"$escapedCfi\", ${saved.id})", null)
                        }
                        vm.closeMemoEditor()
                    }
                },
                onCancel = { vm.closeMemoEditor(); onNavigationCompleteRef.value = null },
                onDelete = if (annotationUiState.editingMemo != null) ({
                    val id = annotationUiState.editingMemo!!.id
                    vm.removeMemo(id)
                    viewerWebView.value?.evaluateJavascript("window._removeMemo($id)", null)
                    vm.closeMemoEditor()
                }) else null,
                onNavigate = if (annotationUiState.editingMemo != null) ({
                    if (onNavigationCompleteRef.value != null) return@MemoEditorScreen
                    val cfi = annotationUiState.editingMemo!!.cfi
                    onNavigationCompleteRef.value = { vm.closeMemoEditor(); vm.setShowMenu(false) }
                    viewerWebView.value?.post {
                        val escaped = cfi.escapeCfiForJs()
                        viewerWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                    }
                }) else null
            )
        }

        // 설정 바텀시트
        if (popupState.showSettingsPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { vm.setShowSettingsPopup(false) } }
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
                        onSettingsChange = { vm.updateSettings(it) },
                        onDismiss = { vm.setShowSettingsPopup(false) },
                        onOpenFontPopup = { vm.setShowFontPopup(true) },
                        isPdf = isPdf
                    )
                }
            }
        }

        // 글꼴 팝업
        if (popupState.showFontPopup) {
            FontLayerPopup(
                currentFontName = readerSettings.fontName,
                onSelect = { vm.updateSettings(readerSettings.copy(fontName = it)); vm.setShowFontPopup(false) },
                onFontChanged = { vm.updateSettings(readerSettings.copy(fontName = it)) },
                onFontImported = {},
                onDismiss = { vm.setShowFontPopup(false) }
            )
        }

        // 북마크 팝업
        if (popupState.showBookmarkPopup) {
            BookmarkPopup(
                bookmarks = annotationState.bookmarks,
                spinePageOffsets = pageCalcState.spinePageOffsets,
                cfiPageMap = pageCalcState.cfiPageMap,
                onNavigate = { cfi ->
                    if (isPdf) {
                        if (onNavigationCompleteRef.value != null) return@BookmarkPopup
                        onNavigationCompleteRef.value = { vm.setShowBookmarkPopup(false); vm.setShowMenu(false) }
                        val pageNum = cfi.removePrefix("pdf-page:").toIntOrNull() ?: 1
                        viewerWebView.value?.evaluateJavascript("window._goToPage($pageNum)", null)
                    } else {
                        if (onNavigationCompleteRef.value != null) return@BookmarkPopup
                        onNavigationCompleteRef.value = { vm.setShowBookmarkPopup(false); vm.setShowMenu(false) }
                        viewerWebView.value?.post {
                            val escaped = cfi.escapeCfiForJs()
                            viewerWebView.value?.evaluateJavascript("window._displayCfi(\"$escaped\")", null)
                        }
                    }
                },
                onDelete = { bookmark -> vm.removeBookmark(bookmark) },
                onDismiss = { vm.setShowBookmarkPopup(false); onNavigationCompleteRef.value = null }
            )
        }
    }
}

