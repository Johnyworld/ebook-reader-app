// init.js — PDF.js 초기화, 페이지 렌더링

window._showBookmarkRibbon = function(show) {
    var el = document.getElementById('bookmark-ribbon');
    if (el) el.style.display = show ? 'block' : 'none';
};

var _pdf = {};
_pdf.pdfDoc = null;
_pdf.currentPage = 1;
_pdf.totalPages = 0;
_pdf.rendering = false;
_pdf.pendingPage = null;
_pdf.dualPage = _pdfConfig.dualPage || false;

// 두 쪽 보기 활성 여부 (설정 켜짐 + 가로모드)
function _isDualActive() {
    return _pdf.dualPage && window.innerWidth > window.innerHeight;
}

// 현재 페이지에서 스프레드에 표시할 페이지 번호 목록 반환
// 첫 페이지(표지)는 항상 단독 표시, 이후 2-3, 4-5, ... 순서
function _getSpreadPages(pageNum) {
    if (!_isDualActive()) return [pageNum];
    if (pageNum <= 1) return [1];
    // 짝수 페이지가 스프레드 시작 (2-3, 4-5, ...)
    if (pageNum % 2 !== 0) pageNum = pageNum - 1;
    var pages = [pageNum];
    if (pageNum + 1 <= _pdf.totalPages) pages.push(pageNum + 1);
    return pages;
}

// 두 쪽 보기에서 스프레드 시작 페이지로 보정
function _adjustToSpreadStart(pageNum) {
    if (!_isDualActive() || pageNum <= 1) return pageNum;
    return pageNum % 2 === 0 ? pageNum : pageNum - 1;
}

// 두 쪽 보기 설정 변경 (Kotlin에서 호출)
window._setDualPage = function(enabled) {
    _pdf.dualPage = !!enabled;
    _renderPage(_pdf.currentPage);
};

// 화면 회전 시 다시 렌더링
window.addEventListener('resize', function() {
    if (_pdf.pdfDoc) _renderPage(_pdf.currentPage);
});

function _initPdf() {
    pdfjsLib.GlobalWorkerOptions.workerSrc = 'file:///android_asset/pdfjs/pdf.worker.min.js';

    pdfjsLib.getDocument(_pdfConfig.pdfPath).promise.then(function(pdf) {
        _pdf.pdfDoc = pdf;
        _pdf.totalPages = pdf.numPages;
        _pdf.currentPage = Math.max(1, Math.min(_pdfConfig.startPage, _pdf.totalPages));

        _renderPage(_pdf.currentPage);
        _loadOutline();
    }).catch(function(error) {
        console.error('PDF load error:', error);
    });
}

function _renderPage(pageNum) {
    if (!_pdf.pdfDoc) return;
    pageNum = Math.max(1, Math.min(pageNum, _pdf.totalPages));
    pageNum = _adjustToSpreadStart(pageNum);

    if (_pdf.rendering) {
        _pdf.pendingPage = pageNum;
        return;
    }
    _pdf.rendering = true;
    _pdf.currentPage = pageNum;

    var spreadPages = _getSpreadPages(pageNum);
    var promises = spreadPages.map(function(pn) { return _pdf.pdfDoc.getPage(pn); });

    Promise.all(promises).then(function(pdfPages) {
        var container = document.getElementById('pdf-container');
        var containerW = container.clientWidth;
        var containerH = container.clientHeight;
        // 두 쪽일 때 각 페이지에 할당할 최대 너비
        var availW = pdfPages.length > 1 ? containerW / 2 : containerW;

        var wrapper = document.getElementById('pdf-wrapper');
        if (!wrapper) {
            wrapper = document.createElement('div');
            wrapper.id = 'pdf-wrapper';
            container.appendChild(wrapper);
        }
        // 기존 캔버스 제거
        wrapper.innerHTML = '';

        var renderPromises = [];

        pdfPages.forEach(function(page, idx) {
            var unscaledViewport = page.getViewport({ scale: 1 });
            var scale = Math.min(availW / unscaledViewport.width, containerH / unscaledViewport.height);
            var viewport = page.getViewport({ scale: scale * (window.devicePixelRatio || 1) });
            var displayViewport = page.getViewport({ scale: scale });

            var canvas = document.createElement('canvas');
            // 첫 번째 캔버스는 호환성을 위해 id 유지
            if (idx === 0) canvas.id = 'pdf-canvas';
            canvas.width = viewport.width;
            canvas.height = viewport.height;
            canvas.style.width = displayViewport.width + 'px';
            canvas.style.height = displayViewport.height + 'px';
            wrapper.appendChild(canvas);

            var ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            var renderPromise = page.render({ canvasContext: ctx, viewport: viewport }).promise.then(function() {
                // TODO: 두 쪽 보기 시 두 번째 페이지의 검색 하이라이트는 미지원
                if (idx === 0) {
                    return page.getTextContent().then(function(textContent) {
                        _pdf.currentTextItems = textContent.items;
                        _pdf.currentDisplayViewport = displayViewport;
                        if (_pdf.searchHighlightQuery) {
                            _applySearchHighlights();
                        }
                    });
                }
            });
            renderPromises.push(renderPromise);
        });

        Promise.all(renderPromises).then(function() {
            _pdf.rendering = false;

            // 스프레드의 마지막 페이지 기준으로 진행률 계산
            var lastDisplayedPage = spreadPages[spreadPages.length - 1];
            var progress = lastDisplayedPage / _pdf.totalPages;
            Android.onPageChanged(_pdf.currentPage, _pdf.totalPages);
            Android.onLocationUpdate(progress, _pdf.currentPage, '');
            Android.onContentLoaded();

            if (_pdf.navigating) {
                _pdf.navigating = false;
                Android.onNavigationComplete();
            }

            // 줌 배율/위치 복원
            if (typeof window._restoreZoom === 'function') window._restoreZoom();

            if (_pdf.pendingPage !== null) {
                var next = _pdf.pendingPage;
                _pdf.pendingPage = null;
                _renderPage(next);
            }
        });
    });
}

_initPdf();
