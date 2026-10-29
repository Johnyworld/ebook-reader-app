package com.rotein.ebookreader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 목록의 뒤쪽 페이지에서 책을 열었다가 돌아왔을 때,
 * 방금 읽은 책이 있는 페이지로 이동해 있는지 확인한다 (리더가 덮고 있는 동안 이동해, 복귀 시 이동 과정이 보이지 않음).
 *
 * 더미 책은 실제 파일이 없어서 리더 진입/복귀 시의 diff 스캔이 목록을 비워버리므로,
 * MainActivity 대신 AllBooksScreen만 띄우고 refreshKey(열린 책)를 직접 제어한다.
 */
@RunWith(AndroidJUnit4::class)
class ReturnFromReaderPageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // 여러 페이지가 생기도록 충분히 많은 더미 책
    private val manyBooks = (1..40).map { i ->
        val title = "Book %02d".format(i)
        BookFile(
            name = "$title.epub",
            path = "/storage/emulated/0/Books/book$i.epub",
            extension = "epub",
            size = 1024L,
            dateAdded = i.toLong(),
            dateModified = i.toLong(),
            metadata = BookMetadata(
                title = title, author = null,
                language = null, publisher = null, publishedDate = null, description = null
            )
        )
    }

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setup() {
        context.getSharedPreferences("sort_pref", Context.MODE_PRIVATE).edit().clear().commit()
        BookDatabase.getInstance(context).clearAllTables()
        BookCache.books = manyBooks
        context.getSharedPreferences("folder_uris", Context.MODE_PRIVATE)
            .edit().putStringSet("selected_uris", setOf("content://test")).commit()
    }

    @After
    fun cleanup() {
        BookCache.books = null
        context.getSharedPreferences("sort_pref", Context.MODE_PRIVATE).edit().clear().commit()
        BookDatabase.getInstance(context).clearAllTables()
        context.getSharedPreferences("folder_uris", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun returningFromReaderMovesToPageOfOpenedBook() {
        // 현재 열린 책 — HomeScreen과 동일하게 refreshKey로 전달
        var openedBook by mutableStateOf<BookFile?>(null)
        rule.setContent {
            AllBooksScreen(onBookClick = { openedBook = it }, refreshKey = openedBook)
        }
        rule.waitUntil(5000) {
            rule.onAllNodesWithContentDescription(context.getString(R.string.menu)).fetchSemanticsNodes().isNotEmpty()
        }
        // 목록 로드 후 캐시를 비워 diff 스캔(가짜 폴더라 목록을 비움)이 건너뛰어지게 한다
        BookCache.books = null

        // 기본 정렬은 읽은 순(최신순) — 3페이지로 이동
        val next = context.getString(R.string.next)
        rule.onNodeWithContentDescription(next).performClick()
        waitForText("2/")
        rule.onNodeWithContentDescription(next).performClick()
        waitForText("3/")

        // 3페이지의 첫 책을 연다
        val firstOnPage = rule.onAllNodesWithText("Book ", substring = true)
            .fetchSemanticsNodes().first()
            .config[SemanticsProperties.Text].map { it.text }.first { it.startsWith("Book ") }
        rule.onNodeWithText(firstOnPage).performClick()
        rule.waitForIdle()
        assertEquals(firstOnPage, openedBook?.metadata?.title)

        // 리더가 목록을 덮고 있는 동안 이미 재정렬·페이지 이동이 끝나 있어야 한다
        // (방금 연 책이 맨 앞으로 정렬되어 1페이지)
        rule.onNodeWithText(firstOnPage).assertIsDisplayed()
        rule.onNodeWithText("1/", substring = true).assertIsDisplayed()

        // 클릭 시 IO 스레드에서 기록하는 읽은 시각이 DB에 반영될 때까지 대기 — 복귀 시 DB 재로드 후에도 유지되는지 보기 위함
        val dao = BookDatabase.getInstance(context).bookReadRecordDao()
        rule.waitUntil(5000) { runBlocking { dao.getAll().any { it.lastReadAt > 0 } } }

        // 리더를 닫고 목록으로 복귀 — 기다리지 않고 바로 1페이지에 그 책이 보여야 한다
        rule.runOnIdle { openedBook = null }
        rule.waitForIdle()
        rule.onNodeWithText(firstOnPage).assertIsDisplayed()
        rule.onNodeWithText("1/", substring = true).assertIsDisplayed()
    }
}
