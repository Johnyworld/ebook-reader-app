package com.rotein.ebookreader.reader

internal class PdfBridge(
    private val onPageChangedCallback: (currentPage: Int, totalPages: Int) -> Unit,
    private val onLocationUpdateCallback: (progress: Float, pageNum: Int, pageTitle: String) -> Unit = { _, _, _ -> },
    private val onContentLoadedCallback: () -> Unit = {},
    private val onTocLoadedCallback: (tocJson: String) -> Unit = {},
    private val onSearchResultsPartialCallback: (resultsJson: String) -> Unit = {},
    private val onSearchCompleteCallback: () -> Unit = {},
    private val onNavigationCompleteCallback: () -> Unit = {},
    private val onLoadErrorCallback: (message: String) -> Unit = {}
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onPageChanged(currentPage: Int, totalPages: Int) {
        mainHandler.post { onPageChangedCallback(currentPage, totalPages) }
    }

    @android.webkit.JavascriptInterface
    fun onLocationUpdate(progress: Float, pageNum: Int, pageTitle: String) {
        mainHandler.post { onLocationUpdateCallback(progress, pageNum, pageTitle) }
    }

    @android.webkit.JavascriptInterface
    fun onContentLoaded() {
        mainHandler.post { onContentLoadedCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onTocLoaded(tocJson: String) {
        mainHandler.post { onTocLoadedCallback(tocJson) }
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
    fun onNavigationComplete() {
        mainHandler.post { onNavigationCompleteCallback() }
    }

    /** PDF.js 로드/렌더링 실패 (구형 WebView 문법 미지원, 손상된 파일 등) */
    @android.webkit.JavascriptInterface
    fun onLoadError(message: String) {
        mainHandler.post { onLoadErrorCallback(message) }
    }
}
