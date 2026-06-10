# PDF 뷰어 하이라이트/메모 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PDF 뷰어에서 텍스트 선택 기반 하이라이트/메모 기능을 추가한다.

**Architecture:** PDF.js 텍스트 레이어 + Selection API로 텍스트를 선택하고, 위치를 `pdf:<pageNum>:<startItemIdx>:<startCharIdx>:<endItemIdx>:<endCharIdx>` 포맷으로 기존 `cfi` 필드에 저장한다. EPUB 뷰어와 동일한 콜백 패턴(onTextSelected, onHighlight, onMemo, onHighlightLongPress, onMemoLongPress)을 PDF에도 적용한다.

**Tech Stack:** PDF.js (텍스트 레이어), WebView JavascriptInterface, Room DB (기존 Highlight/Memo 엔티티), Jetpack Compose

**설계 문서:** `docs/superpowers/specs/2026-05-20-pdf-highlight-memo-design.md`

---

### Task 1: PDF.js 텍스트 레이어 활성화 (`init.js` 수정)

현재 `init.js`의 `_renderPage()`는 Canvas에만 렌더링하고 텍스트 레이어를 생성하지 않는다. 텍스트 레이어를 Canvas 위에 투명하게 배치하여 텍스트 선택이 가능하도록 한다.

**Files:**
- Modify: `app/src/main/assets/pdf/js/init.js:30-105`
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt:24-37` (CSS 추가)

- [ ] **Step 1: PdfHtmlTemplate.kt에 텍스트 레이어 CSS 추가**

`PdfHtmlTemplate.kt`의 `<style>` 블록 내 `#pdf-canvas { display: block; }` 뒤에 텍스트 레이어 스타일을 추가한다:

```kotlin
// 기존 CSS 뒤에 추가
"""
#pdf-text-layer {
    position: absolute; left: 0; top: 0;
    overflow: hidden; opacity: 0.25; line-height: 1.0;
}
#pdf-text-layer > span {
    color: transparent; position: absolute;
    white-space: pre; pointer-events: all;
}
#pdf-text-layer > span::selection {
    background: #baffc6;
}
#pdf-text-layer > br { display: none; }
.pdf-hl-overlay {
    position: absolute; pointer-events: none;
    background: #baffc6; opacity: 0.4;
}
.pdf-memo-overlay {
    position: absolute; pointer-events: none;
    border-bottom: 2px solid #000000;
}
"""
```

- [ ] **Step 2: init.js의 _renderPage()에 텍스트 레이어 렌더링 추가**

`init.js`의 `_renderPage()` 함수에서 Canvas 렌더링 완료 후 텍스트 레이어를 생성한다. 기존 `page.getTextContent().then(...)` 블록(74-81행)을 교체한다:

```javascript
// 기존: page.getTextContent() 블록을 아래로 교체
page.getTextContent().then(function(textContent) {
    _pdf.currentTextItems = textContent.items;
    _pdf.currentDisplayViewport = displayViewport;

    // 텍스트 레이어 생성/갱신
    var oldTextLayer = document.getElementById('pdf-text-layer');
    if (oldTextLayer) oldTextLayer.remove();
    var textLayerDiv = document.createElement('div');
    textLayerDiv.id = 'pdf-text-layer';
    textLayerDiv.style.width = displayViewport.width + 'px';
    textLayerDiv.style.height = displayViewport.height + 'px';
    wrapper.appendChild(textLayerDiv);

    pdfjsLib.renderTextLayer({
        textContentSource: textContent,
        container: textLayerDiv,
        viewport: displayViewport,
        textDivs: []
    });

    if (_pdf.searchHighlightQuery) {
        _applySearchHighlights();
    }
    // 하이라이트/메모 복원
    if (typeof _reapplyAnnotations === 'function') _reapplyAnnotations();
});
```

- [ ] **Step 3: 기기에서 PDF를 열어 텍스트 레이어 동작 확인**

확인 사항:
- PDF 페이지의 텍스트 위에 투명한 텍스트 레이어가 배치되는지
- 텍스트를 롱프레스하여 선택할 수 있는지 (이 시점에서는 overlay가 터치를 가로채므로 아직 안 될 수 있음)
- 기존 검색 하이라이트, 페이지 넘김, 줌이 정상 동작하는지

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/assets/pdf/js/init.js app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt
git commit -m "feat: PDF.js 텍스트 레이어 활성화"
```

---

### Task 2: PDF 텍스트 선택 JS (`selection.js` 신규)

텍스트 선택 이벤트를 감지하여 Android Bridge로 전달하고, 선택 범위를 위치 포맷 문자열로 변환하는 JavaScript 모듈.

**Files:**
- Create: `app/src/main/assets/pdf/js/selection.js`
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt:49-55` (script 태그 추가)

- [ ] **Step 1: selection.js 작성**

```javascript
// selection.js — PDF 텍스트 선택 처리

var _selDebounceTimer = null;

document.addEventListener('selectionchange', function() {
    clearTimeout(_selDebounceTimer);
    _selDebounceTimer = setTimeout(function() {
        try {
            var sel = window.getSelection();
            var text = sel ? sel.toString().trim() : '';
            Android.onTextSelected(text);
        } catch(e) {}
    }, 200);
});

// 현재 선택 범위를 PDF 위치 포맷으로 변환
// 반환: "pdf:<pageNum>:<startItemIdx>:<startCharIdx>:<endItemIdx>:<endCharIdx>"
window._getLocationFromSelection = function() {
    try {
        var sel = window.getSelection();
        if (!sel || sel.rangeCount === 0) return '';
        var range = sel.getRangeAt(0);

        var textLayer = document.getElementById('pdf-text-layer');
        if (!textLayer) return '';

        var spans = textLayer.querySelectorAll('span');
        var startIdx = -1, startCharIdx = 0, endIdx = -1, endCharIdx = 0;

        for (var i = 0; i < spans.length; i++) {
            var span = spans[i];
            // 시작점 찾기
            if (startIdx === -1 && span.contains(range.startContainer)) {
                startIdx = i;
                startCharIdx = range.startOffset;
                // startContainer가 텍스트 노드가 아닌 경우 보정
                if (range.startContainer !== span.firstChild && span.firstChild) {
                    var walker = document.createTreeWalker(span, NodeFilter.SHOW_TEXT);
                    var offset = 0;
                    var node;
                    while (node = walker.nextNode()) {
                        if (node === range.startContainer) { startCharIdx = offset + range.startOffset; break; }
                        offset += node.length;
                    }
                }
            }
            // 끝점 찾기
            if (span.contains(range.endContainer)) {
                endIdx = i;
                endCharIdx = range.endOffset;
                if (range.endContainer !== span.firstChild && span.firstChild) {
                    var walker = document.createTreeWalker(span, NodeFilter.SHOW_TEXT);
                    var offset = 0;
                    var node;
                    while (node = walker.nextNode()) {
                        if (node === range.endContainer) { endCharIdx = offset + range.endOffset; break; }
                        offset += node.length;
                    }
                }
            }
        }

        if (startIdx === -1 || endIdx === -1) return '';
        return 'pdf:' + _pdf.currentPage + ':' + startIdx + ':' + startCharIdx + ':' + endIdx + ':' + endCharIdx;
    } catch(e) { return ''; }
};

window._clearSelection = function() {
    try { window.getSelection().removeAllRanges(); } catch(e) {}
};
```

- [ ] **Step 2: PdfHtmlTemplate.kt에 script 태그 추가**

기존 `<script src="pdf/js/search.js"></script>` 뒤에 추가:

```kotlin
"""<script src="pdf/js/selection.js"></script>
<script src="pdf/js/annotation.js"></script>"""
```

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/assets/pdf/js/selection.js app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt
git commit -m "feat: PDF 텍스트 선택 JS 모듈 추가"
```

---

### Task 3: PDF 어노테이션 렌더링 JS (`annotation.js` 신규)

하이라이트/메모 오버레이를 렌더링하고 관리하는 JavaScript 모듈.

**Files:**
- Create: `app/src/main/assets/pdf/js/annotation.js`

- [ ] **Step 1: annotation.js 작성**

```javascript
// annotation.js — PDF 하이라이트/메모 렌더링

var _pdfHighlights = {};  // id → locationStr
var _pdfMemos = {};       // id → locationStr

// 위치 문자열을 파싱하여 오버레이 사각형 좌표 배열 반환
function _parseLocationToRects(locationStr) {
    var parts = locationStr.split(':');
    if (parts.length !== 6 || parts[0] !== 'pdf') return [];
    var startItemIdx = parseInt(parts[2]);
    var startCharIdx = parseInt(parts[3]);
    var endItemIdx = parseInt(parts[4]);
    var endCharIdx = parseInt(parts[5]);

    var items = _pdf.currentTextItems;
    var vp = _pdf.currentDisplayViewport;
    if (!items || !vp) return [];

    var canvas = document.getElementById('pdf-canvas');
    var wrapper = document.getElementById('pdf-wrapper');
    var offsetX = 0, offsetY = 0;
    if (canvas && wrapper) {
        var cr = canvas.getBoundingClientRect();
        var wr = wrapper.getBoundingClientRect();
        offsetX = cr.left - wr.left;
        offsetY = cr.top - wr.top;
    }

    var rects = [];
    for (var i = startItemIdx; i <= endItemIdx && i < items.length; i++) {
        var item = items[i];
        if (!item.str) continue;
        var tx = item.transform;
        var fontSize = Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1]);

        var pLeft = vp.convertToViewportPoint(tx[4], tx[5]);
        var pRight = vp.convertToViewportPoint(tx[4] + item.width, tx[5]);
        var totalW = pRight[0] - pLeft[0];
        var hlH = fontSize * vp.scale;

        var charStart = (i === startItemIdx) ? startCharIdx : 0;
        var charEnd = (i === endItemIdx) ? endCharIdx : item.str.length;
        var ratioStart = item.str.length > 0 ? charStart / item.str.length : 0;
        var ratioEnd = item.str.length > 0 ? charEnd / item.str.length : 1;

        rects.push({
            x: pLeft[0] + totalW * ratioStart + offsetX,
            y: pLeft[1] - hlH + offsetY,
            w: totalW * (ratioEnd - ratioStart),
            h: hlH
        });
    }
    return rects;
}

function _createOverlays(locationStr, id, cssClass) {
    var wrapper = document.getElementById('pdf-wrapper');
    if (!wrapper) return;
    var rects = _parseLocationToRects(locationStr);
    for (var i = 0; i < rects.length; i++) {
        var r = rects[i];
        var el = document.createElement('div');
        el.className = cssClass + ' ann-' + id;
        el.style.left = r.x + 'px';
        el.style.top = r.y + 'px';
        el.style.width = r.w + 'px';
        el.style.height = r.h + 'px';
        wrapper.appendChild(el);
    }
}

function _removeOverlays(id) {
    var els = document.querySelectorAll('.ann-' + id);
    for (var i = 0; i < els.length; i++) els[i].remove();
}

window._addHighlight = function(locationStr, id) {
    _pdfHighlights[id] = locationStr;
    _createOverlays(locationStr, id, 'pdf-hl-overlay');
};

window._removeHighlight = function(id) {
    delete _pdfHighlights[id];
    _removeOverlays(id);
};

window._applyHighlights = function(json) {
    try {
        var arr = JSON.parse(json);
        for (var i = 0; i < arr.length; i++) {
            window._addHighlight(arr[i].location, arr[i].id);
        }
    } catch(e) {}
};

window._addMemo = function(locationStr, id) {
    _pdfMemos[id] = locationStr;
    _createOverlays(locationStr, id, 'pdf-memo-overlay');
};

window._removeMemo = function(id) {
    delete _pdfMemos[id];
    _removeOverlays(id);
};

window._applyMemos = function(json) {
    try {
        var arr = JSON.parse(json);
        for (var i = 0; i < arr.length; i++) {
            window._addMemo(arr[i].location, arr[i].id);
        }
    } catch(e) {}
};

// 페이지 전환 후 현재 페이지의 어노테이션 재적용
function _reapplyAnnotations() {
    // 기존 오버레이 제거
    var wrapper = document.getElementById('pdf-wrapper');
    if (wrapper) {
        var old = wrapper.querySelectorAll('.pdf-hl-overlay, .pdf-memo-overlay');
        for (var i = 0; i < old.length; i++) old[i].remove();
    }
    // 현재 페이지에 해당하는 것만 다시 그리기
    var page = _pdf.currentPage;
    for (var id in _pdfHighlights) {
        var loc = _pdfHighlights[id];
        if (parseInt(loc.split(':')[1]) === page) {
            _createOverlays(loc, id, 'pdf-hl-overlay');
        }
    }
    for (var id in _pdfMemos) {
        var loc = _pdfMemos[id];
        if (parseInt(loc.split(':')[1]) === page) {
            _createOverlays(loc, id, 'pdf-memo-overlay');
        }
    }
}

window._getAnnotationAtPoint = function(x, y) {
    // 하이라이트 확인
    for (var id in _pdfHighlights) {
        var rects = _parseLocationToRects(_pdfHighlights[id]);
        for (var i = 0; i < rects.length; i++) {
            var r = rects[i];
            if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
                return JSON.stringify({type: 'highlight', id: parseInt(id), cx: r.x + r.w / 2, y: r.y, bottom: r.y + r.h});
            }
        }
    }
    // 메모 확인
    for (var id in _pdfMemos) {
        var rects = _parseLocationToRects(_pdfMemos[id]);
        for (var i = 0; i < rects.length; i++) {
            var r = rects[i];
            if (x >= r.x && x <= r.x + r.w && y >= r.y - 10 && y <= r.y + r.h + 10) {
                return JSON.stringify({type: 'memo', id: parseInt(id), cx: r.x + r.w / 2, y: r.y, bottom: r.y + r.h});
            }
        }
    }
    return 'null';
};
```

- [ ] **Step 2: 커밋**

```bash
git add app/src/main/assets/pdf/js/annotation.js
git commit -m "feat: PDF 어노테이션 렌더링 JS 모듈 추가"
```

---

### Task 4: PdfBridge 콜백 추가

JavaScript → Kotlin 통신을 위한 새 콜백을 PdfBridge에 추가한다.

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/PdfBridge.kt:3-48`

- [ ] **Step 1: PdfBridge에 onTextSelected, onAnnotationLongPress 추가**

기존 `PdfBridge` 클래스의 생성자에 콜백 2개를 추가하고, `@JavascriptInterface` 메서드를 추가한다:

```kotlin
internal class PdfBridge(
    private val onPageChangedCallback: (currentPage: Int, totalPages: Int) -> Unit,
    private val onLocationUpdateCallback: (progress: Float, pageNum: Int, pageTitle: String) -> Unit = { _, _, _ -> },
    private val onContentLoadedCallback: () -> Unit = {},
    private val onTocLoadedCallback: (tocJson: String) -> Unit = {},
    private val onSearchResultsPartialCallback: (resultsJson: String) -> Unit = {},
    private val onSearchCompleteCallback: () -> Unit = {},
    private val onNavigationCompleteCallback: () -> Unit = {},
    // 하이라이트/메모용 콜백
    private val onTextSelectedCallback: (text: String) -> Unit = {},
    private val onAnnotationLongPressCallback: (json: String) -> Unit = {}
) {
    // ... 기존 메서드들 유지 ...

    @android.webkit.JavascriptInterface
    fun onTextSelected(text: String) {
        mainHandler.post { onTextSelectedCallback(text) }
    }

    @android.webkit.JavascriptInterface
    fun onAnnotationLongPress(json: String) {
        mainHandler.post { onAnnotationLongPressCallback(json) }
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/reader/PdfBridge.kt
git commit -m "feat: PdfBridge에 텍스트 선택/어노테이션 콜백 추가"
```

---

### Task 5: PdfViewer 터치 처리 및 콜백 연결

PdfViewer에 하이라이트/메모 콜백 파라미터를 추가하고, 롱프레스 시 텍스트 선택이 가능하도록 터치 처리를 변경한다.

EPUB 뷰어(`EpubViewer.kt:430-468`)의 롱프레스 → overlay 해제 → WebView 이벤트 전달 패턴을 따른다.

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/PdfViewer.kt:28-203`

- [ ] **Step 1: PdfViewer 콜백 파라미터 추가**

`PdfViewer` composable의 파라미터에 다음을 추가한다 (`onWebViewCreated` 뒤에):

```kotlin
onTextSelected: (text: String) -> Unit = {},
onHighlightLongPress: (id: Long, x: Float, y: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
onMemoLongPress: (id: Long, x: Float, y: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
```

- [ ] **Step 2: PdfBridge 생성 시 새 콜백 연결**

`PdfBridge(...)` 생성 부분(68-79행)에 새 콜백을 연결한다:

```kotlin
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
    onNavigationCompleteCallback = onNavigationComplete,
    onTextSelectedCallback = onTextSelected,
    onAnnotationLongPressCallback = { json ->
        try {
            val obj = org.json.JSONObject(json)
            val id = obj.getLong("id")
            val cx = obj.getDouble("cx").toFloat()
            val y = obj.getDouble("y").toFloat()
            val bottom = obj.getDouble("bottom").toFloat()
            when (obj.getString("type")) {
                "highlight" -> onHighlightLongPress(id, cx, y, bottom)
                "memo" -> onMemoLongPress(id, cx, y, bottom)
            }
        } catch (_: Exception) {}
    }
), "Android")
```

- [ ] **Step 3: 터치 처리 변경 — 롱프레스 시 overlay 해제**

EPUB 뷰어의 패턴을 따라 overlay에 `GestureDetector`의 `onLongPress`를 추가한다. 기존 overlay `setOnTouchListener` 블록(144-180행)을 변경한다.

overlay 변수 선언부(`val overlay = android.view.View(ctx).apply {`)에 `var isLongPress = false` 를 추가하고, GestureDetector에 `onLongPress` 핸들러를 추가한다:

```kotlin
val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // ... 기존 탭 처리 그대로 유지 ...
    }
    override fun onLongPress(e: MotionEvent) {
        // 줌 상태에서는 선택 모드 진입 안 함
        if (currentZoomScale > 1.05f) return
        val density = ctx.resources.displayMetrics.density
        val cssX = e.x / density
        val cssY = e.y / density
        // 먼저 기존 어노테이션 히트 확인
        webView.evaluateJavascript("window._getAnnotationAtPoint($cssX, $cssY)") { result ->
            val cleaned = result?.trim()?.let { r ->
                if (r == "null" || r == "\"null\"") null
                else r.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
            }
            val hitData = try {
                if (cleaned != null) org.json.JSONObject(cleaned) else null
            } catch (_: Exception) { null }
            if (hitData != null) {
                // 기존 어노테이션 롱프레스 — Kotlin에서 직접 콜백 호출
                val id = hitData.getLong("id")
                when (hitData.getString("type")) {
                    "highlight" -> onHighlightLongPress(
                        id,
                        hitData.getDouble("cx").toFloat(),
                        hitData.getDouble("y").toFloat(),
                        hitData.getDouble("bottom").toFloat()
                    )
                    "memo" -> onMemoLongPress(
                        id,
                        hitData.getDouble("cx").toFloat(),
                        hitData.getDouble("y").toFloat(),
                        hitData.getDouble("bottom").toFloat()
                    )
                }
            } else {
                // 텍스트 선택 모드 진입: overlay 숨기고 WebView에 터치 전달
                isLongPress = true
                this@apply.visibility = android.view.View.GONE
                val ex = e.x; val ey = e.y
                val downEvent = MotionEvent.obtain(e.downTime, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, ex, ey, 0)
                webView.dispatchTouchEvent(downEvent)
                downEvent.recycle()
            }
        }
    }
})
```

그리고 `setOnTouchListener`에서 `isLongPress` 상태일 때 WebView에 이벤트를 전달하고, ACTION_UP 시 overlay를 복원하는 로직을 추가한다:

```kotlin
setOnTouchListener { _, event ->
    if (event.pointerCount > 1) wasMultiTouch = true
    scaleDetector.onTouchEvent(event)

    if (!wasMultiTouch) {
        gestureDetector.onTouchEvent(event)
    }

    // 텍스트 선택 모드: WebView에 이벤트 전달
    if (isLongPress) {
        when (event.action) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                webView.dispatchTouchEvent(event)
                // UP 시에는 overlay 복원하지 않음 — onTextSelected('')가 올 때 복원
            }
        }
    }

    // 줌 상태 팬 처리 (기존 코드 유지)
    if (!isLongPress && currentZoomScale > 1.05f && event.pointerCount == 1 && !scaleDetector.isInProgress) {
        // ... 기존 팬 코드 ...
    }

    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        wasMultiTouch = false
        panTracking = false
    }
    true
}
```

onTextSelected 콜백에서 텍스트가 비어있을 때 overlay를 복원해야 하므로, overlay 참조를 보관한다. `val overlayRef = remember { java.util.concurrent.atomic.AtomicReference<android.view.View?>(null) }` 를 webViewRef 옆에 추가하고, overlay 생성 후 `overlayRef.set(overlay)`. onTextSelected 콜백을 감싸서:

```kotlin
onTextSelectedCallback = { text ->
    onTextSelected(text)
    if (text.isEmpty()) {
        overlayRef.get()?.let { ov ->
            ov.visibility = android.view.View.VISIBLE
        }
    }
}
```

이 `onTextSelectedCallback`을 PdfBridge에 전달한다.

- [ ] **Step 4: 기기에서 동작 확인**

확인 사항:
- 롱프레스로 텍스트 선택이 되는지
- 선택 해제 시 overlay가 복원되어 페이지 넘김/줌이 다시 동작하는지
- 줌 상태에서 롱프레스가 팬으로 동작하는지 (선택 모드 진입 안 함)
- 기존 싱글 탭(페이지 넘김, 센터 탭) 동작이 유지되는지

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/reader/PdfViewer.kt
git commit -m "feat: PdfViewer 롱프레스 텍스트 선택 및 콜백 연결"
```

---

### Task 6: BookReaderScreen에서 PDF 하이라이트/메모 통합

BookReaderScreen의 PDF 뷰어 호출부에 하이라이트/메모 콜백을 연결한다. EPUB 뷰어 호출부(287-299행)의 패턴을 따른다.

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt:350-376`

- [ ] **Step 1: PdfViewer 호출부에 콜백 추가**

기존 `PdfViewer(...)` 호출(350-376행)에 새 콜백을 추가한다. `onWebViewCreated` 뒤에:

```kotlin
onTextSelected = { text ->
    // PDF에서는 위치 좌표를 JS에서 받지 않으므로 고정값 사용 — 팝업은 화면 중앙에 표시
    // TODO: 필요시 JS에서 선택 영역 좌표를 함께 전달하도록 개선 가능
    onTextSelected(text, 0f, 0f, 0f)
},
onHighlightLongPress = { id, x, y, bottom -> vm.onHighlightLongPress(id, x, y, bottom) },
onMemoLongPress = { id, x, y, bottom -> vm.onMemoLongPress(id, x, y, bottom) },
```

참고: `onTextSelected`는 BookReaderScreen 상단에 이미 정의된 로컬 함수/변수이다. EPUB 뷰어와 동일하게 선택 상태를 관리하여 ActionPopup이 표시되도록 한다.

- [ ] **Step 2: ActionPopup에서 하이라이트/메모 저장 시 PDF용 location 처리**

PDF에서 ActionPopup의 하이라이트 버튼 클릭 시, `_getLocationFromSelection()` → DB 저장 → `_addHighlight()` 호출 흐름을 연결해야 한다. 이 로직은 현재 EPUB에서는 EpubViewer 내부의 ActionPopup에서 처리한다.

PDF의 경우 BookReaderScreen에서 처리한다. 기존 PDF 분기 내에 하이라이트/메모 액션 팝업 로직을 추가한다:

```kotlin
// PDF 선택 시 액션 처리를 위한 로직
// onHighlight 역할: JS에서 location을 가져와 DB에 저장하고 JS 하이라이트 추가
val pdfOnHighlight: (String) -> Unit = { text ->
    viewerWebView.value?.evaluateJavascript("window._getLocationFromSelection()") { locResult ->
        val location = locResult?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""
        if (location.startsWith("pdf:")) {
            scope.launch {
                val page = location.split(":").getOrNull(1)?.toIntOrNull() ?: 0
                val saved = vm.addHighlight(location, text, page)
                viewerWebView.value?.evaluateJavascript(
                    "window._addHighlight('${location.replace("'", "\\'")}', ${saved.id})", null
                )
            }
        }
    }
}
```

메모도 동일한 패턴으로 `pdfOnMemo`를 구현한다.

주의: `vm.addHighlight`의 시그니처가 PDF용 page 파라미터를 받을 수 있는지 확인이 필요하다. 기존에는 `addHighlight(cfi, text)`인데, page는 나중에 별도 업데이트할 수도 있다. ViewModel 코드를 확인하여 정확한 연결 방법을 결정한다.

- [ ] **Step 3: 페이지 전환 시 하이라이트/메모 복원**

`onPageChanged` 콜백 내에서, 현재 페이지의 하이라이트/메모를 DB에서 조회하여 JS에 전달한다:

```kotlin
onPageChanged = { page, total ->
    vm.updatePageInfo(page, total)
    // 현재 페이지의 하이라이트/메모를 JS에 전달
    scope.launch {
        val highlights = vm.getHighlightsForPage(page)
        val memos = vm.getMemosForPage(page)
        if (highlights.isNotEmpty()) {
            val json = highlights.map { """{"location":"${it.cfi}","id":${it.id}}""" }
                .joinToString(",", "[", "]")
            viewerWebView.value?.evaluateJavascript("window._applyHighlights('${json.replace("'", "\\'")}')", null)
        }
        if (memos.isNotEmpty()) {
            val json = memos.map { """{"location":"${it.cfi}","id":${it.id}}""" }
                .joinToString(",", "[", "]")
            viewerWebView.value?.evaluateJavascript("window._applyMemos('${json.replace("'", "\\'")}')", null)
        }
    }
},
```

주의: `vm.getHighlightsForPage(page)`, `vm.getMemosForPage(page)` 는 ViewModel에 새로 추가해야 할 수 있다. 기존 `annotationState.highlights`에서 page로 필터링하는 것으로 충분할 수도 있다 — ViewModel 코드를 확인하여 결정한다.

- [ ] **Step 4: 기기에서 전체 흐름 테스트**

확인 사항:
- 롱프레스 → 텍스트 선택 → 액션 팝업 표시
- 하이라이트 버튼 → 하이라이트 오버레이 표시 + DB 저장
- 메모 버튼 → 메모 에디터 오픈 + 저장
- 페이지 이동 후 돌아왔을 때 하이라이트/메모가 복원되는지
- 기존 하이라이트 롱프레스 → 삭제 팝업 동작

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: BookReaderScreen PDF 하이라이트/메모 통합"
```

---

### Task 7: 줌 상태에서 하이라이트 오버레이 동기화

줌만 변경되고 페이지가 안 바뀌는 경우, 하이라이트 오버레이가 CSS transform으로 함께 스케일되도록 한다.

현재 `zoom.js`의 `_applyZoom()`은 `pdf-wrapper`에 transform을 적용한다. 하이라이트 오버레이가 `pdf-wrapper` 안에 있으므로 자동으로 함께 스케일된다 — 별도 처리가 필요 없을 수 있다.

**Files:**
- Possibly no changes needed

- [ ] **Step 1: 줌 시 하이라이트 동작 확인**

기기에서 다음을 확인한다:
1. 하이라이트가 있는 페이지에서 핀치 줌 → 하이라이트 오버레이가 텍스트와 함께 확대/축소되는지
2. 줌 상태에서 팬 → 하이라이트 위치가 텍스트를 정확히 따라가는지
3. 줌 리셋 → 하이라이트 위치가 정확한지

하이라이트 오버레이는 `pdf-wrapper` 안에 absolute 포지션으로 배치되고, `_applyZoom()`이 wrapper에 `transform: translate(...) scale(...)`을 적용하므로, 오버레이도 자동으로 변환된다.

만약 문제가 있다면 `_restoreZoom()` 끝에 `_reapplyAnnotations()` 호출을 추가한다.

- [ ] **Step 2: (조건부) 문제가 있는 경우에만 수정 및 커밋**

---

### Task 8: 최종 통합 테스트 및 엣지 케이스 확인

**Files:** 변경 없음 (테스트만)

- [ ] **Step 1: 기능 테스트 체크리스트**

1. 텍스트가 있는 PDF에서 롱프레스 → 텍스트 선택 → 하이라이트 생성
2. 텍스트가 있는 PDF에서 롱프레스 → 텍스트 선택 → 메모 생성
3. 하이라이트 롱프레스 → 삭제
4. 메모 롱프레스 → 편집/삭제
5. 페이지 이동 후 복귀 → 하이라이트/메모 복원
6. 앱 종료 후 재시작 → 하이라이트/메모 복원
7. 줌 상태에서 하이라이트 위치 정확성
8. 줌 상태에서 롱프레스 → 팬 동작 (선택 모드 진입 안 함)
9. 검색 하이라이트와 어노테이션 하이라이트 공존
10. EPUB 뷰어의 하이라이트/메모가 여전히 정상 동작

- [ ] **Step 2: 엣지 케이스 확인**

1. 텍스트 레이어가 없는/적은 PDF (이미지 기반) — 그레이스풀 디그레이드
2. 매우 긴 텍스트 선택 (여러 줄)
3. 같은 위치에 하이라이트와 메모가 겹치는 경우
