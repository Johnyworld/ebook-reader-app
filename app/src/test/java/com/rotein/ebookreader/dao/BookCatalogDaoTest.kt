package com.rotein.ebookreader.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.BookCatalogDao
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.BookFile
import com.rotein.ebookreader.toCatalogEntry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookCatalogDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: BookCatalogDao

    private fun book(name: String) = BookFile(
        name = name,
        path = "",
        contentUri = "content://test/$name",
        extension = "epub",
        size = 100L,
        dateAdded = 1L,
        dateModified = 1L
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookCatalogDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `getAll - 빈 DB`() = runTest {
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `replaceAll - 저장한 목록을 그대로 조회한다`() = runTest {
        dao.replaceAll(listOf(book("a.epub"), book("b.epub")).map { it.toCatalogEntry() })
        val keys = dao.getAll().map { it.bookKey }.toSet()
        assertEquals(setOf("content://test/a.epub", "content://test/b.epub"), keys)
    }

    @Test
    fun `replaceAll - 이전 목록에만 있던 책은 삭제된다`() = runTest {
        dao.replaceAll(listOf(book("a.epub"), book("b.epub")).map { it.toCatalogEntry() })
        dao.replaceAll(listOf(book("b.epub"), book("c.epub")).map { it.toCatalogEntry() })
        val keys = dao.getAll().map { it.bookKey }.toSet()
        assertEquals(setOf("content://test/b.epub", "content://test/c.epub"), keys)
    }

    @Test
    fun `replaceAll - 빈 목록이면 모두 삭제된다`() = runTest {
        dao.replaceAll(listOf(book("a.epub")).map { it.toCatalogEntry() })
        dao.replaceAll(emptyList())
        assertTrue(dao.getAll().isEmpty())
    }
}
