package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer

class ExternalBookTest {

    private fun book(uri: String, name: String, size: Long = 100) = BookFile(
        name = name, path = "", contentUri = uri, extension = name.substringAfterLast('.'),
        size = size, dateAdded = 0, dateModified = 0
    )

    // ── findLibraryMatch: 외부에서 연 파일과 같은 서재 책 찾기 ──

    @Test
    fun `이름과 크기가 같은 책이 하나면 그 책을 반환한다`() {
        val target = book("content://lib/a", "A Book.epub", size = 100)
        val catalog = listOf(target, book("content://lib/b", "Other.epub", size = 100))
        assertEquals(target, findLibraryMatch(catalog, "A Book.epub", 100))
    }

    @Test
    fun `이름이 같아도 크기가 다르면 매칭하지 않는다`() {
        val catalog = listOf(book("content://lib/a", "A Book.epub", size = 100))
        assertNull(findLibraryMatch(catalog, "A Book.epub", 200))
    }

    @Test
    fun `후보가 여럿이면 어느 책인지 알 수 없으므로 매칭하지 않는다`() {
        val catalog = listOf(
            book("content://lib/a", "A Book.epub", size = 100),
            book("content://lib/b", "A Book.epub", size = 100)
        )
        assertNull(findLibraryMatch(catalog, "A Book.epub", 100))
    }

    @Test
    fun `대소문자와 NFC NFD 차이가 있어도 매칭된다`() {
        val target = book("content://lib/a", Normalizer.normalize("한글 책.epub", Normalizer.Form.NFC), size = 100)
        val nfdUpper = Normalizer.normalize("한글 책.EPUB", Normalizer.Form.NFD)
        assertEquals(target, findLibraryMatch(listOf(target), nfdUpper, 100))
    }

    @Test
    fun `크기를 알 수 없으면 매칭하지 않는다`() {
        val catalog = listOf(book("content://lib/a", "A Book.epub", size = 100))
        assertNull(findLibraryMatch(catalog, "A Book.epub", null))
    }

    // ── externalBookExtension: 파일명/MIME에서 지원 확장자 결정 ──

    @Test
    fun `파일명 확장자가 지원 형식이면 소문자로 반환한다`() {
        assertEquals("epub", externalBookExtension("Book.EPUB", null))
        assertEquals("pdf", externalBookExtension("doc.pdf", "application/octet-stream"))
    }

    @Test
    fun `파일명에 확장자가 없으면 MIME 타입으로 결정한다`() {
        assertEquals("epub", externalBookExtension("12345", "application/epub+zip"))
        assertEquals("pdf", externalBookExtension("12345", "application/pdf"))
    }

    @Test
    fun `지원하지 않는 형식이면 null을 반환한다`() {
        assertNull(externalBookExtension("archive.zip", "application/zip"))
        assertNull(externalBookExtension("12345", null))
    }
}
