package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class BookCatalogTest {

    private val metadata = BookMetadata(
        title = "삼체",
        author = "류츠신",
        language = "ko",
        publisher = "자음과모음",
        publishedDate = "2022-05-01",
        description = "설명"
    )

    private val safBook = BookFile(
        name = "삼체.epub",
        path = "",
        contentUri = "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2F삼체.epub",
        extension = "epub",
        size = 16_512_512L,
        dateAdded = 1_760_000_000L,
        dateModified = 1_760_000_001L,
        metadata = metadata
    )

    @Test
    fun `SAF 책 - 엔트리로 변환 후 복원하면 원본과 같다`() {
        val restored = safBook.toCatalogEntry().toBookFile()
        assertEquals(safBook, restored)
    }

    @Test
    fun `엔트리 키는 bookKey와 같다`() {
        assertEquals(safBook.bookKey(), safBook.toCatalogEntry().bookKey)
    }

    @Test
    fun `메타데이터가 없는 책 - null로 복원된다`() {
        val book = safBook.copy(metadata = null)
        assertNull(book.toCatalogEntry().toBookFile().metadata)
    }

    @Test
    fun `메타데이터 필드가 모두 null이어도 메타데이터 객체는 유지된다`() {
        val emptyMeta = BookMetadata(null, null, null, null, null, null)
        val book = safBook.copy(metadata = emptyMeta)
        assertEquals(emptyMeta, book.toCatalogEntry().toBookFile().metadata)
    }

    @Test
    fun `레거시 절대 경로 책 - path가 키가 되고 그대로 복원된다`() {
        val legacy = safBook.copy(path = "/storage/emulated/0/Books/a.epub", contentUri = "")
        val entry = legacy.toCatalogEntry()
        assertEquals("/storage/emulated/0/Books/a.epub", entry.bookKey)
        assertEquals(legacy, entry.toBookFile())
    }
}
