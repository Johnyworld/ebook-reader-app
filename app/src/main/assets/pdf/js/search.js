// search.js — PDF 본문 검색 + 하이라이트

_pdf.searchHighlightQuery = '';
_pdf.currentTextItems = [];
_pdf.currentDisplayViewport = null;

function _applySearchHighlights() {
    // 기존 하이라이트 제거
    var wrapper = document.getElementById('pdf-wrapper');
    if (!wrapper) return;
    var existing = wrapper.querySelectorAll('.search-hl-overlay');
    for (var i = 0; i < existing.length; i++) existing[i].remove();

    if (!_pdf.searchHighlightQuery || !_pdf.currentTextItems || !_pdf.currentDisplayViewport) return;

    var query = _pdf.searchHighlightQuery.toLowerCase();
    var items = _pdf.currentTextItems;
    var vp = _pdf.currentDisplayViewport;

    // wrapper와 canvas의 오프셋 차이 보정
    var canvas = document.getElementById('pdf-canvas');
    var offsetX = 0;
    var offsetY = 0;
    if (canvas && wrapper) {
        var canvasRect = canvas.getBoundingClientRect();
        var wrapperRect = wrapper.getBoundingClientRect();
        offsetX = canvasRect.left - wrapperRect.left;
        offsetY = canvasRect.top - wrapperRect.top;
    }

    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        if (!item.str) continue;
        var lower = item.str.toLowerCase();
        if (lower.indexOf(query) === -1) continue;

        // transform: [scaleX, skewX, skewY, scaleY, translateX, translateY]
        var tx = item.transform;
        var fontSize = Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1]);

        // 텍스트 아이템의 좌하단, 우하단을 viewport 좌표로 변환
        var pLeft = vp.convertToViewportPoint(tx[4], tx[5]);
        var pRight = vp.convertToViewportPoint(tx[4] + item.width, tx[5]);
        var totalW = pRight[0] - pLeft[0];
        var hlH = fontSize * vp.scale;

        var idx = lower.indexOf(query);
        while (idx !== -1) {
            var ratioStart = idx / item.str.length;
            var ratioEnd = (idx + query.length) / item.str.length;
            var hlX = pLeft[0] + totalW * ratioStart;
            var hlW = totalW * (ratioEnd - ratioStart);

            var hl = document.createElement('div');
            hl.className = 'search-hl-overlay';
            hl.style.left = (hlX + offsetX) + 'px';
            hl.style.top = (pLeft[1] - hlH + offsetY) + 'px';
            hl.style.width = hlW + 'px';
            hl.style.height = hlH + 'px';
            wrapper.appendChild(hl);

            idx = lower.indexOf(query, idx + 1);
        }
    }
}

window._setSearchHighlight = function(query) {
    _pdf.searchHighlightQuery = query;
    _applySearchHighlights();
};

window._clearSearchHighlight = function() {
    _pdf.searchHighlightQuery = '';
    var wrapper = document.getElementById('pdf-wrapper');
    if (wrapper) {
        var overlays = wrapper.querySelectorAll('.search-hl-overlay');
        for (var i = 0; i < overlays.length; i++) overlays[i].remove();
    }
};

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
