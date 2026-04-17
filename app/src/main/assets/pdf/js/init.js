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

        var wrapper = document.getElementById('pdf-wrapper');
        if (!wrapper) {
            wrapper = document.createElement('div');
            wrapper.id = 'pdf-wrapper';
            container.appendChild(wrapper);
        }

        var canvas = document.getElementById('pdf-canvas');
        if (!canvas) {
            canvas = document.createElement('canvas');
            canvas.id = 'pdf-canvas';
            wrapper.appendChild(canvas);
        }

        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = displayViewport.width + 'px';
        canvas.style.height = displayViewport.height + 'px';

        var ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        page.render({ canvasContext: ctx, viewport: viewport }).promise.then(function() {
            // 텍스트 아이템 저장 (검색 하이라이트용)
            page.getTextContent().then(function(textContent) {
                _pdf.currentTextItems = textContent.items;
                _pdf.currentDisplayViewport = displayViewport;
                if (_pdf.searchHighlightQuery) {
                    _applySearchHighlights();
                }
            });

            _pdf.rendering = false;

            var progress = _pdf.currentPage / _pdf.totalPages;
            Android.onPageChanged(_pdf.currentPage, _pdf.totalPages);
            Android.onLocationUpdate(progress, _pdf.currentPage, '');
            Android.onContentLoaded();

            if (_pdf.navigating) {
                _pdf.navigating = false;
                Android.onNavigationComplete();
            }

            if (typeof window._resetZoom === 'function') window._resetZoom();

            if (_pdf.pendingPage !== null) {
                var next = _pdf.pendingPage;
                _pdf.pendingPage = null;
                _renderPage(next);
            }
        });
    });
}

_initPdf();
