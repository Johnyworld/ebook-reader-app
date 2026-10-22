package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 메모리 캐시(BookCache)도, DB에 저장된 도서 목록도 없는 첫 실행 경로.
 * 스캔 완료를 기다리지 않고 화면이 표시되고, 스캔이 끝나면 결과(빈 목록)가 표시되는지 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class AllBooksScreenNoCacheTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        BookCache.books = null
        // DB에 저장된 도서 목록 비우기 → 전체 스캔 경로를 타도록 함
        runBlocking { BookDatabase.getInstance(context).bookCatalogDao().replaceAll(emptyList()) }
        // SAF 폴더 선택 상태 시뮬레이션 (실제 권한이 없으므로 스캔 결과는 빈 목록)
        context.getSharedPreferences("folder_uris", android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("selected_uris", setOf("content://test")).apply()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        context.getSharedPreferences("folder_uris", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun noCacheAndNoSavedCatalog_scanCompletesAndShowsEmptyMessage() {
        val empty = context.getString(R.string.empty)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText(empty).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(empty).assertIsDisplayed()
        // 스캔 결과가 메모리 캐시에 반영되었는지 확인
        composeTestRule.waitUntil(5000) { BookCache.books != null }
    }
}
