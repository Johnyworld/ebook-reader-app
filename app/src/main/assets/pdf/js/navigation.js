// navigation.js — 페이지 넘기기

window._prevPage = function() {
    if (_pdf.currentPage <= 1) return;
    _renderPage(_pdf.currentPage - 1);
};

window._nextPage = function() {
    if (_pdf.currentPage >= _pdf.totalPages) return;
    _renderPage(_pdf.currentPage + 1);
};

window._goToPage = function(pageNum) {
    pageNum = parseInt(pageNum);
    if (isNaN(pageNum)) return;
    _renderPage(pageNum);
};
