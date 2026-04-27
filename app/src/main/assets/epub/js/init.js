// init.js — ResizeObserver polyfill, _epub global, book + rendition setup
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

window._showBookmarkRibbon = function(show) {
    var el = document.getElementById('bookmark-ribbon');
    if (el) el.style.display = show ? 'block' : 'none';
};

var _epub = {};
_epub.dualPage = _config.dualPage;

function _getSpreadMode() {
    return (_epub.dualPage && window.innerWidth > window.innerHeight) ? "always" : "none";
}

_epub.book = ePub(_config.opfPath);

var _viewerEl = document.getElementById('viewer');
var _viewerRect = _viewerEl ? _viewerEl.getBoundingClientRect() : null;
var _viewerW = (_viewerRect && _viewerRect.width > 0) ? _viewerRect.width : (window.innerWidth - _config.paddingHorizontal * 2);
var _viewerH = (_viewerRect && _viewerRect.height > 0) ? _viewerRect.height : (window.innerHeight - _config.paddingVertical * 2 - _config.bottomInfoHeight);

_epub.rendition = _epub.book.renderTo("viewer", {
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
    _epub.rendition.spread(_getSpreadMode(), 0);
    _epub.rendition.resize(window.innerWidth - s.paddingHorizontal * 2, window.innerHeight - s.paddingVertical * 2 - _config.bottomInfoHeight);
    var viewer = document.getElementById('viewer');
    if (viewer) {
        viewer.style.top = s.paddingVertical + 'px';
        viewer.style.left = s.paddingHorizontal + 'px';
        viewer.style.right = s.paddingHorizontal + 'px';
        viewer.style.bottom = (s.paddingVertical + _config.bottomInfoHeight) + 'px';
    }
});
