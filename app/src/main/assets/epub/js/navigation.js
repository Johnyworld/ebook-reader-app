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
    // 레이아웃 유효성 검증: scrollWidth가 delta보다 작으면 비정상 → 1회 재시도
    var m = _epub.rendition.manager;
    if (m && m.container && m.layout) {
        var sw = m.container.scrollWidth;
        var delta = m.layout.delta;
        if (sw < delta && !_epub.retried) {
            _epub.retried = true;
            var loc = _epub.rendition.currentLocation();
            var cfi = (loc && loc.start && loc.start.cfi) ? loc.start.cfi : null;
            if (cfi) {
                _epub.rendition.display(cfi).then(_finishNavigation).catch(_finishNavigation);
                return;
            }
        }
    }
    _epub.retried = false;
    _epub.navigating = false;
    // 네비게이션 중 지연된 resize가 있으면 현재 CFI를 기억한 후 resize를 실행한다.
    // resize()가 scrollLeft를 초기화하므로 현재 CFI로 복원한다.
    if (_epub._pendingResize) {
        _epub._pendingResize = false;
        // 백그라운드 복귀 중이면 pendingResize를 무시한다.
        // dimensions가 바운스(681→658→681)하므로 중간 크기로 resize하면 안 된다.
        if (!_epub._resumeMode) {
            var s = window._readerSettings;
            if (s) {
                var newW = window.innerWidth - s.paddingHorizontal * 2;
                var newH = window.innerHeight - s.paddingVertical * 2 - _config.bottomInfoHeight;
                if (newW !== _epub._lastResizeW || newH !== _epub._lastResizeH) {
                    var cfiBeforeResize = window._currentCfi || _config.savedCfi || '';
                    _epub._lastResizeW = newW;
                    _epub._lastResizeH = newH;
                    _epub.rendition.spread(_getSpreadMode(), 0);
                    _epub.rendition.resize(newW, newH);
                    var viewer = document.getElementById('viewer');
                    if (viewer) {
                        viewer.style.top = s.paddingVertical + 'px';
                        viewer.style.left = s.paddingHorizontal + 'px';
                        viewer.style.right = s.paddingHorizontal + 'px';
                        viewer.style.bottom = (s.paddingVertical + _config.bottomInfoHeight) + 'px';
                    }
                    if (cfiBeforeResize) {
                        _epub.rendition.display(cfiBeforeResize);
                    }
                }
            }
        }
    }
    try {
        var loc = _epub.rendition.currentLocation();
        if (loc && loc.start && loc.start.cfi) {
            window._currentCfi = loc.start.cfi;
            var endCfi = (loc.end && loc.end.cfi) ? loc.end.cfi : "";
            window._currentEndCfi = endCfi;
            Android.onLocationChanged(0, loc.start.cfi, "");
        }
    } catch(e) {}
    // 브라우저가 실제로 화면에 paint를 마친 뒤 콜백을 보낸다.
    // requestAnimationFrame 2회 중첩: 1회째는 다음 프레임 준비, 2회째는 해당 프레임 paint 완료 후 실행.
    requestAnimationFrame(function() {
        requestAnimationFrame(function() {
            Android.onNavigationComplete();
            // resume 모드이면 paint 완료 후 overlay를 제거한다.
            if (_epub._resumeMode) {
                _epub._resumeMode = false;
                try { Android.onResumeRestoreComplete(); } catch(e) {}
            }
        });
    });
    if (_epub.searchHighlightQuery) { setTimeout(_applySearchHighlights, 50); }
}

// Initial display
if (_config.savedCfi.length > 0) {
    _epub.navigating = true;
    _epub.rendition.display(_config.savedCfi).then(_finishNavigation).catch(_finishNavigation);
} else {
    _epub.rendition.display().then(_finishNavigation).catch(_finishNavigation);
}

_epub.pendingPrevChapter = false;
_epub.prevTransition = false;
_epub.nextTransition = false;
_epub.retried = false;

window._isAtChapterStart = function() {
    var manager = _epub.rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var delta = manager.layout.delta;
        if (scrollLeft < delta * 0.5) {
            var loc = _epub.rendition.currentLocation();
            var currentIdx = (loc && loc.start) ? loc.start.index : -1;
            return currentIdx > 0;
        }
    }
    return false;
};

window._prev = function() {
    var manager = _epub.rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var delta = manager.layout.delta;

        // 서브픽셀 오차로 scrollLeft가 0보다 약간 클 수 있다.
        // epub.js는 scrollLeft > 0 이면 같은 챕터 내 이전 페이지로 판단하므로,
        // 0으로 보정하여 이전 챕터로 정상 전환되게 한다.
        if (scrollLeft < delta * 0.5) {
            var loc = _epub.rendition.currentLocation();
            var currentIdx = (loc && loc.start) ? loc.start.index : -1;
            if (currentIdx > 0) {
                _epub.pendingPrevChapter = true;
                _epub.prevTransition = true;
            }
            manager.container.scrollLeft = 0;
        }
    }
    return _epub.rendition.prev();
};

window._isAtChapterEnd = function() {
    var manager = _epub.rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var offsetWidth = manager.container.offsetWidth;
        var scrollWidth = manager.container.scrollWidth;
        var delta = manager.layout.delta;
        // 마지막 페이지: scrollLeft + offsetWidth + delta > scrollWidth
        if (scrollLeft + offsetWidth + delta > scrollWidth + delta * 0.5) {
            var loc = _epub.rendition.currentLocation();
            var currentIdx = (loc && loc.start) ? loc.start.index : -1;
            var totalItems = _epub.book.spine ? (_epub.book.spine.items || []).length : 0;
            return currentIdx < totalItems - 1;
        }
    }
    return false;
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
        // epub.js가 새 챕터를 로드할 때 이전 scrollLeft를 유지하는 문제 보정
        // fragment(#)가 없는 href는 챕터 시작이므로 scrollLeft를 0으로 리셋
        if (href.indexOf('#') < 0) {
            var m = _epub.rendition.manager;
            if (m && m.container) {
                m.container.scrollLeft = 0;
            }
        }
        _finishNavigation();
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
    _epub.rendition.display(navCfi).then(_finishNavigation).catch(_finishNavigation);
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
