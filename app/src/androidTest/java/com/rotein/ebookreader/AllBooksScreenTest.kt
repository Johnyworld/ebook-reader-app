package com.rotein.ebookreader

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllBooksScreenTest {

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
                author = "가나다 저자",
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
                author = "라마바 저자",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        )
    )

    init {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()
        BookCache.books = dummyBooks
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun bookMetadataShowsAuthor() {
        waitForText("테스트 책 1")
        composeTestRule.onNodeWithText("가나다 저자").assertIsDisplayed()
        composeTestRule.onNodeWithText("라마바 저자").assertIsDisplayed()
    }

    @Test
    fun splashScreenTransitionsToBookList() {
        // Splash shows "ebook-reader" for 800ms min, then disappears when fileScanComplete
        // With BookCache pre-set, onLoadComplete fires immediately, so splash goes away ~800ms
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("ebook-reader")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        waitForText("테스트 책 1")
        composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
    }

    @Test
    fun searchFiltersBooksByQuery() {
        waitForText("테스트 책 1")

        // 검색 아이콘 클릭
        composeTestRule.onNodeWithContentDescription("검색").performClick()

        // 검색어 입력
        composeTestRule.onNodeWithTag("searchInput").performTextInput("책 1")

        // "테스트 책 1"은 표시, "테스트 책 2"는 미표시
        composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("테스트 책 2").assertDoesNotExist()
    }
}

@RunWith(AndroidJUnit4::class)
class AllBooksScreenEmptyTest {

    init {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()
        BookCache.books = emptyList()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
    }

    @Test
    fun emptyBookListShowsEmptyMessage() {
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodesWithText("비어있음")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("비어있음").assertIsDisplayed()
    }
}
