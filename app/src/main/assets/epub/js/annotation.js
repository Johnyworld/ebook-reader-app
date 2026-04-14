// annotation.js — highlights and memos

var _highlightCfiMap = {};

window._addHighlight = function(cfi, id) {
    try {
        _highlightCfiMap[id] = cfi;
        _epub.rendition.annotations.add("highlight", cfi, {id: id}, null, "epub-hl-" + id, {
            "background": "#d0d0d0",
            "border": "none"
        });
    } catch(e) {}
};

window._removeHighlight = function(id) {
    try {
        var cfi = _highlightCfiMap[id];
        if (cfi) {
            _epub.rendition.annotations.remove(cfi, "highlight");
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
        _epub.rendition.annotations.add("underline", cfi, {id: id}, null, "epub-memo-" + id, {
            "fill": "#000000",
            "stroke": "none"
        });
    } catch(e) {}
};

window._removeMemo = function(id) {
    try {
        var cfi = _memoCfiMap[id];
        if (cfi) { _epub.rendition.annotations.remove(cfi, "underline"); delete _memoCfiMap[id]; }
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
