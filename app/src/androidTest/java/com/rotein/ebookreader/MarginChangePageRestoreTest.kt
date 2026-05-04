package com.rotein.ebookreader

import android.webkit.WebView
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val instrumentation = InstrumentationRegistry.getInstrumentation()

private fun createTestEpubForSettings(): String {
    val context = instrumentation.targetContext
    val outDir = File(context.getExternalFilesDir(null), "test_books")
    outDir.mkdirs()
    val outFile = File(outDir, "settings_test.epub")
    if (outFile.exists()) return outFile.absolutePath

    ZipOutputStream(outFile.outputStream()).use { zos ->
        val mimeBytes = "application/epub+zip".toByteArray()
        val mimeEntry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimeBytes.size.toLong()
            compressedSize = mimeBytes.size.toLong()
            crc = CRC32().apply { update(mimeBytes) }.value
        }
        zos.putNextEntry(mimeEntry)
        zos.write(mimeBytes)
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray())
        zos.closeEntry()

        val chapterCount = 4
        val manifestItems = (1..chapterCount).joinToString("\n") {
            "    <item id=\"ch$it\" href=\"chapter${it}.xhtml\" media-type=\"application/xhtml+xml\"/>"
        }
        val spineItems = (1..chapterCount).joinToString("\n") {
            "    <itemref idref=\"ch$it\"/>"
        }
        val tocEntries = (1..chapterCount).joinToString("\n") {
            "    <li><a href=\"chapter${it}.xhtml\">제${it}장</a></li>"
        }

        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">settings-test-epub-001</dc:identifier>
    <dc:title>설정 테스트 도서</dc:title>
    <dc:language>ko</dc:language>
    <dc:creator>테스트 저자</dc:creator>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
$manifestItems
  </manifest>
  <spine>
$spineItems
  </spine>
</package>""".toByteArray())
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Navigation</title></head>
<body>
<nav epub:type="toc" id="toc">
  <h1>목차</h1>
  <ol>
$tocEntries
  </ol>
</nav>
</body>
</html>""".toByteArray())
        zos.closeEntry()

        for (i in 1..chapterCount) {
            val paragraphs = (1..50).joinToString("\n") { j ->
                "<p>제${i}장의 ${j}번째 단락입니다. 이 텍스트는 설정 변경 테스트를 위한 더미 콘텐츠입니다. 충분한 양의 텍스트가 필요하므로 좀 더 길게 작성합니다. 설정이 변경되어도 현재 읽고 있는 위치가 유지되어야 합니다.</p>"
            }
            zos.putNextEntry(ZipEntry("OEBPS/chapter${i}.xhtml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>제${i}장</title></head>
<body>
<h1>제${i}장</h1>
$paragraphs
</body>
</html>""".toByteArray())
            zos.closeEntry()
        }
    }

    return outFile.absolutePath
}

private fun grantStoragePermission() {
    val packageName = instrumentation.targetContext.packageName
    instrumentation.uiAutomation
        .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
        .close()
}

private fun evalJs(webView: WebView, script: String): String {
    val latch = CountDownLatch(1)
    var result = ""
    instrumentation.runOnMainSync {
        webView.evaluateJavascript(script) { value ->
            result = value?.removeSurrounding("\"") ?: ""
            latch.countDown()
        }
    }
    latch.await(5, TimeUnit.SECONDS)
    return result
}

/** 현재 페이지에 보이는 첫 번째 텍스트 노드의 내용 50자를 추출하는 JS */
private const val JS_GET_VISIBLE_FIRST_TEXT = """
(function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return '';
        var body = iframe.contentDocument.body;
        if (!body) return '';
        var container = _epub.rendition.manager.container;
        var scrollLeft = container.scrollLeft;
        var delta = _epub.rendition.manager.layout.delta;
        var walker = iframe.contentDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
        var node;
        while ((node = walker.nextNode())) {
            var text = node.textContent;
            if (!text || text.trim().length === 0) continue;
            var range = iframe.contentDocument.createRange();
            range.selectNodeContents(node);
            var rect = range.getBoundingClientRect();
            if (rect.right > scrollLeft && rect.left < scrollLeft + delta && rect.height > 0) {
                return text.trim().substring(0, 50);
            }
        }
        return '';
    } catch(e) { return ''; }
})()
"""

/** 현재 페이지에 보이는 모든 텍스트를 추출하는 JS */
private const val JS_GET_VISIBLE_ALL_TEXT = """
(function() {
    try {
        var iframe = document.querySelector('iframe');
        if (!iframe || !iframe.contentDocument) return '';
        var body = iframe.contentDocument.body;
        if (!body) return '';
        var container = _epub.rendition.manager.container;
        var scrollLeft = container.scrollLeft;
        var delta = _epub.rendition.manager.layout.delta;
        var walker = iframe.contentDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
        var node;
        var texts = [];
        while ((node = walker.nextNode())) {
            var text = node.textContent;
            if (!text || text.trim().length === 0) continue;
            var range = iframe.contentDocument.createRange();
            range.selectNodeContents(node);
            var rect = range.getBoundingClientRect();
            if (rect.right > scrollLeft && rect.left < scrollLeft + delta && rect.height > 0) {
                texts.push(text.trim());
            }
        }
        return texts.join(' ');
    } catch(e) { return ''; }
})()
"""

/**
 * 설정 변경 후 페이지 위치 복원 테스트.
 *
 * 공통 시나리오:
 * 1. 도서를 열고 목차에서 3챕터로 이동
 * 2. 이전 페이지로 이동 (2챕터 마지막 부분)
 * 3. 현재 보이는 텍스트를 기록
 * 4. 설정 변경 (여백 또는 폰트 사이즈)
 * 5. 변경 전 텍스트가 변경 후 페이지 내에 존재하는지 검증
 */
@RunWith(AndroidJUnit4::class)
class SettingsChangePageRestoreTest {

    private val testFilePath: String

    init {
        grantStoragePermission()
        testFilePath = createTestEpubForSettings()
        BookCache.books = listOf(
            BookFile(
                name = "settings_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "설정 테스트 도서",
                    author = "테스트 저자",
                    language = null, publisher = null, publishedDate = null, description = null
                )
            )
        )
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() { BookCache.books = null }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapCenter() {
        composeTestRule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
            up()
        }
        Thread.sleep(500)
    }

    private fun tapPrevPage() {
        composeTestRule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * 0.15f, centerY))
            up()
        }
        Thread.sleep(500)
    }

    /**
     * 도서 진입 → 3챕터 이동 → 이전 페이지 → 텍스트 기록 → 설정 변경 → 텍스트 검증
     * @param settingLabel 검증 메시지에 표시할 설정 이름
     * @param applySettings 설정 변경을 수행하는 람다 (설정 바텀시트가 열린 상태에서 호출)
     */
    private fun runSettingsChangeTest(settingLabel: String, applySettings: () -> Unit) {
        val bookTitle = "설정 테스트 도서"

        // 1. 도서 진입
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText(bookTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(bookTitle).performClick()
        waitForTag("bookReaderScreen")
        waitForTagToDisappear("readerLoading")

        // 페이지 스캔 완료 대기 (tocButton 등장)
        var tocFound = false
        for (attempt in 1..15) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = composeTestRule.onAllNodesWithTag("tocButton").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                tocFound = true
                break
            }
            tapCenter()
            Thread.sleep(2000)
        }
        assertTrue("tocButton이 나타나지 않음 — 페이지 스캔 미완료", tocFound)

        // 2. 목차에서 3챕터로 이동
        composeTestRule.onNodeWithTag("tocButton").performClick()
        waitForTag("tocItem_2")
        composeTestRule.onNodeWithTag("tocItem_2").performClick()
        Thread.sleep(2000)

        // 3. 이전 페이지로 이동 (2챕터 마지막 부분으로)
        tapPrevPage()
        Thread.sleep(2000)

        // 4. 현재 텍스트 기록
        val activity = composeTestRule.activity as MainActivity
        val webView = activity.currentEpubWebView
        assertTrue("WebView를 찾을 수 없음", webView != null)

        val cfiBefore = evalJs(webView!!, "window._currentCfi || ''")
        assertTrue("설정 변경 전 CFI가 비어있음", cfiBefore.isNotEmpty())

        val textBefore = evalJs(webView, JS_GET_VISIBLE_FIRST_TEXT)
        assertTrue("변경 전 텍스트를 추출할 수 없음", textBefore.isNotEmpty())

        // 5. 설정 열기
        tapCenter()
        Thread.sleep(500)
        waitForTag("settingsButton")
        composeTestRule.onNodeWithTag("settingsButton").performClick()
        Thread.sleep(500)

        // 설정 변경 수행
        applySettings()

        // 설정 변경 후 복원 완료 대기:
        // settingsOverlay가 나타났다가 사라지면 onSettingsApplyComplete가 호출된 것
        try {
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithTag("settingsOverlay").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (_: Exception) { /* 오버레이가 너무 빨리 사라졌을 수 있음 */ }
        waitForTagToDisappear("settingsOverlay", 10000)
        // 프로그레시브 스크롤 완료 후 렌더링 안정화 대기
        Thread.sleep(1000)

        // 6. 변경 전 텍스트가 현재 페이지에 존재하는지 검증
        val visibleTextAfter = evalJs(webView, JS_GET_VISIBLE_ALL_TEXT)

        assertTrue(
            "$settingLabel 변경 후 페이지에서 변경 전 텍스트를 찾을 수 없음.\n" +
                "변경 전 텍스트: \"$textBefore\"\n" +
                "변경 후 페이지 텍스트: \"${visibleTextAfter.take(200)}\"",
            visibleTextAfter.contains(textBefore)
        )
    }

    /** WebView에서 현재 설정값을 읽어온다 */
    private fun getSettingValue(webView: WebView, field: String): Double {
        val raw = evalJs(webView, "window._readerSettings ? window._readerSettings.$field : ''")
        return raw.toDoubleOrNull() ?: 0.0
    }

    @Test
    fun marginChangePreservesReadingPosition() {
        runSettingsChangeTest("여백") {
            // "Margin" 탭 클릭
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("Margin").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Margin").performClick()
            Thread.sleep(500)

            val webView = (composeTestRule.activity as MainActivity).currentEpubWebView!!
            val current = getSettingValue(webView, "paddingHorizontal")
            // 30 초과면 감소, 아니면 증가
            val tag = if (current > 30) "paddingHorizontalDecrement" else "paddingHorizontalIncrement"
            waitForTag(tag)
            repeat(3) {
                composeTestRule.onNodeWithTag(tag).performClick()
                Thread.sleep(300)
            }
        }
    }

    @Test
    fun fontSizeChangePreservesReadingPosition() {
        runSettingsChangeTest("폰트 사이즈") {
            // "Text" 탭 (기본 선택됨)
            val webView = (composeTestRule.activity as MainActivity).currentEpubWebView!!
            val current = getSettingValue(webView, "fontSize")
            // 24 초과면 감소, 아니면 증가
            val tag = if (current > 24) "fontSizeDecrement" else "fontSizeIncrement"
            waitForTag(tag)
            repeat(3) {
                composeTestRule.onNodeWithTag(tag).performClick()
                Thread.sleep(300)
            }
        }
    }

    @Test
    fun lineHeightChangePreservesReadingPosition() {
        runSettingsChangeTest("줄간격") {
            // "Margin" 탭 클릭 (줄간격은 Margin 탭에 있음)
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("Margin").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Margin").performClick()
            Thread.sleep(500)

            val webView = (composeTestRule.activity as MainActivity).currentEpubWebView!!
            val current = getSettingValue(webView, "lineHeight")
            // 2.0 초과면 감소, 아니면 증가
            val tag = if (current > 2.0) "lineHeightDecrement" else "lineHeightIncrement"
            waitForTag(tag)
            repeat(3) {
                composeTestRule.onNodeWithTag(tag).performClick()
                Thread.sleep(300)
            }
        }
    }
}
