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

@Entity(tableName = "book_read_records")
data class BookReadRecord(
    @PrimaryKey val bookPath: String,
    val lastReadAt: Long
)

@Dao
interface BookReadRecordDao {
    @Query("SELECT * FROM book_read_records")
    suspend fun getAll(): List<BookReadRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BookReadRecord)
}

@Database(entities = [BookReadRecord::class], version = 1)
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
                ).build().also { INSTANCE = it }
            }
    }
}
