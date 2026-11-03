package com.rotein.ebookreader

import android.content.Context
import androidx.room.withTransaction
import java.net.URLDecoder
import java.text.Normalizer

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

    // 전체 마이그레이션을 단일 트랜잭션으로 실행
    db.withTransaction {
        for (record in legacyRecords) {
            val oldPath = record.bookPath
            // 파일명을 NFC 정규화 후 비교 (FileScanner도 NFC로 정규화)
            val fileName = Normalizer.normalize(
                oldPath.substringAfterLast('/'), Normalizer.Form.NFC
            ).lowercase()
            // 파일명으로 매칭 (대소문자 무시)
            val match = scannedBooks.firstOrNull {
                it.contentUri.isNotEmpty() && it.name.lowercase() == fileName
            } ?: continue

            val newPath = match.contentUri
            // 이미 newPath로 된 레코드가 있으면 충돌 방지를 위해 skip
            if (dao.getByPath(newPath) != null) continue
            // BookReadRecord: PK이므로 새 레코드 삽입 후 구 레코드 삭제
            dao.insertReplace(record.copy(bookPath = newPath))
            dao.deleteByPath(oldPath)
            // Bookmark, Highlight, Memo: 일반 컬럼이므로 UPDATE
            bookmarkDao.updateBookPath(oldPath, newPath)
            highlightDao.updateBookPath(oldPath, newPath)
            memoDao.updateBookPath(oldPath, newPath)
        }
    }
}

/**
 * 폴더를 다른 위치로 다시 등록하거나 파일을 옮겨 SAF URI가 바뀐 책의 기록을 새 URI로 다시 연결한다.
 * SAF 문서 URI에는 등록한 폴더(tree) 경로가 들어가므로, 같은 파일이라도 등록 폴더가 바뀌면 URI가 달라진다.
 * 읽기 기록, 북마크, 하이라이트, 메모를 함께 옮긴다.
 * @return 다시 연결한 책 수 — 0보다 크면 화면의 기록 상태를 다시 읽어야 한다
 */
suspend fun relinkMovedBookRecords(db: BookDatabase, scannedBooks: List<BookFile>): Int {
    val dao = db.bookReadRecordDao()
    val targets = findRelinkTargets(dao.getAll(), scannedBooks)
    if (targets.isEmpty()) return 0

    var relinked = 0
    db.withTransaction {
        for ((oldPath, newPath) in targets) {
            val record = dao.getByPath(oldPath) ?: continue
            // 매칭 후 그 사이 새 URI에 기록이 생겼으면 덮어쓰지 않도록 skip
            if (dao.getByPath(newPath) != null) continue
            // BookReadRecord: PK이므로 새 레코드 삽입 후 구 레코드 삭제
            dao.insertReplace(record.copy(bookPath = newPath))
            dao.deleteByPath(oldPath)
            db.bookmarkDao().updateBookPath(oldPath, newPath)
            db.highlightDao().updateBookPath(oldPath, newPath)
            db.memoDao().updateBookPath(oldPath, newPath)
            relinked++
        }
    }
    return relinked
}

/**
 * 연결이 끊긴 기록(이전 URI) → 새 URI 매칭 결과를 반환한다.
 *
 * - 대상: URI 형식 기록 중 이번 스캔에 없는 것
 * - 후보: 스캔된 책 중 아직 기록이 없는 것
 * - 파일명(NFC·대소문자 무시)이 같아야 하고, 기록에 fileVersion이 있으면 크기도 같아야 한다
 * - 잘못 연결하지 않도록 후보가 여럿이거나, 여러 기록이 같은 후보를 가리키면 건너뛴다
 */
fun findRelinkTargets(records: List<BookReadRecord>, scannedBooks: List<BookFile>): Map<String, String> {
    val scannedUris = scannedBooks.map { it.contentUri }.toSet()
    val recordPaths = records.map { it.bookPath }.toSet()
    val orphans = records.filter { it.bookPath.startsWith("content://") && it.bookPath !in scannedUris }
    val candidates = scannedBooks.filter { it.contentUri.isNotEmpty() && it.contentUri !in recordPaths }

    // 기록마다 후보가 정확히 하나일 때만 매칭
    val matches = orphans.mapNotNull { record ->
        val fileName = fileNameFromDocumentUri(record.bookPath) ?: return@mapNotNull null
        // fileVersion은 "크기:수정시각" 형식 — 비어 있으면 크기 비교를 생략한다
        val size = record.fileVersion.substringBefore(':').toLongOrNull()
        val matched = candidates.filter {
            normalizeFileName(it.name) == fileName && (size == null || it.size == size)
        }
        matched.singleOrNull()?.let { record.bookPath to it.contentUri }
    }

    // 같은 후보를 여러 기록이 가리키면 어느 쪽이 맞는지 알 수 없으므로 모두 제외
    val duplicated = matches.groupingBy { it.second }.eachCount().filterValues { it > 1 }.keys
    return matches.filter { it.second !in duplicated }.toMap()
}

/** SAF 문서 URI의 document id(예: primary:Books/a.epub)에서 정규화된 파일명을 꺼낸다. 파일명이 없으면 null */
private fun fileNameFromDocumentUri(uri: String): String? {
    val encodedId = uri.substringAfterLast("/document/", "").ifEmpty { return null }
    // URLDecoder는 '+'를 공백으로 바꾸므로 미리 이스케이프한다
    // 잘못된 % 시퀀스면 예외가 나므로 해당 기록은 건너뛴다
    val docId = runCatching { URLDecoder.decode(encodedId.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return null
    // "primary:Books/a.epub" → "a.epub", 루트 파일 "primary:a.epub" → "a.epub"
    val name = docId.substringAfterLast('/').substringAfterLast(':')
    // 확장자가 없으면 파일명이 아닌 ID(예: msf:1234)로 본다
    if (!name.contains('.')) return null
    return normalizeFileName(name)
}

private fun normalizeFileName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()
