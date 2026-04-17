package com.rotein.ebookreader.reader

internal fun buildPdfHtml(pdfPath: String, startPage: Int) = """<!DOCTYPE html>
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
var _pdfConfig = {
    pdfPath: "$pdfPath",
    startPage: $startPage
};
</script>
<script src="pdfjs/pdf.min.js"></script>
<script src="pdf/js/init.js"></script>
<script src="pdf/js/zoom.js"></script>
<script src="pdf/js/navigation.js"></script>
<script src="pdf/js/search.js"></script>
<script src="pdf/js/toc.js"></script>
</body>
</html>"""
