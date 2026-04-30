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

// resize 이벤트로 인한 위치 손실 방지
// _lastResizeW/H는 resize 핸들러와 동일한 공식(정수 연산)으로 초기화하여
// getBoundingClientRect의 서브픽셀 차이로 불필요한 resize가 발생하지 않게 한다.
_epub._lastResizeW = window.innerWidth - _config.paddingHorizontal * 2;
_epub._lastResizeH = window.innerHeight - _config.paddingVertical * 2 - _config.bottomInfoHeight;
_epub._preResizeCfi = undefined;
_epub._resizeRestoring = false;

// 네비게이션 중이거나 아직 렌더링 전인 resize는 큐에 저장했다가
// 네비게이션 완료 후 한 번만 실행한다.
_epub._pendingResize = false;

window.addEventListener('resize', function() {
    if (!window._readerSettings) return;
    var s = window._readerSettings;

    var newW = window.innerWidth - s.paddingHorizontal * 2;
    var newH = window.innerHeight - s.paddingVertical * 2 - _config.bottomInfoHeight;

    // 크기가 실제로 변하지 않았으면 무시한다.
    if (newW === _epub._lastResizeW && newH === _epub._lastResizeH) {
        return;
    }

    // 초기 로드 네비게이션 중이면 resize를 지연시킨다.
    // display(savedCfi)가 완료되기 전에 rendition.resize()를 호출하면
    // scrollLeft가 초기화되어 잘못된 페이지로 이동하는 버그가 발생한다.
    if (_epub.navigating) {
        _epub._pendingResize = true;
        return;
    }

    // 백그라운드 복귀 중이면 resize를 무시한다.
    // 복귀 시 dimensions가 681→658→681처럼 바운스하여 원래대로 돌아오므로
    // resize를 실행할 필요가 없다.
    if (_epub._resumeMode) {
        return;
    }

    _epub._lastResizeW = newW;
    _epub._lastResizeH = newH;

    // 현재 CFI를 저장한다 (resize 후 복원용).
    if (!_epub._preResizeCfi && !_epub._resizeRestoring) {
        _epub._preResizeCfi = window._currentCfi || _config.savedCfi || '';
    }

    _epub.rendition.spread(_getSpreadMode(), 0);
    _epub.rendition.resize(newW, newH);
    var viewer = document.getElementById('viewer');
    if (viewer) {
        viewer.style.top = s.paddingVertical + 'px';
        viewer.style.left = s.paddingHorizontal + 'px';
        viewer.style.right = s.paddingHorizontal + 'px';
        viewer.style.bottom = (s.paddingVertical + _config.bottomInfoHeight) + 'px';
    }

    // resize 후 CFI 복원: 디바운스로 마지막 resize 이후 실행
    clearTimeout(_epub._resizeRestoreTimer);
    _epub._resizeRestoreTimer = setTimeout(function() {
        if (_epub._preResizeCfi && !_epub._resizeRestoring) {
            var cfi = _epub._preResizeCfi;
            _epub._preResizeCfi = undefined;
            _epub._resizeRestoring = true;
            _epub.rendition.display(cfi).then(function() {
                _epub._resizeRestoring = false;
                // 크기가 변경되었으므로 페이지 스캔을 다시 실행한다.
                if (typeof computeVisualPages === 'function') {
                    clearTimeout(_epub.rescanTimer);
                    _epub.rescanTimer = setTimeout(function() { computeVisualPages(); }, 500);
                }
            }).catch(function() {
                _epub._resizeRestoring = false;
            });
        }
    }, 500);
});
