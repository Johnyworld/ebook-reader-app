package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class SortPreferenceTest {

    // --- extractFontFamilyName (private이지만 fontDisplayName, getSystemFontFamilies를 통해 간접 테스트 불가하므로
    //     공개 함수/enum 위주로 테스트) ---

    // --- fontDisplayName ---

    @Test
    fun `fontDisplayName - epub original`() {
        assertEquals("전자책 글꼴", fontDisplayName(FONT_EPUB_ORIGINAL))
    }

    @Test
    fun `fontDisplayName - system font`() {
        assertEquals("시스템 글꼴", fontDisplayName(FONT_SYSTEM))
    }

    @Test
    fun `fontDisplayName - 커스텀 폰트명 그대로 반환`() {
        assertEquals("NotoSansKR", fontDisplayName("NotoSansKR"))
    }

    // --- SortField ---

    @Test
    fun `SortField - defaultDescending - DATE_ADDED와 LAST_READ만 true`() {
        assertTrue(SortField.DATE_ADDED.defaultDescending)
        assertTrue(SortField.LAST_READ.defaultDescending)
        assertFalse(SortField.TITLE.defaultDescending)
        assertFalse(SortField.AUTHOR.defaultDescending)
    }

    // --- SortPreference 기본값 ---

    @Test
    fun `SortPreference - 기본 정렬 필드는 LAST_READ`() {
        val pref = SortPreference()
        assertEquals(SortField.LAST_READ, pref.field)
    }

    // --- FilterMode ---

    @Test
    fun `FilterMode - label 확인`() {
        assertEquals("전체보기", FilterMode.ALL.label)
        assertEquals("즐겨찾기", FilterMode.FAVORITE.label)
        assertEquals("숨김보기", FilterMode.HIDDEN.label)
    }

    // --- BookmarkSortOrder ---

    @Test
    fun `BookmarkSortOrder - 모든 값 label 확인`() {
        assertEquals("등록순", BookmarkSortOrder.CREATED_ASC.label)
        assertEquals("최신순", BookmarkSortOrder.CREATED_DESC.label)
        assertEquals("페이지순", BookmarkSortOrder.PAGE_ASC.label)
    }

    // --- ReaderPageFlip ---

    @Test
    fun `ReaderPageFlip - entries 4개`() {
        assertEquals(4, ReaderPageFlip.entries.size)
    }

    // --- ReaderBottomInfo ---

    @Test
    fun `ReaderBottomInfo - NONE 포함 6개`() {
        assertEquals(6, ReaderBottomInfo.entries.size)
        assertNotNull(ReaderBottomInfo.entries.find { it == ReaderBottomInfo.NONE })
    }

    // --- ReaderSettings 기본값 ---

    @Test
    fun `ReaderSettings - 기본값 확인`() {
        val s = ReaderSettings()
        assertEquals(FONT_EPUB_ORIGINAL, s.fontName)
        assertEquals(16, s.fontSize)
        assertEquals(ReaderTextAlign.JUSTIFY, s.textAlign)
        assertEquals(1.5f, s.lineHeight, 0.001f)
        assertEquals(0, s.paragraphSpacing)
        assertEquals(20, s.paddingVertical)
        assertEquals(20, s.paddingHorizontal)
        assertEquals(ReaderPageFlip.LR_PREV_NEXT, s.pageFlip)
        assertEquals(ReaderBottomInfo.NONE, s.leftInfo)
        assertEquals(ReaderBottomInfo.PAGE, s.rightInfo)
        assertFalse(s.dualPage)
    }
}
