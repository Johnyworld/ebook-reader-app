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

// 설정 변경 전 현재 위치의 문자 비율 정보를 저장한다.
// 문자 비율(charsBefore/totalChars)은 폰트 사이즈가 변해도 불변이므로
// 새 레이아웃에서 정확한 페이지를 추정할 수 있다.
function _saveCharPosition() {
    try {
        var m = _epub.rendition.manager;
        if (!m || !m.container || !m.layout) return null;
        var delta = m.layout.delta;
        var scrollLeft = m.container.scrollLeft;
        var currentPage = Math.round(scrollLeft / delta);

        // spine 정보
        var spineHref = '';
        var spineIndex = -1;
        try {
            var loc = _epub.rendition.currentLocation();
            if (loc && loc.start) spineHref = loc.start.href || '';
        } catch(e) {}
        var items = _epub.book.spine ? (_epub.book.spine.items || []) : [];
        for (var i = 0; i < items.length; i++) {
            var ih = items[i].href || '';
            if (ih === spineHref || ih.endsWith('/' + spineHref) || spineHref.endsWith('/' + ih)) {
                spineIndex = i; break;
            }
        }

        // 스캔에서 구한 정확한 페이지 수로 문자 비율 계산
        var scanPageCount = spineIndex >= 0 ? (_epub.spinePageCounts[spineIndex] || 0) : 0;
        if (scanPageCount === 0) scanPageCount = Math.max(1, Math.ceil(m.container.scrollWidth / delta));

        // iframe에서 전체 문자수를 세고, 페이지 비율로 charsBefore 추정
        var iframe = document.querySelector('iframe');
        var totalChars = 0;
        if (iframe && iframe.contentDocument && iframe.contentDocument.body) {
            var walker = iframe.contentDocument.createTreeWalker(
                iframe.contentDocument.body, NodeFilter.SHOW_TEXT, null, false);
            var n;
            while ((n = walker.nextNode())) { totalChars += n.textContent.length; }
        }
        if (totalChars === 0) return null;

        var charsBefore = Math.floor((currentPage / scanPageCount) * totalChars);
        return { charsBefore: charsBefore, totalChars: totalChars, spineHref: spineHref, spineIndex: spineIndex };
    } catch(e) { return null; }
}

// 문자 비율 기반 프로그레시브 스크롤 + CFI 정밀 보정으로 위치를 복원한다.
// 1단계: 프로그레시브 배치 스크롤로 CSS column lazy rendering을 강제
// 2단계: 안정화 루프로 scrollWidth 수렴 확인
// 3단계: CFI가 있으면 display(cfi)로 정밀 보정 (컬럼이 이미 렌더링되어 정확)
function _restoreByCharRatio(charInfo, cfi, onComplete) {
    if (!charInfo || charInfo.totalChars === 0 || !charInfo.spineHref) {
        onComplete(); return;
    }
    // spine item의 시작으로 이동하여 scrollLeft를 0으로 초기화
    _epub.rendition.display(charInfo.spineHref).then(function() {
        var m = _epub.rendition.manager;
        if (!m || !m.container || !m.layout) { onComplete(); return; }
        m.container.scrollLeft = 0;

        var delta = m.layout.delta;
        var curTotal = Math.max(1, Math.ceil(m.container.scrollWidth / delta));
        var ratio = charInfo.charsBefore / charInfo.totalChars;
        var page = Math.min(Math.floor(ratio * curTotal), curTotal - 1);

        if (page <= 0) {
            // 타겟이 첫 페이지 → CFI 정밀 보정만 시도
            _finishWithCfi(m, cfi, onComplete);
            return;
        }

        // 배치 스크롤 중 중간 페이지가 보이지 않도록 숨김
        m.container.style.visibility = 'hidden';
        var batchSize = 20;
        function advanceBatch(from) {
            var end = Math.min(from + batchSize, page);
            for (var p = from; p <= end; p++) {
                m.container.scrollLeft = p * delta;
            }
            // scrollWidth 확장 시 타겟 페이지 재계산
            var sw = m.container.scrollWidth;
            var newTotal = Math.ceil(sw / delta);
            if (newTotal > curTotal) {
                curTotal = newTotal;
                var newPage = Math.min(Math.floor(ratio * newTotal), newTotal - 1);
                if (newPage > page) page = newPage;
            }
            if (end >= page) {
                // 안정화: rAF 후에도 scrollWidth가 증가하면 계속 스크롤
                _stabilizeAndFinish(m, delta, ratio, page, end, curTotal, cfi, 0, onComplete);
            } else {
                requestAnimationFrame(function() {
                    advanceBatch(end + 1);
                });
            }
        }
        advanceBatch(0);
    }).catch(function() { onComplete(); });
}

// 안정화 루프: rAF 후 scrollWidth가 아직 증가 중이면 추가 스크롤
function _stabilizeAndFinish(m, delta, ratio, page, endSoFar, curTotal, cfi, attempt, onComplete) {
    requestAnimationFrame(function() {
        var sw = m.container.scrollWidth;
        var newTotal = Math.ceil(sw / delta);
        if (newTotal > curTotal && attempt < 5) {
            curTotal = newTotal;
            var newPage = Math.min(Math.floor(ratio * newTotal), newTotal - 1);
            if (newPage > endSoFar) {
                // 아직 도달하지 못한 페이지가 있음 → 추가 배치 스크롤
                var batchSize = 20;
                var end = Math.min(endSoFar + batchSize, newPage);
                for (var p = endSoFar + 1; p <= end; p++) {
                    m.container.scrollLeft = p * delta;
                }
                _stabilizeAndFinish(m, delta, ratio, newPage, end, curTotal, cfi, attempt + 1, onComplete);
                return;
            }
        }
        // scrollWidth 안정화 완료 → CFI 정밀 보정
        _finishWithCfi(m, cfi, onComplete);
    });
}

// 프로그레시브 스크롤 완료 후 CFI로 정밀 위치 보정
function _finishWithCfi(m, cfi, onComplete) {
    if (cfi) {
        // 컬럼이 이미 렌더링되었으므로 display(cfi)가 정확하게 동작
        _epub.rendition.display(cfi).then(function() {
            m.container.style.visibility = '';
            onComplete();
        }).catch(function() {
            m.container.style.visibility = '';
            onComplete();
        });
    } else {
        m.container.style.visibility = '';
        onComplete();
    }
}

window._applyReaderSettings = function(fontFamily, fontFilePath, fontSize, textAlign, lineHeight, paragraphSpacing, paddingVertical, paddingHorizontal, dualPage) {
    window._readerSettings = { fontFamily: fontFamily, fontFilePath: fontFilePath, fontSize: fontSize, textAlign: textAlign, lineHeight: lineHeight, paragraphSpacing: paragraphSpacing, paddingVertical: paddingVertical, paddingHorizontal: paddingHorizontal };
    _epub.dualPage = !!dualPage;
    // 위치 복원용 문자 비율 정보 + CFI를 저장한다 (최초 1회만).
    // display(cfi)는 긴 챕터에서 CSS column lazy rendering으로 실패하므로
    // 프로그레시브 스크롤로 컬럼 렌더링을 강제한 뒤 CFI로 정밀 보정한다.
    if (!_epub._settingsCharInfo && !_epub._resizeRestoring) {
        _epub._settingsCharInfo = _saveCharPosition();
        _epub._settingsCfi = window._currentCfi || _config.savedCfi || '';
        // init.js resize 핸들러의 중복 복원 방지
        _epub._preResizeCfi = undefined;
    }
    try {
        var viewer = document.getElementById('viewer');
        viewer.style.top = paddingVertical + 'px';
        viewer.style.left = paddingHorizontal + 'px';
        viewer.style.right = paddingHorizontal + 'px';
        viewer.style.bottom = (paddingVertical + _config.bottomInfoHeight) + 'px';
        var newW = window.innerWidth - paddingHorizontal * 2;
        var newH = window.innerHeight - paddingVertical * 2 - _config.bottomInfoHeight;
        _epub._lastResizeW = newW;
        _epub._lastResizeH = newH;
        _epub.rendition.spread(_getSpreadMode(), 0);
        _epub.rendition.resize(newW, newH);
    } catch(e) {}
    try {
        _epub.rendition.getContents().forEach(function(c) { _injectReaderStyle(c.document); });
    } catch(e) {}
    // 위치 복원: init.js resize 핸들러와 동일한 디바운스 타이머를 공유하여
    // 설정 연속 변경 및 window resize와의 충돌을 방지한다.
    clearTimeout(_epub._resizeRestoreTimer);
    clearTimeout(_epub.rescanTimer);
    _epub._resizeRestoreTimer = setTimeout(function() {
        var charInfo = _epub._settingsCharInfo;
        var cfi = _epub._settingsCfi;
        if (charInfo && !_epub._resizeRestoring) {
            _epub._settingsCharInfo = undefined;
            _epub._settingsCfi = undefined;
            _epub._resizeRestoring = true;
            _restoreByCharRatio(charInfo, cfi, function() {
                _epub._resizeRestoring = false;
                try { Android.onSettingsApplyComplete(); } catch(e) {}
                if (_epub.locationsReady) {
                    clearTimeout(_epub.rescanTimer);
                    _epub.rescanTimer = setTimeout(function() { computeVisualPages(); }, 500);
                }
            });
        } else {
            _epub._settingsCharInfo = undefined;
            _epub._settingsCfi = undefined;
            try { Android.onSettingsApplyComplete(); } catch(e) {}
            if (_epub.locationsReady) {
                clearTimeout(_epub.rescanTimer);
                _epub.rescanTimer = setTimeout(function() { computeVisualPages(); }, 500);
            }
        }
    }, 500);
};
