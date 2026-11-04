// settings.js — reader style settings, CSS injection, applyReaderSettings
window._readerSettings = {
    fontFamily: _config.fontFamilyForJs,
    fontFilePath: _config.fontFilePath,
    fontSize: _config.fontSize,
    textAlign: _config.textAlign.toLowerCase(),
    lineHeight: _config.lineHeight,
    paragraphSpacing: _config.paragraphSpacing,
    paddingVertical: _config.paddingVertical,
    paddingHorizontal: _config.paddingHorizontal,
    fontWeightLevel: _config.fontWeightLevel,
    fontWeightRange: _config.fontWeightRange
};

function _buildReaderCss(s) {
    // font-size는 body에만 설정하여 EPUB 원본 CSS(드롭캡, ::first-letter 등)가 동작하도록 한다.
    // line-height는 EPUB CSS가 개별 요소에 지정하면 상속이 무시되므로 모든 요소에 !important로 적용한다.
    var allElements = 'html, body, p, div, span, li, td, blockquote, h1, h2, h3, h4, h5, h6';
    var bodyRule = 'body { font-size: ' + s.fontSize + 'px !important; }';
    var lineHeightRule = allElements + ' { line-height: ' + s.lineHeight + ' !important; }';
    var paragraphRule = s.paragraphSpacing > 0 ? 'p { margin-bottom: ' + s.paragraphSpacing + 'px !important; }' : '';
    // 이미지가 컬럼 너비를 초과하여 텍스트와 겹치는 것을 방지한다.
    var imgRule = 'img, svg, image { max-width: 100% !important; height: auto !important; break-inside: avoid; box-sizing: border-box; }';
    // 글자를 줄여도 넘치는 표(_fitTables가 _tw 클래스를 붙임)는 셀 안 줄바꿈을 허용해 컬럼 안에 맞춘다.
    var tableRule = 'table._tw { max-width: 100% !important; } table._tw td, table._tw th { overflow-wrap: anywhere !important; }';
    // 글꼴 두께: 가변 글꼴(wght 범위 있음)은 실제 font-weight, 그 외에는 text-stroke로 획을 덧그려 굵게 한다.
    // font-weight는 font-family와 같이 본문 요소 전체에 !important로 적용한다.
    // 인디자인 EPUB 등은 문단 클래스마다 font-weight: normal을 지정하므로 상속만으로는 적용되지 않는다.
    // 소제목(h1~h6)과 b/strong은 본문보다 300 굵게 따로 지정해 굵은 글씨 구분을 유지한다.
    // (클래스로 굵게 만든 문단·span은 본문과 같은 굵기가 된다)
    // text-stroke는 상속되고 EPUB이 지정하지 않는 속성이므로 html, body에만 지정하면 되며 레이아웃(글자 폭)은 바뀌지 않는다.
    var weightRule = '';
    if (s.fontWeightLevel) {
        if (s.fontWeightRange) {
            var weight = 400 + 100 * s.fontWeightLevel;
            var boldWeight = Math.min(900, weight + 300);
            weightRule = 'html, body, p, div, span, li, td, blockquote { font-weight: ' + weight + ' !important; }' +
                // 하위 선택자(명시도 0,0,2)로 <h1><span>, <b><span> 같은 구조의 span(0,0,1)보다 우선 적용한다
                'h1, h2, h3, h4, h5, h6, b, strong, h1 *, h2 *, h3 *, h4 *, h5 *, h6 *, b *, strong * { font-weight: ' + boldWeight + ' !important; }';
        } else {
            weightRule = 'html, body { -webkit-text-stroke: ' + (0.02 * s.fontWeightLevel).toFixed(2) + 'em currentColor !important; }';
        }
    }
    if (s.fontFamily === 'epub_original') {
        return bodyRule + lineHeightRule + paragraphRule + imgRule + tableRule + weightRule;
    }
    var fontFaceDecl = '';
    var ff;
    if (s.fontFilePath) {
        // CSS url() 인젝션 방지를 위해 특수문자 이스케이프
        var safePath = s.fontFilePath.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\)/g, '\\)');
        // 가변 글꼴은 font-weight 범위를 선언해야 wght 축이 font-weight 값을 따라간다.
        // 기본(0)일 때는 선언하지 않아 기존 렌더링·페이지 수 캐시와 동일하게 유지한다.
        var weightDesc = (s.fontWeightRange && s.fontWeightLevel) ? ' font-weight: ' + s.fontWeightRange + ';' : '';
        fontFaceDecl = '@font-face { font-family: "_imported_"; src: url("' + safePath + '");' + weightDesc + ' }';
        ff = "'_imported_', sans-serif";
    } else {
        ff = s.fontFamily ? ("'" + s.fontFamily + "', sans-serif") : 'sans-serif';
    }
    // 폰트 패밀리는 모든 요소에 적용한다.
    return fontFaceDecl + allElements + ' {font-family: ' + ff + ' !important; }' + bodyRule + lineHeightRule + paragraphRule + imgRule + tableRule + weightRule;
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
    _fitTables(doc);
    // 가져온 글꼴은 비동기로 로드되어 글자 폭이 바뀌므로 로드 완료 후 다시 맞춘다.
    try { doc.fonts.ready.then(function() { _fitTables(doc); }); } catch(e) {}
}

// 표 글자를 줄일 수 있는 최소 비율 (원래 글자 크기 대비)
var _TABLE_MIN_FONT_RATIO = 0.6;

// 컬럼(페이지) 너비보다 넓은 표의 글자 크기를 줄여 컬럼 안에 맞춘다.
// CSS columns 레이아웃에서는 컬럼보다 넓은 요소가 잘리지 않고 다음 컬럼(다음 페이지)으로 넘쳐 보이기 때문이다.
// 표는 셀 안의 긴 단어·숫자 때문에 최소 폭 아래로 줄어들지 않으므로 글자 크기를 줄여 최소 폭 자체를 줄인다.
// 최소 비율까지 줄여도 넘치면 _tw 클래스로 셀 안 줄바꿈을 허용한다.
// 글자 크기를 줄여도 폭이 줄지 않는 표(고정 폭 표, 셀이 px 글자 크기를 직접 지정한 표)는 글자를 되돌리고 바로 줄바꿈만 허용한다.
// 글자 크기·여백·화면 크기가 바뀌면 다시 호출되어 처음부터 다시 계산한다.
function _fitTables(doc) {
    if (!doc || !doc.body) return;
    var tables = doc.body.querySelectorAll('table');
    for (var i = 0; i < tables.length; i++) {
        // 표 하나에서 오류가 나도 나머지 표는 계속 맞춘다.
        try { _fitTable(tables[i]); } catch(e) {}
    }
}

// 소수점 폭 반올림 오차로 딱 맞는 표(width: 100% 등)를 넘친다고 보지 않도록 두는 허용 오차(px)
var _TABLE_FIT_TOLERANCE = 1;

function _fitTable(t) {
    // 중첩된 표는 바깥 표를 줄이면 함께 줄어들므로 건너뛴다.
    if (t.parentElement && t.parentElement.closest('table')) return;
    // 이전 계산 결과를 지우고 원래 크기 기준으로 다시 측정한다.
    // (클래스가 없을 때 class 속성을 다시 쓰지 않도록 확인 후 지운다)
    t.style.removeProperty('font-size');
    if (t.classList.contains('_tw')) t.classList.remove('_tw');
    var limit = _availableWidthBefore(t) + _TABLE_FIT_TOLERANCE;
    if (limit <= _TABLE_FIT_TOLERANCE) return;
    var width = _fragmentWidth(t);
    if (width <= limit) return;
    var baseSize = parseFloat(t.ownerDocument.defaultView.getComputedStyle(t).fontSize);
    if (!baseSize) return;
    var minSize = baseSize * _TABLE_MIN_FONT_RATIO;
    var size = baseSize;
    // 테두리처럼 글자 크기에 비례하지 않는 폭이 있어 한 번에 맞지 않을 수 있으므로 몇 번 반복한다.
    for (var n = 0; n < 4 && width > limit && size > minSize; n++) {
        var prevWidth = width;
        size = Math.max(minSize, Math.floor(size * limit / width * 10) / 10);
        t.style.setProperty('font-size', size + 'px', 'important');
        width = _fragmentWidth(t);
        // 글자를 줄여도 폭이 그대로면 글자 크기로는 맞출 수 없는 표이므로 원래 크기로 되돌린다.
        if (width > prevWidth - 0.5) {
            t.style.removeProperty('font-size');
            break;
        }
    }
    if (width > limit) t.classList.add('_tw');
}

// 요소가 그려진 폭. 여러 컬럼에 나뉘어 그려진 표는 offsetWidth가 컬럼 폭보다 크게 나오므로
// 컬럼마다 하나씩 생기는 조각(getClientRects)의 폭 중 가장 큰 값을 쓴다.
function _fragmentWidth(el) {
    var rects = el.getClientRects();
    var w = 0;
    for (var i = 0; i < rects.length; i++) w = Math.max(w, rects[i].width);
    return w;
}

// 표가 놓인 자리에서 쓸 수 있는 너비(컬럼 너비에서 부모 여백을 뺀 값)를 잰다.
// 표 바로 앞에 빈 블록을 잠시 넣어 그 너비를 재는 방식이라 부모 요소의 padding·margin도 반영된다.
function _availableWidthBefore(el) {
    var probe = el.ownerDocument.createElement('div');
    probe.style.cssText = 'display:block;height:0;margin:0;padding:0;border:0;';
    el.parentNode.insertBefore(probe, el);
    // offsetWidth는 정수로 반올림되므로 소수점까지 나오는 getBoundingClientRect로 잰다.
    var w = probe.getBoundingClientRect().width;
    el.parentNode.removeChild(probe);
    return w;
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

window._applyReaderSettings = function(fontFamily, fontFilePath, fontSize, textAlign, lineHeight, paragraphSpacing, paddingVertical, paddingHorizontal, dualPage, fontWeightLevel, fontWeightRange) {
    window._readerSettings = { fontFamily: fontFamily, fontFilePath: fontFilePath, fontSize: fontSize, textAlign: textAlign, lineHeight: lineHeight, paragraphSpacing: paragraphSpacing, paddingVertical: paddingVertical, paddingHorizontal: paddingHorizontal, fontWeightLevel: fontWeightLevel, fontWeightRange: fontWeightRange };
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
