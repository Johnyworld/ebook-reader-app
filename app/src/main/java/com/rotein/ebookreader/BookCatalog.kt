package com.rotein.ebookreader

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * 마지막 스캔 결과(도서 목록 + 메타데이터)를 저장하는 테이블.
 * 앱 프로세스가 종료되어도(e-ink 기기의 앱 자동 프리즈 등) 다음 실행 시
 * 전체 재스캔 없이 즉시 목록을 표시하고, 변경분만 백그라운드에서 diff 스캔하기 위해 사용한다.
 */
@Entity(tableName = "book_catalog")
data class BookCatalogEntry(
    @PrimaryKey val bookKey: String,  // BookFile.bookKey() — SAF URI 또는 레거시 절대 경로
    val name: String,
    val path: String,
    val contentUri: String,
    val extension: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val hasMetadata: Boolean,  // 메타데이터 객체 존재 여부 (필드가 모두 null인 경우와 구분)
    val title: String?,
    val author: String?,
    val language: String?,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?
)

@Dao
interface BookCatalogDao {
    @Query("SELECT * FROM book_catalog")
    suspend fun getAll(): List<BookCatalogEntry>

    @Query("DELETE FROM book_catalog")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BookCatalogEntry>)

    /** 저장된 목록을 주어진 목록으로 통째로 교체한다 */
    @Transaction
    suspend fun replaceAll(entries: List<BookCatalogEntry>) {
        deleteAll()
        insertAll(entries)
    }
}

fun BookFile.toCatalogEntry(): BookCatalogEntry = BookCatalogEntry(
    bookKey = bookKey(),
    name = name,
    path = path,
    contentUri = contentUri,
    extension = extension,
    size = size,
    dateAdded = dateAdded,
    dateModified = dateModified,
    hasMetadata = metadata != null,
    title = metadata?.title,
    author = metadata?.author,
    language = metadata?.language,
    publisher = metadata?.publisher,
    publishedDate = metadata?.publishedDate,
    description = metadata?.description
)

fun BookCatalogEntry.toBookFile(): BookFile = BookFile(
    name = name,
    path = path,
    contentUri = contentUri,
    extension = extension,
    size = size,
    dateAdded = dateAdded,
    dateModified = dateModified,
    metadata = if (hasMetadata) {
        BookMetadata(title, author, language, publisher, publishedDate, description)
    } else null
)
