package com.rotein.ebookreader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 홈 폴더 보기: 폴더 진입·상위 이동·검색 시 평면 목록·리더 복귀 시 폴더 유지를 확인한다.
 * 루트 하나("Books") 아래에 직속 책 1권과 SF 폴더 책 1권을 둔다.
 */
@RunWith(AndroidJUnit4::class)
class AllBooksFolderViewTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = "content://com.android.externalstorage.documents/tree/primary%3ABooks"

    private fun book(title: String, folder: String) = BookFile(
        name = "$title.epub",
        path = "",
        contentUri = "$root/document/$title",
        extension = "epub",
        size = 1024L,
        dateAdded = 1L,
        dateModified = 1L,
        metadata = BookMetadata(title, null, null, null, null, null),
        rootUri = root,
        folderPath = folder
    )

    private val books = listOf(book("직속 책", ""), book("SF 책", "SF"))

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setup() {
        BookDatabase.getInstance(context).clearAllTables()
        BookCache.books = books
        context.getSharedPreferences("folder_uris", Context.MODE_PRIVATE)
            .edit().putStringSet("selected_uris", setOf(root)).commit()
        // 폴더 보기로 시작
        context.getSharedPreferences("view_mode_pref", Context.MODE_PRIVATE)
            .edit().putString("view_mode", ViewMode.FOLDER.name).commit()
    }

    @After
    fun cleanup() {
        BookCache.books = null
        BookDatabase.getInstance(context).clearAllTables()
        context.getSharedPreferences("folder_uris", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("view_mode_pref", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("folder_settings_pref", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private var openedBook by mutableStateOf<BookFile?>(null)

    /** 화면을 띄우고 목록 로드 후 캐시를 비워 가짜 폴더의 diff 스캔이 목록을 비우지 않게 한다 */
    private fun launch() {
        rule.setContent {
            AllBooksScreen(onBookClick = { openedBook = it }, refreshKey = openedBook)
        }
        rule.waitUntil(5000) {
            rule.onAllNodesWithText("직속 책").fetchSemanticsNodes().isNotEmpty()
        }
        BookCache.books = null
    }

    private fun nodeCount(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun topShowsFolderAndDirectBooksOnly() {
        launch()
        rule.onNodeWithTag("folderItem_SF").assertIsDisplayed()
        rule.onNodeWithText("직속 책").assertIsDisplayed()
        assertEquals(0, nodeCount("SF 책"))
        // 루트가 하나라 최상위에서는 경로 줄이 없다
        assertEquals(0, rule.onAllNodesWithContentDescription(context.getString(R.string.go_up)).fetchSemanticsNodes().size)
    }

    @Test
    fun enterFolderThenGoUpWithPathBar() {
        launch()
        rule.onNodeWithTag("folderItem_SF").performClick()
        rule.onNodeWithText("SF 책").assertIsDisplayed()
        rule.onNodeWithTag("folderPathBar").assertIsDisplayed()
        assertEquals(0, nodeCount("직속 책"))

        rule.onNodeWithContentDescription(context.getString(R.string.go_up)).performClick()
        rule.onNodeWithText("직속 책").assertIsDisplayed()
        rule.onNodeWithTag("folderItem_SF").assertIsDisplayed()
    }

    @Test
    fun searchShowsFlatResultsAcrossFolders() {
        launch()
        rule.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
        // 검색어를 책 제목과 다르게 입력해 검색창 텍스트와 목록 항목이 같은 노드로 잡히지 않게 한다
        rule.onNodeWithTag("searchInput").performTextInput("SF")
        rule.onNodeWithText("SF 책").assertIsDisplayed()
    }

    @Test
    fun returningFromReaderStaysInFolder() {
        launch()
        rule.onNodeWithTag("folderItem_SF").performClick()
        rule.onNodeWithText("SF 책").performClick()
        rule.waitForIdle()
        assertEquals("SF 책", openedBook?.metadata?.title)

        // 리더를 닫고 복귀 — 같은 폴더가 그대로 보여야 한다
        rule.runOnIdle { openedBook = null }
        rule.waitForIdle()
        rule.onNodeWithText("SF 책").assertIsDisplayed()
        rule.onNodeWithTag("folderPathBar").assertIsDisplayed()
    }
}
