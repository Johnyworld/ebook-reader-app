package com.rotein.ebookreader.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.Highlight
import com.rotein.ebookreader.HighlightDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HighlightDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: HighlightDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.highlightDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `getByBook - 빈 결과`() = runTest {
        assertTrue(dao.getByBook("/a.epub").isEmpty())
    }

    @Test
    fun `getByBook - 해당 bookPath만 필터링`() = runTest {
        dao.insert(Highlight(bookPath = "/a.epub", cfi = "cfi1", text = "hello", createdAt = 100))
        dao.insert(Highlight(bookPath = "/b.epub", cfi = "cfi2", text = "world", createdAt = 200))

        val highlights = dao.getByBook("/a.epub")
        assertEquals(1, highlights.size)
        assertEquals("hello", highlights[0].text)
    }

    @Test
    fun `getByBook - createdAt ASC 정렬`() = runTest {
        dao.insert(Highlight(bookPath = "/a.epub", cfi = "cfi2", text = "second", createdAt = 200))
        dao.insert(Highlight(bookPath = "/a.epub", cfi = "cfi1", text = "first", createdAt = 100))

        val highlights = dao.getByBook("/a.epub")
        assertEquals(100L, highlights[0].createdAt)
        assertEquals(200L, highlights[1].createdAt)
    }

    @Test
    fun `insert - ID 반환`() = runTest {
        val id = dao.insert(Highlight(bookPath = "/a.epub", cfi = "cfi1", text = "hello"))
        assertTrue(id > 0)
    }

    @Test
    fun `deleteById - 삭제 확인`() = runTest {
        val id = dao.insert(Highlight(bookPath = "/a.epub", cfi = "cfi1", text = "hello"))
        dao.deleteById(id)
        assertTrue(dao.getByBook("/a.epub").isEmpty())
    }

    @Test
    fun `updatePage - 페이지 갱신`() = runTest {
        val id = dao.insert(Highlight(bookPath = "/a.epub", cfi = "cfi1", text = "hello", page = 0))
        dao.updatePage(id, 15)
        val highlights = dao.getByBook("/a.epub")
        assertEquals(15, highlights[0].page)
    }
}
