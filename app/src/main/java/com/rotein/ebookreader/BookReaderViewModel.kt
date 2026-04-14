package com.rotein.ebookreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PageCalcState(
    val spinePageOffsets: Map<Int, Int> = emptyMap(),
    val cfiPageMap: Map<String, Int> = emptyMap(),
    val spineCharPageBreaksJson: String = ""
)

data class ReadingState(
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val readingProgress: Float = 0f,
    val currentCfi: String = "",
    val chapterTitle: String = "",
    val savedCfi: String? = null,
    val prevProgress: Float = -1f
)

data class ContentState(
    val isLoading: Boolean = true,
    val isContentRendered: Boolean = false,
    val locationsReady: Boolean = false,
    val isScanning: Boolean = false,
    val scanCacheValid: Boolean = false
)

data class AnnotationState(
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val memos: List<Memo> = emptyList(),
    val isCurrentPageBookmarked: Boolean = false
)

data class PopupState(
    val showMenu: Boolean = false,
    val showTocPopup: Boolean = false,
    val showSearchPopup: Boolean = false,
    val showBookmarkPopup: Boolean = false,
    val showHighlightPopup: Boolean = false,
    val showMemoListPopup: Boolean = false,
    val showMemoEditor: Boolean = false,
    val showSettingsPopup: Boolean = false,
    val showFontPopup: Boolean = false
)

data class SearchState(
    val query: String = "",
    val results: List<SearchResultItem>? = null,
    val isSearching: Boolean = false
)

data class HighlightActionState(val id: Long, val x: Float, val y: Float, val bottom: Float)
data class MemoActionState(val id: Long, val x: Float, val y: Float, val bottom: Float)
data class CombinedAnnotationState(val highlightId: Long, val memoId: Long, val x: Float, val y: Float, val bottom: Float)

data class AnnotationUiState(
    val highlightActionState: HighlightActionState? = null,
    val memoActionState: MemoActionState? = null,
    val combinedAnnotationState: CombinedAnnotationState? = null,
    val editingMemo: Memo? = null,
    val pendingMemoText: String = "",
    val pendingMemoCfi: String = ""
)

class BookReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BookDatabase.getInstance(application)
    private val dao = db.bookReadRecordDao()
    private val bookmarkDao = db.bookmarkDao()
    private val highlightDao = db.highlightDao()
    private val memoDao = db.memoDao()

    var bookPath: String = ""
        private set

    private val _readingState = MutableStateFlow(ReadingState())
    val readingState: StateFlow<ReadingState> = _readingState.asStateFlow()

    private val _contentState = MutableStateFlow(ContentState())
    val contentState: StateFlow<ContentState> = _contentState.asStateFlow()

    private val _popupState = MutableStateFlow(PopupState())
    val popupState: StateFlow<PopupState> = _popupState.asStateFlow()

    private val _readerSettings = MutableStateFlow(ReaderSettingsStore.load(getApplication()))
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _tocItems = MutableStateFlow<List<TocItem>>(emptyList())
    val tocItems: StateFlow<List<TocItem>> = _tocItems.asStateFlow()

    private val _pageCalcState = MutableStateFlow(PageCalcState())
    val pageCalcState: StateFlow<PageCalcState> = _pageCalcState.asStateFlow()

    private val _annotationState = MutableStateFlow(AnnotationState())
    val annotationState: StateFlow<AnnotationState> = _annotationState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _annotationUiState = MutableStateFlow(AnnotationUiState())
    val annotationUiState: StateFlow<AnnotationUiState> = _annotationUiState.asStateFlow()

    init {
        // 설정 변경 시 자동 저장
        viewModelScope.launch {
            _readerSettings.collect { settings ->
                withContext(Dispatchers.IO) {
                    ReaderSettingsStore.save(getApplication(), settings)
                }
            }
        }

        // 시계 업데이트
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            while (true) {
                _currentTime.value = sdf.format(java.util.Date())
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    fun loadBook(path: String, isEpub: Boolean) {
        bookPath = path
        _readingState.value = ReadingState()
        _contentState.value = ContentState(isLoading = isEpub)
        _tocItems.value = emptyList()
        _pageCalcState.value = PageCalcState()

        viewModelScope.launch {
            val record = withContext(Dispatchers.IO) { dao.getByPath(path) }
            _readingState.update { it.copy(savedCfi = record?.lastCfi ?: "") }

            val cachedToc = record?.tocJson.orEmpty()
            if (cachedToc.isNotEmpty()) {
                try {
                    _tocItems.value = parseTocJson(org.json.JSONArray(cachedToc))
                    _contentState.update { it.copy(locationsReady = true) }
                } catch (_: Exception) {}
            }

            val currentFingerprint = _readerSettings.value.layoutFingerprint()
            if (record != null && record.cachedSettingsFingerprint == currentFingerprint && record.cachedTotalPages > 0) {
                _readingState.update { it.copy(totalPages = record.cachedTotalPages) }
                try {
                    val obj = org.json.JSONObject(record.cachedSpinePageOffsetsJson)
                    val map = mutableMapOf<Int, Int>()
                    obj.keys().forEach { key -> map[key.toInt()] = obj.getInt(key) }
                    _pageCalcState.update {
                        it.copy(
                            spinePageOffsets = map,
                            spineCharPageBreaksJson = record.cachedSpineCharPageBreaksJson
                        )
                    }
                } catch (_: Exception) {}
                _contentState.update { it.copy(scanCacheValid = true) }
            }

            val bookmarks = withContext(Dispatchers.IO) { bookmarkDao.getByBook(path) }
            val highlights = withContext(Dispatchers.IO) { highlightDao.getByBook(path) }
            val memos = withContext(Dispatchers.IO) { memoDao.getByBook(path) }
            _annotationState.value = AnnotationState(bookmarks = bookmarks, highlights = highlights, memos = memos)
        }
    }

    fun saveCfi(cfi: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.upsertCfi(bookPath, cfi) }
    }

    fun onTocLoaded(tocJson: String) {
        if (!_contentState.value.locationsReady) {
            try { _tocItems.value = parseTocJson(org.json.JSONArray(tocJson)) } catch (_: Exception) {}
        }
    }

    fun onTocReady(tocJson: String) {
        try {
            val newItems = parseTocJson(org.json.JSONArray(tocJson))
            val hasPageData = flattenToc(newItems).any { it.page > 0 }
            if (hasPageData || flattenToc(_tocItems.value).none { it.page > 0 }) {
                _tocItems.value = newItems
                _contentState.update { it.copy(locationsReady = true) }
            }
            if (hasPageData) {
                viewModelScope.launch(Dispatchers.IO) { dao.upsertTocJson(bookPath, tocJson) }
            }
        } catch (_: Exception) {}
    }

    fun onScanComplete(scannedTotal: Int, spinePageOffsetsJson: String, cfiPageMapJson: String, charPageBreaksJson: String) {
        if (scannedTotal != _readingState.value.totalPages) {
            _readingState.update { it.copy(totalPages = scannedTotal) }
        }
        _contentState.update { it.copy(isScanning = false) }

        try {
            val obj = org.json.JSONObject(spinePageOffsetsJson)
            val offsetMap = mutableMapOf<Int, Int>()
            obj.keys().forEach { key -> offsetMap[key.toInt()] = obj.getInt(key) }
            _pageCalcState.update { it.copy(spinePageOffsets = offsetMap, spineCharPageBreaksJson = charPageBreaksJson) }

            val fingerprint = _readerSettings.value.layoutFingerprint()
            viewModelScope.launch(Dispatchers.IO) {
                dao.upsertPageScanCache(bookPath, scannedTotal, spinePageOffsetsJson, charPageBreaksJson, fingerprint)
            }
        } catch (_: Exception) {}

        try {
            val obj = org.json.JSONObject(cfiPageMapJson)
            val cfiMap = mutableMapOf<String, Int>()
            obj.keys().forEach { key -> cfiMap[key] = obj.getInt(key) }
            _pageCalcState.update { it.copy(cfiPageMap = cfiMap) }
        } catch (_: Exception) {}

        val sr = _searchState.value.results
        if (!sr.isNullOrEmpty()) {
            _searchState.update {
                it.copy(results = recalcSearchPages(sr, _pageCalcState.value.spinePageOffsets, charPageBreaksJson))
            }
        }

        remapAnnotationPages()
    }

    fun addToCfiPageMap(cfi: String, page: Int) {
        _pageCalcState.update { it.copy(cfiPageMap = it.cfiPageMap + (cfi to page)) }
    }

    fun setCurrentPageBookmarked(bookmarked: Boolean) {
        _annotationState.update { it.copy(isCurrentPageBookmarked = bookmarked) }
    }

    fun addBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { bookmarkDao.insert(bookmark) }
            _annotationState.update { it.copy(bookmarks = it.bookmarks + bookmark.copy(id = id)) }
        }
    }

    fun removeBookmarksByCfis(cfis: Set<String>) {
        viewModelScope.launch {
            for (cfi in cfis) {
                withContext(Dispatchers.IO) { bookmarkDao.deleteByCfi(bookPath, cfi) }
            }
            _annotationState.update { it.copy(bookmarks = it.bookmarks.filter { bm -> bm.cfi !in cfis }) }
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { bookmarkDao.deleteByCfi(bookmark.bookPath, bookmark.cfi) }
            _annotationState.update { it.copy(bookmarks = it.bookmarks.filter { it.id != bookmark.id }) }
        }
    }

    /** Returns the saved Highlight with its ID set. Caller can use it for JS injection. */
    suspend fun addHighlight(cfi: String, text: String): Highlight {
        val rs = _readingState.value
        if (rs.currentPage > 0) addToCfiPageMap(cfi, rs.currentPage)
        val highlight = Highlight(
            bookPath = bookPath,
            cfi = cfi,
            text = text,
            chapterTitle = rs.chapterTitle,
            page = rs.currentPage,
            createdAt = System.currentTimeMillis()
        )
        val id = withContext(Dispatchers.IO) { highlightDao.insert(highlight) }
        val saved = highlight.copy(id = id)
        _annotationState.update { it.copy(highlights = it.highlights + saved) }
        return saved
    }

    fun removeHighlight(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { highlightDao.deleteById(id) }
            _annotationState.update { it.copy(highlights = it.highlights.filter { it.id != id }) }
        }
    }

    suspend fun addHighlightFromMemo(memo: Memo): Highlight {
        val rs = _readingState.value
        val pcs = _pageCalcState.value
        val highlight = Highlight(
            bookPath = bookPath,
            cfi = memo.cfi,
            text = memo.text,
            chapterTitle = rs.chapterTitle,
            page = memo.page.takeIf { it > 0 } ?: cfiToPage(memo.cfi, pcs.spinePageOffsets, pcs.cfiPageMap),
            createdAt = System.currentTimeMillis()
        )
        val id = withContext(Dispatchers.IO) { highlightDao.insert(highlight) }
        val saved = highlight.copy(id = id)
        _annotationState.update { it.copy(highlights = it.highlights + saved) }
        return saved
    }

    /** Returns saved Memo (new or updated). Null if pendingCfi is empty for new memo. */
    suspend fun saveMemo(note: String, existingMemo: Memo?, pendingText: String, pendingCfi: String): Memo? {
        if (existingMemo != null) {
            withContext(Dispatchers.IO) { memoDao.updateNote(existingMemo.id, note) }
            _annotationState.update {
                it.copy(memos = it.memos.map { m -> if (m.id == existingMemo.id) m.copy(note = note) else m })
            }
            return existingMemo.copy(note = note)
        } else if (pendingCfi.isNotEmpty()) {
            val rs = _readingState.value
            if (rs.currentPage > 0) addToCfiPageMap(pendingCfi, rs.currentPage)
            val newMemo = Memo(
                bookPath = bookPath,
                cfi = pendingCfi,
                text = pendingText,
                note = note,
                chapterTitle = rs.chapterTitle,
                page = rs.currentPage,
                createdAt = System.currentTimeMillis()
            )
            val id = withContext(Dispatchers.IO) { memoDao.insert(newMemo) }
            val saved = newMemo.copy(id = id)
            _annotationState.update { it.copy(memos = it.memos + saved) }
            return saved
        }
        return null
    }

    fun removeMemo(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { memoDao.deleteById(id) }
            _annotationState.update { it.copy(memos = it.memos.filter { it.id != id }) }
        }
    }

    fun startSearch(query: String) {
        _searchState.update { it.copy(query = query, isSearching = true, results = emptyList()) }
    }

    fun onSearchResultsPartial(json: String) {
        try {
            val partial = parseSearchResults(org.json.JSONArray(json))
            _searchState.update { it.copy(results = (it.results ?: emptyList()) + partial) }
        } catch (_: Exception) {}
    }

    fun onSearchComplete() {
        _searchState.update { it.copy(isSearching = false) }
    }

    fun clearSearch() {
        _searchState.update { SearchState() }
    }

    fun remapAnnotationPages() {
        val pcs = _pageCalcState.value
        viewModelScope.launch(Dispatchers.IO) {
            val `as` = _annotationState.value
            val newBookmarks = `as`.bookmarks.map { bm ->
                val newPage = cfiToPage(bm.cfi, pcs.spinePageOffsets, pcs.cfiPageMap)
                if (newPage > 0 && newPage != bm.page) { bookmarkDao.updatePage(bm.id, newPage); bm.copy(page = newPage) } else bm
            }
            val newHighlights = `as`.highlights.map { hl ->
                val newPage = cfiToPage(hl.cfi, pcs.spinePageOffsets, pcs.cfiPageMap)
                if (newPage > 0 && newPage != hl.page) { highlightDao.updatePage(hl.id, newPage); hl.copy(page = newPage) } else hl
            }
            val newMemos = `as`.memos.map { m ->
                val newPage = cfiToPage(m.cfi, pcs.spinePageOffsets, pcs.cfiPageMap)
                if (newPage > 0 && newPage != m.page) { memoDao.updatePage(m.id, newPage); m.copy(page = newPage) } else m
            }
            _annotationState.update { it.copy(bookmarks = newBookmarks, highlights = newHighlights, memos = newMemos) }
        }
    }

    fun updateLocation(progress: Float, cfi: String, chapter: String) {
        _contentState.update { it.copy(locationsReady = true) }
        val rs = _readingState.value
        var newPage = rs.currentPage
        var newProgress = rs.readingProgress
        if (rs.prevProgress >= 0f && rs.currentPage > 0 && rs.totalPages > 0) {
            if (progress > rs.prevProgress) {
                newPage = (rs.currentPage + 1).coerceAtMost(rs.totalPages)
            } else if (progress < rs.prevProgress) {
                newPage = (rs.currentPage - 1).coerceAtLeast(1)
            }
            newProgress = newPage.toFloat() / rs.totalPages.toFloat()
        }
        _readingState.update {
            it.copy(
                currentCfi = cfi,
                chapterTitle = chapter,
                currentPage = newPage,
                readingProgress = newProgress,
                prevProgress = progress
            )
        }
    }

    fun updateCurrentCfi(cfi: String) {
        _readingState.update { it.copy(currentCfi = cfi) }
    }

    fun updateChapterTitle(chapter: String) {
        _readingState.update { it.copy(chapterTitle = chapter) }
    }

    fun updatePageInfo(page: Int, total: Int) {
        _readingState.update {
            val newTotal = if (total > 0) total else it.totalPages
            val progress = if (newTotal > 0) page.toFloat() / newTotal.toFloat() else it.readingProgress
            it.copy(currentPage = page, totalPages = newTotal, readingProgress = progress)
        }
    }

    fun setLoading(loading: Boolean) {
        _contentState.update { it.copy(isLoading = loading) }
    }

    fun setContentRendered(rendered: Boolean) {
        _contentState.update { it.copy(isContentRendered = rendered) }
    }

    fun setScanning(scanning: Boolean) {
        _contentState.update { it.copy(isScanning = scanning) }
    }

    fun setScanCacheValid(valid: Boolean) {
        _contentState.update { it.copy(scanCacheValid = valid) }
    }

    fun setLocationsReady(ready: Boolean) {
        _contentState.update { it.copy(locationsReady = ready) }
    }

    fun setSavedCfi(cfi: String?) {
        _readingState.update { it.copy(savedCfi = cfi) }
    }

    fun setTotalPages(total: Int) {
        _readingState.update { it.copy(totalPages = total) }
    }

    fun updateSettings(settings: ReaderSettings) {
        _readerSettings.value = settings
    }

    fun toggleMenu() {
        _popupState.update { it.copy(showMenu = !it.showMenu) }
    }

    fun setShowMenu(show: Boolean) {
        _popupState.update { it.copy(showMenu = show) }
    }

    fun setShowTocPopup(show: Boolean) {
        _popupState.update { it.copy(showTocPopup = show) }
    }

    fun setShowSearchPopup(show: Boolean) {
        _popupState.update { it.copy(showSearchPopup = show) }
    }

    fun setShowBookmarkPopup(show: Boolean) {
        _popupState.update { it.copy(showBookmarkPopup = show) }
    }

    fun setShowHighlightPopup(show: Boolean) {
        _popupState.update { it.copy(showHighlightPopup = show) }
    }

    fun setShowMemoListPopup(show: Boolean) {
        _popupState.update { it.copy(showMemoListPopup = show) }
    }

    fun setShowMemoEditor(show: Boolean) {
        _popupState.update { it.copy(showMemoEditor = show) }
    }

    fun setShowSettingsPopup(show: Boolean) {
        _popupState.update { it.copy(showSettingsPopup = show) }
    }

    fun setShowFontPopup(show: Boolean) {
        _popupState.update { it.copy(showFontPopup = show) }
    }

    fun setHighlightAction(state: HighlightActionState?) {
        _annotationUiState.update { it.copy(highlightActionState = state, memoActionState = null, combinedAnnotationState = null) }
    }

    fun setMemoAction(state: MemoActionState?) {
        _annotationUiState.update { it.copy(memoActionState = state, highlightActionState = null, combinedAnnotationState = null) }
    }

    fun setCombinedAnnotation(state: CombinedAnnotationState?) {
        _annotationUiState.update { it.copy(combinedAnnotationState = state, highlightActionState = null, memoActionState = null) }
    }

    fun clearAnnotationActions() {
        _annotationUiState.update { it.copy(highlightActionState = null, memoActionState = null, combinedAnnotationState = null) }
    }

    fun openMemoEditor(text: String, cfi: String, existingMemo: Memo?) {
        _annotationUiState.update {
            it.copy(pendingMemoText = text, pendingMemoCfi = cfi, editingMemo = existingMemo)
        }
        _popupState.update { it.copy(showMemoEditor = true) }
    }

    fun closeMemoEditor() {
        _popupState.update { it.copy(showMemoEditor = false) }
        _annotationUiState.update { it.copy(editingMemo = null, pendingMemoText = "", pendingMemoCfi = "") }
    }

    fun onHighlightLongPress(id: Long, x: Float, y: Float, bottom: Float) {
        val cfi = _annotationState.value.highlights.find { it.id == id }?.cfi
        val overlappingMemo = if (cfi != null) _annotationState.value.memos.find { it.cfi == cfi } else null
        if (overlappingMemo != null) {
            setCombinedAnnotation(CombinedAnnotationState(id, overlappingMemo.id, x, y, bottom))
        } else {
            setHighlightAction(HighlightActionState(id, x, y, bottom))
        }
    }

    fun onMemoLongPress(id: Long, x: Float, y: Float, bottom: Float) {
        val cfi = _annotationState.value.memos.find { it.id == id }?.cfi
        val overlappingHighlight = if (cfi != null) _annotationState.value.highlights.find { it.cfi == cfi } else null
        if (overlappingHighlight != null) {
            setCombinedAnnotation(CombinedAnnotationState(overlappingHighlight.id, id, x, y, bottom))
        } else {
            setMemoAction(MemoActionState(id, x, y, bottom))
        }
    }

    fun onMemoRequest(text: String, cfi: String) {
        val existingMemo = _annotationState.value.memos.find { it.cfi == cfi }
        openMemoEditor(text, cfi, existingMemo)
    }
}
