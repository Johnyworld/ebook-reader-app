package com.rotein.ebookreader.reader

import com.rotein.ebookreader.ReaderSettings
import com.rotein.ebookreader.fontFamilyForJs

/** 하단 정보 영역을 위한 추가 여백 (px) */
internal const val BOTTOM_INFO_HEIGHT = 32

internal fun buildEpubJsHtml(opfPath: String, savedCfi: String, settings: ReaderSettings, fontFilePath: String = "") = """<!DOCTYPE html>
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
var _config = {
    opfPath: "$opfPath",
    savedCfi: "${savedCfi.replace("\"", "\\\"")}",
    dualPage: ${settings.dualPage},
    fontName: "${settings.fontName}",
    fontSize: ${settings.fontSize},
    textAlign: "${settings.textAlign.name}",
    lineHeight: ${settings.lineHeight},
    paragraphSpacing: ${settings.paragraphSpacing},
    paddingVertical: ${settings.paddingVertical},
    paddingHorizontal: ${settings.paddingHorizontal},
    fontFilePath: "$fontFilePath",
    fontFamilyForJs: "${fontFamilyForJs(settings.fontName)}",
    bottomInfoHeight: $BOTTOM_INFO_HEIGHT
};
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
