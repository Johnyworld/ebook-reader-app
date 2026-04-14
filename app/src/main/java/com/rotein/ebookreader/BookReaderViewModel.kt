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

            // Annotation loading will be added in Task 5
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
    }

    fun addToCfiPageMap(cfi: String, page: Int) {
        _pageCalcState.update { it.copy(cfiPageMap = it.cfiPageMap + (cfi to page)) }
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
}
