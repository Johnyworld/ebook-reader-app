package com.rotein.ebookreader

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 도서 목록 재진입 시 페이지 유지 테스트
 *
 * 시나리오:
 * 1. EPUB 열기 → TOC에서 7번째 챕터 선택 → 이전 페이지 이동 (6챕터 마지막)
 * 2. 현재 페이지 번호 기억
 * 3. 가운데 탭 → 메뉴 열기 → 뒤로가기 → 도서 목록
 * 4. 다시 도서 진입 → 페이지 번호 비교
 * 5. 2-4 과정을 10회 반복
 *
 * 사전 조건:
 * adb push "프로젝트 헤일메리 - 앤디 위어.epub" /sdcard/Download/test_hailmary.epub
 */
@RunWith(AndroidJUnit4::class)
class ReaderReentryPageTest {

    companion object {
        private const val TAG = "ReentryPageTest"
        private const val EPUB_PATH = "/sdcard/Download/test_hailmary.epub"
        private const val BOOK_TITLE = "프로젝트 헤일메리"
        private const val REPEAT_COUNT = 10
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    init {
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()

        val file = File(EPUB_PATH)
        require(file.exists()) {
            "테스트용 EPUB 파일이 기기에 없습니다. 다음 명령어로 먼저 넣어주세요:\n" +
            "adb push \"프로젝트 헤일메리 - 앤디 위어.epub\" $EPUB_PATH"
        }

        BookCache.books = listOf(
            BookFile(
                name = "test_hailmary.epub",
                path = EPUB_PATH,
                extension = "epub",
                size = file.length(),
                dateAdded = 1000L,
                dateModified = 1000L,
                metadata = BookMetadata(
                    title = BOOK_TITLE,
                    author = "앤디 위어",
                    language = null, publisher = null, publishedDate = null, description = null
                )
            )
        )
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanup() { BookCache.books = null }

    // --- 유틸리티 함수 ---

    private fun waitForTag(tag: String, timeoutMillis: Long = 15000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 60000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapCenter() {
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.3f))
            up()
        }
        Thread.sleep(500)
    }

    private fun tapPrevPage() {
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * 0.15f, centerY))
            up()
        }
        Thread.sleep(500)
    }

    private fun getCurrentPage(): Int {
        val node = rule.onNodeWithTag("pageInfoText").fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].firstOrNull()?.text ?: ""
        return text.split("/")[0].trim().toInt()
    }

    /** 메뉴를 열고 페이지 스캔 완료까지 대기 */
    private fun openMenuAndWaitForScan(): Boolean {
        repeat(40) {
            tapCenter()
            Thread.sleep(1500)
            val nodes = rule.onAllNodesWithTag("tocButton").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return true
            tapCenter()
            Thread.sleep(2000)
        }
        return false
    }

    /** 도서 진입 후 로딩 완료까지 대기 */
    private fun enterBook() {
        rule.waitUntil(15000) {
            rule.onAllNodesWithText(BOOK_TITLE, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(BOOK_TITLE, substring = true).performClick()
        waitForTag("bookReaderScreen")
        waitForTagToDisappear("readerLoading")
    }

    /** 메뉴를 열고 현재 페이지 번호를 읽는다 */
    private fun openMenuAndGetPage(): Int {
        tapCenter()
        waitForTag("pageInfoText")
        return getCurrentPage()
    }

    /** 뒤로가기로 도서 목록으로 나간다 */
    private fun goBackToList() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        Thread.sleep(1000)
    }

    // --- 테스트 ---

    @Test
    fun pagePreservedAfterListReentry() {
        // 1. 리더 진입
        Log.d(TAG, "단계 1: 리더 진입")
        enterBook()
        Log.d(TAG, "단계 1 완료: 리더 로딩 완료")

        // 2. 메뉴 열고 스캔 대기
        Log.d(TAG, "단계 2: 페이지 스캔 대기")
        assertTrue("페이지 스캔 완료 실패", openMenuAndWaitForScan())
        Log.d(TAG, "단계 2 완료: 스캔 완료")

        // 3. TOC에서 7번째 챕터 선택
        Log.d(TAG, "단계 3: 7번째 챕터로 이동")
        rule.onNodeWithTag("tocButton").performClick()
        waitForTag("tocItem_6")
        rule.onNodeWithTag("tocItem_6").performClick()
        Thread.sleep(3000)
        Log.d(TAG, "단계 3 완료: 챕터 이동 완료")

        // 4. 이전 페이지로 이동 (6챕터 마지막 페이지)
        Log.d(TAG, "단계 4: 이전 페이지로 이동")
        tapPrevPage()
        Thread.sleep(2000)

        // 페이지 안정화 대기 후 기준 페이지 기록
        val expectedPage = openMenuAndGetPage()
        Log.d(TAG, "단계 4 완료: 기준 페이지 = $expectedPage")

        // 5. 메뉴 닫기
        tapCenter()
        Thread.sleep(500)

        // 6. 10회 반복: 목록으로 나갔다 들어와서 페이지 비교
        for (i in 1..REPEAT_COUNT) {
            Log.d(TAG, "반복 $i/$REPEAT_COUNT: 목록으로 나가기")

            // 메뉴 열기 → 뒤로가기로 목록으로 나감
            tapCenter()
            Thread.sleep(500)
            goBackToList()

            Log.d(TAG, "반복 $i/$REPEAT_COUNT: 다시 진입")

            // 다시 도서 진입
            enterBook()

            // 메뉴를 열고 스캔 완료 대기
            assertTrue("반복 $i: 페이지 스캔 완료 실패", openMenuAndWaitForScan())

            // 현재 페이지 확인
            val currentPage = getCurrentPage()
            Log.d(TAG, "반복 $i/$REPEAT_COUNT: 기대=$expectedPage, 실제=$currentPage")

            assertEquals(
                "반복 $i: 목록 재진입 후 페이지가 변경됨 (기대: $expectedPage, 실제: $currentPage)",
                expectedPage,
                currentPage
            )

            // 메뉴 닫기
            tapCenter()
            Thread.sleep(500)
        }

        Log.d(TAG, "테스트 통과: $REPEAT_COUNT 회 재진입 모두 페이지 유지 확인")
    }
}
