package com.rotein.ebookreader

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "book_read_records")
data class BookReadRecord(
    @PrimaryKey val bookPath: String,
    val lastReadAt: Long,
    val lastCfi: String = "",
    val tocJson: String = "",
    val totalPages: Int = 0
)

@Dao
interface BookReadRecordDao {
    @Query("SELECT * FROM book_read_records")
    suspend fun getAll(): List<BookReadRecord>

    @Query("SELECT * FROM book_read_records WHERE bookPath = :bookPath LIMIT 1")
    suspend fun getByPath(bookPath: String): BookReadRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(record: BookReadRecord)

    @Query("UPDATE book_read_records SET lastReadAt = :lastReadAt WHERE bookPath = :bookPath")
    suspend fun updateLastReadAt(bookPath: String, lastReadAt: Long)

    @Query("UPDATE book_read_records SET lastCfi = :cfi WHERE bookPath = :bookPath")
    suspend fun updateCfi(bookPath: String, cfi: String)

    @Query("UPDATE book_read_records SET tocJson = :tocJson WHERE bookPath = :bookPath")
    suspend fun updateTocJson(bookPath: String, tocJson: String)

    @Query("UPDATE book_read_records SET totalPages = :totalPages WHERE bookPath = :bookPath")
    suspend fun updateTotalPages(bookPath: String, totalPages: Int)

    @Transaction
    suspend fun upsertLastReadAt(bookPath: String, lastReadAt: Long) {
        insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = lastReadAt))
        updateLastReadAt(bookPath, lastReadAt)
    }

    @Transaction
    suspend fun upsertCfi(bookPath: String, cfi: String) {
        insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = 0L))
        updateCfi(bookPath, cfi)
    }

    @Transaction
    suspend fun upsertTocJson(bookPath: String, tocJson: String) {
        insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = 0L))
        updateTocJson(bookPath, tocJson)
    }

    @Transaction
    suspend fun upsertTotalPages(bookPath: String, totalPages: Int) {
        insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = 0L))
        updateTotalPages(bookPath, totalPages)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN readingProgress REAL NOT NULL DEFAULT 0.0")
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN lastCfi TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN tocJson TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN totalPages INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN currentPage INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // currentPage 컬럼은 SQLite에서 DROP COLUMN이 불가하므로 테이블 재생성
        database.execSQL("CREATE TABLE book_read_records_new (bookPath TEXT NOT NULL PRIMARY KEY, lastReadAt INTEGER NOT NULL, readingProgress REAL NOT NULL DEFAULT 0.0, lastCfi TEXT NOT NULL DEFAULT '', tocJson TEXT NOT NULL DEFAULT '', totalPages INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("INSERT INTO book_read_records_new SELECT bookPath, lastReadAt, readingProgress, lastCfi, tocJson, totalPages FROM book_read_records")
        database.execSQL("DROP TABLE book_read_records")
        database.execSQL("ALTER TABLE book_read_records_new RENAME TO book_read_records")
    }
}

@Entity(tableName = "bookmarks", indices = [Index("bookPath")])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookPath: String,
    val cfi: String,
    val chapterTitle: String = "",
    val page: Int = 0,
    val excerpt: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookPath = :bookPath ORDER BY createdAt ASC")
    suspend fun getByBook(bookPath: String): List<Bookmark>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE bookPath = :bookPath AND cfi = :cfi")
    suspend fun deleteByCfi(bookPath: String, cfi: String)
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE book_read_records_new (bookPath TEXT NOT NULL PRIMARY KEY, lastReadAt INTEGER NOT NULL, lastCfi TEXT NOT NULL DEFAULT '', tocJson TEXT NOT NULL DEFAULT '', totalPages INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("INSERT INTO book_read_records_new SELECT bookPath, lastReadAt, lastCfi, tocJson, totalPages FROM book_read_records")
        database.execSQL("DROP TABLE book_read_records")
        database.execSQL("ALTER TABLE book_read_records_new RENAME TO book_read_records")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookPath TEXT NOT NULL, cfi TEXT NOT NULL, chapterTitle TEXT NOT NULL DEFAULT '', page INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("CREATE INDEX index_bookmarks_bookPath ON bookmarks (bookPath)")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE bookmarks ADD COLUMN excerpt TEXT NOT NULL DEFAULT ''")
    }
}

@Entity(tableName = "highlights", indices = [Index("bookPath")])
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookPath: String,
    val cfi: String,
    val text: String = "",
    val chapterTitle: String = "",
    val page: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookPath = :bookPath ORDER BY createdAt ASC")
    suspend fun getByBook(bookPath: String): List<Highlight>

    @Insert
    suspend fun insert(highlight: Highlight): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookPath TEXT NOT NULL, cfi TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', chapterTitle TEXT NOT NULL DEFAULT '', page INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_bookPath ON highlights (bookPath)")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // highlights 테이블 스키마가 잘못된 경우(selectedText 컬럼 등) 재생성
        database.execSQL("DROP TABLE IF EXISTS highlights")
        database.execSQL("CREATE TABLE highlights (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookPath TEXT NOT NULL, cfi TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', chapterTitle TEXT NOT NULL DEFAULT '', page INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL DEFAULT 0)")
        database.execSQL("CREATE INDEX index_highlights_bookPath ON highlights (bookPath)")
    }
}

@Database(entities = [BookReadRecord::class, Bookmark::class, Highlight::class], version = 10)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookReadRecordDao(): BookReadRecordDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile private var INSTANCE: BookDatabase? = null

        fun getInstance(context: Context): BookDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "book_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build().also { INSTANCE = it }
            }
    }
}
