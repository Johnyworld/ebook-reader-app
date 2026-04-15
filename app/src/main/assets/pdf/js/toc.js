// toc.js — PDF outline(목차) 추출

function _loadOutline() {
    if (!_pdf.pdfDoc) return;

    _pdf.pdfDoc.getOutline().then(function(outline) {
        if (!outline || outline.length === 0) {
            Android.onTocLoaded('[]');
            return;
        }

        _convertOutline(outline, 0).then(function(tocItems) {
            Android.onTocLoaded(JSON.stringify(tocItems));
        });
    }).catch(function() {
        Android.onTocLoaded('[]');
    });
}

function _convertOutline(items, depth) {
    var promises = items.map(function(item) {
        return _resolveOutlineDest(item).then(function(pageNum) {
            var subPromise = (item.items && item.items.length > 0)
                ? _convertOutline(item.items, depth + 1)
                : Promise.resolve([]);
            return subPromise.then(function(subitems) {
                return {
                    label: item.title || '',
                    href: 'pdf-page:' + pageNum,
                    depth: depth,
                    page: pageNum,
                    subitems: subitems
                };
            });
        });
    });
    return Promise.all(promises);
}

function _resolveOutlineDest(item) {
    if (!item.dest) return Promise.resolve(1);

    if (typeof item.dest === 'string') {
        return _pdf.pdfDoc.getDestination(item.dest).then(function(dest) {
            if (!dest) return 1;
            return _pdf.pdfDoc.getPageIndex(dest[0]).then(function(idx) { return idx + 1; });
        }).catch(function() { return 1; });
    }

    if (Array.isArray(item.dest) && item.dest.length > 0) {
        return _pdf.pdfDoc.getPageIndex(item.dest[0]).then(function(idx) {
            return idx + 1;
        }).catch(function() { return 1; });
    }

    return Promise.resolve(1);
}
