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
    display: flex; align-items: center; justify-content: center;
    overflow: hidden;
}
#pdf-canvas { display: block; }
</style>
</head>
<body>
<div id="pdf-container"></div>
<script>
var _pdfConfig = {
    pdfPath: "$pdfPath",
    startPage: $startPage
};
</script>
<script src="pdfjs/pdf.min.js"></script>
<script src="pdf/js/init.js"></script>
<script src="pdf/js/navigation.js"></script>
<script src="pdf/js/search.js"></script>
<script src="pdf/js/toc.js"></script>
</body>
</html>"""
