// page-scan.js — computeVisualPages, locations generation

function computeVisualPages() {
    try { Android.onScanStart(); } catch(e) {}
    var s = window._readerSettings;
    var scanW = window.innerWidth - s.paddingHorizontal * 2;
    var scanH = window.innerHeight - s.paddingVertical * 2 - 16;
    var scanDiv = document.createElement('div');
    scanDiv.style.cssText = 'position:fixed;left:-' + (scanW + 10) + 'px;top:0;width:' + scanW + 'px;height:' + scanH + 'px;overflow:hidden;';
    document.body.appendChild(scanDiv);
    var scanBook = ePub(_config.opfPath);
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
                    _epub.cfiPageMap[cfis[idx]] = _epub.spinePageOffsets[i] + pg;
                } catch(e) {}
                _resolveCfisInSpine(cfis, idx + 1, done);
            }).catch(function() { _resolveCfisInSpine(cfis, idx + 1, done); });
        }
        function next() {
            if (i >= items.length) {
                _epub.totalVisualPages = _runningOffset;
                try {
                    var tocNav = _epub.book.navigation ? (_epub.book.navigation.toc || []) : [];
                    var spineItemsForToc = _epub.book.spine ? (_epub.book.spine.items || []) : [];
                    function buildTocWithPages(tocItems, depth) {
                        var result = [];
                        for (var ti = 0; ti < tocItems.length; ti++) {
                            var tocItem = tocItems[ti];
                            var hrefBase = tocItem.href.split('#')[0];
                            var page = 0;
                            for (var si = 0; si < spineItemsForToc.length; si++) {
                                var siHref = (spineItemsForToc[si].href || '').split('?')[0];
                                if (siHref === hrefBase || siHref.endsWith('/' + hrefBase) || hrefBase.endsWith('/' + siHref)) {
                                    page = (_epub.spinePageOffsets[si] || 0) + 1;
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
                _epub.pendingLocation = null;
                var _scanCurrentPage = 0;
                var loc = _epub.rendition.currentLocation();
                if (loc && loc.start) {
                    var idx = loc.start.index !== undefined ? loc.start.index : 0;
                    var pg = loc.start.displayed ? loc.start.displayed.page : 1;
                    try {
                        var delta = _epub.rendition.manager && _epub.rendition.manager.layout ? _epub.rendition.manager.layout.delta : 0;
                        if (delta > 0 && _epub.rendition.manager.container) {
                            var scrollLeft = _epub.rendition.manager.container.scrollLeft;
                            pg = Math.round(scrollLeft / delta) + 1;
                        }
                    } catch(e) {}
                    _scanCurrentPage = (_epub.spinePageOffsets[idx] || 0) + pg;
                }
                Android.onScanComplete(_epub.totalVisualPages, _scanCurrentPage, JSON.stringify(_epub.spinePageOffsets), JSON.stringify(_epub.cfiPageMap), JSON.stringify(_epub.spineCharPageBreaks));
                setTimeout(function() {
                    try { scanRendition.destroy(); } catch(e) {}
                    try { scanBook.destroy(); } catch(e) {}
                    document.body.removeChild(scanDiv);
                }, 100);
                return;
            }
            scanRendition.display(items[i].href).then(function() {
                var loc = scanRendition.currentLocation();
                _epub.spinePageCounts[i] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
                _epub.spinePageOffsets[i] = _runningOffset;
                try {
                    var pageBreaks = [0];
                    var sTotal = _epub.spinePageCounts[i];
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
                                                var nodeEndPage = Math.floor(Math.max(rect.right - 1, 0) / sDelta);
                                                if (nodePage > lastPage) {
                                                    for (var np = lastPage + 1; np <= nodePage; np++) {
                                                        pageBreaks.push(charOffset);
                                                    }
                                                }
                                                if (nodeEndPage > nodePage) {
                                                    for (var tp = nodePage + 1; tp <= nodeEndPage; tp++) {
                                                        var lo = 0, hi = len;
                                                        while (lo < hi) {
                                                            var mid = (lo + hi) >> 1;
                                                            range.setStart(sNd, mid);
                                                            range.setEnd(sNd, Math.min(mid + 1, len));
                                                            var mr = range.getBoundingClientRect();
                                                            if (Math.floor(mr.left / sDelta) < tp) lo = mid + 1; else hi = mid;
                                                        }
                                                        pageBreaks.push(charOffset + lo);
                                                    }
                                                }
                                                lastPage = Math.max(lastPage, nodeEndPage);
                                            }
                                        } catch(re) {}
                                        charOffset += len;
                                    }
                                }
                            }
                        }
                    }
                    _epub.spineCharPageBreaks[i] = pageBreaks;
                } catch(pe) {
                    _epub.spineCharPageBreaks[i] = [0];
                }
                var cfisForSpine = [];
                for (var ci = 0; ci < _epub.pendingCfiList.length; ci++) {
                    if (_epub.pendingCfiList[ci].spineIndex === i) cfisForSpine.push(_epub.pendingCfiList[ci].cfi);
                }
                _resolveCfisInSpine(cfisForSpine, 0, function() {
                    _runningOffset += _epub.spinePageCounts[i];
                    i++;
                    next();
                });
            });
        }
        next();
    });
}

_epub.rescanTimer = null;

_epub.book.ready.then(function() {
    return _epub.book.locations.generate(150);
}).then(function() {
    try {
        var locs = _epub.book.locations._locations || [];
        _epub.totalLocations = locs.length || 1;
        var toc = _epub.book.navigation ? (_epub.book.navigation.toc || []) : [];
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

    _epub.locationsReady = true;
    clearTimeout(_epub.rescanTimer);
    computeVisualPages();
});
