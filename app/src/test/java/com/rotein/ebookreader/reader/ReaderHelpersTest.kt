package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class ReaderHelpersTest {

    // --- cfiToPage ---

    @Test
    fun `cfiToPage - 정상 CFI 변환`() {
        // CFI: /6/4 → step=4, spineIndex=(4/2-1)=1
        val spinePageOffsets = mapOf(0 to 0, 1 to 10, 2 to 20)
        val page = cfiToPage("epubcfi(/6/4!/4/2/1:0)", spinePageOffsets)
        assertEquals(11, page) // spinePageOffsets[1] + 1 = 11
    }

    @Test
    fun `cfiToPage - 첫 번째 spine`() {
        // CFI: /6/2 → step=2, spineIndex=(2/2-1)=0
        val spinePageOffsets = mapOf(0 to 0, 1 to 10)
        val page = cfiToPage("epubcfi(/6/2!/4/2)", spinePageOffsets)
        assertEquals(1, page) // spinePageOffsets[0] + 1 = 1
    }

    @Test
    fun `cfiToPage - cfiPageMap에 있는 경우 우선 사용`() {
        val cfi = "epubcfi(/6/4!/4/2)"
        val page = cfiToPage(cfi, mapOf(0 to 0), mapOf(cfi to 42))
        assertEquals(42, page)
    }

    @Test
    fun `cfiToPage - 빈 CFI`() {
        assertEquals(0, cfiToPage("", mapOf(0 to 0)))
    }

    @Test
    fun `cfiToPage - 빈 spinePageOffsets`() {
        assertEquals(0, cfiToPage("epubcfi(/6/4!/4/2)", emptyMap()))
    }

    @Test
    fun `cfiToPage - 매칭 안 되는 CFI 형식`() {
        assertEquals(0, cfiToPage("invalid-cfi", mapOf(0 to 0)))
    }

    // --- escapeCfiForJs ---

    @Test
    fun `escapeCfiForJs - 백슬래시 이스케이프`() {
        assertEquals("a\\\\b", "a\\b".escapeCfiForJs())
    }

    @Test
    fun `escapeCfiForJs - 큰따옴표 이스케이프`() {
        assertEquals("a\\\"b", "a\"b".escapeCfiForJs())
    }

    @Test
    fun `escapeCfiForJs - 줄바꿈 이스케이프`() {
        assertEquals("a\\nb", "a\nb".escapeCfiForJs())
    }

    @Test
    fun `escapeCfiForJs - 특수문자 없으면 그대로`() {
        val cfi = "epubcfi(/6/4!/4/2/1:0)"
        assertEquals(cfi, cfi.escapeCfiForJs())
    }

    // --- layoutFingerprint ---

    @Test
    fun `layoutFingerprint - 기본 설정`() {
        val settings = ReaderSettings()
        val fp = settings.layoutFingerprint()
        assertEquals("epub_original|16|1.5|0|20|20|false|JUSTIFY", fp)
    }

    @Test
    fun `layoutFingerprint - 커스텀 설정`() {
        val settings = ReaderSettings(
            fontName = "NotoSans",
            fontSize = 20,
            lineHeight = 1.8f,
            paragraphSpacing = 5,
            paddingVertical = 30,
            paddingHorizontal = 15,
            dualPage = true,
            textAlign = ReaderTextAlign.LEFT
        )
        val fp = settings.layoutFingerprint()
        assertEquals("NotoSans|20|1.8|5|30|15|true|LEFT", fp)
    }

    // --- readerBottomInfoText ---

    private val testBook = BookFile(
        name = "test-book.epub",
        path = "/books/test-book.epub",
        extension = "epub",
        size = 1024,
        dateAdded = 0,
        dateModified = 0,
        metadata = BookMetadata(
            title = "테스트 책",
            author = "작가",
            language = null,
            publisher = null,
            publishedDate = null,
            description = null
        )
    )

    @Test
    fun `readerBottomInfoText - NONE`() {
        assertNull(readerBottomInfoText(ReaderBottomInfo.NONE, testBook, "Ch1", 5, 100, 0.05f, "12:00"))
    }

    @Test
    fun `readerBottomInfoText - BOOK_TITLE with metadata`() {
        assertEquals("테스트 책", readerBottomInfoText(ReaderBottomInfo.BOOK_TITLE, testBook, "", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - BOOK_TITLE without metadata`() {
        val bookNoMeta = testBook.copy(metadata = null)
        assertEquals("test-book.epub", readerBottomInfoText(ReaderBottomInfo.BOOK_TITLE, bookNoMeta, "", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - BOOK_TITLE with null title`() {
        val bookNullTitle = testBook.copy(metadata = BookMetadata(null, null, null, null, null, null))
        assertEquals("test-book.epub", readerBottomInfoText(ReaderBottomInfo.BOOK_TITLE, bookNullTitle, "", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - CHAPTER_TITLE`() {
        assertEquals("Chapter 1", readerBottomInfoText(ReaderBottomInfo.CHAPTER_TITLE, testBook, "Chapter 1", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - CHAPTER_TITLE empty`() {
        assertNull(readerBottomInfoText(ReaderBottomInfo.CHAPTER_TITLE, testBook, "", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - PAGE`() {
        assertEquals("5 / 100", readerBottomInfoText(ReaderBottomInfo.PAGE, testBook, "", 5, 100, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - PAGE with zero totalPages`() {
        assertNull(readerBottomInfoText(ReaderBottomInfo.PAGE, testBook, "", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - CLOCK`() {
        assertEquals("14:30", readerBottomInfoText(ReaderBottomInfo.CLOCK, testBook, "", 0, 0, 0f, "14:30"))
    }

    @Test
    fun `readerBottomInfoText - CLOCK empty`() {
        assertNull(readerBottomInfoText(ReaderBottomInfo.CLOCK, testBook, "", 0, 0, 0f, ""))
    }

    @Test
    fun `readerBottomInfoText - PROGRESS`() {
        assertEquals("45%", readerBottomInfoText(ReaderBottomInfo.PROGRESS, testBook, "", 0, 0, 0.45f, ""))
    }
}
