package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class BookFileTest {

    private fun book(size: Long, dateModified: Long) = BookFile(
        name = "a.epub", path = "", contentUri = "content://a", extension = "epub",
        size = size, dateAdded = 0, dateModified = dateModified
    )

    @Test
    fun `fileVersion - 크기와 수정 시각이 같으면 같은 버전`() {
        assertEquals(book(100, 5).fileVersion(), book(100, 5).fileVersion())
    }

    @Test
    fun `fileVersion - 크기가 같아도 수정 시각이 다르면 다른 버전`() {
        assertNotEquals(book(100, 5).fileVersion(), book(100, 6).fileVersion())
    }

    @Test
    fun `fileVersion - 수정 시각이 같아도 크기가 다르면 다른 버전`() {
        assertNotEquals(book(100, 5).fileVersion(), book(101, 5).fileVersion())
    }
}
