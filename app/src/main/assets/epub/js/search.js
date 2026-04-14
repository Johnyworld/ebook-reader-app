// search.js — search highlights and full-text search

_epub.searchHighlightQuery = '';

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
    if (!_epub.searchHighlightQuery) return;
    _removeSearchHighlights();
    try {
        var iframe = document.querySelector('iframe');
        var iDoc = iframe && (iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document));
        if (!iDoc || !iDoc.body) return;
        var lowerQuery = _epub.searchHighlightQuery.toLowerCase();
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
    _epub.searchHighlightQuery = query;
    _applySearchHighlights();
};

window._clearSearchHighlight = function() {
    _epub.searchHighlightQuery = '';
    _removeSearchHighlights();
};

window._search = function(query) {
    var items = _epub.book.spine ? (_epub.book.spine.items || []) : [];
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
            if (_epub.book.navigation && _epub.book.navigation.toc) {
                chapter = findTocEntry(href, _epub.book.navigation.toc);
            }
        } catch(e) {}
        try {
        var sectionHref = typeof section.url === 'string' ? section.url : (section.canonical || section.href || href);
        _epub.book.load(sectionHref)
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
                            if (_epub.totalVisualPages > 0) {
                                var breaks = _epub.spineCharPageBreaks[spineIndex];
                                if (breaks && breaks.length > 1) {
                                    var pageWithin = 0;
                                    for (var bi = breaks.length - 1; bi >= 0; bi--) {
                                        if (pos >= breaks[bi]) { pageWithin = bi; break; }
                                    }
                                    matchPage = (_epub.spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
                                } else {
                                    var sectionPages = _epub.spinePageCounts[spineIndex] || 1;
                                    var pageWithin = Math.floor((pos / Math.max(fullText.length, 1)) * sectionPages);
                                    matchPage = (_epub.spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
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
