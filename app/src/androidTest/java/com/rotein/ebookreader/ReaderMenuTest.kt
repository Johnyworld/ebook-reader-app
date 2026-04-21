package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
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
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val instrumentation = InstrumentationRegistry.getInstrumentation()

private fun createTestEpubForMenu(): String {
    val context = instrumentation.targetContext
    val outDir = File(context.getExternalFilesDir(null), "test_books")
    outDir.mkdirs()
    val outFile = File(outDir, "menu_test.epub")
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

        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">menu-test-epub-001</dc:identifier>
    <dc:title>메뉴 테스트 도서</dc:title>
    <dc:language>ko</dc:language>
    <dc:creator>테스트 저자</dc:creator>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
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
    <li><a href="chapter1.xhtml">제1장</a></li>
    <li><a href="chapter2.xhtml">제2장</a></li>
  </ol>
</nav>
</body>
</html>""".toByteArray())
        zos.closeEntry()

        for (i in 1..2) {
            val paragraphs = (1..30).joinToString("\n") { j ->
                "<p>제${i}장의 ${j}번째 단락입니다. 이 텍스트는 페이지를 채우기 위한 더미 콘텐츠입니다. 충분한 양의 텍스트가 필요합니다.</p>"
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

private fun grantStoragePermissionForMenu() {
    val packageName = instrumentation.targetContext.packageName
    instrumentation.uiAutomation
        .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
        .close()
}

private fun str(resId: Int): String =
    instrumentation.targetContext.getString(resId)

@RunWith(AndroidJUnit4::class)
class ReaderMenuOpenCloseTest {

    private val testFilePath: String

    init {
        grantStoragePermissionForMenu()
        testFilePath = createTestEpubForMenu()
        BookCache.books = listOf(
            BookFile(
                name = "menu_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "메뉴 테스트 도서",
                    author = "테스트 저자",
                    language = null, publisher = null, publishedDate = null, description = null
                )
            )
        )
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() { BookCache.books = null }

    private fun enterReader() {
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("메뉴 테스트 도서").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("메뉴 테스트 도서").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("bookReaderScreen").fetchSemanticsNodes().isNotEmpty()
        }
        // 콘텐츠 로딩 대기
        rule.waitUntil(15000) {
            rule.onAllNodesWithTag("readerLoading").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapCenter() {
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
            up()
        }
        Thread.sleep(500)
    }

    private fun waitForMenuWithRetry(): Boolean {
        for (attempt in 1..10) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = rule.onAllNodesWithTag("tocButton").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return true
            tapCenter()
            Thread.sleep(1000)
        }
        return false
    }

    /** 7.1.1 본문 가운데 영역을 탭 → 읽기 메뉴가 표시 */
    @Test
    fun centerTapOpensMenu() {
        enterReader()
        val found = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", found)
        rule.onNodeWithTag("tocButton").assertIsDisplayed()
    }

    /** 7.1.2 메뉴가 열린 상태에서 다시 가운데를 탭 → 메뉴가 닫힘 */
    @Test
    fun centerTapClosesMenu() {
        enterReader()
        val found = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", found)

        // 메뉴 닫기
        tapCenter()
        Thread.sleep(1000)

        // tocButton이 사라졌는지 확인
        val nodesAfter = rule.onAllNodesWithTag("tocButton").fetchSemanticsNodes()
        assertTrue("메뉴가 닫히지 않음", nodesAfter.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class ReaderProgressDisplayTest {

    private val testFilePath: String

    init {
        grantStoragePermissionForMenu()
        testFilePath = createTestEpubForMenu()
        BookCache.books = listOf(
            BookFile(
                name = "menu_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "메뉴 테스트 도서",
                    author = "테스트 저자",
                    language = null, publisher = null, publishedDate = null, description = null
                )
            )
        )
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() { BookCache.books = null }

    private fun enterReader() {
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("메뉴 테스트 도서").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("메뉴 테스트 도서").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("bookReaderScreen").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(15000) {
            rule.onAllNodesWithTag("readerLoading").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapCenter() {
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
            up()
        }
        Thread.sleep(500)
    }

    /** 7.2.1 읽기 메뉴를 열어 진행률 확인 → "N% 읽음", "현재 페이지 / 전체 페이지" 표시 */
    @Test
    fun menuShowsProgressAndPageInfo() {
        enterReader()

        // 메뉴 열기 (페이지 스캔 완료까지 대기)
        var found = false
        for (attempt in 1..15) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = rule.onAllNodesWithTag("pageInfoText").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                found = true
                break
            }
            tapCenter()
            Thread.sleep(2000)
        }
        assertTrue("메뉴에 페이지 정보가 표시되지 않음", found)

        // 페이지 정보 확인
        rule.onNodeWithTag("pageInfoText").assertIsDisplayed()

        // "N% 읽음" 텍스트 확인 (진행률 표시)
        val readPercentageNodes = rule.onAllNodesWithText(
            str(R.string.read_percentage).replace("%d%%", ""),
            substring = true
        ).fetchSemanticsNodes()
        assertTrue("진행률 텍스트가 표시되지 않음", readPercentageNodes.isNotEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class ReaderNextPageTest {

    private val testFilePath: String

    init {
        grantStoragePermissionForMenu()
        testFilePath = createTestEpubForMenu()
        BookCache.books = listOf(
            BookFile(
                name = "menu_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "메뉴 테스트 도서",
                    author = "테스트 저자",
                    language = null, publisher = null, publishedDate = null, description = null
                )
            )
        )
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() { BookCache.books = null }

    private fun enterReader() {
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("메뉴 테스트 도서").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("메뉴 테스트 도서").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("bookReaderScreen").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(15000) {
            rule.onAllNodesWithTag("readerLoading").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapCenter() {
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
            up()
        }
        Thread.sleep(500)
    }

    private fun tapNextPage() {
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * 0.85f, centerY))
            up()
        }
        Thread.sleep(500)
    }

    private fun getCurrentPage(): Int {
        val node = rule.onNodeWithTag("pageInfoText").fetchSemanticsNode()
        val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .firstOrNull()?.text ?: ""
        return text.split("/")[0].trim().toIntOrNull() ?: 0
    }

    /** 3.2.1 화면 오른쪽 영역을 탭 (좌우 모드) → 다음 페이지로 넘어감 */
    @Test
    fun rightTapAdvancesToNextPage() {
        enterReader()

        // 메뉴 열어서 페이지 스캔 완료까지 대기
        var menuFound = false
        for (attempt in 1..15) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = rule.onAllNodesWithTag("tocButton").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                menuFound = true
                break
            }
            tapCenter()
            Thread.sleep(2000)
        }
        assertTrue("메뉴 열기 실패", menuFound)

        // 현재 페이지 확인
        val pageA = getCurrentPage()

        // 메뉴 닫기
        tapCenter()
        Thread.sleep(500)

        // 다음 페이지
        tapNextPage()
        Thread.sleep(2000)

        // 메뉴 열어서 페이지 확인
        tapCenter()
        rule.waitUntil(5000) {
            rule.onAllNodesWithTag("pageInfoText").fetchSemanticsNodes().isNotEmpty()
        }

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

        assertTrue(
            "다음 페이지로 이동하지 않음: pageA=$pageA, pageB=$pageB",
            pageB == pageA + 1
        )
    }
}
