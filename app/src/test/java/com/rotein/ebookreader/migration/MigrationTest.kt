package com.rotein.ebookreader.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.rotein.ebookreader.ALL_MIGRATIONS
import com.rotein.ebookreader.BookDatabase
import com.rotein.ebookreader.MIGRATION_11_12
import com.rotein.ebookreader.MIGRATION_4_5
import com.rotein.ebookreader.MIGRATION_5_6
import com.rotein.ebookreader.MIGRATION_16_17
import com.rotein.ebookreader.MIGRATION_9_10
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun teardown() {
        context.deleteDatabase(TEST_DB_NAME)
    }

    private fun createDatabase(version: Int, onCreate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        context.deleteDatabase(TEST_DB_NAME)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    @Test
    fun `migration 4 to 5 - 데이터 보존`() {
        val db = createDatabase(4) { db ->
            db.execSQL(
                "CREATE TABLE book_read_records (" +
                "bookPath TEXT NOT NULL PRIMARY KEY, " +
                "lastReadAt INTEGER NOT NULL, " +
                "readingProgress REAL NOT NULL DEFAULT 0.0, " +
                "lastCfi TEXT NOT NULL DEFAULT '', " +
                "tocJson TEXT NOT NULL DEFAULT '', " +
                "totalPages INTEGER NOT NULL DEFAULT 0, " +
                "currentPage INTEGER NOT NULL DEFAULT 0)"
            )
        }
        db.execSQL("INSERT INTO book_read_records (bookPath, lastReadAt, readingProgress, lastCfi, tocJson, totalPages, currentPage) VALUES ('/book.epub', 1000, 0.5, 'cfi1', '{\"toc\":true}', 100, 50)")

        MIGRATION_4_5.migrate(db)

        val cursor = db.query("SELECT * FROM book_read_records")
        assertTrue(cursor.moveToFirst())
        assertEquals("/book.epub", cursor.getString(cursor.getColumnIndexOrThrow("bookPath")))
        assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastReadAt")))
        assertEquals("cfi1", cursor.getString(cursor.getColumnIndexOrThrow("lastCfi")))
        assertEquals("{\"toc\":true}", cursor.getString(cursor.getColumnIndexOrThrow("tocJson")))
        assertEquals(100, cursor.getInt(cursor.getColumnIndexOrThrow("totalPages")))
        assertEquals(-1, cursor.getColumnIndex("currentPage"))
        cursor.close()
        db.close()
    }

    @Test
    fun `migration 5 to 6 - 데이터 보존`() {
        val db = createDatabase(5) { db ->
            db.execSQL(
                "CREATE TABLE book_read_records (" +
                "bookPath TEXT NOT NULL PRIMARY KEY, " +
                "lastReadAt INTEGER NOT NULL, " +
                "readingProgress REAL NOT NULL DEFAULT 0.0, " +
                "lastCfi TEXT NOT NULL DEFAULT '', " +
                "tocJson TEXT NOT NULL DEFAULT '', " +
                "totalPages INTEGER NOT NULL DEFAULT 0)"
            )
        }
        db.execSQL("INSERT INTO book_read_records (bookPath, lastReadAt, readingProgress, lastCfi, tocJson, totalPages) VALUES ('/book.epub', 2000, 0.75, 'cfi2', '{\"toc\":2}', 200)")

        MIGRATION_5_6.migrate(db)

        val cursor = db.query("SELECT * FROM book_read_records")
        assertTrue(cursor.moveToFirst())
        assertEquals("/book.epub", cursor.getString(cursor.getColumnIndexOrThrow("bookPath")))
        assertEquals(2000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastReadAt")))
        assertEquals("cfi2", cursor.getString(cursor.getColumnIndexOrThrow("lastCfi")))
        assertEquals("{\"toc\":2}", cursor.getString(cursor.getColumnIndexOrThrow("tocJson")))
        assertEquals(200, cursor.getInt(cursor.getColumnIndexOrThrow("totalPages")))
        assertEquals(-1, cursor.getColumnIndex("readingProgress"))
        cursor.close()
        db.close()
    }

    @Test
    fun `migration 9 to 10 - highlights 재생성`() {
        val db = createDatabase(9) { db ->
            db.execSQL(
                "CREATE TABLE highlights (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "bookPath TEXT NOT NULL, " +
                "cfi TEXT NOT NULL, " +
                "text TEXT NOT NULL DEFAULT '', " +
                "chapterTitle TEXT NOT NULL DEFAULT '', " +
                "page INTEGER NOT NULL DEFAULT 0, " +
                "createdAt INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX index_highlights_bookPath ON highlights (bookPath)")
        }
        db.execSQL("INSERT INTO highlights (bookPath, cfi, text) VALUES ('/book.epub', 'cfi1', 'old data')")

        MIGRATION_9_10.migrate(db)

        val cursor = db.query("SELECT * FROM highlights")
        assertEquals(0, cursor.count)
        cursor.close()
        db.close()
    }

    @Test
    fun `migration 11 to 12 - 핵심 데이터 보존`() {
        val db = createDatabase(11) { db ->
            db.execSQL(
                "CREATE TABLE book_read_records (" +
                "bookPath TEXT NOT NULL PRIMARY KEY, " +
                "lastReadAt INTEGER NOT NULL, " +
                "lastCfi TEXT NOT NULL DEFAULT '', " +
                "tocJson TEXT NOT NULL DEFAULT '', " +
                "totalPages INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "CREATE TABLE bookmarks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "bookPath TEXT NOT NULL, " +
                "cfi TEXT NOT NULL, " +
                "chapterTitle TEXT NOT NULL DEFAULT '', " +
                "excerpt TEXT NOT NULL DEFAULT '', " +
                "page INTEGER NOT NULL DEFAULT 0, " +
                "createdAt INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX index_bookmarks_bookPath ON bookmarks (bookPath)")
            db.execSQL(
                "CREATE TABLE highlights (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "bookPath TEXT NOT NULL, " +
                "cfi TEXT NOT NULL, " +
                "text TEXT NOT NULL DEFAULT '', " +
                "chapterTitle TEXT NOT NULL DEFAULT '', " +
                "page INTEGER NOT NULL DEFAULT 0, " +
                "createdAt INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX index_highlights_bookPath ON highlights (bookPath)")
            db.execSQL(
                "CREATE TABLE memos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "bookPath TEXT NOT NULL, " +
                "cfi TEXT NOT NULL, " +
                "text TEXT NOT NULL DEFAULT '', " +
                "note TEXT NOT NULL DEFAULT '', " +
                "chapterTitle TEXT NOT NULL DEFAULT '', " +
                "page INTEGER NOT NULL DEFAULT 0, " +
                "createdAt INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX index_memos_bookPath ON memos (bookPath)")
        }

        db.execSQL("INSERT INTO book_read_records (bookPath, lastReadAt, lastCfi, tocJson, totalPages) VALUES ('/book.epub', 3000, 'cfi3', '{\"toc\":3}', 300)")
        db.execSQL("INSERT INTO bookmarks (bookPath, cfi, chapterTitle, excerpt, page, createdAt) VALUES ('/book.epub', 'bm-cfi', 'Ch1', 'some text', 10, 500)")
        db.execSQL("INSERT INTO highlights (bookPath, cfi, text, chapterTitle, page, createdAt) VALUES ('/book.epub', 'hl-cfi', 'highlighted', 'Ch2', 20, 600)")
        db.execSQL("INSERT INTO memos (bookPath, cfi, text, note, chapterTitle, page, createdAt) VALUES ('/book.epub', 'mm-cfi', 'quoted', 'my note', 'Ch3', 30, 700)")

        MIGRATION_11_12.migrate(db)

        // book_read_records: totalPages 제거, 나머지 보존
        var cursor = db.query("SELECT * FROM book_read_records")
        assertTrue(cursor.moveToFirst())
        assertEquals("/book.epub", cursor.getString(cursor.getColumnIndexOrThrow("bookPath")))
        assertEquals(3000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastReadAt")))
        assertEquals("cfi3", cursor.getString(cursor.getColumnIndexOrThrow("lastCfi")))
        assertEquals(-1, cursor.getColumnIndex("totalPages"))
        cursor.close()

        // bookmarks: page 제거, 나머지 보존
        cursor = db.query("SELECT * FROM bookmarks")
        assertTrue(cursor.moveToFirst())
        assertEquals("bm-cfi", cursor.getString(cursor.getColumnIndexOrThrow("cfi")))
        assertEquals("some text", cursor.getString(cursor.getColumnIndexOrThrow("excerpt")))
        assertEquals(-1, cursor.getColumnIndex("page"))
        cursor.close()

        // highlights: page 제거, 나머지 보존
        cursor = db.query("SELECT * FROM highlights")
        assertTrue(cursor.moveToFirst())
        assertEquals("highlighted", cursor.getString(cursor.getColumnIndexOrThrow("text")))
        assertEquals(-1, cursor.getColumnIndex("page"))
        cursor.close()

        // memos: page 제거, 나머지 보존
        cursor = db.query("SELECT * FROM memos")
        assertTrue(cursor.moveToFirst())
        assertEquals("my note", cursor.getString(cursor.getColumnIndexOrThrow("note")))
        assertEquals(-1, cursor.getColumnIndex("page"))
        cursor.close()

        db.close()
    }

    @Test
    fun `migration 16 to 17 - readingProgress 컬럼 추가`() {
        val db = createDatabase(16) { db ->
            db.execSQL(
                "CREATE TABLE book_read_records (" +
                "bookPath TEXT NOT NULL PRIMARY KEY, " +
                "lastReadAt INTEGER NOT NULL, " +
                "lastCfi TEXT NOT NULL DEFAULT '', " +
                "tocJson TEXT NOT NULL DEFAULT '', " +
                "cachedTotalPages INTEGER NOT NULL DEFAULT 0, " +
                "cachedSpinePageOffsetsJson TEXT NOT NULL DEFAULT '', " +
                "cachedSpineCharPageBreaksJson TEXT NOT NULL DEFAULT '', " +
                "cachedSettingsFingerprint TEXT NOT NULL DEFAULT '', " +
                "isFavorite INTEGER NOT NULL DEFAULT 0, " +
                "isHidden INTEGER NOT NULL DEFAULT 0)"
            )
        }
        db.execSQL("INSERT INTO book_read_records (bookPath, lastReadAt, isFavorite, isHidden) VALUES ('/book.epub', 5000, 1, 0)")

        MIGRATION_16_17.migrate(db)

        val cursor = db.query("SELECT * FROM book_read_records")
        assertTrue(cursor.moveToFirst())
        assertEquals("/book.epub", cursor.getString(cursor.getColumnIndexOrThrow("bookPath")))
        assertEquals(5000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastReadAt")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isFavorite")))
        // 새로 추가된 readingProgress 컬럼 기본값 0.0 확인
        assertEquals(0.0f, cursor.getFloat(cursor.getColumnIndexOrThrow("readingProgress")), 0.001f)
        cursor.close()
        db.close()
    }

    @Test
    fun `전체 마이그레이션 v1 to v15 - Room DB 정상 오픈`() {
        val db = Room.databaseBuilder(context, BookDatabase::class.java, TEST_DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        db.openHelper.writableDatabase
        db.close()
    }

    companion object {
        private const val TEST_DB_NAME = "test_migration.db"
    }
}
