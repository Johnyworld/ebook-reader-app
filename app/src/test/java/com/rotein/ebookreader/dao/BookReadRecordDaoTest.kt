package com.rotein.ebookreader.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.BookReadRecord
import com.rotein.ebookreader.BookReadRecordDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookReadRecordDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: BookReadRecordDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookReadRecordDao()
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
    fun `getAll - 여러 레코드 조회`() = runTest {
        dao.insertIfNotExists(BookReadRecord(bookPath = "/a.epub", lastReadAt = 100))
        dao.insertIfNotExists(BookReadRecord(bookPath = "/b.epub", lastReadAt = 200))

        val all = dao.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun `getByPath - 존재하는 경로`() = runTest {
        dao.insertIfNotExists(BookReadRecord(bookPath = "/a.epub", lastReadAt = 100))

        val record = dao.getByPath("/a.epub")
        assertNotNull(record)
        assertEquals("/a.epub", record!!.bookPath)
        assertEquals(100L, record.lastReadAt)
    }

    @Test
    fun `getByPath - 존재하지 않는 경로`() = runTest {
        assertNull(dao.getByPath("/nonexistent.epub"))
    }

    @Test
    fun `insertIfNotExists - 신규 삽입`() = runTest {
        dao.insertIfNotExists(BookReadRecord(bookPath = "/a.epub", lastReadAt = 100))
        assertNotNull(dao.getByPath("/a.epub"))
    }

    @Test
    fun `insertIfNotExists - 이미 존재하면 무시`() = runTest {
        dao.insertIfNotExists(BookReadRecord(bookPath = "/a.epub", lastReadAt = 100))
        dao.insertIfNotExists(BookReadRecord(bookPath = "/a.epub", lastReadAt = 999))

        val record = dao.getByPath("/a.epub")
        assertEquals(100L, record!!.lastReadAt)
    }

    @Test
    fun `upsertLastReadAt - 신규 경로`() = runTest {
        dao.upsertLastReadAt("/a.epub", 500)

        val record = dao.getByPath("/a.epub")
        assertNotNull(record)
        assertEquals(500L, record!!.lastReadAt)
    }

    @Test
    fun `upsertLastReadAt - 기존 경로 갱신`() = runTest {
        dao.insertIfNotExists(BookReadRecord(bookPath = "/a.epub", lastReadAt = 100))
        dao.upsertLastReadAt("/a.epub", 999)

        assertEquals(999L, dao.getByPath("/a.epub")!!.lastReadAt)
    }

    @Test
    fun `upsertCfi - CFI 저장 및 갱신`() = runTest {
        dao.upsertCfi("/a.epub", "epubcfi(/6/4!/4/2)")

        val record = dao.getByPath("/a.epub")
        assertEquals("epubcfi(/6/4!/4/2)", record!!.lastCfi)

        dao.upsertCfi("/a.epub", "epubcfi(/6/8!/4/2)")
        assertEquals("epubcfi(/6/8!/4/2)", dao.getByPath("/a.epub")!!.lastCfi)
    }

    @Test
    fun `upsertTocJson - TOC JSON 저장`() = runTest {
        val tocJson = """[{"label":"Ch1","href":"ch1.xhtml"}]"""
        dao.upsertTocJson("/a.epub", tocJson)

        assertEquals(tocJson, dao.getByPath("/a.epub")!!.tocJson)
    }

    @Test
    fun `upsertPageScanCache - 페이지 캐시 저장`() = runTest {
        dao.upsertPageScanCache("/a.epub", 150, """{"0":0,"1":50}""", """{"0":[0,100]}""", "font|16|1.5")

        val record = dao.getByPath("/a.epub")!!
        assertEquals(150, record.cachedTotalPages)
        assertEquals("""{"0":0,"1":50}""", record.cachedSpinePageOffsetsJson)
        assertEquals("""{"0":[0,100]}""", record.cachedSpineCharPageBreaksJson)
        assertEquals("font|16|1.5", record.cachedSettingsFingerprint)
    }
}
