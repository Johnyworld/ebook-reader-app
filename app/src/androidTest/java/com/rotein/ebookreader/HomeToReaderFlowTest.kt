package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeToReaderFlowTest {

    private val dummyBooks = listOf(
        BookFile(
            name = "테스트 책 1",
            path = "/storage/emulated/0/Books/test1.epub",
            extension = "epub",
            size = 1024L,
            dateAdded = 1000L,
            dateModified = 1000L,
            metadata = BookMetadata(
                title = "테스트 책 1",
                author = "테스트 저자",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        ),
        BookFile(
            name = "테스트 책 2",
            path = "/storage/emulated/0/Books/test2.epub",
            extension = "epub",
            size = 2048L,
            dateAdded = 2000L,
            dateModified = 2000L,
            metadata = BookMetadata(
                title = "테스트 책 2",
                author = "테스트 저자 2",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        )
    )

    init {
        // Activity 실행 전에 권한 부여 + BookCache 세팅
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()
        BookCache.books = dummyBooks
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        BookCache.books = dummyBooks
    }

    @After
    fun cleanup() {
        BookCache.books = null
    }

    private fun waitForBookList() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("테스트 책 1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun booksAreDisplayedOnHomeScreen() {
        waitForBookList()
        composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("테스트 책 2").assertIsDisplayed()
    }

    @Test
    fun clickingBookOpensReaderScreen() {
        waitForBookList()
        composeTestRule.onNodeWithText("테스트 책 1").performClick()
        composeTestRule.onNodeWithTag("bookReaderScreen").assertIsDisplayed()
    }
}
