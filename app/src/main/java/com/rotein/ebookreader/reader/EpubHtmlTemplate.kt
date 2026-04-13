package com.rotein.ebookreader.reader

import com.rotein.ebookreader.ReaderSettings
import com.rotein.ebookreader.fontFamilyForJs

internal fun buildEpubJsHtml(opfPath: String, savedCfi: String, settings: ReaderSettings, fontFilePath: String = "") = """<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'/>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 100%; height: 100%; overflow: hidden; background: #fff; }
#viewer { position: absolute; top: ${settings.paddingVertical}px; left: ${settings.paddingHorizontal}px; right: ${settings.paddingHorizontal}px; bottom: ${settings.paddingVertical + 16}px; }
.epub-view svg { mix-blend-mode: multiply; }
</style>
</head>
<body>
<div id="bookmark-ribbon" style="display:none;position:absolute;top:-12px;right:16px;width:16px;height:40px;z-index:0;pointer-events:none;">
<svg width="16" height="40" viewBox="0 0 16 40" xmlns="http://www.w3.org/2000/svg">
<path d="M0,0 L16,0 L16,40 L8,30 L0,40 Z" fill="#ccc"/>
</svg>
</div>
<div id="viewer"></div>
<script>
(function() {
    var _RO = window.ResizeObserver;
    if (!_RO) return;
    window.ResizeObserver = function(callback) {
        return new _RO(function(entries, observer) {
            requestAnimationFrame(function() { callback(entries, observer); });
        });
    };
    window.ResizeObserver.prototype = _RO.prototype;
})();
</script>
<script src="epub.min.js"></script>
<script>
window._showBookmarkRibbon = function(show) {
    var el = document.getElementById('bookmark-ribbon');
    if (el) el.style.display = show ? 'block' : 'none';
};
var _dualPage = ${settings.dualPage};
function _getSpreadMode() {
    return (_dualPage && window.innerWidth > window.innerHeight) ? "always" : "none";
}
var book = ePub("$opfPath");
var _viewerEl = document.getElementById('viewer');
var _viewerRect = _viewerEl ? _viewerEl.getBoundingClientRect() : null;
var _viewerW = (_viewerRect && _viewerRect.width > 0) ? _viewerRect.width : (window.innerWidth - ${settings.paddingHorizontal * 2});
var _viewerH = (_viewerRect && _viewerRect.height > 0) ? _viewerRect.height : (window.innerHeight - ${settings.paddingVertical * 2 + 16});
var rendition = book.renderTo("viewer", {
    width: _viewerW,
    height: _viewerH,
    spread: _getSpreadMode(),
    flow: "paginated",
    manager: "default",
    minSpreadWidth: 0
});
window.addEventListener('resize', function() {
    if (!window._readerSettings) return;
    var s = window._readerSettings;
    rendition.spread(_getSpreadMode(), 0);
    rendition.resize(window.innerWidth - s.paddingHorizontal * 2, window.innerHeight - s.paddingVertical * 2 - 16);
    var viewer = document.getElementById('viewer');
    if (viewer) {
        viewer.style.top = s.paddingVertical + 'px';
        viewer.style.left = s.paddingHorizontal + 'px';
        viewer.style.right = s.paddingHorizontal + 'px';
        viewer.style.bottom = (s.paddingVertical + 16) + 'px';
    }
});

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

function reportLocation(location) {
    try {
        var percentage = (location.start && location.start.percentage) ? location.start.percentage : 0;
        if (percentage === 0 && location.start && book.spine && book.spine.items.length > 0) {
            percentage = location.start.index / book.spine.items.length;
        }
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        var href = (location.start && location.start.href) ? location.start.href : "";
        var chapter = "";
        if (book.navigation && book.navigation.toc) {
            chapter = findTocEntry(href, book.navigation.toc);
        }
        Android.onLocationChanged(percentage, cfi, chapter);
        if (_totalVisualPages > 0 && location.start) {
            var idx = location.start.index !== undefined ? location.start.index : 0;
            var pg = location.start.displayed ? location.start.displayed.page : 1;
            var total = location.start.displayed ? location.start.displayed.total : 0;
            try {
                var delta = rendition.manager && rendition.manager.layout ? rendition.manager.layout.delta : 0;
                if (delta > 0 && rendition.manager.container) {
                    var scrollLeft = rendition.manager.container.scrollLeft;
                    pg = Math.round(scrollLeft / delta) + 1;
                }
            } catch(e) {}
            Android.onPageInfoChanged((_spinePageOffsets[idx] || 0) + pg, _totalVisualPages);
            var _scrollX = 0, _scrollW = 0, _deltaX = 0;
            try {
                if (rendition.manager && rendition.manager.container) {
                    _scrollX = rendition.manager.container.scrollLeft;
                    _scrollW = rendition.manager.container.scrollWidth;
                }
                if (rendition.manager && rendition.manager.layout) {
                    _deltaX = rendition.manager.layout.delta;
                }
            } catch(e) {}
            Android.onDebugInfo(idx, pg, total, _scrollX, _scrollW, _deltaX);
        }
    } catch(e) {}
}

var _totalLocations = 0;
var _spinePageCounts = {};
var _spinePageOffsets = {};
var _totalVisualPages = 0;
var _spineCharPageBreaks = {};
var _locationsReady = false;
var _rendered = false;
var _pendingLocation = null;
var _pendingCfiList = [];
var _cfiPageMap = {};
function _getSpineIndexFromCfi(cfi) {
    var m = cfi.match(/\/6\/(\d+)/);
    return m ? (parseInt(m[1]) / 2) - 1 : -1;
}
window._setCfiList = function(jsonStr) {
    try {
        var cfis = JSON.parse(jsonStr);
        _pendingCfiList = [];
        _cfiPageMap = {};
        for (var ci = 0; ci < cfis.length; ci++) {
            var si = _getSpineIndexFromCfi(cfis[ci]);
            if (si >= 0) _pendingCfiList.push({cfi: cfis[ci], spineIndex: si});
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
var _waitingForFonts = false;
rendition.on("relocated", function(location) {
    if (!_rendered || _waitingForFonts) {
        _rendered = true;
        if (!_waitingForFonts) {
            try {
                var iframe = document.querySelector('iframe');
                var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
                if (iDoc && iDoc.fonts && iDoc.fonts.status === 'loading' && _savedCfi) {
                    _waitingForFonts = true;
                    _navigating = true;
                    iDoc.fonts.ready.then(function() {
                        rendition.display(_savedCfi).then(function() {
                            setTimeout(function() {
                                rendition.display(_savedCfi).then(_finishNavigation).catch(_finishNavigation);
                            }, 0);
                        }).catch(_finishNavigation);
                    });
                    return;
                }
            } catch(e) {}
        }
        _waitingForFonts = false;
        Android.onContentRendered();
    }
    try {
        var href = (location.start && location.start.href) ? location.start.href : "";
        if (href && book.navigation && book.navigation.toc) {
            var chapter = findTocEntry(href, book.navigation.toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
    try {
        var cfi = (location.start && location.start.cfi) ? location.start.cfi : "";
        if (cfi) {
            window._currentCfi = cfi;
            var endCfi = (location.end && location.end.cfi) ? location.end.cfi : "";
            window._currentEndCfi = endCfi;
            if (!_navigating) {
                Android.onLocationChanged(0, cfi, "");
            }
        }
    } catch(e) {}
    if (_locationsReady || _totalVisualPages > 0) {
        reportLocation(location);
    } else {
        _pendingLocation = location;
    }
    if (_searchHighlightQuery) { setTimeout(_applySearchHighlights, 50); }
    if (_pendingAutoSelect) {
        _pendingAutoSelect = false;
        requestAnimationFrame(function() {
            var result = window._autoSelectFirstWord();
            if (result) {
                var pos = JSON.parse(result);
                Android.onAutoSelectReady(pos.x, pos.y);
            }
        });
    }
});

book.loaded.navigation.then(function(nav) {
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
        var loc = rendition.currentLocation();
        if (loc && loc.start && loc.start.href) {
            var chapter = findTocEntry(loc.start.href, toc);
            if (chapter) Android.onChapterChanged(chapter);
        }
    } catch(e) {}
});

function computeVisualPages() {
    try { Android.onScanStart(); } catch(e) {}
    var s = window._readerSettings;
    var scanW = window.innerWidth - s.paddingHorizontal * 2;
    var scanH = window.innerHeight - s.paddingVertical * 2 - 16;
    var scanDiv = document.createElement('div');
    scanDiv.style.cssText = 'position:fixed;left:-' + (scanW + 10) + 'px;top:0;width:' + scanW + 'px;height:' + scanH + 'px;overflow:hidden;';
    document.body.appendChild(scanDiv);
    var scanBook = ePub("$opfPath");
    var scanRendition = scanBook.renderTo(scanDiv, {
        width: scanW,
        height: scanH,
        spread: "none",
        flow: "paginated",
        manager: "default",
        minSpreadWidth: 9999
    });
    scanRendition.hooks.content.register(function(contents) {
        _injectReaderStyle(contents.document);
    });
    scanBook.ready.then(function() {
        var items = scanBook.spine ? (scanBook.spine.items || []) : [];
        if (items.length === 0) {
            setTimeout(function() {
                try { scanRendition.destroy(); } catch(e) {}
                try { scanBook.destroy(); } catch(e) {}
                document.body.removeChild(scanDiv);
            }, 100);
            return;
        }
        var i = 0;
        var _runningOffset = 0;
        function _resolveCfisInSpine(cfis, idx, done) {
            if (idx >= cfis.length) { done(); return; }
            scanRendition.display(cfis[idx]).then(function() {
                try {
                    var cfiLoc = scanRendition.currentLocation();
                    var pg = (cfiLoc && cfiLoc.start && cfiLoc.start.displayed) ? cfiLoc.start.displayed.page : 1;
                    try {
                        var sDelta = scanRendition.manager && scanRendition.manager.layout ? scanRendition.manager.layout.delta : 0;
                        if (sDelta > 0 && scanRendition.manager.container) {
                            var sScrollLeft = scanRendition.manager.container.scrollLeft;
                            pg = Math.round(sScrollLeft / sDelta) + 1;
                        }
                    } catch(e2) {}
                    _cfiPageMap[cfis[idx]] = _spinePageOffsets[i] + pg;
                } catch(e) {}
                _resolveCfisInSpine(cfis, idx + 1, done);
            }).catch(function() { _resolveCfisInSpine(cfis, idx + 1, done); });
        }
        function next() {
            if (i >= items.length) {
                _totalVisualPages = _runningOffset;
                try {
                    var tocNav = book.navigation ? (book.navigation.toc || []) : [];
                    var spineItemsForToc = book.spine ? (book.spine.items || []) : [];
                    function buildTocWithPages(tocItems, depth) {
                        var result = [];
                        for (var ti = 0; ti < tocItems.length; ti++) {
                            var tocItem = tocItems[ti];
                            var hrefBase = tocItem.href.split('#')[0];
                            var page = 0;
                            for (var si = 0; si < spineItemsForToc.length; si++) {
                                var siHref = (spineItemsForToc[si].href || '').split('?')[0];
                                if (siHref === hrefBase || siHref.endsWith('/' + hrefBase) || hrefBase.endsWith('/' + siHref)) {
                                    page = (_spinePageOffsets[si] || 0) + 1;
                                    break;
                                }
                            }
                            result.push({
                                label: tocItem.label.trim(),
                                href: tocItem.href,
                                page: page,
                                depth: depth,
                                subitems: tocItem.subitems ? buildTocWithPages(tocItem.subitems, depth + 1) : []
                            });
                        }
                        return result;
                    }
                    Android.onTocReady(JSON.stringify(buildTocWithPages(tocNav, 0)));
                } catch(e) {}
                _pendingLocation = null;
                var _scanCurrentPage = 0;
                var loc = rendition.currentLocation();
                if (loc && loc.start) {
                    var idx = loc.start.index !== undefined ? loc.start.index : 0;
                    var pg = loc.start.displayed ? loc.start.displayed.page : 1;
                    try {
                        var delta = rendition.manager && rendition.manager.layout ? rendition.manager.layout.delta : 0;
                        if (delta > 0 && rendition.manager.container) {
                            var scrollLeft = rendition.manager.container.scrollLeft;
                            pg = Math.round(scrollLeft / delta) + 1;
                        }
                    } catch(e) {}
                    _scanCurrentPage = (_spinePageOffsets[idx] || 0) + pg;
                }
                Android.onScanComplete(_totalVisualPages, _scanCurrentPage, JSON.stringify(_spinePageOffsets), JSON.stringify(_cfiPageMap), JSON.stringify(_spineCharPageBreaks));
                setTimeout(function() {
                    try { scanRendition.destroy(); } catch(e) {}
                    try { scanBook.destroy(); } catch(e) {}
                    document.body.removeChild(scanDiv);
                }, 100);
                return;
            }
            scanRendition.display(items[i].href).then(function() {
                var loc = scanRendition.currentLocation();
                _spinePageCounts[i] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
                _spinePageOffsets[i] = _runningOffset;
                try {
                    var pageBreaks = [0];
                    var sTotal = _spinePageCounts[i];
                    if (sTotal > 1) {
                        var sDelta = scanRendition.manager && scanRendition.manager.layout ? scanRendition.manager.layout.delta : 0;
                        if (sDelta > 0) {
                            var sIframe = scanDiv.querySelector('iframe');
                            var sDoc = sIframe && (sIframe.contentDocument || (sIframe.contentWindow && sIframe.contentWindow.document));
                            if (sDoc) {
                                var sBody = sDoc.body || sDoc.querySelector('body') || sDoc.documentElement;
                                if (sBody) {
                                    var sWalker = sDoc.createTreeWalker(sBody, NodeFilter.SHOW_TEXT, null, false);
                                    var sNd, charOffset = 0;
                                    var lastPage = 0;
                                    while ((sNd = sWalker.nextNode())) {
                                        var len = sNd.textContent.length;
                                        if (len === 0) continue;
                                        try {
                                            var range = sDoc.createRange();
                                            range.selectNodeContents(sNd);
                                            var rect = range.getBoundingClientRect();
                                            if (rect.width > 0) {
                                                var nodePage = Math.floor(rect.left / sDelta);
                                                if (nodePage > lastPage) {
                                                    for (var np = lastPage + 1; np <= nodePage; np++) {
                                                        pageBreaks.push(charOffset);
                                                    }
                                                    lastPage = nodePage;
                                                }
                                            }
                                        } catch(re) {}
                                        charOffset += len;
                                    }
                                }
                            }
                        }
                    }
                    _spineCharPageBreaks[i] = pageBreaks;
                } catch(pe) {
                    _spineCharPageBreaks[i] = [0];
                }
                var cfisForSpine = [];
                for (var ci = 0; ci < _pendingCfiList.length; ci++) {
                    if (_pendingCfiList[ci].spineIndex === i) cfisForSpine.push(_pendingCfiList[ci].cfi);
                }
                _resolveCfisInSpine(cfisForSpine, 0, function() {
                    _runningOffset += _spinePageCounts[i];
                    i++;
                    next();
                });
            });
        }
        next();
    });
}

book.ready.then(function() {
    return book.locations.generate(150);
}).then(function() {
    try {
        var locs = book.locations._locations || [];
        _totalLocations = locs.length || 1;
        var toc = book.navigation ? (book.navigation.toc || []) : [];
        function buildTocSimple(items, depth) {
            var result = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                result.push({
                    label: item.label.trim(),
                    href: item.href,
                    depth: depth,
                    subitems: item.subitems ? buildTocSimple(item.subitems, depth + 1) : []
                });
            }
            return result;
        }
        Android.onTocReady(JSON.stringify(buildTocSimple(toc, 0)));
    } catch(e) {}

    _locationsReady = true;
    clearTimeout(_rescanTimer);
    computeVisualPages();
});

rendition.hooks.content.register(function(contents) {
    var doc = contents.document;
    _injectReaderStyle(doc);
    if (doc.body) { void doc.body.offsetHeight; }
    var debounceTimer = null;
    var lastSelectionTime = 0;

    // 선택 드래그 중 CSS columns 스크롤 방지
    // iframe 내부 + 외부 컨테이너 모두 잠금
    var _selLocked = false;
    var _savedScrolls = [];
    function _getAllScrollTargets() {
        var targets = [];
        if (doc.body) targets.push(doc.body);
        if (doc.documentElement) targets.push(doc.documentElement);
        try {
            var outer = document; // 외부 문서
            var epubView = outer.querySelector('.epub-view');
            var epubContainer = outer.querySelector('.epub-container');
            if (epubView) targets.push(epubView);
            if (epubContainer) targets.push(epubContainer);
            if (outer.querySelector('#viewer')) targets.push(outer.querySelector('#viewer'));
        } catch(e) {}
        return targets;
    }
    function _captureAll() {
        _savedScrolls = _getAllScrollTargets().map(function(el) {
            return { el: el, left: el.scrollLeft };
        });
    }
    function _restoreAll() {
        _savedScrolls.forEach(function(s) {
            if (s.el.scrollLeft !== s.left) s.el.scrollLeft = s.left;
        });
    }
    var _rafId = 0;
    function _lockLoop() {
        if (!_selLocked) return;
        _restoreAll();
        _rafId = (doc.defaultView || window).requestAnimationFrame(_lockLoop);
    }
    _captureAll();
    doc.addEventListener('selectionchange', function() {
        var sel = doc.getSelection();
        var hasSelection = sel && sel.toString().trim().length > 0;
        if (hasSelection && !_selLocked) {
            _captureAll();
            _selLocked = true;
            _rafId = (doc.defaultView || window).requestAnimationFrame(_lockLoop);
        } else if (!hasSelection && _selLocked) {
            _selLocked = false;
            if (_rafId) (doc.defaultView || window).cancelAnimationFrame(_rafId);
        }
    });
    _getAllScrollTargets().forEach(function(el) {
        el.addEventListener('scroll', function() {
            if (_selLocked) _restoreAll();
            else _captureAll();
        });
    });

    window._getCfiFromSelection = function() {
        try {
            var sel = doc.getSelection();
            if (!sel || sel.rangeCount === 0) return '';
            return contents.cfiFromRange(sel.getRangeAt(0)) || '';
        } catch(e) { return ''; }
    };
    window._saveContStart = function() {
        try {
            var sel = doc.getSelection();
            if (!sel || sel.rangeCount === 0) return;
            var range = sel.getRangeAt(0);
            window._contStartContainer = range.startContainer;
            window._contStartOffset = range.startOffset;
        } catch(e) {}
    };
    window._clearContStart = function() {
        window._contStartContainer = null;
        window._contStartOffset = null;
    };
    window._getContMergedCfi = function() {
        try {
            if (!window._contStartContainer) return '';
            var sel = doc.getSelection();
            if (!sel || sel.rangeCount === 0) return '';
            var curRange = sel.getRangeAt(0);
            var merged = doc.createRange();
            merged.setStart(window._contStartContainer, window._contStartOffset);
            merged.setEnd(curRange.endContainer, curRange.endOffset);
            return contents.cfiFromRange(merged) || '';
        } catch(e) { return ''; }
    };
    doc.addEventListener('selectionchange', function() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function() {
            try {
                var sel = doc.getSelection();
                var text = sel ? sel.toString().trim() : '';
                if (text.length > 0) {
                    lastSelectionTime = Date.now();
                    Android.onTextSelected(text, 0, 0, 0);
                } else if (Date.now() - lastSelectionTime > 500) {
                    Android.onTextSelected('', 0, 0, 0);
                }
            } catch(e) {}
        }, 200);
    });
});

window._clearSelection = function() {
    try {
        var iframe = document.querySelector('iframe');
        if (iframe && iframe.contentDocument) {
            iframe.contentDocument.getSelection().removeAllRanges();
        }
    } catch(e) {}
};

var _searchHighlightQuery = '';
function _removeSearchHighlights() {
    try {
        var iframe = document.querySelector('iframe');
        var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
        if (!iDoc) return;
        var marks = iDoc.querySelectorAll('.search-hl');
        for (var i = marks.length - 1; i >= 0; i--) {
            var mark = marks[i];
            var parent = mark.parentNode;
            parent.replaceChild(iDoc.createTextNode(mark.textContent), mark);
            parent.normalize();
        }
    } catch(e) {}
}
function _applySearchHighlights() {
    if (!_searchHighlightQuery) return;
    _removeSearchHighlights();
    try {
        var iframe = document.querySelector('iframe');
        var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
        if (!iDoc || !iDoc.body) return;
        var lowerQuery = _searchHighlightQuery.toLowerCase();
        var walker = iDoc.createTreeWalker(iDoc.body, NodeFilter.SHOW_TEXT, null, false);
        var textNodes = [];
        var nd;
        while ((nd = walker.nextNode())) textNodes.push(nd);
        for (var i = 0; i < textNodes.length; i++) {
            var tn = textNodes[i];
            var text = tn.textContent;
            var lower = text.toLowerCase();
            var idx = lower.indexOf(lowerQuery);
            if (idx === -1) continue;
            var frag = iDoc.createDocumentFragment();
            var cursor = 0;
            while (idx !== -1) {
                if (idx > cursor) frag.appendChild(iDoc.createTextNode(text.substring(cursor, idx)));
                var span = iDoc.createElement('span');
                span.className = 'search-hl';
                span.textContent = text.substring(idx, idx + lowerQuery.length);
                frag.appendChild(span);
                cursor = idx + lowerQuery.length;
                idx = lower.indexOf(lowerQuery, cursor);
            }
            if (cursor < text.length) frag.appendChild(iDoc.createTextNode(text.substring(cursor)));
            tn.parentNode.replaceChild(frag, tn);
        }
    } catch(e) {}
}
window._setSearchHighlight = function(query) {
    _searchHighlightQuery = query;
    _applySearchHighlights();
};
window._clearSearchHighlight = function() {
    _searchHighlightQuery = '';
    _removeSearchHighlights();
};

var _highlightCfiMap = {};
window._addHighlight = function(cfi, id) {
    try {
        _highlightCfiMap[id] = cfi;
        rendition.annotations.add("highlight", cfi, {id: id}, null, "epub-hl-" + id, {
            "background": "#d0d0d0",
            "border": "none"
        });
    } catch(e) {}
};
window._removeHighlight = function(id) {
    try {
        var cfi = _highlightCfiMap[id];
        if (cfi) {
            rendition.annotations.remove(cfi, "highlight");
            delete _highlightCfiMap[id];
        }
    } catch(e) {}
};
window._applyHighlights = function(json) {
    try {
        var hl = JSON.parse(json);
        for (var i = 0; i < hl.length; i++) {
            window._addHighlight(hl[i].cfi, hl[i].id);
        }
    } catch(e) {}
};
var _memoCfiMap = {};
window._addMemo = function(cfi, id) {
    try {
        _memoCfiMap[id] = cfi;
        rendition.annotations.add("underline", cfi, {id: id}, null, "epub-memo-" + id, {
            "fill": "#000000",
            "stroke": "none"
        });
    } catch(e) {}
};
window._removeMemo = function(id) {
    try {
        var cfi = _memoCfiMap[id];
        if (cfi) { rendition.annotations.remove(cfi, "underline"); delete _memoCfiMap[id]; }
    } catch(e) {}
};
window._applyMemos = function(json) {
    try {
        var items = JSON.parse(json);
        for (var i = 0; i < items.length; i++) { window._addMemo(items[i].cfi, items[i].id); }
    } catch(e) {}
};
window._getAnnotationAtPoint = function(x, y) {
    try {
        var hlGroups = document.querySelectorAll('[class^="epub-hl-"]');
        for (var i = 0; i < hlGroups.length; i++) {
            var g = hlGroups[i];
            var rects = g.querySelectorAll('rect');
            for (var j = 0; j < rects.length; j++) {
                var br = rects[j].getBoundingClientRect();
                if (x >= br.left && x <= br.right && y >= br.top && y <= br.bottom) {
                    var match = (g.getAttribute('class') || '').match(/epub-hl-(\d+)/);
                    if (match) {
                        var gbr = g.getBoundingClientRect();
                        return JSON.stringify({type: 'highlight', id: parseInt(match[1]), cx: gbr.left + gbr.width/2, y: gbr.top, bottom: gbr.bottom});
                    }
                }
            }
        }
        var memoGroups = document.querySelectorAll('[class^="epub-memo-"]');
        for (var i = 0; i < memoGroups.length; i++) {
            var g = memoGroups[i];
            var gbr = g.getBoundingClientRect();
            if (x >= gbr.left && x <= gbr.right && y >= gbr.top - 20 && y <= gbr.bottom + 10) {
                var match = (g.getAttribute('class') || '').match(/epub-memo-(\d+)/);
                if (match) {
                    return JSON.stringify({type: 'memo', id: parseInt(match[1]), cx: gbr.left + gbr.width/2, y: gbr.top, bottom: gbr.bottom});
                }
            }
        }
    } catch(e) {}
    return 'null';
};

window._isSelectionAtPageEnd = function() {
    try {
        var manager = rendition.manager;
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
        var container = rendition.manager.container;
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

var _savedCfi = "${savedCfi.replace("\"", "\\\"")}";
var _navigating = false;
function _finishNavigation() {
    _navigating = false;
    try {
        var loc = rendition.currentLocation();
        if (loc && loc.start && loc.start.cfi) {
            window._currentCfi = loc.start.cfi;
            var endCfi = (loc.end && loc.end.cfi) ? loc.end.cfi : "";
            window._currentEndCfi = endCfi;
            Android.onLocationChanged(0, loc.start.cfi, "");
        }
    } catch(e) {}
    Android.onNavigationComplete();
}
if (_savedCfi.length > 0) {
    _navigating = true;
    rendition.display(_savedCfi).then(function() {
        setTimeout(function() {
            rendition.display(_savedCfi).then(_finishNavigation).catch(_finishNavigation);
        }, 0);
    }).catch(_finishNavigation);
} else {
    rendition.display();
}
window._prev = function() { rendition.prev(); };
window._next = function() {
    // epub.js는 scrollLeft + offsetWidth + delta <= scrollWidth 로 같은 챕터 내 다음 페이지 존재 여부를 판단한다.
    // 브라우저가 scrollLeft를 물리 픽셀에 스냅하면서 서브픽셀 오차가 누적되어,
    // 실제로 마지막 페이지가 남아있는데도 이 조건이 false가 되어 챕터를 건너뛰는 문제가 있다.
    // tolerance(delta/2)를 두어 오차 범위 내일 때는 직접 스크롤하여 마지막 페이지를 보여준다.
    var manager = rendition.manager;
    if (manager && manager.container && manager.layout) {
        var scrollLeft = manager.container.scrollLeft;
        var offsetWidth = manager.container.offsetWidth;
        var scrollWidth = manager.container.scrollWidth;
        var delta = manager.layout.delta;
        var tolerance = delta * 0.5;
        var scrollEnd = scrollLeft + offsetWidth + delta;
        if (scrollEnd > scrollWidth && scrollEnd <= scrollWidth + tolerance) {
            manager.scrollBy(delta, 0, true);
            rendition.reportLocation();
            return Promise.resolve();
        }
    }
    return rendition.next();
};
var _pendingAutoSelect = false;
window._nextThenAutoSelect = function() {
    _pendingAutoSelect = true;
    window._next();
};

window._displayHref = function(href) {
    _navigating = true;
    rendition.display(href).then(function() {
        setTimeout(function() {
            rendition.display(href).then(_finishNavigation).catch(_finishNavigation);
        }, 0);
    }).catch(_finishNavigation);
};
window._displayCfi = function(cfi) {
    _navigating = true;
    rendition.display(cfi).then(function() {
        setTimeout(function() {
            rendition.display(cfi).then(_finishNavigation).catch(_finishNavigation);
        }, 0);
    }).catch(_finishNavigation);
};
window._displayPageNum = function(pageNum) {
    try {
        var items = book.spine ? (book.spine.items || []) : [];
        var targetIdx = 0;
        var pageWithin = 0;
        for (var i = 0; i < items.length; i++) {
            var offset = _spinePageOffsets[i] || 0;
            var count = _spinePageCounts[i] || 1;
            if (pageNum - 1 < offset + count) {
                targetIdx = i;
                pageWithin = Math.max(0, pageNum - 1 - offset);
                break;
            }
        }
        var capturedTargetIdx = targetIdx;
        rendition.display(items[targetIdx].href).then(function() {
            var remaining = pageWithin;
            function step() {
                if (remaining <= 0) { return; }
                remaining--;
                rendition.next().then(function() {
                    var loc = rendition.currentLocation();
                    if (loc && loc.start && loc.start.index !== undefined && loc.start.index !== capturedTargetIdx) {
                        rendition.prev();
                    } else {
                        step();
                    }
                }).catch(function() {});
            }
            step();
        }).catch(function() {});
    } catch(e) {}
};
window._search = function(query) {
    var items = book.spine ? (book.spine.items || []) : [];
    var lowerQuery = query.toLowerCase();
    var nextIdx = 0;
    var activeChains = 0;
    var CONCURRENCY = 3;

    function next() {
        if (nextIdx >= items.length) {
            if (--activeChains === 0) Android.onSearchComplete();
            return;
        }
        var spineIndex = nextIdx;
        var section = items[nextIdx++];
        var href = section.href;
        var chapter = '';
        try {
            if (book.navigation && book.navigation.toc) {
                chapter = findTocEntry(href, book.navigation.toc);
            }
        } catch(e) {}
        try {
        var sectionHref = typeof section.url === 'string' ? section.url : (section.canonical || section.href || href);
        book.load(sectionHref)
            .then(function(doc) {
                try {
                    var body = doc.body || doc.querySelector('body') || doc.documentElement;
                    // 텍스트 노드별 문서 내 시작 offset 수집
                    var nodeMap = [];
                    var walker = doc.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
                    var nd, offset = 0;
                    while ((nd = walker.nextNode())) {
                        nodeMap.push({ node: nd, start: offset });
                        offset += nd.textContent.length;
                    }
                    var fullText = body ? body.textContent : '';
                    var lowerText = fullText.toLowerCase();
                    var positions = [];
                    var p = lowerText.indexOf(lowerQuery);
                    while (p !== -1 && positions.length < 5) { positions.push(p); p = lowerText.indexOf(lowerQuery, p + 1); }
                    if (positions.length > 0) {
                        var found = [];
                        for (var i = 0; i < positions.length; i++) {
                            var pos = positions[i];
                            var matchPage = 0;
                            if (_totalVisualPages > 0) {
                                var breaks = _spineCharPageBreaks[spineIndex];
                                if (breaks && breaks.length > 1) {
                                    var pageWithin = 0;
                                    for (var bi = breaks.length - 1; bi >= 0; bi--) {
                                        if (pos >= breaks[bi]) { pageWithin = bi; break; }
                                    }
                                    matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
                                } else {
                                    var sectionPages = _spinePageCounts[spineIndex] || 1;
                                    var pageWithin = Math.floor((pos / Math.max(fullText.length, 1)) * sectionPages);
                                    matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
                                }
                            }
                            var s = Math.max(0, pos - 60);
                            var e = Math.min(fullText.length, pos + query.length + 60);
                            var cfi = href;
                            try {
                                for (var j = 0; j < nodeMap.length; j++) {
                                    var nm = nodeMap[j];
                                    var nodeEnd = nm.start + nm.node.textContent.length;
                                    if (nm.start <= pos && pos < nodeEnd) {
                                        var charOffset = pos - nm.start;
                                        var steps = [];
                                        var cur = nm.node;
                                        // text node: position among all childNodes (1-indexed)
                                        var nodeIdx = Array.prototype.indexOf.call(cur.parentNode.childNodes, cur) + 1;
                                        steps.unshift('/' + nodeIdx + ':' + charOffset);
                                        cur = cur.parentNode;
                                        // walk up to body (case-insensitive for xhtml)
                                        while (cur && cur.parentNode) {
                                            var n = cur.nodeName.toUpperCase();
                                            if (n === 'BODY' || n === 'HTML' || n === '#DOCUMENT') break;
                                            var idx = 1;
                                            var s2 = cur.previousSibling;
                                            while (s2) { if (s2.nodeType === 1) idx++; s2 = s2.previousSibling; }
                                            steps.unshift('/' + (idx * 2));
                                            cur = cur.parentNode;
                                        }
                                        var cfiBase = section.cfiBase || '';
                                        if (cfiBase) {
                                            cfi = 'epubcfi(' + cfiBase + '!/4' + steps.join('') + ')';
                                        }
                                        break;
                                    }
                                }
                            } catch(e2) {
                                var cfiBase = section.cfiBase || '';
                                if (cfiBase) { cfi = 'epubcfi(' + cfiBase + ')'; }
                            }
                            found.push({ cfi: cfi, excerpt: fullText.substring(s, e).replace(/\s+/g, ' ').trim(), chapter: chapter, page: matchPage, spineIndex: spineIndex, charPos: pos });
                        }
                        if (found.length) Android.onSearchResultsPartial(JSON.stringify(found));
                    }
                } catch(e4) {}
                next();
            })
            .catch(function() { next(); });
        } catch(e5) { next(); }
    }

    var count = Math.min(CONCURRENCY, items.length);
    if (count === 0) { Android.onSearchComplete(); return; }
    activeChains = count;
    for (var c = 0; c < count; c++) next();
};

window._readerSettings = {
    fontFamily: "${fontFamilyForJs(settings.fontName)}",
    fontFilePath: "$fontFilePath",
    fontSize: ${settings.fontSize},
    textAlign: "${settings.textAlign.name.lowercase()}",
    lineHeight: ${settings.lineHeight},
    paragraphSpacing: ${settings.paragraphSpacing},
    paddingVertical: ${settings.paddingVertical},
    paddingHorizontal: ${settings.paddingHorizontal}
};

function _buildReaderCss(s) {
    var baseRule = 'font-size: ' + s.fontSize + 'px !important; line-height: ' + s.lineHeight + ' !important;';
    var paragraphRule = s.paragraphSpacing > 0 ? 'p { margin-bottom: ' + s.paragraphSpacing + 'px !important; }' : '';
    if (s.fontFamily === 'epub_original') {
        return 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6 {' + baseRule + '}' + paragraphRule;
    }
    var fontFaceDecl = '';
    var ff;
    if (s.fontFilePath) {
        fontFaceDecl = '@font-face { font-family: "_imported_"; src: url("' + s.fontFilePath + '"); }';
        ff = "'_imported_', sans-serif";
    } else {
        ff = s.fontFamily ? ("'" + s.fontFamily + "', sans-serif") : 'sans-serif';
    }
    return fontFaceDecl + 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6 {font-family: ' + ff + ' !important; ' + baseRule + '}' + paragraphRule;
}

function _injectReaderStyle(doc) {
    try {
        var style = doc.getElementById('_rs');
        if (!style) {
            style = doc.createElement('style');
            style.id = '_rs';
            doc.head.appendChild(style);
        }
        style.textContent = _buildReaderCss(window._readerSettings) + ' .search-hl { background: #e8a230; color: #fff; }';
    } catch(e) {}
}

var _rescanTimer = null;
window._applyReaderSettings = function(fontFamily, fontFilePath, fontSize, textAlign, lineHeight, paragraphSpacing, paddingVertical, paddingHorizontal, dualPage) {
    window._readerSettings = { fontFamily: fontFamily, fontFilePath: fontFilePath, fontSize: fontSize, textAlign: textAlign, lineHeight: lineHeight, paragraphSpacing: paragraphSpacing, paddingVertical: paddingVertical, paddingHorizontal: paddingHorizontal };
    _dualPage = !!dualPage;
    try {
        var viewer = document.getElementById('viewer');
        viewer.style.top = paddingVertical + 'px';
        viewer.style.left = paddingHorizontal + 'px';
        viewer.style.right = paddingHorizontal + 'px';
        viewer.style.bottom = (paddingVertical + 16) + 'px';
        rendition.spread(_getSpreadMode(), 0);
        rendition.resize(window.innerWidth - paddingHorizontal * 2, window.innerHeight - paddingVertical * 2 - 16);
    } catch(e) {}
    try {
        rendition.getContents().forEach(function(c) { _injectReaderStyle(c.document); });
    } catch(e) {}
    clearTimeout(_rescanTimer);
    if (_locationsReady) {
        _rescanTimer = setTimeout(function() { computeVisualPages(); }, 500);
    }
};
</script>
</body>
</html>"""
