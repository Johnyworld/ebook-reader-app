package com.rotein.ebookreader.reader

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.rotein.ebookreader.CenteredMessage
import com.rotein.ebookreader.ReaderPageFlip
import java.io.File

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Composable
internal fun PdfViewer(
    path: String,
    savedPage: Int = 1,
    pageFlip: ReaderPageFlip = ReaderPageFlip.LR_PREV_NEXT,
    onCenterTap: () -> Unit,
    onPageChanged: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    onLocationUpdate: (progress: Float, pageNum: Int) -> Unit = { _, _ -> },
    onContentLoaded: () -> Unit = {},
    onTocLoaded: (tocJson: String) -> Unit = {},
    onSearchResultsPartial: (resultsJson: String) -> Unit = {},
    onSearchComplete: () -> Unit = {},
    onNavigationComplete: () -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val file = remember(path) { File(path) }
    if (!file.exists() || !file.canRead()) {
        CenteredMessage("PDF 파일을 읽을 수 없습니다.")
        return
    }

    var contentLoaded by remember(path) { mutableStateOf(false) }
    val webViewRef = remember { java.util.concurrent.atomic.AtomicReference<WebView?>(null) }
    val pageFlipRef = remember { java.util.concurrent.atomic.AtomicReference(pageFlip) }
    pageFlipRef.set(pageFlip)

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val webView = WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = true
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = false
                        webViewClient = WebViewClient()

                        addJavascriptInterface(PdfBridge(
                            onPageChangedCallback = onPageChanged,
                            onLocationUpdateCallback = { progress, pageNum, _ -> onLocationUpdate(progress, pageNum) },
                            onContentLoadedCallback = {
                                contentLoaded = true
                                onContentLoaded()
                            },
                            onTocLoadedCallback = onTocLoaded,
                            onSearchResultsPartialCallback = onSearchResultsPartial,
                            onSearchCompleteCallback = onSearchComplete,
                            onNavigationCompleteCallback = onNavigationComplete
                        ), "Android")
                    }
                    webViewRef.set(webView)
                    onWebViewCreated(webView)

                    val overlay = android.view.View(ctx).apply {
                        var currentZoomScale = 1f
                        var wasMultiTouch = false
                        var panTracking = false
                        var lastPanX = 0f
                        var lastPanY = 0f
                        val density = ctx.resources.displayMetrics.density

                        val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                val factor = detector.scaleFactor
                                val focusX = detector.focusX / density
                                val focusY = detector.focusY / density
                                currentZoomScale = (currentZoomScale * factor).coerceIn(1f, 5f)
                                webView.evaluateJavascript("window._zoomBy($factor,$focusX,$focusY)", null)
                                return true
                            }
                            override fun onScaleEnd(detector: ScaleGestureDetector) {
                                if (currentZoomScale < 1.05f) {
                                    currentZoomScale = 1f
                                    webView.evaluateJavascript("window._resetZoom()", null)
                                }
                            }
                        })

                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                val x = e.x
                                val y = e.y
                                val w = this@apply.width.toFloat()
                                val h = this@apply.height.toFloat()
                                val doPrev = { currentZoomScale = 1f; webView.evaluateJavascript("window._prevPage()", null) }
                                val doNext = { currentZoomScale = 1f; webView.evaluateJavascript("window._nextPage()", null) }
                                when (pageFlipRef.get()) {
                                    ReaderPageFlip.LR_PREV_NEXT -> when {
                                        x < w / 3f -> doPrev()
                                        x > w * 2f / 3f -> doNext()
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.LR_NEXT_PREV -> when {
                                        x < w / 3f -> doNext()
                                        x > w * 2f / 3f -> doPrev()
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_PREV_NEXT -> when {
                                        y < h / 3f -> doPrev()
                                        y > h * 2f / 3f -> doNext()
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_NEXT_PREV -> when {
                                        y < h / 3f -> doNext()
                                        y > h * 2f / 3f -> doPrev()
                                        else -> onCenterTap()
                                    }
                                }
                                this@apply.performClick()
                                return true
                            }
                        })

                        setOnTouchListener { _, event ->
                            if (event.pointerCount > 1) wasMultiTouch = true
                            scaleDetector.onTouchEvent(event)

                            // 핀치 중이 아닌 클린 싱글 터치만 제스처 디텍터에 전달
                            if (!wasMultiTouch) {
                                gestureDetector.onTouchEvent(event)
                            }

                            // 줌 상태에서 1손가락 드래그 → 팬
                            if (currentZoomScale > 1.05f && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        lastPanX = event.x
                                        lastPanY = event.y
                                        panTracking = true
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        if (panTracking) {
                                            val dx = (event.x - lastPanX) / density
                                            val dy = (event.y - lastPanY) / density
                                            webView.evaluateJavascript("window._panBy($dx,$dy)", null)
                                        }
                                        lastPanX = event.x
                                        lastPanY = event.y
                                    }
                                }
                            } else {
                                panTracking = false
                            }

                            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                                wasMultiTouch = false
                                panTracking = false
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
                        "file:///android_asset/",
                        buildPdfHtml(path, savedPage),
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
