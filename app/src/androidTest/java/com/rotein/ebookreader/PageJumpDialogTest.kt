package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

private fun createTestEpubForPageJump(): String {
    val context = instrumentation.targetContext
    val outDir = File(context.getExternalFilesDir(null), "test_books")
    outDir.mkdirs()
    val outFile = File(outDir, "page_jump_test.epub")
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

        // 챕터 4개, 각 챕터에 단락 50개 → 충분한 페이지 수 확보
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
    <dc:identifier id="uid">page-jump-test-epub-001</dc:identifier>
    <dc:title>페이지 이동 테스트 도서</dc:title>
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
                "<p>제${i}장의 ${j}번째 단락입니다. 이 텍스트는 페이지를 채우기 위한 더미 콘텐츠입니다. 충분한 양의 텍스트가 필요하므로 좀 더 길게 작성합니다. 페이지 이동 테스트를 위해 여러 페이지가 필요합니다.</p>"
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

private fun str(resId: Int): String =
    instrumentation.targetContext.getString(resId)

/**
 * 페이지 이동 다이얼로그 열기/닫기 테스트.
 * 바텀시트의 페이지 정보 텍스트 클릭 시 다이얼로그가 표시되고, 취소 시 닫힌다.
 */
@RunWith(AndroidJUnit4::class)
class PageJumpDialogOpenCloseTest {

    private val testFilePath: String

    init {
        grantStoragePermission()
        testFilePath = createTestEpubForPageJump()
        BookCache.books = listOf(
            BookFile(
                name = "page_jump_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "페이지 이동 테스트 도서",
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
            rule.onAllNodesWithText("페이지 이동 테스트 도서").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("페이지 이동 테스트 도서").performClick()
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

    private fun waitForMenuWithRetry(): Boolean {
        for (attempt in 1..15) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = rule.onAllNodesWithTag("pageInfoText").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return true
            tapCenter()
            Thread.sleep(2000)
        }
        return false
    }

    /** 페이지 정보 텍스트 클릭 → 페이지 이동 다이얼로그 표시 */
    @Test
    fun pageInfoTextClickOpensDialog() {
        enterReader()
        val menuFound = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", menuFound)

        // 페이지 정보 텍스트 클릭
        rule.onNodeWithTag("pageInfoText").performClick()
        Thread.sleep(500)

        // 다이얼로그 입력 필드 표시 확인
        rule.onNodeWithTag("pageJumpInput").assertIsDisplayed()
        // 이동 버튼 표시 확인
        rule.onNodeWithTag("pageJumpMove").assertIsDisplayed()
        // 취소 버튼 표시 확인
        rule.onNodeWithTag("pageJumpCancel").assertIsDisplayed()
    }

    /** 다이얼로그에서 취소 버튼 클릭 → 다이얼로그 닫힘 */
    @Test
    fun cancelButtonClosesDialog() {
        enterReader()
        val menuFound = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", menuFound)

        rule.onNodeWithTag("pageInfoText").performClick()
        Thread.sleep(500)
        rule.onNodeWithTag("pageJumpInput").assertIsDisplayed()

        // 취소 버튼 클릭
        rule.onNodeWithTag("pageJumpCancel").performClick()
        Thread.sleep(500)

        // 다이얼로그가 닫혔는지 확인
        val dialogNodes = rule.onAllNodesWithTag("pageJumpInput").fetchSemanticsNodes()
        assertTrue("다이얼로그가 닫히지 않음", dialogNodes.isEmpty())
    }
}

/**
 * 페이지 이동 다이얼로그의 스텝 버튼 동작 테스트.
 * +/- 버튼 클릭 시 입력 필드의 페이지 번호가 증가/감소한다.
 */
@RunWith(AndroidJUnit4::class)
class PageJumpStepButtonTest {

    private val testFilePath: String

    init {
        grantStoragePermission()
        testFilePath = createTestEpubForPageJump()
        BookCache.books = listOf(
            BookFile(
                name = "page_jump_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "페이지 이동 테스트 도서",
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
            rule.onAllNodesWithText("페이지 이동 테스트 도서").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("페이지 이동 테스트 도서").performClick()
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

    private fun waitForMenuWithRetry(): Boolean {
        for (attempt in 1..15) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = rule.onAllNodesWithTag("pageInfoText").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return true
            tapCenter()
            Thread.sleep(2000)
        }
        return false
    }

    private fun getInputValue(): String {
        val node = rule.onNodeWithTag("pageJumpInput").fetchSemanticsNode()
        val editableText = node.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
        return editableText.text
    }

    /** +5 버튼 클릭 → 입력 값이 5 증가 */
    @Test
    fun plusFiveButtonIncreasesPage() {
        enterReader()
        val menuFound = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", menuFound)

        rule.onNodeWithTag("pageInfoText").performClick()
        Thread.sleep(500)

        val initialValue = getInputValue().toInt()

        // +5 버튼 클릭
        rule.onNodeWithText("+5").performClick()
        Thread.sleep(300)

        val newValue = getInputValue().toInt()
        assertTrue(
            "+5 버튼이 동작하지 않음: 이전=$initialValue, 이후=$newValue",
            newValue == initialValue + 5
        )
    }

    /** -1 버튼으로 페이지가 1 미만이 되지 않음 (하한 클램핑) */
    @Test
    fun minusButtonClampsToPageOne() {
        enterReader()
        val menuFound = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", menuFound)

        rule.onNodeWithTag("pageInfoText").performClick()
        Thread.sleep(500)

        // 입력값을 1로 설정
        rule.onNodeWithTag("pageJumpInput").performTextClearance()
        rule.onNodeWithTag("pageJumpInput").performTextInput("1")
        Thread.sleep(300)

        // -1 버튼 클릭
        rule.onNodeWithText("-1").performClick()
        Thread.sleep(300)

        val value = getInputValue().toInt()
        assertTrue("페이지가 1 미만으로 감소함: $value", value >= 1)
    }
}

/**
 * 페이지 이동 다이얼로그에서 [이동] 버튼으로 실제 페이지 이동 테스트.
 * 숫자 입력 후 이동하면 해당 페이지로 이동한다.
 */
@RunWith(AndroidJUnit4::class)
class PageJumpNavigationTest {

    private val testFilePath: String

    init {
        grantStoragePermission()
        testFilePath = createTestEpubForPageJump()
        BookCache.books = listOf(
            BookFile(
                name = "page_jump_test.epub",
                path = testFilePath,
                extension = "epub",
                size = File(testFilePath).length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = "페이지 이동 테스트 도서",
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
            rule.onAllNodesWithText("페이지 이동 테스트 도서").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("페이지 이동 테스트 도서").performClick()
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

    private fun waitForMenuWithRetry(): Boolean {
        for (attempt in 1..15) {
            tapCenter()
            Thread.sleep(1000)
            val nodes = rule.onAllNodesWithTag("pageInfoText").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return true
            tapCenter()
            Thread.sleep(2000)
        }
        return false
    }

    private fun getCurrentPage(): Int {
        val node = rule.onNodeWithTag("pageInfoText").fetchSemanticsNode()
        val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .firstOrNull()?.text ?: ""
        return text.split("/")[0].trim().toIntOrNull() ?: 0
    }

    /** 다이얼로그에서 페이지 번호 입력 후 [이동] → 해당 페이지로 이동 */
    @Test
    fun navigatesToEnteredPage() {
        enterReader()
        val menuFound = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", menuFound)

        // 다이얼로그 열기
        rule.onNodeWithTag("pageInfoText").performClick()
        Thread.sleep(500)

        // 목표 페이지 입력
        val targetPage = 3
        rule.onNodeWithTag("pageJumpInput").performTextClearance()
        rule.onNodeWithTag("pageJumpInput").performTextInput(targetPage.toString())
        Thread.sleep(300)

        // 이동 버튼 클릭
        rule.onNodeWithTag("pageJumpMove").performClick()

        // 다이얼로그가 닫히고 이동 완료 대기
        Thread.sleep(5000)

        // 메뉴 다시 열어서 페이지 확인
        val menuFoundAgain = waitForMenuWithRetry()
        assertTrue("이동 후 메뉴가 열리지 않음", menuFoundAgain)

        // 여러 번 시도하여 페이지 번호 확인
        var currentPage = 0
        for (i in 1..10) {
            try {
                currentPage = getCurrentPage()
                if (currentPage == targetPage) break
            } catch (_: Exception) { }
            Thread.sleep(500)
        }

        assertTrue(
            "목표 페이지($targetPage)로 이동하지 않음: 현재=$currentPage",
            currentPage == targetPage
        )
    }

    /** +5 버튼으로 페이지 증가 후 [이동] → 증가된 페이지로 이동 */
    @Test
    fun navigatesAfterStepButton() {
        enterReader()
        val menuFound = waitForMenuWithRetry()
        assertTrue("메뉴가 열리지 않음", menuFound)

        val initialPage = getCurrentPage()

        // 다이얼로그 열기
        rule.onNodeWithTag("pageInfoText").performClick()
        Thread.sleep(500)

        // +5 버튼 클릭
        rule.onNodeWithText("+5").performClick()
        Thread.sleep(300)

        // 이동 버튼 클릭
        rule.onNodeWithTag("pageJumpMove").performClick()

        // 이동 완료 대기
        Thread.sleep(5000)

        // 메뉴 다시 열어서 페이지 확인
        val menuFoundAgain = waitForMenuWithRetry()
        assertTrue("이동 후 메뉴가 열리지 않음", menuFoundAgain)

        val targetPage = initialPage + 5
        var currentPage = 0
        for (i in 1..10) {
            try {
                currentPage = getCurrentPage()
                if (currentPage == targetPage) break
            } catch (_: Exception) { }
            Thread.sleep(500)
        }

        assertTrue(
            "목표 페이지($targetPage)로 이동하지 않음: 현재=$currentPage",
            currentPage == targetPage
        )
    }
}
