package com.rotein.ebookreader.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.Memo
import com.rotein.ebookreader.MemoDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: MemoDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.memoDao()
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
        dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "quote1", note = "note1", createdAt = 100))
        dao.insert(Memo(bookPath = "/b.epub", cfi = "cfi2", text = "quote2", note = "note2", createdAt = 200))

        val memos = dao.getByBook("/a.epub")
        assertEquals(1, memos.size)
        assertEquals("quote1", memos[0].text)
    }

    @Test
    fun `getByBook - createdAt ASC 정렬`() = runTest {
        dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi2", text = "q2", createdAt = 200))
        dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "q1", createdAt = 100))

        val memos = dao.getByBook("/a.epub")
        assertEquals(100L, memos[0].createdAt)
        assertEquals(200L, memos[1].createdAt)
    }

    @Test
    fun `getByCfi - 존재하는 CFI`() = runTest {
        dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "quote", note = "my note", createdAt = 100))

        val memo = dao.getByCfi("/a.epub", "cfi1")
        assertNotNull(memo)
        assertEquals("my note", memo!!.note)
    }

    @Test
    fun `getByCfi - 존재하지 않는 CFI`() = runTest {
        assertNull(dao.getByCfi("/a.epub", "nonexistent"))
    }

    @Test
    fun `insert - ID 반환`() = runTest {
        val id = dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "quote"))
        assertTrue(id > 0)
    }

    @Test
    fun `updateNote - 메모 내용 수정`() = runTest {
        val id = dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "quote", note = "old note"))
        dao.updateNote(id, "new note")
        val memo = dao.getByCfi("/a.epub", "cfi1")
        assertEquals("new note", memo!!.note)
    }

    @Test
    fun `deleteById - 삭제 확인`() = runTest {
        val id = dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "quote"))
        dao.deleteById(id)
        assertTrue(dao.getByBook("/a.epub").isEmpty())
    }

    @Test
    fun `updatePage - 페이지 갱신`() = runTest {
        val id = dao.insert(Memo(bookPath = "/a.epub", cfi = "cfi1", text = "quote", page = 0))
        dao.updatePage(id, 33)
        val memos = dao.getByBook("/a.epub")
        assertEquals(33, memos[0].page)
    }
}
