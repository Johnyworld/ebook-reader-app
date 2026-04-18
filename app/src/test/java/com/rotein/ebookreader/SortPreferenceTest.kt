package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class SortPreferenceTest {

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
    fun `FilterMode - labelRes 매핑 확인`() {
        assertEquals(R.string.filter_all, FilterMode.ALL.labelRes)
        assertEquals(R.string.filter_favorite, FilterMode.FAVORITE.labelRes)
        assertEquals(R.string.filter_hidden, FilterMode.HIDDEN.labelRes)
    }

    // --- BookmarkSortOrder ---

    @Test
    fun `BookmarkSortOrder - labelRes 매핑 확인`() {
        assertEquals(R.string.sort_created_asc, BookmarkSortOrder.CREATED_ASC.labelRes)
        assertEquals(R.string.sort_created_desc, BookmarkSortOrder.CREATED_DESC.labelRes)
        assertEquals(R.string.sort_page_asc, BookmarkSortOrder.PAGE_ASC.labelRes)
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
