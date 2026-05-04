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
 * 크로스 챕터 북마크 네비게이션 테스트
 *
 * 시나리오:
 * 1. TOC에서 7번째 챕터로 이동
 * 2. 이전 페이지로 이동 (6챕터 마지막 페이지)
 * 3. 현재 페이지 숫자 저장
 * 4. 북마크 추가
 * 5. 다음 페이지로 이동 (7챕터 첫 페이지)
 * 6. 북마크 목록에서 방금 북마크한 아이템으로 이동
 * 7. 저장한 페이지와 같은 페이지인지 확인
 *
 * 사전 조건:
 * adb push "프로젝트 헤일메리 - 앤디 위어.epub" /sdcard/Download/test_hailmary.epub
 */
@RunWith(AndroidJUnit4::class)
class BookmarkCrossChapterNavigationTest {

    companion object {
        private const val TAG = "BookmarkCrossChapterTest"
        private const val EPUB_PATH = "/sdcard/Download/test_hailmary.epub"
        private const val BOOK_TITLE = "프로젝트 헤일메리"
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

    private fun waitForTag(tag: String, timeoutMillis: Long = 15000, useUnmergedTree: Boolean = false) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithTag(tag, useUnmergedTree = useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
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

    /** 메뉴를 열고 페이지 스캔이 완료될 때까지 대기 */
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

    private fun closeMenu() {
        tapCenter()
        Thread.sleep(500)
    }

    // --- 테스트 ---

    @Test
    fun bookmarkNavigationFromDifferentChapterLandsOnCorrectPage() {
        // 1. 리더 진입
        Log.d(TAG, "단계 1: 리더 진입")
        rule.waitUntil(15000) {
            rule.onAllNodesWithText(BOOK_TITLE, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(BOOK_TITLE, substring = true).performClick()
        waitForTag("bookReaderScreen")
        rule.waitUntil(60000) {
            rule.onAllNodesWithTag("readerLoading").fetchSemanticsNodes().isEmpty()
        }
        Log.d(TAG, "단계 1 완료: 리더 로딩 완료")

        // 2. 메뉴 열기 (페이지 스캔 완료 대기)
        Log.d(TAG, "단계 2: 메뉴 열기 (페이지 스캔 대기)")
        val menuOpened = openMenuAndWaitForScan()
        assertTrue("메뉴/페이지 스캔 완료 실패", menuOpened)
        Log.d(TAG, "단계 2 완료: 페이지 스캔 완료")

        // 3. TOC 열기 → 7번째 챕터(인덱스 6)로 이동
        Log.d(TAG, "단계 3: TOC에서 7번째 챕터로 이동")
        rule.onNodeWithTag("tocButton").performClick()
        waitForTag("tocItem_6", 10000)
        rule.onNodeWithTag("tocItem_6").performClick()
        Thread.sleep(3000)
        Log.d(TAG, "단계 3 완료: 7번째 챕터로 이동 완료")

        // 4. 이전 페이지로 이동 (6챕터 마지막 페이지)
        Log.d(TAG, "단계 4: 이전 페이지로 이동")
        tapPrevPage()
        Thread.sleep(2000)
        Log.d(TAG, "단계 4 완료: 이전 페이지 이동 완료")

        // 5. 현재 페이지 숫자 저장
        Log.d(TAG, "단계 5: 현재 페이지 저장")
        val menuAfterPrev = openMenuAndWaitForScan()
        assertTrue("이전 페이지 이동 후 메뉴 열기 실패", menuAfterPrev)
        waitForTag("pageInfoText")
        val savedPage = getCurrentPage()
        Log.d(TAG, "단계 5 완료: 저장된 페이지 = $savedPage")
        assertTrue("저장된 페이지가 0보다 커야 합니다", savedPage > 0)

        // 6. 북마크 추가
        Log.d(TAG, "단계 6: 북마크 추가")
        rule.onNodeWithTag("bookmarkToggleButton").performClick()
        Thread.sleep(3000)
        Log.d(TAG, "단계 6 완료: 북마크 추가됨")

        // 7. 메뉴 닫기 → 다음 페이지로 이동 (7챕터 첫 페이지)
        Log.d(TAG, "단계 7: 다음 페이지로 이동")
        closeMenu()
        tapNextPage()
        Thread.sleep(2000)
        Log.d(TAG, "단계 7 완료: 다음 페이지 이동 완료")

        // 8. 메뉴 열기 → 북마크 목록 열기
        Log.d(TAG, "단계 8: 북마크 목록 열기")
        val menuForBookmarks = openMenuAndWaitForScan()
        assertTrue("북마크 목록 열기 위한 메뉴 열기 실패", menuForBookmarks)
        rule.onNodeWithTag("bookmarkListButton").performClick()
        Thread.sleep(2000)

        // 북마크 아이템 존재 확인
        val bookmarkItems = rule.onAllNodesWithTag("bookmarkItem").fetchSemanticsNodes()
        Log.d(TAG, "북마크 아이템 수: ${bookmarkItems.size}")
        assertTrue("북마크 리스트에 아이템이 없습니다", bookmarkItems.isNotEmpty())
        Log.d(TAG, "단계 8 완료: 북마크 목록 열림")

        // 9. 북마크 아이템 탭하여 이동
        Log.d(TAG, "단계 9: 북마크 탭하여 이동")
        rule.onNodeWithTag("bookmarkItem").performClick()
        Thread.sleep(3000)
        Log.d(TAG, "단계 9 완료: 북마크 이동 완료")

        // 10. 이동 후 실제 페이지 번호 확인
        Log.d(TAG, "단계 10: 이동 후 페이지 확인")
        val menuAfterNav = openMenuAndWaitForScan()
        assertTrue("북마크 이동 후 메뉴 열기 실패", menuAfterNav)
        waitForTag("pageInfoText")
        val actualPage = getCurrentPage()
        Log.d(TAG, "단계 10 완료: 실제 페이지 = $actualPage")

        // 11. 저장한 페이지와 동일한지 검증
        assertEquals(
            "크로스 챕터 북마크 이동 실패: 저장된 페이지($savedPage)와 실제 페이지($actualPage)가 다릅니다",
            savedPage,
            actualPage
        )
        Log.d(TAG, "테스트 통과: 저장 페이지($savedPage) == 실제 페이지($actualPage)")
    }
}
