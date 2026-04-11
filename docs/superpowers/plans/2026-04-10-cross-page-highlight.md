# Cross-Page Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 텍스트 선택이 페이지 끝에 닿았을 때 "이어하기"로 다음 페이지까지 확장하여 2페이지에 걸친 하이라이트를 생성한다.

**Architecture:** EpubViewer 내부에 "이어하기" 모드 상태를 추가한다. JavaScript에서 선택이 페이지 마지막 텍스트에 닿았는지 판단하는 함수와 페이지 첫 글자를 선택하는 함수를 추가한다. 이전 페이지 시작 CFI + 다음 페이지 끝 CFI를 합성하여 하나의 범위 CFI로 만든다.

**Tech Stack:** Kotlin (Jetpack Compose), JavaScript (epub.js), Android WebView

**Target File:** `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`

---

### Task 1: JavaScript 헬퍼 함수 추가

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt` (HTML template 내 JavaScript 영역, line ~3575 `window._getAnnotationAtPoint` 함수 아래)

- [ ] **Step 1: `_isSelectionAtPageEnd()` 함수 추가**

`window._getAnnotationAtPoint` 함수 뒤(line ~3575), `_savedCfi` 변수 선언 전(line ~3577)에 다음 JavaScript 함수들을 추가한다:

```javascript
window._isSelectionAtPageEnd = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return false;
        var doc = iframe.contentDocument;
        var sel = doc.getSelection();
        if (!sel || sel.rangeCount === 0 || sel.toString().trim().length === 0) return false;
        var range = sel.getRangeAt(0);

        // 현재 페이지의 visible area 계산
        var manager = rendition.manager;
        if (!manager || !manager.container) return false;
        var scrollLeft = manager.container.scrollLeft;
        var pageWidth = manager.layout ? manager.layout.delta : manager.container.offsetWidth;
        var rightEdge = scrollLeft + pageWidth;

        // selection 끝의 bounding rect 확인
        var endRange = doc.createRange();
        endRange.setStart(range.endContainer, range.endOffset);
        endRange.setEnd(range.endContainer, range.endOffset);

        // 페이지의 마지막 visible 텍스트 노드를 찾아서 비교
        var walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT, null, false);
        var lastVisibleTextNode = null;
        var node;
        while (node = walker.nextNode()) {
            if (node.textContent.trim().length === 0) continue;
            var r = doc.createRange();
            r.selectNodeContents(node);
            var rects = r.getClientRects();
            for (var i = 0; i < rects.length; i++) {
                var rect = rects[i];
                // 이 텍스트 노드의 rect가 현재 페이지 내에 있는지 확인
                if (rect.right > scrollLeft && rect.left < rightEdge) {
                    lastVisibleTextNode = node;
                }
            }
        }

        if (!lastVisibleTextNode) return false;

        // selection의 끝이 마지막 visible 텍스트 노드의 끝과 일치하는지 확인
        return range.endContainer === lastVisibleTextNode &&
               range.endOffset === lastVisibleTextNode.textContent.length;
    } catch(e) { return false; }
};
```

- [ ] **Step 2: `_selectFirstCharOfPage()` 함수 추가**

`_isSelectionAtPageEnd` 바로 아래에 추가:

```javascript
window._selectFirstCharOfPage = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return;
        var doc = iframe.contentDocument;

        var manager = rendition.manager;
        if (!manager || !manager.container) return;
        var scrollLeft = manager.container.scrollLeft;
        var pageWidth = manager.layout ? manager.layout.delta : manager.container.offsetWidth;
        var rightEdge = scrollLeft + pageWidth;

        // 현재 페이지의 첫 번째 visible 텍스트 노드 찾기
        var walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT, null, false);
        var firstVisibleTextNode = null;
        var node;
        while (node = walker.nextNode()) {
            if (node.textContent.trim().length === 0) continue;
            var r = doc.createRange();
            r.selectNodeContents(node);
            var rects = r.getClientRects();
            for (var i = 0; i < rects.length; i++) {
                var rect = rects[i];
                if (rect.right > scrollLeft && rect.left < rightEdge) {
                    firstVisibleTextNode = node;
                    break;
                }
            }
            if (firstVisibleTextNode) break;
        }

        if (!firstVisibleTextNode) return;

        // 첫 글자 선택
        var range = doc.createRange();
        range.setStart(firstVisibleTextNode, 0);
        range.setEnd(firstVisibleTextNode, Math.min(1, firstVisibleTextNode.textContent.length));
        var sel = doc.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
    } catch(e) {}
};
```

- [ ] **Step 3: `_mergeCfi(startCfi, endCfi)` 함수 추가**

`_selectFirstCharOfPage` 바로 아래에 추가:

```javascript
window._mergeCfi = function(startCfi, endCfi) {
    try {
        // CFI 범위 형식: epubcfi(/6/N[id]!/X/Y,/A:B,/C:D)
        // startCfi에서 base + 시작점을, endCfi에서 끝점을 추출하여 합성
        // 같은 spine item 내에서만 동작
        var sMatch = startCfi.match(/^(epubcfi\([^,]+),([^,]+),([^)]+)\)$/);
        var eMatch = endCfi.match(/^(epubcfi\([^,]+),([^,]+),([^)]+)\)$/);
        if (sMatch && eMatch) {
            return sMatch[1] + ',' + sMatch[2] + ',' + eMatch[3] + ')';
        }
        // 매칭 실패 시 endCfi 반환 (폴백)
        return endCfi;
    } catch(e) { return endCfi; }
};
```

- [ ] **Step 4: 수동 테스트**

앱을 빌드하고 실행한다. 브라우저 콘솔 또는 Android Logcat에서 에러가 없는지 확인한다. 기존 하이라이트 기능이 정상 동작하는지 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: 크로스 페이지 하이라이트를 위한 JS 헬퍼 함수 추가"
```

---

### Task 2: `onSelectionTapped` 콜백에 `isAtPageEnd` 전달

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`

- [ ] **Step 1: EpubBridge에 `isAtPageEnd` 파라미터 추가**

`EpubBridge` 클래스(line ~3830)의 `onSelectionTappedCallback` 시그니처를 변경:

```kotlin
// 변경 전 (line 3843):
private val onSelectionTappedCallback: (text: String, x: Float, y: Float, bottom: Float, cfi: String) -> Unit = { _, _, _, _, _ -> }

// 변경 후:
private val onSelectionTappedCallback: (text: String, x: Float, y: Float, bottom: Float, cfi: String, isAtPageEnd: Boolean) -> Unit = { _, _, _, _, _, _ -> }
```

`onSelectionTapped` 메서드(line ~3911)도 변경:

```kotlin
// 변경 전:
@android.webkit.JavascriptInterface
fun onSelectionTapped(text: String, x: Float, y: Float, bottom: Float, cfi: String) {
    mainHandler.post { onSelectionTappedCallback(text, x, y, bottom, cfi) }
}

// 변경 후:
@android.webkit.JavascriptInterface
fun onSelectionTapped(text: String, x: Float, y: Float, bottom: Float, cfi: String, isAtPageEnd: Boolean) {
    mainHandler.post { onSelectionTappedCallback(text, x, y, bottom, cfi, isAtPageEnd) }
}
```

- [ ] **Step 2: JavaScript의 `onSelectionTapped` 호출에 `isAtPageEnd` 추가**

`onSingleTapUp` 내 JavaScript 코드(line ~2493)에서 `Android.onSelectionTapped` 호출 부분을 변경:

```javascript
// 변경 전 (line 2493-2498):
var cfi = typeof window._getCfiFromSelection === 'function' ? (window._getCfiFromSelection() || '') : '';
Android.onSelectionTapped(
    sel.toString().trim(),
    boundRect.left + boundRect.width/2 + iframeRect.left,
    boundRect.top + iframeRect.top,
    boundRect.bottom + iframeRect.top,
    cfi
);

// 변경 후:
var cfi = typeof window._getCfiFromSelection === 'function' ? (window._getCfiFromSelection() || '') : '';
var isAtPageEnd = typeof window._isSelectionAtPageEnd === 'function' ? window._isSelectionAtPageEnd() : false;
Android.onSelectionTapped(
    sel.toString().trim(),
    boundRect.left + boundRect.width/2 + iframeRect.left,
    boundRect.top + iframeRect.top,
    boundRect.bottom + iframeRect.top,
    cfi,
    isAtPageEnd
);
```

- [ ] **Step 3: `SelectionState`에 `isAtPageEnd` 추가**

`SelectionState` data class(line 2374)를 변경:

```kotlin
// 변경 전:
data class SelectionState(val text: String, val x: Float, val y: Float, val bottom: Float, val cfi: String = "")

// 변경 후:
data class SelectionState(val text: String, val x: Float, val y: Float, val bottom: Float, val cfi: String = "", val isAtPageEnd: Boolean = false)
```

- [ ] **Step 4: `selectionOnSelectionTapped` 콜백 업데이트**

line ~2398의 콜백을 변경:

```kotlin
// 변경 전:
val selectionOnSelectionTapped: (String, Float, Float, Float, String) -> Unit = { text, x, y, bottom, cfi ->
    if (text.isNotEmpty()) selectionState = SelectionState(text, x, y, bottom, cfi)
}

// 변경 후:
val selectionOnSelectionTapped: (String, Float, Float, Float, String, Boolean) -> Unit = { text, x, y, bottom, cfi, isAtPageEnd ->
    if (text.isNotEmpty()) selectionState = SelectionState(text, x, y, bottom, cfi, isAtPageEnd)
}
```

- [ ] **Step 5: EpubViewer 파라미터 타입 업데이트**

EpubViewer 함수 시그니처(line ~2347)는 `onTextSelected`와 `onHighlight`를 개별 파라미터로 받지 않고, `selectionOnSelectionTapped`를 직접 `EpubBridge`에 전달하므로 EpubBridge 생성자 호출(line ~2463)이 기존 `selectionOnSelectionTapped`를 그대로 전달하면 된다. 타입 불일치가 없는지 확인한다.

- [ ] **Step 6: 수동 테스트**

앱 빌드 후 텍스트 선택 → 팝오버가 정상 표시되는지 확인. 기존 기능 동작 확인.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: onSelectionTapped에 isAtPageEnd 파라미터 추가"
```

---

### Task 3: "이어하기" 모드 상태 및 팝오버 UI

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`

- [ ] **Step 1: EpubViewer 내부에 이어하기 모드 상태 추가**

line ~2375 (`selectionState` 선언 근처)에 상태 추가:

```kotlin
var selectionState by remember { mutableStateOf<SelectionState?>(null) }
// 아래 추가:
var pendingStartCfi by remember { mutableStateOf<String?>(null) }
var pendingStartText by remember { mutableStateOf<String?>(null) }
var isContinuationMode by remember { mutableStateOf(false) }
```

- [ ] **Step 2: `SelectionPopup`에 `onContinue` 파라미터 추가**

`SelectionPopup` composable(line ~2630)을 변경:

```kotlin
// 변경 전:
@Composable
private fun SelectionPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    onHighlight: () -> Unit,
    onMemo: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
)

// 변경 후:
@Composable
private fun SelectionPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    onHighlight: () -> Unit,
    onMemo: () -> Unit,
    onShare: () -> Unit,
    onContinue: (() -> Unit)?,
    onDismiss: () -> Unit
)
```

- [ ] **Step 3: `SelectionPopup` UI에 "이어하기" 버튼 추가**

`SelectionPopup` 내부 `Row`(line ~2652)에 조건부 버튼 추가. 기존 "공유" 버튼 뒤에:

```kotlin
// 기존 공유 버튼 뒤에 추가:
if (onContinue != null) {
    Box(Modifier.width(1.dp).height(20.dp).background(Color.Black))
    TextButton(onClick = onContinue) {
        Text("이어하기", color = Color.Black, fontSize = 14.sp)
    }
}
```

- [ ] **Step 4: `SelectionPopup` 호출 부분 업데이트**

line ~2608의 `selectionState?.let` 블록에서 `SelectionPopup` 호출을 변경:

```kotlin
selectionState?.let { sel ->
    SelectionPopup(
        selectionY = sel.y,
        selectionBottom = sel.bottom,
        selectionCx = sel.x,
        onHighlight = {
            if (isContinuationMode && pendingStartCfi != null) {
                // 이어하기 모드: CFI 합성 후 하이라이트 생성
                val startCfi = pendingStartCfi!!
                val startText = pendingStartText ?: ""
                val endText = sel.text
                val combinedText = startText + endText
                webViewRef.get()?.evaluateJavascript(
                    "window._mergeCfi(\"${startCfi.replace("\\", "\\\\").replace("\"", "\\\"")}\", \"${sel.cfi.replace("\\", "\\\\").replace("\"", "\\\"")}\")"
                ) { mergedCfi ->
                    val cfi = mergedCfi?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: sel.cfi
                    onHighlight(combinedText, cfi)
                }
                pendingStartCfi = null
                pendingStartText = null
                isContinuationMode = false
                clearSelection()
            } else {
                onHighlight(sel.text, sel.cfi)
                clearSelection()
            }
        },
        onMemo = {
            if (isContinuationMode && pendingStartCfi != null) {
                val startCfi = pendingStartCfi!!
                val startText = pendingStartText ?: ""
                val endText = sel.text
                val combinedText = startText + endText
                webViewRef.get()?.evaluateJavascript(
                    "window._mergeCfi(\"${startCfi.replace("\\", "\\\\").replace("\"", "\\\"")}\", \"${sel.cfi.replace("\\", "\\\\").replace("\"", "\\\"")}\")"
                ) { mergedCfi ->
                    val cfi = mergedCfi?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: sel.cfi
                    onMemo(combinedText, cfi)
                }
                pendingStartCfi = null
                pendingStartText = null
                isContinuationMode = false
                clearSelection()
            } else {
                onMemo(sel.text, sel.cfi)
                clearSelection()
            }
        },
        onShare = {
            val shareText = if (isContinuationMode) (pendingStartText ?: "") + sel.text else sel.text
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, null))
            pendingStartCfi = null
            pendingStartText = null
            isContinuationMode = false
            clearSelection()
        },
        onContinue = if (sel.isAtPageEnd && !isContinuationMode) {
            {
                // 시작 CFI와 텍스트 저장
                pendingStartCfi = sel.cfi
                pendingStartText = sel.text
                isContinuationMode = true
                selectionState = null
                // 다음 페이지로 이동
                webViewRef.get()?.evaluateJavascript("window._next()", null)
                // 이동 완료 후 첫 글자 선택 (relocated 이벤트 대기 필요)
                webViewRef.get()?.postDelayed({
                    webViewRef.get()?.evaluateJavascript("window._selectFirstCharOfPage()", null)
                }, 300)
            }
        } else null,
        onDismiss = {
            if (isContinuationMode) {
                pendingStartCfi = null
                pendingStartText = null
                isContinuationMode = false
            }
            clearSelection()
        }
    )
}
```

- [ ] **Step 5: 이어하기 모드 취소 시 상태 정리**

`clearSelection` 함수(line ~2384)는 이미 `selectionState = null`을 하므로 추가 변경 불필요. 단, 이어하기 모드를 취소하지 않으려면 `clearSelection`에서는 continuation 상태를 건드리지 않는다 (이미 팝오버 onDismiss에서 처리).

- [ ] **Step 6: 수동 테스트**

1. 텍스트 선택 후 페이지 끝까지 드래그 → 팝오버에 "이어하기" 버튼 표시 확인
2. "이어하기" 클릭 → 다음 페이지로 이동 → 첫 글자 선택 상태 확인
3. 핸들 드래그로 범위 조정 → 탭 → 팝오버 표시 ("이어하기" 없음 확인)
4. "하이라이트" 클릭 → 하이라이트 생성 확인
5. 이전 페이지로 돌아가서 하이라이트 렌더링 확인
6. 기존 단일 페이지 하이라이트 정상 동작 확인

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: 크로스 페이지 하이라이트 이어하기 기능 구현"
```

---

### Task 4: 엣지 케이스 처리

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`

- [ ] **Step 1: spine item 마지막 페이지에서 "이어하기" 방지**

`_isSelectionAtPageEnd` 함수에서, 현재 페이지가 spine item의 마지막 페이지인 경우(다음 페이지가 다른 chapter) `false`를 반환하도록 수정. spine item이 바뀌면 CFI 합성이 불가능하기 때문이다.

`_isSelectionAtPageEnd` 함수 시작 부분에 다음 체크를 추가:

```javascript
// spine item의 마지막 페이지인지 확인
var manager = rendition.manager;
if (!manager || !manager.container) return false;
var scrollLeft = manager.container.scrollLeft;
var offsetWidth = manager.container.offsetWidth;
var scrollWidth = manager.container.scrollWidth;
var delta = manager.layout ? manager.layout.delta : offsetWidth;
// 마지막 페이지이면 false (다음 페이지가 다른 spine item)
if (scrollLeft + offsetWidth + delta > scrollWidth + delta * 0.5) return false;
```

이 코드는 `_isSelectionAtPageEnd` 함수의 `try {` 바로 아래, iframe 접근 코드 전에 삽입한다.

- [ ] **Step 2: 페이지 이동 타이밍 개선**

Task 3 Step 4에서 `postDelayed(300ms)`를 사용했는데, `relocated` 이벤트를 활용하면 더 안정적이다. JavaScript에 `_pendingContinuation` 플래그를 추가:

HTML template의 `rendition.on("relocated", ...)` 핸들러(line ~3084) 내부, `reportLocation` 호출 후에 추가:

```javascript
// 기존 relocated 핸들러 끝 부분 (line ~3124 근처)에 추가:
if (window._pendingContinuation) {
    window._pendingContinuation = false;
    setTimeout(function() { window._selectFirstCharOfPage(); }, 50);
}
```

그리고 Task 3 Step 4의 "이어하기" 핸들러에서 `postDelayed` 대신 플래그를 사용:

```kotlin
// 변경 전:
webViewRef.get()?.evaluateJavascript("window._next()", null)
webViewRef.get()?.postDelayed({
    webViewRef.get()?.evaluateJavascript("window._selectFirstCharOfPage()", null)
}, 300)

// 변경 후:
webViewRef.get()?.evaluateJavascript("window._pendingContinuation = true; window._next()", null)
```

- [ ] **Step 3: 수동 테스트**

1. chapter 마지막 페이지에서 텍스트 끝까지 선택 → "이어하기" 버튼 없는지 확인
2. 일반 페이지에서 이어하기 → 다음 페이지 첫 글자 선택 확인 (타이밍 안정적인지)
3. 빠른 연타 시에도 정상 동작하는지 확인

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "fix: 크로스 페이지 하이라이트 엣지 케이스 처리"
```
