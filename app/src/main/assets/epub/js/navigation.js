// navigation.js — page navigation, display, prev/next

window._isSelectionAtPageEnd = function() {
    try {
        var manager = _epub.rendition.manager;
        if (!manager || !manager.container) return false;
        var scrollLeft = manager.container.scrollLeft;
        var offsetWidth = manager.container.offsetWidth;
        var scrollWidth = manager.container.scrollWidth;
        var delta = manager.layout ? manager.layout.delta : offsetWidth;
        // spine item 마지막 페이지이면 이어하기 불가
        if (scrollLeft + offsetWidth + delta > scrollWidth + delta * 0.5) return false;

        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return false;
        var doc = iframe.contentDocument;
        var sel = doc.getSelection();
        if (!sel || sel.rangeCount === 0 || sel.toString().trim().length === 0) return false;
        var range = sel.getRangeAt(0);
        var rightEdge = scrollLeft + delta;

        // 선택 영역의 마지막 rect의 오른쪽 끝이 페이지 오른쪽 끝에 닿아 있는지
        var selRects = range.getClientRects();
        if (selRects.length === 0) return false;
        var lastRect = selRects[selRects.length - 1];
        var tolerance = 20;
        return lastRect.right >= rightEdge - tolerance;
    } catch(e) { return false; }
};

window._autoSelectFirstWord = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return '';
        var iDoc = iframe.contentDocument;
        var body = iDoc.body;
        if (!body) return '';
        var container = _epub.rendition.manager.container;
        var scrollLeft = container.scrollLeft;
        var viewRight = scrollLeft + container.offsetWidth;
        var walker = iDoc.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
        var node;
        while ((node = walker.nextNode())) {
            var text = node.textContent;
            if (!text || text.trim().length === 0) continue;
            var testRange = iDoc.createRange();
            for (var i = 0; i < text.length; i++) {
                if (/\s/.test(text[i])) continue;
                testRange.setStart(node, i);
                testRange.setEnd(node, i + 1);
                var cr = testRange.getBoundingClientRect();
                if (cr.left >= scrollLeft && cr.right <= viewRight && cr.height > 0) {
                    var iframeRect = iframe.getBoundingClientRect();
                    return JSON.stringify({
                        x: iframeRect.left + (cr.left + cr.right) / 2,
                        y: iframeRect.top + (cr.top + cr.bottom) / 2
                    });
                }
            }
        }
        return '';
    } catch(e) { return ''; }
};

_epub.navigating = false;
_epub.pendingAutoSelect = false;

function _finishNavigation() {
    _epub.navigating = false;
    try {
        var loc = _epub.rendition.currentLocation();
        if (loc && loc.start && loc.start.cfi) {
            window._currentCfi = loc.start.cfi;
            var endCfi = (loc.end && loc.end.cfi) ? loc.end.cfi : "";
            window._currentEndCfi = endCfi;
            Android.onLocationChanged(0, loc.start.cfi, "");
        }
    } catch(e) {}
    Android.onNavigationComplete();
    if (_epub.searchHighlightQuery) { setTimeout(_applySearchHighlights, 50); }
}

// Initial display
if (_config.savedCfi.length > 0) {
    _epub.navigating = true;
    _epub.rendition.display(_config.savedCfi).then(function() {
        setTimeout(function() {
            _epub.rendition.display(_config.savedCfi).then(_finishNavigation).catch(_finishNavigation);
        }, 0);
    }).catch(_finishNavigation);
} else {
    _epub.rendition.display();
}

window._prev = function() {
    var manager = _epub.rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var delta = manager.layout.delta;

        // 서브픽셀 오차로 scrollLeft가 0보다 약간 클 수 있다.
        // epub.js는 scrollLeft > 0 이면 같은 챕터 내 이전 페이지로 판단하므로,
        // 0으로 보정하여 이전 챕터로 정상 전환되게 한다.
        if (scrollLeft > 0 && scrollLeft < delta * 0.5) {
            manager.container.scrollLeft = 0;
        }
    }
    return _epub.rendition.prev();
};

window._next = function() {
    // epub.js는 scrollLeft + offsetWidth + delta <= scrollWidth 로 같은 챕터 내 다음 페이지 존재 여부를 판단한다.
    // 브라우저가 scrollLeft를 물리 픽셀에 스냅하면서 서브픽셀 오차가 누적되어,
    // 실제로 마지막 페이지가 남아있는데도 이 조건이 false가 되어 챕터를 건너뛰는 문제가 있다.
    // tolerance(delta/2)를 두어 오차 범위 내일 때는 직접 스크롤하여 마지막 페이지를 보여준다.
    var manager = _epub.rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var offsetWidth = manager.container.offsetWidth;
        var scrollWidth = manager.container.scrollWidth;
        var delta = manager.layout.delta;
        var tolerance = delta * 0.5;
        var scrollEnd = scrollLeft + offsetWidth + delta;
        if (scrollEnd > scrollWidth && scrollEnd <= scrollWidth + tolerance) {
            manager.scrollBy(delta, 0, true);
            _epub.rendition.reportLocation();
            return Promise.resolve();
        }
    }
    return _epub.rendition.next();
};

window._nextThenAutoSelect = function() {
    _epub.pendingAutoSelect = true;
    window._next();
};

window._displayHref = function(href) {
    _epub.navigating = true;
    _removeSearchHighlights();
    _epub.rendition.display(href).then(function() {
        setTimeout(function() {
            _epub.rendition.display(href).then(_finishNavigation).catch(_finishNavigation);
        }, 0);
    }).catch(_finishNavigation);
};

window._displayCfi = function(cfi) {
    // range CFI를 point CFI로 변환 (rendition.display()는 range CFI 미지원)
    var navCfi = cfi;
    var m = cfi.match(/^(epubcfi\(.+),(\/.+),(\/.+)\)$/);
    if (m) {
        navCfi = m[1] + m[2] + ')';
    }
    _epub.navigating = true;
    _removeSearchHighlights();
    _epub.rendition.display(navCfi).then(function() {
        setTimeout(function() {
            _epub.rendition.display(navCfi).then(_finishNavigation).catch(_finishNavigation);
        }, 0);
    }).catch(_finishNavigation);
};

window._displayPageNum = function(pageNum) {
    try {
        var items = _epub.book.spine ? (_epub.book.spine.items || []) : [];
        var targetIdx = 0;
        var pageWithin = 0;
        for (var i = 0; i < items.length; i++) {
            var offset = _epub.spinePageOffsets[i] || 0;
            var count = _epub.spinePageCounts[i] || 1;
            if (pageNum - 1 < offset + count) {
                targetIdx = i;
                pageWithin = Math.max(0, pageNum - 1 - offset);
                break;
            }
        }
        var capturedTargetIdx = targetIdx;
        _epub.rendition.display(items[targetIdx].href).then(function() {
            var remaining = pageWithin;
            function step() {
                if (remaining <= 0) { return; }
                remaining--;
                _epub.rendition.next().then(function() {
                    var loc = _epub.rendition.currentLocation();
                    if (loc && loc.start && loc.start.index !== undefined && loc.start.index !== capturedTargetIdx) {
                        _epub.rendition.prev();
                    } else {
                        step();
                    }
                }).catch(function() {});
            }
            step();
        }).catch(function() {});
    } catch(e) {}
};
