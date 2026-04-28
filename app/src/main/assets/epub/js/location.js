// location.js — TOC, location reporting, relocated handler

function findTocEntry(href, items) {
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        var itemHref = item.href.split('#')[0];
        if (href === itemHref || href.endsWith('/' + itemHref) || itemHref.endsWith('/' + href)) {
            return item.label.trim();
        }
        if (item.subitems && item.subitems.length > 0) {
            var found = findTocEntry(href, item.subitems);
            if (found) return found;
        }
    }
    return "";
}

_epub.lastReportedSpineIndex = -1;

function reportLocation(location) {
    try {
        var percentage = (location.start && location.start.percentage) ? location.start.percentage : 0;
        if (percentage === 0 && location.start && _epub.book.spine && _epub.book.spine.items.length > 0) {
            percentage = location.start.index / _epub.book.spine.items.length;
        }
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        var href = (location.start && location.start.href) ? location.start.href : "";
        var chapter = "";
        if (_epub.book.navigation && _epub.book.navigation.toc) {
            chapter = findTocEntry(href, _epub.book.navigation.toc);
        }
        Android.onLocationChanged(percentage, cfi, chapter);
        if (_epub.totalVisualPages > 0 && location.start) {
            var idx = location.start.index !== undefined ? location.start.index : 0;
            var pg = location.start.displayed ? location.start.displayed.page : 1;
            var total = location.start.displayed ? location.start.displayed.total : 0;
            try {
                var delta = _epub.rendition.manager && _epub.rendition.manager.layout ? _epub.rendition.manager.layout.delta : 0;
                if (delta > 0 && _epub.rendition.manager.container) {
                    var scrollLeft = _epub.rendition.manager.container.scrollLeft;
                    var scrollWidth = _epub.rendition.manager.container.scrollWidth;

                    // 이전 챕터로 전환 시 스크롤 보정
                    // epub.js의 prev()가 iframe 레이아웃 완료 전에 scrollTo를 호출하여
                    // 마지막 페이지가 아닌 중간 위치에 머무는 문제를 보정한다.
                    // spine index가 감소했고, 마지막 페이지가 아니면 보정한다.
                    if (_epub.lastReportedSpineIndex >= 0 && idx < _epub.lastReportedSpineIndex && _epub.pendingPrevChapter) {
                        var target = scrollWidth - delta;
                        if (target > delta * 0.5 && scrollLeft < target - delta * 0.1) {
                            _epub.rendition.manager.container.scrollLeft = target;
                            scrollLeft = target;
                        }
                    }
                    _epub.lastReportedSpineIndex = idx;

                    pg = Math.round(scrollLeft / delta) + 1;
                }
            } catch(e) {}
            Android.onPageInfoChanged((_epub.spinePageOffsets[idx] || 0) + pg, _epub.totalVisualPages);
            var _scrollX = 0, _scrollW = 0, _deltaX = 0;
            try {
                if (_epub.rendition.manager && _epub.rendition.manager.container) {
                    _scrollX = _epub.rendition.manager.container.scrollLeft;
                    _scrollW = _epub.rendition.manager.container.scrollWidth;
                }
                if (_epub.rendition.manager && _epub.rendition.manager.layout) {
                    _deltaX = _epub.rendition.manager.layout.delta;
                }
            } catch(e) {}
            Android.onDebugInfo(idx, pg, total, _scrollX, _scrollW, _deltaX);
        }
    } catch(e) {}
}

_epub.totalLocations = 0;
_epub.spinePageCounts = {};
_epub.spinePageOffsets = {};
_epub.totalVisualPages = 0;
_epub.spineCharPageBreaks = {};
_epub.locationsReady = false;
_epub.rendered = false;
_epub.navigating = false;
_epub.pendingLocation = null;
_epub.pendingCfiList = [];
_epub.cfiPageMap = {};

function _getSpineIndexFromCfi(cfi) {
    var m = cfi.match(/\/6\/(\d+)/);
    return m ? (parseInt(m[1]) / 2) - 1 : -1;
}

window._setCfiList = function(jsonStr) {
    try {
        var cfis = JSON.parse(jsonStr);
        _epub.pendingCfiList = [];
        _epub.cfiPageMap = {};
        for (var ci = 0; ci < cfis.length; ci++) {
            var si = _getSpineIndexFromCfi(cfis[ci]);
            if (si >= 0) _epub.pendingCfiList.push({cfi: cfis[ci], spineIndex: si});
        }
    } catch(e) {}
};

window._currentCfi = "";
window._currentEndCfi = "";

window._isBookmarkedInRange = function(cfiListJson) {
    try {
        var cfis = JSON.parse(cfiListJson);
        if (!cfis.length || !window._currentCfi) return false;
        var startCfi = window._currentCfi;
        var endCfi = window._currentEndCfi;
        var epubcfi = new ePub.CFI();
        for (var i = 0; i < cfis.length; i++) {
            var c = cfis[i];
            if (c === startCfi) return true;
            if (endCfi) {
                var afterStart = epubcfi.compare(c, startCfi) >= 0;
                var beforeEnd = epubcfi.compare(c, endCfi) <= 0;
                if (afterStart && beforeEnd) return true;
            }
        }
    } catch(e) {}
    return false;
};

_epub.waitingForFonts = false;

_epub.rendition.on("relocated", function(location) {
    // 이전 챕터 전환 시 브라우저가 첫 페이지를 paint하기 전에 마지막 페이지로 스크롤한다.
    if (_epub.pendingPrevChapter) {
        _epub.pendingPrevChapter = false;
        try {
            var m = _epub.rendition.manager;
            if (m && m.container && m.layout) {
                var target = m.container.scrollWidth - m.layout.delta;
                if (target > 0) m.container.scrollLeft = target;
            }
        } catch(e) {}
        // 스크롤 보정 후 location을 갱신하여 올바른 CFI가 저장되도록 한다.
        // (relocated 이벤트의 location 파라미터는 보정 전 위치 기준이므로 stale)
        try { location = _epub.rendition.currentLocation(); } catch(e) {}
    }
    if (!_epub.rendered || _epub.waitingForFonts) {
        _epub.rendered = true;
        if (!_epub.waitingForFonts) {
            try {
                var iframe = document.querySelector('iframe');
                var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
                if (iDoc && iDoc.fonts && iDoc.fonts.status === 'loading' && _config.savedCfi) {
                    _epub.waitingForFonts = true;
                    _epub.navigating = true;
                    iDoc.fonts.ready.then(function() {
                        _epub.rendition.display(_config.savedCfi).then(_finishNavigation).catch(_finishNavigation);
                    });
                    return;
                }
            } catch(e) {}
        }
        _epub.waitingForFonts = false;
        Android.onContentRendered();
    }
    try {
        var href = (location.start && location.start.href) ? location.start.href : "";
        if (href && _epub.book.navigation && _epub.book.navigation.toc) {
            var chapter = findTocEntry(href, _epub.book.navigation.toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
    try {
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        if (cfi) {
            window._currentCfi = cfi;
            var endCfi = (location.end && location.end.cfi) ? location.end.cfi : "";
            window._currentEndCfi = endCfi;
            if (!_epub.navigating) {
                Android.onLocationChanged(0, cfi, "");
            }
        }
    } catch(e) {}
    if (_epub.locationsReady || _epub.totalVisualPages > 0) {
        reportLocation(location);
    } else {
        _epub.pendingLocation = location;
    }
    if (_epub.searchHighlightQuery && !_epub.navigating) { setTimeout(_applySearchHighlights, 50); }
    if (_epub.pendingAutoSelect) {
        _epub.pendingAutoSelect = false;
        requestAnimationFrame(function() {
            var result = window._autoSelectFirstWord();
            if (result) {
                var pos = JSON.parse(result);
                Android.onAutoSelectReady(pos.x, pos.y);
            }
        });
    }
    if (_epub.prevTransition) {
        _epub.prevTransition = false;
        try { Android.onPrevTransitionDone(); } catch(e) {}
    }
    if (_epub.nextTransition) {
        _epub.nextTransition = false;
        try { Android.onNextTransitionDone(); } catch(e) {}
    }
});

_epub.book.loaded.navigation.then(function(nav) {
    try {
        var toc = nav.toc || [];
        function buildToc(items, depth) {
            var result = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                result.push({
                    label: item.label.trim(),
                    href: item.href,
                    depth: depth,
                    subitems: item.subitems ? buildToc(item.subitems, depth + 1) : []
                });
            }
            return result;
        }
        Android.onTocLoaded(JSON.stringify(buildToc(toc, 0)));
        var loc = _epub.rendition.currentLocation();
        if (loc && loc.start && loc.start.href) {
            var chapter = findTocEntry(loc.start.href, toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
});
