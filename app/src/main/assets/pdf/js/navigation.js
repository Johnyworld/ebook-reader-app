// navigation.js — 페이지 넘기기

_pdf.navigating = false;

window._prevPage = function() {
    if (_pdf.currentPage <= 1) return;
    _renderPage(_pdf.currentPage - 1);
};

window._nextPage = function() {
    if (_pdf.currentPage >= _pdf.totalPages) return;
    _renderPage(_pdf.currentPage + 1);
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
