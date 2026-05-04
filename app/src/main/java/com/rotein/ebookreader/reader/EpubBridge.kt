package com.rotein.ebookreader.reader

internal class EpubBridge(
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
    private val onSelectionTappedCallback: (text: String, x: Float, y: Float, bottom: Float, cfi: String, isAtPageEnd: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    private val onAutoSelectReadyCallback: (cssX: Float, cssY: Float) -> Unit = { _, _ -> },
    private val onNavigationCompleteCallback: () -> Unit = {},
    private val onPrevTransitionDoneCallback: () -> Unit = {},
    private val onNextTransitionDoneCallback: () -> Unit = {},
    private val onResumeRestoreCompleteCallback: () -> Unit = {},
    private val onSettingsApplyCompleteCallback: () -> Unit = {}
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

    @android.webkit.JavascriptInterface
    fun onAutoSelectReady(cssX: Float, cssY: Float) {
        mainHandler.post { onAutoSelectReadyCallback(cssX, cssY) }
    }

    @android.webkit.JavascriptInterface
    fun onNavigationComplete() {
        mainHandler.post { onNavigationCompleteCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onNextTransitionDone() {
        mainHandler.post { onNextTransitionDoneCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onPrevTransitionDone() {
        mainHandler.post { onPrevTransitionDoneCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onResumeRestoreComplete() {
        mainHandler.post { onResumeRestoreCompleteCallback() }
    }

    @android.webkit.JavascriptInterface
    fun onSettingsApplyComplete() {
        mainHandler.post { onSettingsApplyCompleteCallback() }
    }

}
