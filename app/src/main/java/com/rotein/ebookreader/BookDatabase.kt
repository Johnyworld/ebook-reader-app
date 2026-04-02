package com.rotein.ebookreader

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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
    val readingProgress: Float = 0f,
    val lastCfi: String = ""
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

    @Query("UPDATE book_read_records SET readingProgress = :progress, lastCfi = :cfi WHERE bookPath = :bookPath")
    suspend fun updateProgress(bookPath: String, progress: Float, cfi: String)

    @Transaction
    suspend fun upsertLastReadAt(bookPath: String, lastReadAt: Long) {
        insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = lastReadAt))
        updateLastReadAt(bookPath, lastReadAt)
    }

    @Transaction
    suspend fun upsertProgress(bookPath: String, progress: Float, cfi: String) {
        insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = 0L))
        updateProgress(bookPath, progress, cfi)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN readingProgress REAL NOT NULL DEFAULT 0.0")
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN lastCfi TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [BookReadRecord::class], version = 2)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookReadRecordDao(): BookReadRecordDao

    companion object {
        @Volatile private var INSTANCE: BookDatabase? = null

        fun getInstance(context: Context): BookDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "book_database"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
