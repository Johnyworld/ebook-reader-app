// settings.js — reader style settings, CSS injection, applyReaderSettings
window._readerSettings = {
    fontFamily: _config.fontFamilyForJs,
    fontFilePath: _config.fontFilePath,
    fontSize: _config.fontSize,
    textAlign: _config.textAlign.toLowerCase(),
    lineHeight: _config.lineHeight,
    paragraphSpacing: _config.paragraphSpacing,
    paddingVertical: _config.paddingVertical,
    paddingHorizontal: _config.paddingHorizontal
};

function _buildReaderCss(s) {
    var baseRule = 'font-size: ' + s.fontSize + 'px !important; line-height: ' + s.lineHeight + ' !important;';
    var paragraphRule = s.paragraphSpacing > 0 ? 'p { margin-bottom: ' + s.paragraphSpacing + 'px !important; }' : '';
    if (s.fontFamily === 'epub_original') {
        return 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6 {' + baseRule + '}' + paragraphRule;
    }
    var fontFaceDecl = '';
    var ff;
    if (s.fontFilePath) {
        // CSS url() 인젝션 방지를 위해 특수문자 이스케이프
        var safePath = s.fontFilePath.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\)/g, '\\)');
        fontFaceDecl = '@font-face { font-family: "_imported_"; src: url("' + safePath + '"); }';
        ff = "'_imported_', sans-serif";
    } else {
        ff = s.fontFamily ? ("'" + s.fontFamily + "', sans-serif") : 'sans-serif';
    }
    return fontFaceDecl + 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6 {font-family: ' + ff + ' !important; ' + baseRule + '}' + paragraphRule;
}

function _injectReaderStyle(doc) {
    try {
        var style = doc.getElementById('_rs');
        if (!style) {
            style = doc.createElement('style');
            style.id = '_rs';
            doc.head.appendChild(style);
        }
        style.textContent = _buildReaderCss(window._readerSettings) + ' .search-hl { background: #e8a230; color: #fff; }';
    } catch(e) {}
}

window._applyReaderSettings = function(fontFamily, fontFilePath, fontSize, textAlign, lineHeight, paragraphSpacing, paddingVertical, paddingHorizontal, dualPage) {
    window._readerSettings = { fontFamily: fontFamily, fontFilePath: fontFilePath, fontSize: fontSize, textAlign: textAlign, lineHeight: lineHeight, paragraphSpacing: paragraphSpacing, paddingVertical: paddingVertical, paddingHorizontal: paddingHorizontal };
    _epub.dualPage = !!dualPage;
    try {
        var viewer = document.getElementById('viewer');
        viewer.style.top = paddingVertical + 'px';
        viewer.style.left = paddingHorizontal + 'px';
        viewer.style.right = paddingHorizontal + 'px';
        viewer.style.bottom = (paddingVertical + _config.bottomInfoHeight) + 'px';
        _epub.rendition.spread(_getSpreadMode(), 0);
        _epub.rendition.resize(window.innerWidth - paddingHorizontal * 2, window.innerHeight - paddingVertical * 2 - _config.bottomInfoHeight);
    } catch(e) {}
    try {
        _epub.rendition.getContents().forEach(function(c) { _injectReaderStyle(c.document); });
    } catch(e) {}
    clearTimeout(_epub.rescanTimer);
    if (_epub.locationsReady) {
        _epub.rescanTimer = setTimeout(function() { computeVisualPages(); }, 500);
    }
};
