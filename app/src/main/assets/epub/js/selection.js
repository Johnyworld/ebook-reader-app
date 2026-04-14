// selection.js — content hook for selection handling, scroll lock, CFI from selection

_epub.rendition.hooks.content.register(function(contents) {
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
