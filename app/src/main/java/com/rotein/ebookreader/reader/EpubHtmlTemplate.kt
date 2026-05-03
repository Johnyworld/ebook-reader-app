package com.rotein.ebookreader.reader

import com.rotein.ebookreader.ReaderSettings
import com.rotein.ebookreader.fontFamilyForJs
import org.json.JSONObject

/** 하단 정보 영역을 위한 추가 여백 (px) */
internal const val BOTTOM_INFO_HEIGHT = 32

internal fun buildEpubJsHtml(opfPath: String, savedCfi: String, settings: ReaderSettings, fontFilePath: String = ""): String {
    // JSONObject로 빌드하여 문자열 인젝션을 구조적으로 방지
    val config = JSONObject().apply {
        put("opfPath", opfPath)
        put("savedCfi", savedCfi)
        put("dualPage", settings.dualPage)
        put("fontName", settings.fontName)
        put("fontSize", settings.fontSize)
        put("textAlign", settings.textAlign.name)
        put("lineHeight", settings.lineHeight)
        put("paragraphSpacing", settings.paragraphSpacing)
        put("paddingVertical", settings.paddingVertical)
        put("paddingHorizontal", settings.paddingHorizontal)
        put("fontFilePath", fontFilePath)
        put("fontFamilyForJs", fontFamilyForJs(settings.fontName))
        put("bottomInfoHeight", BOTTOM_INFO_HEIGHT)
    }
    // JSON을 JS 작은따옴표 문자열 안에 안전하게 삽입
    // JSONObject.toString()이 이미 JSON 표준 이스케이프를 수행하므로,
    // 작은따옴표와 </script> 탈출만 추가 처리
    val safeJson = config.toString()
        .replace("'", "\\'")     // 작은따옴표 이스케이프
        .replace("</", "<\\/")   // </script> 탈출 방지

    return """<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'/>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 100%; height: 100%; overflow: hidden; background: #fff; }
#viewer { position: absolute; top: ${settings.paddingVertical}px; left: ${settings.paddingHorizontal}px; right: ${settings.paddingHorizontal}px; bottom: ${settings.paddingVertical + BOTTOM_INFO_HEIGHT}px; }
.epub-view svg { mix-blend-mode: multiply; }
</style>
</head>
<body>
<div id="bookmark-ribbon" style="display:none;position:absolute;top:-12px;right:16px;width:16px;height:40px;z-index:0;pointer-events:none;">
<svg width="16" height="40" viewBox="0 0 16 40" xmlns="http://www.w3.org/2000/svg">
<path d="M0,0 L16,0 L16,40 L8,30 L0,40 Z" fill="#ccc"/>
</svg>
</div>
<div id="viewer"></div>
<script>
var _config = JSON.parse('$safeJson');
</script>
<script src="epub.min.js"></script>
<script src="js/init.js"></script>
<script src="js/settings.js"></script>
<script src="js/location.js"></script>
<script src="js/page-scan.js"></script>
<script src="js/selection.js"></script>
<script src="js/search.js"></script>
<script src="js/annotation.js"></script>
<script src="js/navigation.js"></script>
</body>
</html>"""
}
