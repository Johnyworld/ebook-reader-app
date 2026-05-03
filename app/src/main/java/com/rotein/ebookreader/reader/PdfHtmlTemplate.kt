package com.rotein.ebookreader.reader

import org.json.JSONObject

internal fun buildPdfHtml(pdfPath: String, startPage: Int): String {
    // JSONObject로 빌드하여 문자열 인젝션을 구조적으로 방지
    val config = JSONObject().apply {
        put("pdfPath", pdfPath)
        put("startPage", startPage)
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
#pdf-container {
    width: 100%; height: 100%;
    overflow: hidden;
    position: relative;
}
#pdf-wrapper { position: absolute; top: 0; left: 0; transform-origin: 0 0; }
#pdf-canvas { display: block; }
.search-hl-overlay {
    position: absolute; pointer-events: none;
    background: #FFFF00; opacity: 0.4;
}
</style>
</head>
<body>
<div id="bookmark-ribbon" style="display:none;position:absolute;top:-12px;right:16px;width:16px;height:40px;z-index:10;pointer-events:none;">
<svg width="16" height="40" viewBox="0 0 16 40" xmlns="http://www.w3.org/2000/svg">
<path d="M0,0 L16,0 L16,40 L8,30 L0,40 Z" fill="#ccc"/>
</svg>
</div>
<div id="pdf-container"></div>
<script>
var _pdfConfig = JSON.parse('$safeJson');
</script>
<script src="pdfjs/pdf.min.js"></script>
<script src="pdf/js/init.js"></script>
<script src="pdf/js/zoom.js"></script>
<script src="pdf/js/navigation.js"></script>
<script src="pdf/js/search.js"></script>
<script src="pdf/js/toc.js"></script>
</body>
</html>"""
}
