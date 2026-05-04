package com.rotein.ebookreader

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
 * 북마크 페이지 번호 일관성 테스트
 *
 * 시나리오:
 * 1. EPUB 열기 → TOC에서 7번째 챕터로 이동 → 북마크 추가
 * 2. 폰트 사이즈 변경 (re-pagination 발생)
 * 3. 북마크 리스트 열기 → 표시된 페이지 번호 캡처
 * 4. 해당 북마크 탭하여 이동 → 실제 페이지 번호 캡처
 * 5. 두 페이지 번호가 일치하는지 검증
 *
 * 사전 조건:
 * adb push "프로젝트 헤일메리 - 앤디 위어.epub" /sdcard/Download/test_hailmary.epub
 */
@RunWith(AndroidJUnit4::class)
class BookmarkPageConsistencyTest {

    companion object {
        private const val TAG = "BookmarkPageTest"
        // adb push로 미리 넣어둔 파일 경로
        private const val EPUB_PATH = "/sdcard/Download/test_hailmary.epub"
        private const val BOOK_TITLE = "프로젝트 헤일메리"
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    init {
        // 저장소 권한 부여
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()

        // 파일 존재 확인
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

    private fun getCurrentPage(): Int {
        val node = rule.onNodeWithTag("pageInfoText").fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].firstOrNull()?.text ?: ""
        return text.split("/")[0].trim().toInt()
    }

    /** 메뉴를 열고 페이지 스캔이 완료될 때까지 대기 (최대 약 2분) */
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

    /** 메뉴 닫기 */
    private fun closeMenu() {
        tapCenter()
        Thread.sleep(500)
    }

    // --- 테스트 ---

    @Test
    fun bookmarkPageMatchesAfterFontSizeChange() {
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

        // 2. 메뉴 열기 (페이지 스캔 완료 대기 — 큰 파일이므로 넉넉히)
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
        Log.d(TAG, "단계 3 완료: 챕터 이동 완료")

        // 3-1. 12페이지 앞으로 이동
        Log.d(TAG, "단계 3-1: 12페이지 앞으로 이동")
        repeat(12) {
            rule.onNodeWithTag("bookReaderScreen").performTouchInput {
                down(androidx.compose.ui.geometry.Offset(width * 0.85f, centerY))
                up()
            }
            Thread.sleep(1000)
        }
        Log.d(TAG, "단계 3-1 완료: 12페이지 이동 완료")

        // 4. 메뉴 열기 → 현재 페이지 확인 → 북마크 추가
        Log.d(TAG, "단계 4: 북마크 추가")
        val menuAfterNav = openMenuAndWaitForScan()
        assertTrue("챕터 이동 후 메뉴 열기 실패", menuAfterNav)

        // 북마크 추가 전 현재 페이지 확인
        waitForTag("pageInfoText")
        val pageBeforeBookmark = getCurrentPage()
        Log.d(TAG, "북마크 추가 전 현재 페이지: $pageBeforeBookmark")
        assertTrue("북마크 추가 전 현재 페이지가 0입니다. 위치 정보가 아직 로드되지 않았습니다.", pageBeforeBookmark > 0)

        rule.onNodeWithTag("bookmarkToggleButton").performClick()
        Thread.sleep(3000) // 비동기 북마크 저장 대기
        Log.d(TAG, "단계 4 완료: 북마크 추가됨")

        // 5. 메뉴 닫기 → 설정 열기 → 폰트 사이즈 변경
        Log.d(TAG, "단계 5: 폰트 사이즈 변경")
        closeMenu()
        tapCenter()
        Thread.sleep(500)
        waitForTag("settingsButton")
        rule.onNodeWithTag("settingsButton").performClick()
        Thread.sleep(1000)

        // 폰트 사이즈 증가 (2회)
        waitForTag("fontSizeIncrement")
        rule.onNodeWithTag("fontSizeIncrement").performClick()
        Thread.sleep(500)
        rule.onNodeWithTag("fontSizeIncrement").performClick()
        Thread.sleep(500)
        Log.d(TAG, "단계 5 완료: 폰트 사이즈 +2 적용")

        // 설정 닫기 (바깥 영역 탭)
        rule.onNodeWithTag("bookReaderScreen").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(centerX, height * 0.1f))
            up()
        }
        // 설정 오버레이(3초 타이머) 제거 대기
        Thread.sleep(5000)

        // 6. re-pagination 완료 대기
        Log.d(TAG, "단계 6: re-pagination 대기")
        val scanComplete = openMenuAndWaitForScan()
        assertTrue("폰트 변경 후 re-pagination 완료 실패", scanComplete)
        Log.d(TAG, "단계 6 완료: re-pagination 완료")

        // 6-1. remapAnnotationPages() 완료 대기 — 메뉴 닫고 잠시 대기 후 다시 열기
        closeMenu()
        Thread.sleep(5000)

        // 7. 메뉴 다시 열고 북마크 리스트 열기
        Log.d(TAG, "단계 7: 북마크 리스트 열기")
        val menuForBookmarkList = openMenuAndWaitForScan()
        assertTrue("북마크 리스트 열기 위한 메뉴 열기 실패", menuForBookmarkList)
        rule.onNodeWithTag("bookmarkListButton").performClick()
        Thread.sleep(2000)

        // 8. 북마크 아이템 존재 확인
        val bookmarkItems = rule.onAllNodesWithTag("bookmarkItem").fetchSemanticsNodes()
        Log.d(TAG, "북마크 아이템 수: ${bookmarkItems.size}")
        assertTrue("북마크 리스트에 아이템이 없습니다.", bookmarkItems.isNotEmpty())

        // 9. 북마크 페이지 번호 캡처 (useUnmergedTree: clickable이 시맨틱스를 머지하므로)
        waitForTag("bookmarkPageText", useUnmergedTree = true)
        val pageNodes = rule.onAllNodesWithTag("bookmarkPageText", useUnmergedTree = true).fetchSemanticsNodes()
        val pageText = pageNodes.first().config[SemanticsProperties.Text].firstOrNull()?.text ?: ""
        val bookmarkPage = pageText.removePrefix("p.").trim().toInt()
        Log.d(TAG, "북마크 페이지 번호: $bookmarkPage")
        assertTrue("북마크 페이지 번호가 0보다 커야 함", bookmarkPage > 0)

        // 10. 북마크 탭하여 이동
        Log.d(TAG, "단계 10: 북마크 탭하여 이동")
        rule.onAllNodesWithTag("bookmarkItem").onFirst().performClick()
        Thread.sleep(3000)

        // 11. 이동 후 실제 페이지 번호 캡처
        val menuAfterBookmark = openMenuAndWaitForScan()
        assertTrue("북마크 이동 후 메뉴 열기 실패", menuAfterBookmark)

        val actualPage = getCurrentPage()
        Log.d(TAG, "실제 페이지: $actualPage")

        // 12. 북마크 리스트의 페이지 번호와 실제 페이지 번호 일치 확인
        assertEquals(
            "북마크 리스트 페이지($bookmarkPage)와 실제 이동 페이지($actualPage)가 다릅니다",
            bookmarkPage,
            actualPage
        )
        Log.d(TAG, "테스트 통과: 북마크 페이지($bookmarkPage) == 실제 페이지($actualPage)")
    }
}
