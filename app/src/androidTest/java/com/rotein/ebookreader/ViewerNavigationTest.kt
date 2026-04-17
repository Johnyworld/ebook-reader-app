package com.rotein.ebookreader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewerNavigationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var testFilePath: String

    init {
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun copyAssetToStorage(assetName: String): String {
        val context = instrumentation.targetContext
        val outDir = File(context.getExternalFilesDir(null), "test_books")
        outDir.mkdirs()
        val outFile = File(outDir, assetName)
        if (!outFile.exists()) {
            instrumentation.context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun tapCenter() {
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        Thread.sleep(500)
    }

    private fun tapPrevPage() {
        device.click(device.displayWidth / 6, device.displayHeight / 2)
        Thread.sleep(500)
    }

    private fun getCurrentPage(): Int {
        val node = composeTestRule.onNodeWithTag("pageInfoText")
            .fetchSemanticsNode()
        val textList = node.config[SemanticsProperties.Text]
        val text = textList.firstOrNull()?.text ?: ""
        // Format: "3 / 15"
        return text.split("/")[0].trim().toInt()
    }

    @After
    fun cleanup() {
        BookCache.books = null
    }

    @Test
    fun epubTocNavigationAndPrevPage() {
        testFilePath = copyAssetToStorage("test.epub")
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
                    language = null,
                    publisher = null,
                    publishedDate = null,
                    description = null
                )
            )
        )

        // Enter reader
        waitForText("테스트 도서")
        composeTestRule.onNodeWithText("테스트 도서").performClick()
        waitForTag("bookReaderScreen")

        // Wait for content to load
        waitForTagToDisappear("readerLoading")
        Thread.sleep(3000) // Extra wait for page scan

        // Open menu
        tapCenter()

        // Open TOC
        waitForTag("tocButton")
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
        Thread.sleep(300)

        // Previous page
        tapPrevPage()
        Thread.sleep(1000)

        // Open menu → read page B
        tapCenter()
        waitForTag("pageInfoText")
        val pageB = getCurrentPage()

        // Assert B + 1 == A
        assert(pageB + 1 == pageA) {
            "EPUB prev page failed: pageB($pageB) + 1 != pageA($pageA)"
        }
    }
}
