// navigation.js — 페이지 넘기기

_pdf.navigating = false;

window._prevPage = function() {
    if (_pdf.currentPage <= 1) return;
    if (_isDualActive()) {
        // 두 쪽 보기: 현재 2-3이면 → 1(표지), 4-5이면 → 2-3
        var target = _pdf.currentPage === 2 ? 1 : _pdf.currentPage - 2;
        _renderPage(Math.max(1, target));
    } else {
        _renderPage(_pdf.currentPage - 1);
    }
};

window._nextPage = function() {
    if (_pdf.currentPage >= _pdf.totalPages) return;
    if (_isDualActive()) {
        // 두 쪽 보기: 1(표지) → 2-3, 2-3 → 4-5
        var target = _pdf.currentPage === 1 ? 2 : _pdf.currentPage + 2;
        if (target > _pdf.totalPages) return;
        _renderPage(target);
    } else {
        _renderPage(_pdf.currentPage + 1);
    }
};

// MainActivity 볼륨키 핸들러가 _prev/_next를 호출하므로 별칭 추가
window._prev = window._prevPage;
window._next = window._nextPage;

window._goToPage = function(pageNum) {
    if (_pdf.navigating) return;
    pageNum = parseInt(pageNum);
    if (isNaN(pageNum)) return;
    _pdf.navigating = true;
    _renderPage(pageNum);
};
