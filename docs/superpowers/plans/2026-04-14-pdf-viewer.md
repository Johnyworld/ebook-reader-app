# PDF 뷰어 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** WebView + PDF.js 기반 PDF 뷰어 구현 — EPUB 뷰어와 동일한 아키텍처, 동일한 UI

**Architecture:** PDF.js를 WebView에서 실행하여 단일 페이지 렌더링. PdfBridge로 JS→Kotlin 콜백 처리. 기존 BookReaderScreen/ViewModel의 메뉴, 북마크, 검색, TOC UI를 그대로 재사용.

**Tech Stack:** PDF.js (Mozilla), Android WebView, Jetpack Compose, Room DB

---

## 파일 구조

### 생성할 파일

| 파일 | 역할 |
|------|------|
| `reader/PdfBridge.kt` | JS → Kotlin 콜백 브릿지 |
| `reader/PdfHtmlTemplate.kt` | PDF.js HTML 템플릿 생성 |
| `assets/pdfjs/pdf.min.js` | PDF.js 라이브러리 |
| `assets/pdfjs/pdf.worker.min.js` | PDF.js 웹 워커 |
| `assets/pdf/js/init.js` | PDF 초기화, 페이지 렌더링 |
| `assets/pdf/js/navigation.js` | 페이지 넘기기 |
| `assets/pdf/js/search.js` | 본문 검색 |
| `assets/pdf/js/toc.js` | 목차(outline) 추출 |

### 수정할 파일

| 파일 | 변경 내용 |
|------|-----------|
| `reader/PdfViewer.kt` | PdfRenderer 기반 → WebView 기반으로 전체 교체 |
| `BookReaderScreen.kt:131` | `loadBook` 호출 시 isPdf 분기 추가 |
| `BookReaderScreen.kt:167-231` | PDF 뷰어 분기에 콜백 연결 |
| `BookReaderScreen.kt:120-164` | 메뉴 오픈 시 CFI 업데이트, 북마크 체크 등 PDF 분기 처리 |
| `BookReaderScreen.kt:349-368` | 메뉴 항목 중 PDF에서 불필요한 것(하이라이트, 메모) 숨김 |
| `BookReaderScreen.kt:463-513` | TOC/검색 팝업에서 PDF WebView로 네비게이션 |
| `BookReaderScreen.kt:714-741` | 설정 시트에서 PDF일 때 뷰어 탭만 표시 |
| `BookReaderViewModel.kt:138-179` | `loadBook`에서 PDF 분기 — 스캔 건너뛰기, TOC 로드 없음 |
| `reader/ReaderSettingsSheet.kt:74-106` | PDF일 때 "글자", "여백" 탭 숨기고 "뷰어" 탭만 표시 |

---

### Task 1: PDF.js 라이브러리 준비 및 JS 모듈 작성

**Files:**
- Create: `app/src/main/assets/pdfjs/pdf.min.js`
- Create: `app/src/main/assets/pdfjs/pdf.worker.min.js`
- Create: `app/src/main/assets/pdf/js/init.js`
- Create: `app/src/main/assets/pdf/js/navigation.js`
- Create: `app/src/main/assets/pdf/js/search.js`
- Create: `app/src/main/assets/pdf/js/toc.js`

- [ ] **Step 1: PDF.js 라이브러리 다운로드**

PDF.js 4.x stable 빌드를 다운로드하여 assets에 배치한다.

```bash
cd /Users/rotein/dev/ebook-reader-app
mkdir -p app/src/main/assets/pdfjs
curl -L "https://cdn.jsdelivr.net/npm/pdfjs-dist@4.10.38/build/pdf.min.mjs" -o app/src/main/assets/pdfjs/pdf.min.mjs
curl -L "https://cdn.jsdelivr.net/npm/pdfjs-dist@4.10.38/build/pdf.worker.min.mjs" -o app/src/main/assets/pdfjs/pdf.worker.min.mjs
```

> **참고:** PDF.js 4.x는 ESM(`.mjs`)만 제공한다. HTML 템플릿에서 `<script type="module">`로 로드해야 한다. 만약 WebView ESM 호환 문제가 있으면 3.x legacy 빌드(`pdf.min.js`)로 대체한다.

- [ ] **Step 2: init.js 작성**

```javascript
// init.js — PDF.js 초기화, 페이지 렌더링

var _pdf = {};
_pdf.pdfDoc = null;
_pdf.currentPage = 1;
_pdf.totalPages = 0;
_pdf.rendering = false;
_pdf.pendingPage = null;

function _initPdf() {
    pdfjsLib.GlobalWorkerOptions.workerSrc = 'file:///android_asset/pdfjs/pdf.worker.min.mjs';

    pdfjsLib.getDocument(_pdfConfig.pdfPath).promise.then(function(pdf) {
        _pdf.pdfDoc = pdf;
        _pdf.totalPages = pdf.numPages;
        _pdf.currentPage = Math.max(1, Math.min(_pdfConfig.startPage, _pdf.totalPages));

        _renderPage(_pdf.currentPage);

        // TOC(outline) 추출
        _loadOutline();
    }).catch(function(error) {
        console.error('PDF load error:', error);
    });
}

function _renderPage(pageNum) {
    if (!_pdf.pdfDoc) return;
    pageNum = Math.max(1, Math.min(pageNum, _pdf.totalPages));

    if (_pdf.rendering) {
        _pdf.pendingPage = pageNum;
        return;
    }
    _pdf.rendering = true;
    _pdf.currentPage = pageNum;

    _pdf.pdfDoc.getPage(pageNum).then(function(page) {
        var container = document.getElementById('pdf-container');
        var containerW = container.clientWidth;
        var containerH = container.clientHeight;

        var unscaledViewport = page.getViewport({ scale: 1 });
        var scale = Math.min(containerW / unscaledViewport.width, containerH / unscaledViewport.height);
        var viewport = page.getViewport({ scale: scale * (window.devicePixelRatio || 1) });
        var displayViewport = page.getViewport({ scale: scale });

        var canvas = document.getElementById('pdf-canvas');
        if (!canvas) {
            canvas = document.createElement('canvas');
            canvas.id = 'pdf-canvas';
            container.appendChild(canvas);
        }

        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = displayViewport.width + 'px';
        canvas.style.height = displayViewport.height + 'px';

        var ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        page.render({ canvasContext: ctx, viewport: viewport }).promise.then(function() {
            _pdf.rendering = false;

            var progress = _pdf.currentPage / _pdf.totalPages;
            Android.onPageChanged(_pdf.currentPage, _pdf.totalPages);
            Android.onLocationUpdate(progress, _pdf.currentPage, '');
            Android.onContentLoaded();

            if (_pdf.pendingPage !== null) {
                var next = _pdf.pendingPage;
                _pdf.pendingPage = null;
                _renderPage(next);
            }
        });
    });
}

_initPdf();
```

- [ ] **Step 3: navigation.js 작성**

```javascript
// navigation.js — 페이지 넘기기

window._prevPage = function() {
    if (_pdf.currentPage <= 1) return;
    _renderPage(_pdf.currentPage - 1);
};

window._nextPage = function() {
    if (_pdf.currentPage >= _pdf.totalPages) return;
    _renderPage(_pdf.currentPage + 1);
};

window._goToPage = function(pageNum) {
    pageNum = parseInt(pageNum);
    if (isNaN(pageNum)) return;
    _renderPage(pageNum);
};
```

- [ ] **Step 4: search.js 작성**

```javascript
// search.js — PDF 본문 검색

window._search = function(query) {
    if (!_pdf.pdfDoc || !query) {
        Android.onSearchComplete();
        return;
    }

    var lowerQuery = query.toLowerCase();
    var pageIdx = 0;

    function searchNextPage() {
        if (pageIdx >= _pdf.totalPages) {
            Android.onSearchComplete();
            return;
        }

        var pageNum = pageIdx + 1;
        pageIdx++;

        _pdf.pdfDoc.getPage(pageNum).then(function(page) {
            return page.getTextContent();
        }).then(function(textContent) {
            var text = textContent.items.map(function(item) { return item.str; }).join(' ');
            var lowerText = text.toLowerCase();
            var positions = [];
            var idx = lowerText.indexOf(lowerQuery);
            while (idx !== -1 && positions.length < 5) {
                positions.push(idx);
                idx = lowerText.indexOf(lowerQuery, idx + 1);
            }

            if (positions.length > 0) {
                var found = [];
                for (var i = 0; i < positions.length; i++) {
                    var pos = positions[i];
                    var start = Math.max(0, pos - 60);
                    var end = Math.min(text.length, pos + query.length + 60);
                    found.push({
                        cfi: 'pdf-page:' + pageNum,
                        excerpt: text.substring(start, end).replace(/\s+/g, ' ').trim(),
                        chapter: '',
                        page: pageNum,
                        spineIndex: pageNum - 1,
                        charPos: pos
                    });
                }
                Android.onSearchResultsPartial(JSON.stringify(found));
            }

            setTimeout(searchNextPage, 0);
        }).catch(function() {
            setTimeout(searchNextPage, 0);
        });
    }

    searchNextPage();
};

window._clearSearch = function() {};
```

- [ ] **Step 5: toc.js 작성**

```javascript
// toc.js — PDF outline(목차) 추출

function _loadOutline() {
    if (!_pdf.pdfDoc) return;

    _pdf.pdfDoc.getOutline().then(function(outline) {
        if (!outline || outline.length === 0) {
            Android.onTocLoaded('[]');
            return;
        }

        _convertOutline(outline, 0).then(function(tocItems) {
            Android.onTocLoaded(JSON.stringify(tocItems));
        });
    }).catch(function() {
        Android.onTocLoaded('[]');
    });
}

function _convertOutline(items, depth) {
    var promises = items.map(function(item) {
        return _resolveOutlineDest(item).then(function(pageNum) {
            var subPromise = (item.items && item.items.length > 0)
                ? _convertOutline(item.items, depth + 1)
                : Promise.resolve([]);
            return subPromise.then(function(subitems) {
                return {
                    label: item.title || '',
                    href: 'pdf-page:' + pageNum,
                    depth: depth,
                    page: pageNum,
                    subitems: subitems
                };
            });
        });
    });
    return Promise.all(promises);
}

function _resolveOutlineDest(item) {
    if (!item.dest) return Promise.resolve(1);

    if (typeof item.dest === 'string') {
        return _pdf.pdfDoc.getDestination(item.dest).then(function(dest) {
            if (!dest) return 1;
            return _pdf.pdfDoc.getPageIndex(dest[0]).then(function(idx) { return idx + 1; });
        }).catch(function() { return 1; });
    }

    if (Array.isArray(item.dest) && item.dest.length > 0) {
        return _pdf.pdfDoc.getPageIndex(item.dest[0]).then(function(idx) {
            return idx + 1;
        }).catch(function() { return 1; });
    }

    return Promise.resolve(1);
}
```

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/assets/pdfjs/ app/src/main/assets/pdf/js/
git commit -m "feat: PDF.js 라이브러리 및 JS 모듈 추가 (init, navigation, search, toc)"
```

---

### Task 2: PdfBridge + PdfHtmlTemplate 작성

**Files:**
- Create: `app/src/main/java/com/rotein/ebookreader/reader/PdfBridge.kt`
- Create: `app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt`

- [ ] **Step 1: PdfBridge.kt 작성**

```kotlin
package com.rotein.ebookreader.reader

internal class PdfBridge(
    private val onPageChangedCallback: (currentPage: Int, totalPages: Int) -> Unit,
    private val onLocationUpdateCallback: (progress: Float, pageNum: Int, pageTitle: String) -> Unit = { _, _, _ -> },
    private val onContentLoadedCallback: () -> Unit = {},
    private val onTocLoadedCallback: (tocJson: String) -> Unit = {},
    private val onSearchResultsPartialCallback: (resultsJson: String) -> Unit = {},
    private val onSearchCompleteCallback: () -> Unit = {}
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
}
```

- [ ] **Step 2: PdfHtmlTemplate.kt 작성**

```kotlin
package com.rotein.ebookreader.reader

internal fun buildPdfHtml(pdfPath: String, startPage: Int) = """<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'/>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 100%; height: 100%; overflow: hidden; background: #fff; }
#pdf-container {
    width: 100%; height: 100%;
    display: flex; align-items: center; justify-content: center;
    overflow: hidden;
}
#pdf-canvas { display: block; }
</style>
</head>
<body>
<div id="pdf-container"></div>
<script>
var _pdfConfig = {
    pdfPath: "$pdfPath",
    startPage: $startPage
};
</script>
<script type="module">
import * as pdfjsMod from './pdfjs/pdf.min.mjs';
window.pdfjsLib = pdfjsMod;
</script>
<script src="pdf/js/init.js"></script>
<script src="pdf/js/navigation.js"></script>
<script src="pdf/js/search.js"></script>
<script src="pdf/js/toc.js"></script>
</body>
</html>"""
```

> **참고:** PDF.js 4.x ESM을 `<script type="module">`로 로드 후 `window.pdfjsLib`에 할당한다. `init.js`에서 `pdfjsLib`을 참조한다. module 스크립트가 먼저 실행되므로 init.js보다 앞에 배치. 만약 module 로드 순서 문제가 있으면, module 안에서 init까지 import하는 방식으로 변경한다.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/reader/PdfBridge.kt app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt
git commit -m "feat: PdfBridge, PdfHtmlTemplate 추가"
```

---

### Task 3: PdfViewer.kt 교체 (WebView 기반)

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/PdfViewer.kt` (전체 교체)

- [ ] **Step 1: PdfViewer.kt를 WebView 기반으로 교체**

```kotlin
package com.rotein.ebookreader.reader

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
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
                            onSearchCompleteCallback = onSearchComplete
                        ), "Android")
                    }
                    webViewRef.set(webView)
                    onWebViewCreated(webView)

                    val overlay = android.view.View(ctx).apply {
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                val x = e.x
                                val y = e.y
                                val w = this@apply.width.toFloat()
                                val h = this@apply.height.toFloat()
                                when (pageFlipRef.get()) {
                                    ReaderPageFlip.LR_PREV_NEXT -> when {
                                        x < w / 3f -> webView.evaluateJavascript("window._prevPage()", null)
                                        x > w * 2f / 3f -> webView.evaluateJavascript("window._nextPage()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.LR_NEXT_PREV -> when {
                                        x < w / 3f -> webView.evaluateJavascript("window._nextPage()", null)
                                        x > w * 2f / 3f -> webView.evaluateJavascript("window._prevPage()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_PREV_NEXT -> when {
                                        y < h / 3f -> webView.evaluateJavascript("window._prevPage()", null)
                                        y > h * 2f / 3f -> webView.evaluateJavascript("window._nextPage()", null)
                                        else -> onCenterTap()
                                    }
                                    ReaderPageFlip.TB_NEXT_PREV -> when {
                                        y < h / 3f -> webView.evaluateJavascript("window._nextPage()", null)
                                        y > h * 2f / 3f -> webView.evaluateJavascript("window._prevPage()", null)
                                        else -> onCenterTap()
                                    }
                                }
                                this@apply.performClick()
                                return true
                            }
                        })
                        setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
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
```

- [ ] **Step 2: 빌드 확인**

```bash
cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/reader/PdfViewer.kt
git commit -m "feat: PdfViewer를 WebView + PDF.js 기반으로 교체"
```

---

### Task 4: BookReaderScreen에 PDF 뷰어 연결

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`

- [ ] **Step 1: epubWebView를 viewerWebView로 이름 변경 (EPUB/PDF 공용)**

`BookReaderScreen.kt`에서 `epubWebView`를 `viewerWebView`로 rename한다. 이 WebView 참조를 EPUB과 PDF 모두에서 사용하기 위함.

파일 전체에서 `epubWebView`를 `viewerWebView`로 치환한다.

- [ ] **Step 2: isPdf 플래그 추가 및 loadBook 호출 수정**

```kotlin
// BookReaderScreen.kt 77번째 줄 근처, 기존 변수 선언 영역에 추가
val isPdf = book.extension.lowercase() == "pdf"
val isEpub = book.extension.lowercase() == "epub"
```

`LaunchedEffect(book.path)` (line 129) 수정:
```kotlin
LaunchedEffect(book.path) {
    vm.loadBook(book.path, isEpub)
}
```
(이미 동일하므로 변경 불필요)

- [ ] **Step 3: PDF 뷰어 분기 연결 (line 231)**

현재:
```kotlin
"pdf"  -> PdfViewer(book.path, onCenterTap)
```

변경:
```kotlin
"pdf"  -> PdfViewer(
    path = book.path,
    savedPage = readingState.savedCfi
        ?.removePrefix("pdf-page:")?.toIntOrNull() ?: 1,
    pageFlip = readerSettings.pageFlip,
    onCenterTap = onCenterTap,
    onPageChanged = { page, total ->
        vm.updatePageInfo(page, total)
    },
    onLocationUpdate = { progress, pageNum ->
        val cfi = "pdf-page:$pageNum"
        vm.updateCurrentCfi(cfi)
        vm.saveCfi(cfi)
        vm.updateLocation(progress, cfi, "")
    },
    onContentLoaded = {
        vm.setLoading(false)
        vm.setContentRendered(true)
    },
    onTocLoaded = { tocJson -> vm.onTocLoaded(tocJson) },
    onSearchResultsPartial = { json -> vm.onSearchResultsPartial(json) },
    onSearchComplete = { vm.onSearchComplete() },
    onWebViewCreated = { webView -> viewerWebView.value = webView }
)
```

- [ ] **Step 4: 메뉴 오픈 시 CFI 업데이트 분기 (line 120-127)**

현재 EPUB 전용 JS 호출:
```kotlin
LaunchedEffect(popupState.showMenu) {
    if (popupState.showMenu) {
        viewerWebView.value?.evaluateJavascript("window._currentCfi || ''") { ... }
    }
}
```

PDF일 때는 JS에 `_currentCfi`가 없으므로 분기:
```kotlin
LaunchedEffect(popupState.showMenu) {
    if (popupState.showMenu && isEpub) {
        viewerWebView.value?.evaluateJavascript("window._currentCfi || ''") { result ->
            val cfi = result?.removeSurrounding("\"")?.trim().orEmpty()
            if (cfi.isNotEmpty()) vm.updateCurrentCfi(cfi)
        }
    }
}
```

- [ ] **Step 5: 북마크 체크 로직 PDF 분기 (line 149-164)**

EPUB의 CFI 범위 비교 로직은 PDF에서 동작하지 않는다. PDF에서는 단순 페이지 번호 비교:

```kotlin
LaunchedEffect(readingState.currentCfi, annotationState.bookmarks, contentState.isContentRendered) {
    if (!contentState.isContentRendered) return@LaunchedEffect
    if (isPdf) {
        val currentPageCfi = readingState.currentCfi
        val isBookmarked = annotationState.bookmarks.any { it.cfi == currentPageCfi }
        vm.setCurrentPageBookmarked(isBookmarked)
        return@LaunchedEffect
    }
    // 기존 EPUB 로직 유지...
}
```

- [ ] **Step 6: 하이라이트/메모 주입 스킵 (line 133-147)**

`contentState.isContentRendered` LaunchedEffect에서 PDF일 때 하이라이트/메모 JS 주입을 건너뛴다:

```kotlin
LaunchedEffect(contentState.isContentRendered) {
    if (!contentState.isContentRendered) return@LaunchedEffect
    if (isPdf) return@LaunchedEffect  // PDF: 하이라이트/메모 미지원
    // 기존 EPUB 로직...
}
```

- [ ] **Step 7: 헤더 북마크 버튼 PDF 분기 (line 396-449)**

현재 헤더의 북마크 추가/삭제가 EPUB JS를 호출한다. PDF에서는 단순 페이지 기반:

```kotlin
IconButton(onClick = {
    val currentCfi = readingState.currentCfi
    if (currentCfi.isEmpty()) return@IconButton
    if (annotationState.isCurrentPageBookmarked) {
        if (isPdf) {
            vm.removeBookmarksByCfis(setOf(currentCfi))
        } else {
            // 기존 EPUB 로직 (CFI 범위 비교)...
        }
    } else {
        if (isPdf) {
            val pageNum = currentCfi.removePrefix("pdf-page:").toIntOrNull() ?: 0
            val bookmark = Bookmark(
                bookPath = book.path,
                cfi = currentCfi,
                chapterTitle = "",
                excerpt = "${pageNum}페이지",
                page = pageNum,
                createdAt = System.currentTimeMillis()
            )
            vm.addBookmark(bookmark)
        } else {
            // 기존 EPUB 로직 (WebView에서 excerpt 추출)...
        }
    }
}) { ... }
```

- [ ] **Step 8: 메뉴 항목 — 하이라이트/메모 숨김 (line 349-368)**

PDF에서는 하이라이트와 메모 메뉴를 표시하지 않는다:

```kotlin
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
```

- [ ] **Step 9: TOC 팝업 — PDF 네비게이션 (line 463-481)**

TOC 팝업의 `onNavigate`에서 PDF일 때 `_goToPage` 호출:

```kotlin
if (popupState.showTocPopup) {
    TocPopup(
        tocItems = tocItems,
        bookTitle = book.metadata?.title ?: book.name,
        currentChapterTitle = readingState.chapterTitle,
        totalBookPages = readingState.totalPages,
        onNavigate = { href ->
            if (isPdf) {
                val pageNum = href.removePrefix("pdf-page:").toIntOrNull() ?: 1
                viewerWebView.value?.evaluateJavascript("window._goToPage($pageNum)", null)
                vm.setShowTocPopup(false)
                vm.setShowMenu(false)
            } else {
                if (onNavigationCompleteRef.value != null) return@TocPopup
                onNavigationCompleteRef.value = { vm.setShowTocPopup(false); vm.setShowMenu(false) }
                viewerWebView.value?.post {
                    viewerWebView.value?.evaluateJavascript(
                        "window._displayHref(\"${href.escapeCfiForJs()}\")", null
                    )
                }
            }
        },
        onDismiss = { vm.setShowTocPopup(false); onNavigationCompleteRef.value = null }
    )
}
```

- [ ] **Step 10: 검색 팝업 — PDF 네비게이션 (line 484-514)**

검색 팝업에서 PDF일 때 검색/네비게이션 분기:

```kotlin
if (popupState.showSearchPopup) {
    SearchPopup(
        searchResults = searchState.results,
        isSearching = searchState.isSearching,
        tocItems = tocItems,
        initialQuery = searchState.query,
        onSearch = { query ->
            vm.startSearch(query)
            if (isPdf) {
                viewerWebView.value?.evaluateJavascript("window._search(\"${query.escapeCfiForJs()}\")", null)
            } else {
                val escaped = query.escapeCfiForJs()
                viewerWebView.value?.post {
                    viewerWebView.value?.evaluateJavascript("window._setSearchHighlight(\"$escaped\")", null)
                    viewerWebView.value?.evaluateJavascript("window._search(\"$escaped\")", null)
                }
            }
        },
        onNavigate = { cfi, page ->
            if (isPdf) {
                val pageNum = cfi.removePrefix("pdf-page:").toIntOrNull() ?: page
                viewerWebView.value?.evaluateJavascript("window._goToPage($pageNum)", null)
                vm.setShowSearchPopup(false)
                vm.setShowMenu(false)
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
            if (!isPdf) {
                viewerWebView.value?.post {
                    viewerWebView.value?.evaluateJavascript("window._clearSearchHighlight()", null)
                }
            }
        },
        onDismiss = { vm.setShowSearchPopup(false); onNavigationCompleteRef.value = null }
    )
}
```

- [ ] **Step 11: 북마크 팝업 — PDF 네비게이션 (line 755-771)**

```kotlin
if (popupState.showBookmarkPopup) {
    BookmarkPopup(
        bookmarks = annotationState.bookmarks,
        spinePageOffsets = pageCalcState.spinePageOffsets,
        cfiPageMap = pageCalcState.cfiPageMap,
        onNavigate = { cfi ->
            if (isPdf) {
                val pageNum = cfi.removePrefix("pdf-page:").toIntOrNull() ?: 1
                viewerWebView.value?.evaluateJavascript("window._goToPage($pageNum)", null)
                vm.setShowBookmarkPopup(false)
                vm.setShowMenu(false)
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
```

- [ ] **Step 12: 빌드 확인**

```bash
cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 13: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: BookReaderScreen에 PDF 뷰어 콜백 및 팝업 네비게이션 연결"
```

---

### Task 5: BookReaderViewModel PDF 분기 처리

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderViewModel.kt`

- [ ] **Step 1: loadBook에서 PDF 분기**

`loadBook` 함수(line 138)에서 PDF일 때 불필요한 EPUB 로직을 건너뛴다:

```kotlin
fun loadBook(path: String, isEpub: Boolean) {
    bookPath = path
    _readingState.value = ReadingState()
    _contentState.value = ContentState(isLoading = true)  // PDF도 로딩 표시
    _tocItems.value = emptyList()
    _pageCalcState.value = PageCalcState()

    viewModelScope.launch {
        val record = withContext(Dispatchers.IO) { dao.getByPath(path) }
        _readingState.update { it.copy(savedCfi = record?.lastCfi ?: "") }

        if (isEpub) {
            // EPUB 전용: 캐시된 TOC, 페이지 스캔 캐시 로드
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
        }

        val bookmarks = withContext(Dispatchers.IO) { bookmarkDao.getByBook(path) }
        val highlights = withContext(Dispatchers.IO) { highlightDao.getByBook(path) }
        val memos = withContext(Dispatchers.IO) { memoDao.getByBook(path) }
        _annotationState.value = AnnotationState(bookmarks = bookmarks, highlights = highlights, memos = memos)
    }
}
```

핵심 변경: `ContentState(isLoading = isEpub)` → `ContentState(isLoading = true)`로 변경하여 PDF도 로딩을 표시. EPUB 전용 TOC/스캔캐시 로직을 `if (isEpub)` 블록으로 감싼다.

- [ ] **Step 2: 빌드 확인**

```bash
cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderViewModel.kt
git commit -m "feat: BookReaderViewModel loadBook에서 PDF 분기 처리"
```

---

### Task 6: 설정 시트 PDF 분기

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt` (설정 시트 호출부)

- [ ] **Step 1: ReaderSettingsBottomSheet에 isPdf 파라미터 추가**

```kotlin
@Composable
internal fun ReaderSettingsBottomSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenFontPopup: () -> Unit = {},
    isPdf: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(if (isPdf) 0 else 0) }
    val tabLabels = if (isPdf) listOf("뷰어") else listOf("글자", "여백", "뷰어")

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EreaderTabBar(
                tabs = tabLabels,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                trailingContent = {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
            )
        }
        HorizontalDivider(color = EreaderColors.Black)

        Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
            if (isPdf) {
                ReaderViewerTab(settings, onSettingsChange)
            } else {
                when (selectedTab) {
                    0 -> ReaderGlyphTab(settings, onSettingsChange, onOpenFontPopup)
                    1 -> ReaderMarginTab(settings, onSettingsChange)
                    2 -> ReaderViewerTab(settings, onSettingsChange)
                }
            }
        }
    }
}
```

- [ ] **Step 2: BookReaderScreen의 설정 시트 호출부에 isPdf 전달 (line 733)**

```kotlin
ReaderSettingsBottomSheet(
    settings = readerSettings,
    onSettingsChange = { vm.updateSettings(it) },
    onDismiss = { vm.setShowSettingsPopup(false) },
    onOpenFontPopup = { vm.setShowFontPopup(true) },
    isPdf = isPdf
)
```

- [ ] **Step 3: 빌드 확인**

```bash
cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/reader/ReaderSettingsSheet.kt app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: 설정 시트에서 PDF일 때 뷰어 탭만 표시"
```

---

### Task 7: PDF.js ESM 로딩 문제 대응 및 통합 테스트

**Files:**
- Modify: `app/src/main/assets/pdf/js/init.js` (필요 시)
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/PdfHtmlTemplate.kt` (필요 시)

- [ ] **Step 1: PDF.js ESM 로딩 확인**

Android WebView에서 `<script type="module">` + `file:///` 경로 조합이 정상 동작하는지 확인한다. 만약 ESM import가 실패하면:

**대안 A:** PDF.js 3.x legacy 빌드(UMD)를 대신 사용
```bash
curl -L "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js" -o app/src/main/assets/pdfjs/pdf.min.js
curl -L "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js" -o app/src/main/assets/pdfjs/pdf.worker.min.js
```

`PdfHtmlTemplate.kt` 변경:
```kotlin
// module import 대신 일반 script 태그
<script src="pdfjs/pdf.min.js"></script>
```

`init.js` 변경:
```javascript
pdfjsLib.GlobalWorkerOptions.workerSrc = 'file:///android_asset/pdfjs/pdf.worker.min.js';
```

**대안 B:** PDF.js 4.x를 module 블록 안에서 window에 할당하되, 나머지 JS도 module로 import

실제 디바이스 또는 에뮬레이터에서 테스트하여 어느 방식이 동작하는지 확인한다.

- [ ] **Step 2: 기기에서 PDF 열기 테스트**

테스트 항목:
1. PDF 파일이 화면에 렌더링되는가
2. 좌/우 탭으로 페이지 넘기기가 동작하는가
3. 중앙 탭으로 메뉴가 열리는가
4. 메뉴에서 하이라이트/메모 항목이 보이지 않는가
5. 하단 정보에 페이지/진행률이 표시되는가

- [ ] **Step 3: 북마크 테스트**

1. 메뉴 열고 북마크 아이콘 탭 → 북마크 추가
2. 다른 페이지로 이동
3. 북마크 팝업에서 북마크 탭 → 해당 페이지로 이동
4. 앱 재시작 → 마지막 읽은 페이지에서 이어서 열리는가

- [ ] **Step 4: TOC 테스트**

1. outline이 있는 PDF 열기
2. 메뉴 → 목차 버튼 탭
3. 목차 항목 탭 → 해당 페이지로 이동
4. outline이 없는 PDF → 빈 목차 또는 비활성화

- [ ] **Step 5: 검색 테스트**

1. 메뉴 → 검색 팝업 열기
2. 키워드 입력 → 결과 표시 (페이지 번호 + excerpt)
3. 결과 탭 → 해당 페이지로 이동

- [ ] **Step 6: 설정 테스트**

1. 메뉴 → 설정
2. 탭이 "뷰어" 하나만 보이는가
3. 페이지 넘김 방향 변경 → 즉시 반영
4. 하단 정보 변경 → 즉시 반영

- [ ] **Step 7: 문제 수정 후 커밋**

```bash
git add -A
git commit -m "fix: PDF 뷰어 통합 테스트 및 수정"
```
