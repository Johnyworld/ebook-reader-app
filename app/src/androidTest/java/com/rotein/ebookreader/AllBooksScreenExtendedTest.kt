package com.rotein.ebookreader

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun str(resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

private fun grantPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    instrumentation.uiAutomation
        .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
        .close()
}

/** 테스트 간 상태 오염을 방지하기 위해 DB와 SharedPreferences를 초기화한다. */
private fun resetTestState() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.getSharedPreferences("sort_pref", Context.MODE_PRIVATE).edit().clear().apply()
    BookDatabase.getInstance(context).clearAllTables()
}

private val threeBooks = listOf(
    BookFile(
        name = "alice.epub",
        path = "/storage/emulated/0/Books/alice.epub",
        extension = "epub",
        size = 1024L,
        dateAdded = 3000L,
        dateModified = 3000L,
        metadata = BookMetadata(
            title = "Alice in Wonderland",
            author = "Lewis Carroll",
            language = null, publisher = null, publishedDate = null, description = null
        )
    ),
    BookFile(
        name = "나무.epub",
        path = "/storage/emulated/0/Books/나무.epub",
        extension = "epub",
        size = 2048L,
        dateAdded = 1000L,
        dateModified = 1000L,
        metadata = BookMetadata(
            title = "나무",
            author = "김작가",
            language = null, publisher = null, publishedDate = null, description = null
        )
    ),
    BookFile(
        name = "바다.epub",
        path = "/storage/emulated/0/Books/바다.epub",
        extension = "epub",
        size = 512L,
        dateAdded = 2000L,
        dateModified = 2000L,
        metadata = BookMetadata(
            title = "바다 이야기",
            author = "박작가",
            language = null, publisher = null, publishedDate = null, description = null
        )
    )
)

private fun waitForBookList(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>) {
    rule.waitUntil(5000) {
        rule.onAllNodesWithText("Alice in Wonderland").fetchSemanticsNodes().isNotEmpty()
    }
}

// ── 2.2 검색 테스트 ──

@RunWith(AndroidJUnit4::class)
class SearchByAuthorTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.2.3 저자 이름으로 검색 */
    @Test
    fun searchByAuthorFiltersCorrectly() {
        waitForBookList(rule)
        rule.onNodeWithContentDescription(str(R.string.search)).performClick()
        rule.onNodeWithTag("searchInput").performTextInput("김작가")

        rule.onNodeWithText("나무").assertIsDisplayed()
        rule.onNodeWithText("Alice in Wonderland").assertIsNotDisplayed()
        rule.onNodeWithText("바다 이야기").assertIsNotDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SearchNoResultsTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.2.5 존재하지 않는 키워드로 검색 */
    @Test
    fun searchWithNoMatchShowsEmpty() {
        waitForBookList(rule)
        rule.onNodeWithContentDescription(str(R.string.search)).performClick()
        rule.onNodeWithTag("searchInput").performTextInput("존재하지않는키워드")

        rule.waitUntil(3000) {
            rule.onAllNodesWithText(str(R.string.empty)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(str(R.string.empty)).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SearchClearRestoresListTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.2.6 검색어를 입력한 후 지운다 → 전체 도서 목록이 다시 표시된다 */
    @Test
    fun clearSearchRestoresAllBooks() {
        waitForBookList(rule)
        rule.onNodeWithContentDescription(str(R.string.search)).performClick()
        rule.onNodeWithTag("searchInput").performTextInput("Alice")
        rule.onNodeWithText("나무").assertIsNotDisplayed()

        // 검색 닫기 버튼 클릭
        rule.onNodeWithContentDescription(str(R.string.close_search)).performClick()

        // 전체 목록 복원 확인
        rule.waitUntil(3000) {
            rule.onAllNodesWithText("나무").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Alice in Wonderland").assertIsDisplayed()
        rule.onNodeWithText("나무").assertIsDisplayed()
        rule.onNodeWithText("바다 이야기").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SearchMixedCharactersTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.2.7 한글, 영문, 특수문자를 포함한 검색어 입력 → 오류 없이 동작 */
    @Test
    fun searchWithMixedCharactersDoesNotCrash() {
        waitForBookList(rule)
        rule.onNodeWithContentDescription(str(R.string.search)).performClick()
        rule.onNodeWithTag("searchInput").performTextInput("한글English#@!\$%")

        rule.waitUntil(3000) {
            rule.onAllNodesWithText(str(R.string.empty)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(str(R.string.empty)).assertIsDisplayed()
    }
}

// ── 2.3 정렬 테스트 ──

@RunWith(AndroidJUnit4::class)
class SortByTitleTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.3.1 제목순 정렬 선택 → 가나다/ABC 순 */
    @Test
    fun sortByTitleOrdersAlphabetically() {
        waitForBookList(rule)
        // 기본 정렬 드롭다운 클릭 (기본값 "읽은순")
        rule.onNodeWithText(str(R.string.sort_last_read)).performClick()
        // "제목순" 선택
        rule.onNodeWithText(str(R.string.sort_title)).performClick()

        // 제목순(오름차순): Alice → 나무 → 바다 이야기
        rule.onNodeWithText("Alice in Wonderland").assertIsDisplayed()

        // Y 좌표로 실제 순서 검증
        val aliceY = rule.onNodeWithTag("bookItem_alice.epub").fetchSemanticsNode().boundsInRoot.top
        val namuY = rule.onNodeWithTag("bookItem_나무.epub").fetchSemanticsNode().boundsInRoot.top
        val badaY = rule.onNodeWithTag("bookItem_바다.epub").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Alice가 나무보다 위에 있어야 함", aliceY < namuY)
        assertTrue("나무가 바다보다 위에 있어야 함", namuY < badaY)
    }
}

@RunWith(AndroidJUnit4::class)
class SortByDateAddedTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.3.3 추가일순 정렬 선택 → 파일 추가일 기준 정렬(내림차순) */
    @Test
    fun sortByDateAddedShowsNewestFirst() {
        waitForBookList(rule)
        rule.onNodeWithText(str(R.string.sort_last_read)).performClick()
        rule.onNodeWithText(str(R.string.sort_date_added)).performClick()

        // dateAdded: alice=3000 > 바다=2000 > 나무=1000 (내림차순)
        rule.onNodeWithText("Alice in Wonderland").assertIsDisplayed()

        // Y 좌표로 실제 순서 검증 (내림차순: alice → 바다 → 나무)
        val aliceY = rule.onNodeWithTag("bookItem_alice.epub").fetchSemanticsNode().boundsInRoot.top
        val badaY = rule.onNodeWithTag("bookItem_바다.epub").fetchSemanticsNode().boundsInRoot.top
        val namuY = rule.onNodeWithTag("bookItem_나무.epub").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Alice가 바다보다 위에 있어야 함", aliceY < badaY)
        assertTrue("바다가 나무보다 위에 있어야 함", badaY < namuY)
    }
}

// ── 2.4 필터 테스트 ──

@RunWith(AndroidJUnit4::class)
class FilterAllTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.4.1 "전체" 필터 선택 → 숨김 제외 모든 도서 표시 */
    @Test
    fun filterAllShowsAllBooks() {
        waitForBookList(rule)
        rule.onNodeWithText("Alice in Wonderland").assertIsDisplayed()
        rule.onNodeWithText("나무").assertIsDisplayed()
        rule.onNodeWithText("바다 이야기").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class FilterFavoriteEmptyTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.4.4 즐겨찾기가 없는 상태에서 "즐겨찾기만" 필터 선택 → 빈 상태 안내 */
    @Test
    fun filterFavoriteWithNoneShowsEmpty() {
        waitForBookList(rule)
        rule.onNodeWithText(str(R.string.filter_all)).performClick()
        rule.onNodeWithText(str(R.string.filter_favorite)).performClick()

        rule.waitUntil(3000) {
            rule.onAllNodesWithText(str(R.string.empty)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(str(R.string.empty)).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class FilterHiddenTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.4.3 "숨긴 도서" 필터 선택 → 숨김 도서만 표시 */
    @Test
    fun filterHiddenShowsOnlyHiddenBooks() {
        waitForBookList(rule)

        // 첫 번째 도서 숨기기
        rule.onAllNodesWithContentDescription(str(R.string.menu))[0].performClick()
        rule.onNodeWithText(str(R.string.hide)).performClick()

        // "숨긴 도서" 필터 선택
        rule.onNodeWithText(str(R.string.filter_all)).performClick()
        rule.onNodeWithText(str(R.string.filter_hidden)).performClick()

        // 숨긴 도서가 정확히 1권 표시되는지 확인
        rule.waitUntil(3000) {
            rule.onAllNodesWithContentDescription(str(R.string.menu)).fetchSemanticsNodes().size == 1
        }
    }
}

// ── 2.5 숨기기 테스트 ──

@RunWith(AndroidJUnit4::class)
class HideBookTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.5.3 숨기기 선택 → 도서가 기본 목록에서 사라짐 */
    @Test
    fun hideBookRemovesFromDefaultList() {
        waitForBookList(rule)

        // 첫 번째 도서의 메뉴 → 숨기기
        rule.onAllNodesWithContentDescription(str(R.string.menu))[0].performClick()
        rule.onNodeWithText(str(R.string.hide)).performClick()

        // 3권 중 2권만 표시 확인
        rule.waitUntil(3000) {
            rule.onAllNodesWithContentDescription(str(R.string.menu)).fetchSemanticsNodes().size == 2
        }
    }
}

@RunWith(AndroidJUnit4::class)
class UnhideBookTest {

    init {
        grantPermission()
        resetTestState()
        BookCache.books = threeBooks
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() {
        BookCache.books = null
        resetTestState()
    }

    /** 2.5.4 숨긴 도서에서 숨기기 해제 → 기본 목록에 복원 */
    @Test
    fun unhideBookRestoresToDefaultList() {
        waitForBookList(rule)

        // 도서 숨기기
        rule.onAllNodesWithContentDescription(str(R.string.menu))[0].performClick()
        rule.onNodeWithText(str(R.string.hide)).performClick()

        // "숨긴 도서" 필터로 이동
        rule.onNodeWithText(str(R.string.filter_all)).performClick()
        rule.onNodeWithText(str(R.string.filter_hidden)).performClick()

        rule.waitUntil(3000) {
            rule.onAllNodesWithContentDescription(str(R.string.menu)).fetchSemanticsNodes().isNotEmpty()
        }

        // 숨기기 해제
        rule.onAllNodesWithContentDescription(str(R.string.menu))[0].performClick()
        rule.onNodeWithText(str(R.string.unhide)).performClick()

        // "전체" 필터로 복귀
        rule.onNodeWithText(str(R.string.filter_hidden)).performClick()
        rule.onNodeWithText(str(R.string.filter_all)).performClick()

        // 3권 모두 표시 확인
        rule.waitUntil(3000) {
            rule.onAllNodesWithContentDescription(str(R.string.menu)).fetchSemanticsNodes().size == 3
        }
    }
}
