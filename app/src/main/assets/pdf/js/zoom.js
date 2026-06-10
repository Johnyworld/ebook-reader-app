// zoom.js — pinch zoom & pan

_pdf.zoomScale = 1;
_pdf.zoomOffsetX = 0;
_pdf.zoomOffsetY = 0;

function _clampZoom() {
    var container = document.getElementById('pdf-container');
    var wrapper = document.getElementById('pdf-wrapper');
    if (!container || !wrapper) return;

    var cw = container.clientWidth;
    var ch = container.clientHeight;
    // wrapper 전체 크기 기준 (두 쪽 보기 시 캔버스 2개 포함)
    var sw = wrapper.offsetWidth * _pdf.zoomScale;
    var sh = wrapper.offsetHeight * _pdf.zoomScale;

    if (sw <= cw) {
        _pdf.zoomOffsetX = (cw - sw) / 2;
    } else {
        _pdf.zoomOffsetX = Math.min(0, Math.max(cw - sw, _pdf.zoomOffsetX));
    }
    if (sh <= ch) {
        _pdf.zoomOffsetY = (ch - sh) / 2;
    } else {
        _pdf.zoomOffsetY = Math.min(0, Math.max(ch - sh, _pdf.zoomOffsetY));
    }
}

function _applyZoom() {
    var wrapper = document.getElementById('pdf-wrapper');
    if (!wrapper) return;
    wrapper.style.transformOrigin = '0 0';
    wrapper.style.transform = 'translate(' + _pdf.zoomOffsetX + 'px,' + _pdf.zoomOffsetY + 'px) scale(' + _pdf.zoomScale + ')';
}

window._resetZoom = function() {
    _pdf.zoomScale = 1;
    var container = document.getElementById('pdf-container');
    var wrapper = document.getElementById('pdf-wrapper');
    if (container && wrapper && wrapper.offsetWidth > 0) {
        _pdf.zoomOffsetX = (container.clientWidth - wrapper.offsetWidth) / 2;
        _pdf.zoomOffsetY = (container.clientHeight - wrapper.offsetHeight) / 2;
    } else {
        _pdf.zoomOffsetX = 0;
        _pdf.zoomOffsetY = 0;
    }
    _applyZoom();
};

// 페이지 이동 후 이전 줌 배율과 오프셋을 복원
window._restoreZoom = function() {
    _clampZoom();
    _applyZoom();
};

window._zoomBy = function(factor, focusX, focusY) {
    var newScale = Math.max(1, Math.min(5, _pdf.zoomScale * factor));
    var ratio = newScale / _pdf.zoomScale;
    _pdf.zoomOffsetX = focusX - (focusX - _pdf.zoomOffsetX) * ratio;
    _pdf.zoomOffsetY = focusY - (focusY - _pdf.zoomOffsetY) * ratio;
    _pdf.zoomScale = newScale;
    _clampZoom();
    _applyZoom();
};

window._panBy = function(dx, dy) {
    if (_pdf.zoomScale <= 1) return;
    _pdf.zoomOffsetX += dx;
    _pdf.zoomOffsetY += dy;
    _clampZoom();
    _applyZoom();
};
