// search.js — PDF 본문 검색

window._search = function(query) {
    if (!_pdf.pdfDoc || !query) {
        Android.onSearchComplete();
        return;
    }

    var lowerQuery = query.toLowerCase();
    var pageIdx = 0;

    function searchNextPage() {
        if (pageIdx >= _pdf.totalPages) {
            Android.onSearchComplete();
            return;
        }

        var pageNum = pageIdx + 1;
        pageIdx++;

        _pdf.pdfDoc.getPage(pageNum).then(function(page) {
            return page.getTextContent();
        }).then(function(textContent) {
            var text = textContent.items.map(function(item) { return item.str; }).join(' ');
            var lowerText = text.toLowerCase();
            var positions = [];
            var idx = lowerText.indexOf(lowerQuery);
            while (idx !== -1 && positions.length < 5) {
                positions.push(idx);
                idx = lowerText.indexOf(lowerQuery, idx + 1);
            }

            if (positions.length > 0) {
                var found = [];
                for (var i = 0; i < positions.length; i++) {
                    var pos = positions[i];
                    var start = Math.max(0, pos - 60);
                    var end = Math.min(text.length, pos + query.length + 60);
                    found.push({
                        cfi: 'pdf-page:' + pageNum,
                        excerpt: text.substring(start, end).replace(/\s+/g, ' ').trim(),
                        chapter: '',
                        page: pageNum,
                        spineIndex: pageNum - 1,
                        charPos: pos
                    });
                }
                Android.onSearchResultsPartial(JSON.stringify(found));
            }

            setTimeout(searchNextPage, 0);
        }).catch(function() {
            setTimeout(searchNextPage, 0);
        });
    }

    searchNextPage();
};

window._clearSearch = function() {};
