package com.rotein.ebookreader

import androidx.compose.ui.semantics.SemanticsProperties
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val instrumentation = InstrumentationRegistry.getInstrumentation()

private fun createTestEpub(): String {
    val context = instrumentation.targetContext
    val outDir = File(context.getExternalFilesDir(null), "test_books")
    outDir.mkdirs()
    val outFile = File(outDir, "test.epub")
    if (outFile.exists()) return outFile.absolutePath

    ZipOutputStream(outFile.outputStream()).use { zos ->
        // mimetype must be first entry, stored (no compression)
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

        // container.xml
        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray())
        zos.closeEntry()

        // content.opf with nav + 4 chapters + spine
        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">test-epub-001</dc:identifier>
    <dc:title>테스트 도서</dc:title>
    <dc:language>ko</dc:language>
    <dc:creator>테스트 저자</dc:creator>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch4" href="chapter4.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
    <itemref idref="ch3"/>
    <itemref idref="ch4"/>
  </spine>
</package>""".toByteArray())
        zos.closeEntry()

        // nav.xhtml (EPUB 3 TOC)
        zos.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Navigation</title></head>
<body>
<nav epub:type="toc" id="toc">
  <h1>목차</h1>
  <ol>
    <li><a href="chapter1.xhtml">제1장 시작</a></li>
    <li><a href="chapter2.xhtml">제2장 전개</a></li>
    <li><a href="chapter3.xhtml">제3장 절정</a></li>
    <li><a href="chapter4.xhtml">제4장 결말</a></li>
  </ol>
</nav>
</body>
</html>""".toByteArray())
        zos.closeEntry()

        // 4 chapters with enough text to fill multiple pages
        val chapters = listOf("제1장 시작", "제2장 전개", "제3장 절정", "제4장 결말")
        for ((i, title) in chapters.withIndex()) {
            val paragraphs = (1..30).joinToString("\n") { j ->
                "<p>이것은 ${title}의 ${j}번째 단락입니다. 테스트를 위해 충분한 길이의 텍스트를 포함합니다. 이 단락은 페이지를 채우기 위한 더미 콘텐츠입니다. 각 챕터는 여러 페이지에 걸쳐 표시되어야 하므로 충분한 양의 텍스트가 필요합니다.</p>"
            }
            zos.putNextEntry(ZipEntry("OEBPS/chapter${i + 1}.xhtml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title></head>
<body>
<h1>$title</h1>
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

private fun runTocNavigationTest(
    composeTestRule: AndroidComposeTestRule<*, *>,
    bookTitle: String
) {
    fun waitForTag(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    fun tapCenter() {
        composeTestRule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
            up()
        }
        Thread.sleep(500)
    }

    fun tapPrevPage() {
        composeTestRule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * 0.15f, centerY))
            up()
        }
        Thread.sleep(500)
    }

    fun getCurrentPage(): Int {
        val node = composeTestRule.onNodeWithTag("pageInfoText").fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].firstOrNull()?.text ?: ""
        return text.split("/")[0].trim().toInt()
    }

    // Enter reader
    composeTestRule.waitUntil(10000) {
        composeTestRule.onAllNodesWithText(bookTitle).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText(bookTitle).performClick()
    waitForTag("bookReaderScreen")

    // Wait for content to load
    waitForTagToDisappear("readerLoading")

    // Open menu and wait for page scanning to complete (tocButton appears after scanning)
    var tocFound = false
    for (attempt in 1..15) {
        tapCenter()
        Thread.sleep(1000)
        val nodes = composeTestRule.onAllNodesWithTag("tocButton").fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            tocFound = true
            break
        }
        // Menu might be open but scanning — close it and wait
        tapCenter()
        Thread.sleep(2000)
    }
    org.junit.Assert.assertTrue("tocButton not found after retries — page scanning may not have completed", tocFound)

    // Open TOC
    composeTestRule.onNodeWithTag("tocButton").performClick()

    // Tap 3rd chapter
    waitForTag("tocItem_2")
    composeTestRule.onNodeWithTag("tocItem_2").performClick()
    Thread.sleep(2000)

    // Open menu → read page A
    tapCenter()
    waitForTag("pageInfoText")
    val pageA = getCurrentPage()

    // Close menu
    tapCenter()
    Thread.sleep(500)

    // Previous page
    tapPrevPage()
    Thread.sleep(2000)

    // Open menu → read page B (wait until page info updates to non-zero)
    tapCenter()
    waitForTag("pageInfoText")
    // Wait for page info to stabilize (non-zero and different from pageA)
    var pageB = 0
    for (i in 1..10) {
        try {
            val current = getCurrentPage()
            if (current > 0 && current != pageA) {
                pageB = current
                break
            }
        } catch (_: Exception) { }
        Thread.sleep(500)
    }
    if (pageB == 0) {
        // One more attempt — reopen menu
        tapCenter()
        Thread.sleep(500)
        tapCenter()
        Thread.sleep(1000)
        pageB = getCurrentPage()
    }

    // Assert B + 1 == A
    org.junit.Assert.assertTrue(
        "Prev page failed: pageB($pageB) + 1 != pageA($pageA)",
        pageB + 1 == pageA
    )
}

@RunWith(AndroidJUnit4::class)
class EpubViewerNavigationTest {

    private val testFilePath: String

    init {
        grantStoragePermission()
        testFilePath = createTestEpub()
        BookCache.books = listOf(
            BookFile(
                name = "test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "테스트 도서",
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

    @Test
    fun epubTocNavigationAndPrevPage() {
        runTocNavigationTest(composeTestRule, "테스트 도서")
    }

    @Test
    fun tocNavigateToPreviousChapterShowsFirstPage() {
        val bookTitle = "테스트 도서"

        fun waitForTag(tag: String, timeoutMillis: Long = 10000) {
            composeTestRule.waitUntil(timeoutMillis) {
                composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 10000) {
            composeTestRule.waitUntil(timeoutMillis) {
                composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
            }
        }

        fun tapCenter() {
            composeTestRule.onNodeWithTag("bookReaderScreen").performTouchInput {
                down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
                up()
            }
            Thread.sleep(500)
        }

        fun getCurrentPage(): Int {
            val node = composeTestRule.onNodeWithTag("pageInfoText").fetchSemanticsNode()
            val text = node.config[SemanticsProperties.Text].firstOrNull()?.text ?: ""
            return text.split("/")[0].trim().toInt()
        }

        // Enter reader
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText(bookTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(bookTitle).performClick()
        waitForTag("bookReaderScreen")
        waitForTagToDisappear("readerLoading")

        // Wait for TOC button (page scanning complete)
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
        org.junit.Assert.assertTrue("tocButton not found", tocFound)

        // Navigate to chapter 3 via TOC
        composeTestRule.onNodeWithTag("tocButton").performClick()
        waitForTag("tocItem_2")
        composeTestRule.onNodeWithTag("tocItem_2").performClick()
        Thread.sleep(2000)

        // Record page after navigating to chapter 3
        tapCenter()
        waitForTag("pageInfoText")
        val pageAtChapter3 = getCurrentPage()

        // Now navigate back to chapter 1 via TOC (earlier chapter)
        waitForTag("tocButton")
        composeTestRule.onNodeWithTag("tocButton").performClick()
        waitForTag("tocItem_0")
        composeTestRule.onNodeWithTag("tocItem_0").performClick()
        Thread.sleep(2000)

        // Read page — should be first page of chapter 1
        tapCenter()
        waitForTag("pageInfoText")
        val pageAtChapter1 = getCurrentPage()

        // Chapter 1 first page should be page 1
        org.junit.Assert.assertEquals(
            "TOC에서 이전 챕터 선택 시 첫 페이지(1)로 이동해야 하지만 $pageAtChapter1 페이지로 이동함",
            1,
            pageAtChapter1
        )

        // Also verify we actually moved (chapter 3 page should be different from page 1)
        org.junit.Assert.assertNotEquals(
            "챕터 3($pageAtChapter3)과 챕터 1($pageAtChapter1)의 페이지가 달라야 함",
            pageAtChapter3,
            pageAtChapter1
        )
    }
}

