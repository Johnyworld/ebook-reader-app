package com.rotein.ebookreader.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.util.Xml
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.rotein.ebookreader.CenteredMessage
import com.rotein.ebookreader.FONT_EPUB_ORIGINAL
import com.rotein.ebookreader.ImportedFontStore
import com.rotein.ebookreader.LoadingIndicator
import com.rotein.ebookreader.ReaderPageFlip
import com.rotein.ebookreader.ReaderSettings
import com.rotein.ebookreader.fontFamilyForJs
import com.rotein.ebookreader.R
import com.rotein.ebookreader.ui.components.ActionItem
import com.rotein.ebookreader.ui.components.ActionPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Composable
internal fun EpubViewer(
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
    onNavigationComplete: () -> Unit = {},
    readerSettings: ReaderSettings = ReaderSettings()
) {
    val context = LocalContext.current
    var bookDir by remember(path) { mutableStateOf<String?>(null) }
    var opfPath by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf(false) }
    data class SelectionState(val text: String, val x: Float, val y: Float, val bottom: Float, val cfi: String = "", val isAtPageEnd: Boolean = false)
    var selectionState by remember { mutableStateOf<SelectionState?>(null) }
    var pendingStartText by remember { mutableStateOf<String?>(null) }
    var isContinuationMode by remember { mutableStateOf(false) }
    var isContinuationTransitioning by remember { mutableStateOf(false) }
    val selectionActive = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val webViewRef = remember { java.util.concurrent.atomic.AtomicReference<android.webkit.WebView?>(null) }
    val overlayRef = remember { java.util.concurrent.atomic.AtomicReference<android.view.View?>(null) }
    val prevSafetyRef = remember { java.util.concurrent.atomic.AtomicReference<Runnable?>(null) }
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
    val resetContinuation: () -> Unit = {
        pendingStartText = null
        isContinuationMode = false
        webViewRef.get()?.evaluateJavascript("window._clearContStart()", null)
    }
    val selectionOnTextSelected: (String, Float, Float, Float) -> Unit = { text, x, y, bottom ->
        onTextSelected(text, x, y, bottom)
        if (text.isNotEmpty()) {
            selectionActive.set(true)
            isContinuationTransitioning = false
        } else {
            selectionActive.set(false)
            selectionState = null
            if (isContinuationMode && !isContinuationTransitioning) {
                resetContinuation()
            }
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
                        // 초기 로딩 중 epub.js가 페이지를 잡기 전의 중간 상태가 보이지 않도록
                        // INVISIBLE로 시작하고, onNavigationComplete에서 VISIBLE로 전환한다.
                        visibility = View.INVISIBLE
                        webViewClient = WebViewClient()
                        addJavascriptInterface(EpubBridge(onLocationUpdate, onTocLoaded, onContentRendered, onChapterChanged, onTocReady, onPageInfoChanged, onDebugInfo, onScanStart, onScanComplete, onSearchResultsPartial, onSearchComplete, selectionOnTextSelected, selectionOnSelectionTapped,
                            onAutoSelectReadyCallback = autoSelect@{ cssX, cssY ->
                                val wv = webViewRef.get() ?: return@autoSelect
                                val ov = overlayRef.get()
                                ov?.visibility = View.GONE
                                val density = wv.context.resources.displayMetrics.density
                                val px = cssX * density
                                val py = cssY * density
                                wv.postDelayed({
                                    val downTime = SystemClock.uptimeMillis()
                                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, px, py, 0)
                                    wv.dispatchTouchEvent(down)
                                    down.recycle()
                                    wv.postDelayed({
                                        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, px, py, 0)
                                        wv.dispatchTouchEvent(up)
                                        up.recycle()
                                        ov?.visibility = View.VISIBLE
                                    }, 600)
                                }, 100)
                            },
                            onNavigationCompleteCallback = {
                                onNavigationComplete()
                            },
                            onPrevTransitionDoneCallback = {
                                val wv = webViewRef.get()
                                val ov = overlayRef.get()
                                prevSafetyRef.getAndSet(null)?.let { wv?.removeCallbacks(it) }
                                (ov?.background as? BitmapDrawable)?.bitmap?.recycle()
                                ov?.background = null
                            }
                        ), "Android")
                    }
                    webViewRef.set(webView)
                    onWebViewCreated(webView)
                    val doPrev = {
                        val wv = webViewRef.get()
                        val ov = overlayRef.get()
                        wv?.evaluateJavascript("window._isAtChapterStart()") { result ->
                            val isChapterStart = result?.trim() == "true"
                            if (isChapterStart && wv.width > 0 && wv.height > 0 && ov != null) {
                                val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.RGB_565)
                                wv.draw(Canvas(bmp))
                                ov.background = BitmapDrawable(ctx.resources, bmp)
                                // 안전장치: 콜백이 오지 않을 경우 500ms 후 오버레이 제거
                                val runnable = Runnable {
                                    prevSafetyRef.set(null)
                                    (ov.background as? BitmapDrawable)?.bitmap?.recycle()
                                    ov.background = null
                                }
                                prevSafetyRef.getAndSet(runnable)?.let { wv.removeCallbacks(it) }
                                wv.postDelayed(runnable, 500)
                            }
                            wv.evaluateJavascript("window._prev()", null)
                        }
                    }
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
                                                            $cssX,
                                                            $cssY,
                                                            $cssY,
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
                                        x < w / 3f -> doPrev()
                                        x > w * 2f / 3f -> webView.evaluateJavascript("window._next()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.LR_NEXT_PREV -> when {
                                        x < w / 3f -> webView.evaluateJavascript("window._next()", null)
                                        x > w * 2f / 3f -> doPrev()
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_PREV_NEXT -> when {
                                        y < h / 3f -> doPrev()
                                        y > h * 2f / 3f -> webView.evaluateJavascript("window._next()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_NEXT_PREV -> when {
                                        y < h / 3f -> webView.evaluateJavascript("window._next()", null)
                                        y > h * 2f / 3f -> doPrev()
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
                                        when (hitData.getString("type")) {
                                            "highlight" -> onHighlightLongPressRef.get()?.invoke(id, cssX, cssY, cssY)
                                            "memo" -> onMemoLongPressRef.get()?.invoke(id, cssX, cssY, cssY)
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
                    overlayRef.set(overlay)
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
        ActionPopup(
            selectionY = sel.y,
            selectionBottom = sel.bottom,
            selectionCx = sel.x,
            actions = buildList {
                add(ActionItem(stringResource(R.string.highlight)) {
                    fun doHighlight(text: String, cfi: String) {
                        onHighlight(text, cfi)
                        resetContinuation()
                        clearSelection()
                    }
                    if (isContinuationMode) {
                        val combinedText = (pendingStartText ?: "") + sel.text
                        webViewRef.get()?.evaluateJavascript("window._getContMergedCfi()") { mergedCfi ->
                            val cfi = mergedCfi?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                                ?.takeIf { it.isNotEmpty() } ?: sel.cfi
                            doHighlight(combinedText, cfi)
                        }
                    } else {
                        doHighlight(sel.text, sel.cfi)
                    }
                })
                add(ActionItem(stringResource(R.string.memo)) {
                    fun doMemo(text: String, cfi: String) {
                        onMemo(text, cfi)
                        resetContinuation()
                        clearSelection()
                    }
                    if (isContinuationMode) {
                        val combinedText = (pendingStartText ?: "") + sel.text
                        webViewRef.get()?.evaluateJavascript("window._getContMergedCfi()") { mergedCfi ->
                            val cfi = mergedCfi?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                                ?.takeIf { it.isNotEmpty() } ?: sel.cfi
                            doMemo(combinedText, cfi)
                        }
                    } else {
                        doMemo(sel.text, sel.cfi)
                    }
                })
                add(ActionItem(stringResource(R.string.share)) {
                    val shareText = if (isContinuationMode) (pendingStartText ?: "") + sel.text else sel.text
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    resetContinuation()
                    clearSelection()
                })
                if (sel.isAtPageEnd && !isContinuationMode) {
                    add(ActionItem(stringResource(R.string.continue_selection)) {
                        pendingStartText = sel.text
                        isContinuationMode = true
                        isContinuationTransitioning = true
                        webViewRef.get()?.evaluateJavascript("window._saveContStart()", null)
                        selectionState = null
                        clearSelection()
                        webViewRef.get()?.evaluateJavascript("window._nextThenAutoSelect()", null)
                    })
                }
            },
            onDismiss = {
                if (isContinuationMode) resetContinuation()
                clearSelection()
            },
            usePopup = true,
        )
    }
    } // Box
}

/** EPUB ZIP 을 캐시 디렉토리에 압축 해제하고 epub.js 를 복사한다. */
private val jsModuleFiles = listOf("init.js", "settings.js", "location.js", "page-scan.js", "selection.js", "search.js", "annotation.js", "navigation.js")

private fun copyJsModules(context: Context, outDir: File) {
    val jsDir = File(outDir, "js")
    jsDir.mkdirs()
    for (jsFile in jsModuleFiles) {
        context.assets.open("epub/js/$jsFile").use { input ->
            File(jsDir, jsFile).outputStream().use { output -> input.copyTo(output) }
        }
    }
}

private fun extractEpub(context: Context, epubPath: String): Pair<String, String>? {
    val hash = epubPath.hashCode().toString()
    val outDir = File(context.cacheDir, "epub/$hash")
    val opfMarker = File(outDir, ".opf_path")

    if (outDir.exists() && opfMarker.exists() && File(outDir, "epub.min.js").exists() && File(outDir, "js/init.js").exists()) {
        // JS 모듈 파일은 항상 최신 assets로 덮어쓴다 (코드 수정 반영)
        copyJsModules(context, outDir)
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

    // assets 에서 JS 모듈 파일 복사
    copyJsModules(context, outDir)

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
