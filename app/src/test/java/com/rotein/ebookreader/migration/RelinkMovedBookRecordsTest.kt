package com.rotein.ebookreader.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.BookFile
import com.rotein.ebookreader.BookReadRecord
import com.rotein.ebookreader.Bookmark
import com.rotein.ebookreader.Highlight
import com.rotein.ebookreader.Memo
import com.rotein.ebookreader.relinkMovedBookRecords
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelinkMovedBookRecordsTest {

    private lateinit var db: BookDatabase

    private val oldUri =
        "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fa.epub"
    private val newUri =
        "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2FBooks%2Fa.epub"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `매칭되면 기록 북마크 하이라이트 메모가 새 URI로 옮겨지고 이전 기록은 삭제된다`() = runTest {
        db.bookReadRecordDao().insertReplace(
            BookReadRecord(bookPath = oldUri, lastReadAt = 1, lastCfi = "cfi-last", readingProgress = 0.5f)
        )
        db.bookmarkDao().insert(Bookmark(bookPath = oldUri, cfi = "b", createdAt = 1))
        db.highlightDao().insert(Highlight(bookPath = oldUri, cfi = "h", createdAt = 1))
        db.memoDao().insert(Memo(bookPath = oldUri, cfi = "m", createdAt = 1))

        val scanned = listOf(
            BookFile(
                name = "a.epub", path = "", contentUri = newUri, extension = "epub",
                size = 100, dateAdded = 0, dateModified = 0
            )
        )
        assertEquals(1, relinkMovedBookRecords(db, scanned))

        assertNull(db.bookReadRecordDao().getByPath(oldUri))
        val moved = db.bookReadRecordDao().getByPath(newUri)
        assertEquals("cfi-last", moved?.lastCfi)
        assertEquals(0.5f, moved?.readingProgress)
        assertEquals(1, db.bookmarkDao().getByBook(newUri).size)
        assertEquals(1, db.highlightDao().getByBook(newUri).size)
        assertEquals(1, db.memoDao().getByBook(newUri).size)
        assertTrue(db.bookmarkDao().getByBook(oldUri).isEmpty())
        assertTrue(db.highlightDao().getByBook(oldUri).isEmpty())
        assertTrue(db.memoDao().getByBook(oldUri).isEmpty())
    }

    @Test
    fun `매칭이 없으면 아무것도 바뀌지 않는다`() = runTest {
        db.bookReadRecordDao().insertReplace(BookReadRecord(bookPath = oldUri, lastReadAt = 1))

        assertEquals(0, relinkMovedBookRecords(db, emptyList()))

        assertNotNull(db.bookReadRecordDao().getByPath(oldUri))
    }
}
