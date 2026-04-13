package com.rotein.ebookreader.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.Bookmark
import com.rotein.ebookreader.BookmarkDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookmarkDao()
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
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi1", createdAt = 100))
        dao.insert(Bookmark(bookPath = "/b.epub", cfi = "cfi2", createdAt = 200))
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi3", createdAt = 300))

        val bookmarks = dao.getByBook("/a.epub")
        assertEquals(2, bookmarks.size)
        assertTrue(bookmarks.all { it.bookPath == "/a.epub" })
    }

    @Test
    fun `getByBook - createdAt ASC 정렬`() = runTest {
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi2", createdAt = 300))
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi1", createdAt = 100))
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi3", createdAt = 200))

        val bookmarks = dao.getByBook("/a.epub")
        assertEquals(100L, bookmarks[0].createdAt)
        assertEquals(200L, bookmarks[1].createdAt)
        assertEquals(300L, bookmarks[2].createdAt)
    }

    @Test
    fun `insert - ID 반환`() = runTest {
        val id = dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi1"))
        assertTrue(id > 0)
    }

    @Test
    fun `deleteByCfi - 삭제 확인`() = runTest {
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi1"))
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi2"))

        dao.deleteByCfi("/a.epub", "cfi1")

        val bookmarks = dao.getByBook("/a.epub")
        assertEquals(1, bookmarks.size)
        assertEquals("cfi2", bookmarks[0].cfi)
    }

    @Test
    fun `deleteByCfi - 다른 책의 같은 CFI는 유지`() = runTest {
        dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi1"))
        dao.insert(Bookmark(bookPath = "/b.epub", cfi = "cfi1"))

        dao.deleteByCfi("/a.epub", "cfi1")

        assertTrue(dao.getByBook("/a.epub").isEmpty())
        assertEquals(1, dao.getByBook("/b.epub").size)
    }

    @Test
    fun `updatePage - 페이지 갱신`() = runTest {
        val id = dao.insert(Bookmark(bookPath = "/a.epub", cfi = "cfi1", page = 0))

        dao.updatePage(id, 42)

        val bookmarks = dao.getByBook("/a.epub")
        assertEquals(42, bookmarks[0].page)
    }
}
