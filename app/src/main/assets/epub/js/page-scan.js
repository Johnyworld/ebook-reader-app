// page-scan.js — computeVisualPages, locations generation

function _scanFallback() {
    try { Android.onScanComplete(0, 0, '{}', '{}', '{}'); } catch(e) {}
}

function computeVisualPages() {
    try { Android.onScanStart(); } catch(e) {}
    var s = window._readerSettings;
    if (!s) { _scanFallback(); return; }
    var scanW = window.innerWidth - s.paddingHorizontal * 2;
    var scanH = window.innerHeight - s.paddingVertical * 2 - _config.bottomInfoHeight;
    var scanDiv = document.createElement('div');
    scanDiv.style.cssText = 'position:fixed;left:-' + (scanW + 10) + 'px;top:0;width:' + scanW + 'px;height:' + scanH + 'px;overflow:hidden;';
    document.body.appendChild(scanDiv);

    // 기존: 새 ePub() 인스턴스를 생성하여 같은 책을 다시 파싱 → 특정 도서에서 무한루프 발생
    // 수정: 이미 로드된 _epub.book을 재사용하여 스캔용 rendition만 새로 생성
    var savedRendition = _epub.book.rendition;
    var scanRendition;
    try {
        scanRendition = _epub.book.renderTo(scanDiv, {
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
    } catch(e) {
        _epub.book.rendition = savedRendition;
        try { document.body.removeChild(scanDiv); } catch(e2) {}
        _scanFallback();
        return;
    }

    // _epub.book은 이미 ready이므로 spine.items를 바로 사용
    var items = _epub.book.spine ? (_epub.book.spine.items || []) : [];
    if (items.length === 0) {
        _epub.book.rendition = savedRendition;
        try { scanRendition.destroy(); } catch(e) {}
        try { document.body.removeChild(scanDiv); } catch(e) {}
        _scanFallback();
        return;
    }

    var i = 0;
    var _runningOffset = 0;

    function _cleanupScan() {
        _epub.book.rendition = savedRendition;
        setTimeout(function() {
            try { scanRendition.destroy(); } catch(e) {}
            try { document.body.removeChild(scanDiv); } catch(e) {}
        }, 100);
    }

    function _resolveCfisInSpine(cfis, idx, spineIdx, done) {
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
                _epub.cfiPageMap[cfis[idx]] = _epub.spinePageOffsets[spineIdx] + pg;
            } catch(e) {}
            _resolveCfisInSpine(cfis, idx + 1, spineIdx, done);
        }).catch(function() { _resolveCfisInSpine(cfis, idx + 1, spineIdx, done); });
    }

    function next() {
        if (i >= items.length) {
            _epub.totalVisualPages = _runningOffset;
            try {
                var tocNav = _epub.book.navigation ? (_epub.book.navigation.toc || []) : [];
                var spineItemsForToc = _epub.book.spine ? (_epub.book.spine.items || []) : [];

                function findSpineIndex(hrefBase) {
                    for (var si = 0; si < spineItemsForToc.length; si++) {
                        var siHref = (spineItemsForToc[si].href || '').split('?')[0];
                        if (siHref === hrefBase || siHref.endsWith('/' + hrefBase) || hrefBase.endsWith('/' + siHref)) {
                            return si;
                        }
                    }
                    return -1;
                }

                // fragment가 있는 href의 정확한 페이지를 비동기로 계산
                function buildTocWithPages(tocItems, depth) {
                    var result = [];
                    for (var ti = 0; ti < tocItems.length; ti++) {
                        var tocItem = tocItems[ti];
                        var hrefBase = tocItem.href.split('#')[0];
                        var si = findSpineIndex(hrefBase);
                        var page = si >= 0 ? (_epub.spinePageOffsets[si] || 0) + 1 : 0;
                        result.push({
                            label: tocItem.label.trim(),
                            href: tocItem.href,
                            page: page,
                            depth: depth,
                            spineIndex: si,
                            subitems: tocItem.subitems ? buildTocWithPages(tocItem.subitems, depth + 1) : []
                        });
                    }
                    return result;
                }

                var tocWithPages = buildTocWithPages(tocNav, 0);

                // fragment가 있는 항목들의 정확한 페이지를 scanRendition으로 계산
                function flattenForResolve(items) {
                    var arr = [];
                    for (var j = 0; j < items.length; j++) {
                        if (items[j].href.indexOf('#') >= 0 && items[j].spineIndex >= 0) {
                            arr.push(items[j]);
                        }
                        if (items[j].subitems) {
                            arr = arr.concat(flattenForResolve(items[j].subitems));
                        }
                    }
                    return arr;
                }

                var toResolve = flattenForResolve(tocWithPages);
                var ri = 0;

                function resolveNext() {
                    if (ri >= toResolve.length) {
                        // spineIndex 필드 제거 후 전송
                        function cleanToc(items) {
                            return items.map(function(it) {
                                var o = { label: it.label, href: it.href, page: it.page, depth: it.depth };
                                if (it.subitems && it.subitems.length > 0) o.subitems = cleanToc(it.subitems);
                                return o;
                            });
                        }
                        Android.onTocReady(JSON.stringify(cleanToc(tocWithPages)));
                        finishToc();
                        return;
                    }
                    var entry = toResolve[ri];
                    scanRendition.display(entry.href).then(function() {
                        try {
                            var pg = 1;
                            var sDelta = scanRendition.manager && scanRendition.manager.layout ? scanRendition.manager.layout.delta : 0;
                            if (sDelta > 0 && scanRendition.manager.container) {
                                var sScrollLeft = scanRendition.manager.container.scrollLeft;
                                pg = Math.round(sScrollLeft / sDelta) + 1;
                            } else {
                                var cfiLoc = scanRendition.currentLocation();
                                pg = (cfiLoc && cfiLoc.start && cfiLoc.start.displayed) ? cfiLoc.start.displayed.page : 1;
                            }
                            entry.page = (_epub.spinePageOffsets[entry.spineIndex] || 0) + pg;
                        } catch(e) {}
                        ri++;
                        resolveNext();
                    }).catch(function() {
                        ri++;
                        resolveNext();
                    });
                }

                function finishToc() {
                    _epub.pendingLocation = null;
                    _cleanupScan();
                }

                if (toResolve.length > 0) {
                    resolveNext();
                } else {
                    Android.onTocReady(JSON.stringify(tocWithPages));
                    finishToc();
                }
            } catch(e) {
                _epub.pendingLocation = null;
                _cleanupScan();
            }
            var _scanCurrentPage = 0;
            try {
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
            } catch(e) {}
            Android.onScanComplete(_epub.totalVisualPages, _scanCurrentPage, JSON.stringify(_epub.spinePageOffsets), JSON.stringify(_epub.cfiPageMap), JSON.stringify(_epub.spineCharPageBreaks));
            return;
        }
        (function scanItem(idx) {
            var done = false;
            // 항목별 타임아웃: display()가 resolve/reject 모두 안 되면 건너뜀
            var timeout = setTimeout(function() {
                if (done) return;
                done = true;
                _epub.spinePageCounts[idx] = 1;
                _epub.spinePageOffsets[idx] = _runningOffset;
                _epub.spineCharPageBreaks[idx] = [0];
                _runningOffset += 1;
                i++;
                next();
            }, 5000);

            scanRendition.display(items[idx].href).then(function() {
                if (done) return;
                done = true;
                clearTimeout(timeout);
                var loc = scanRendition.currentLocation();
                _epub.spinePageCounts[idx] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
                _epub.spinePageOffsets[idx] = _runningOffset;
                try {
                    var pageBreaks = [0];
                    var sTotal = _epub.spinePageCounts[idx];
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
                    _epub.spineCharPageBreaks[idx] = pageBreaks;
                } catch(pe) {
                    _epub.spineCharPageBreaks[idx] = [0];
                }
                var cfisForSpine = [];
                for (var ci = 0; ci < _epub.pendingCfiList.length; ci++) {
                    if (_epub.pendingCfiList[ci].spineIndex === idx) cfisForSpine.push(_epub.pendingCfiList[ci].cfi);
                }
                _resolveCfisInSpine(cfisForSpine, 0, idx, function() {
                    _runningOffset += _epub.spinePageCounts[idx];
                    i++;
                    next();
                });
            }).catch(function() {
                if (done) return;
                done = true;
                clearTimeout(timeout);
                _epub.spinePageCounts[idx] = 1;
                _epub.spinePageOffsets[idx] = _runningOffset;
                _epub.spineCharPageBreaks[idx] = [0];
                _runningOffset += 1;
                i++;
                next();
            });
        })(i);
    }
    next();
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
}).catch(function() {
    // locations 생성 실패 시에도 페이지 스캔은 시도
    _epub.locationsReady = true;
    computeVisualPages();
});
