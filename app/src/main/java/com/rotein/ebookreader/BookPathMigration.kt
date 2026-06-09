package com.rotein.ebookreader

import android.content.Context

/**
 * 기존 절대 경로 기반 DB 레코드를 SAF content URI 기반으로 마이그레이션한다.
 * 파일명 매칭으로 기존 읽기 기록, 북마크, 하이라이트, 메모를 보존한다.
 */
suspend fun migrateBookPathsToUri(context: Context, scannedBooks: List<BookFile>) {
    val db = BookDatabase.getInstance(context)
    val dao = db.bookReadRecordDao()
    val bookmarkDao = db.bookmarkDao()
    val highlightDao = db.highlightDao()
    val memoDao = db.memoDao()

    val records = dao.getAll()
    // 절대 경로 형식인 레코드만 대상 (content:// 로 시작하지 않는 것)
    val legacyRecords = records.filter {
        it.bookPath.startsWith("/") && !it.bookPath.startsWith("content://")
    }
    if (legacyRecords.isEmpty()) return

    for (record in legacyRecords) {
        val oldPath = record.bookPath
        val fileName = oldPath.substringAfterLast('/').lowercase()
        // 파일명으로 매칭 (대소문자 무시)
        val match = scannedBooks.firstOrNull {
            it.contentUri.isNotEmpty() && it.name.lowercase() == fileName
        } ?: continue

        val newPath = match.contentUri
        // BookReadRecord: PK이므로 새 레코드 삽입 후 구 레코드 삭제
        dao.insertReplace(record.copy(bookPath = newPath))
        dao.deleteByPath(oldPath)
        // Bookmark, Highlight, Memo: 일반 컬럼이므로 UPDATE
        bookmarkDao.updateBookPath(oldPath, newPath)
        highlightDao.updateBookPath(oldPath, newPath)
        memoDao.updateBookPath(oldPath, newPath)
    }
}
