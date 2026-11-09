package com.rotein.ebookreader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/**
 * 외부 앱(파일 탐색기, 이메일 첨부 등)이 ACTION_VIEW로 넘겨준 책 파일 처리.
 * 시스템 파일 선택기(DocumentsUI)가 없는 기기에서도 책을 열 수 있게 하는 경로다.
 */

/** 외부에서 열 수 있는 형식 — 매니페스트 인텐트 필터의 MIME 타입과 맞춘다 (octet-stream은 확장자로만 판단) */
private val EXTERNAL_MIME_TO_EXTENSION = mapOf(
    "application/epub+zip" to "epub",
    "application/pdf" to "pdf"
)

/**
 * 파일명 확장자를 우선 사용하고, 확장자가 지원 형식이 아니면 MIME 타입으로 결정한다.
 * 일부 프로바이더는 표시 이름에 확장자가 없으므로(예: 다운로드 ID) MIME 타입을 보조로 쓴다.
 * @return "epub" 또는 "pdf", 지원하지 않으면 null
 */
fun externalBookExtension(fileName: String, mimeType: String?): String? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext in EXTERNAL_MIME_TO_EXTENSION.values) return ext
    return EXTERNAL_MIME_TO_EXTENSION[mimeType]
}

/**
 * 외부에서 연 파일과 같은 서재 책을 찾는다 — 찾으면 서재 책의 기록(읽던 위치·북마크 등)을 이어서 쓴다.
 * 파일명(NFC·대소문자 무시)과 크기가 모두 같고, 후보가 정확히 하나일 때만 매칭한다.
 * 크기를 모르면 잘못 연결할 수 있으므로 매칭하지 않는다.
 */
fun findLibraryMatch(catalog: List<BookFile>, fileName: String, size: Long?): BookFile? {
    if (size == null) return null
    val name = normalizeFileName(fileName)
    return catalog.filter { normalizeFileName(it.name) == name && it.size == size }.singleOrNull()
}

/**
 * ACTION_VIEW로 받은 URI를 리더가 열 수 있는 [BookFile]로 만든다. IO 스레드에서 호출해야 한다.
 * 서재에 같은 책이 있으면 그 책을 반환하고, 없으면 외부 URI를 키로 쓰는 BookFile을 만든다.
 * @return 지원하지 않는 형식이거나 파일을 읽을 수 없으면 null
 */
fun resolveExternalBook(context: Context, uri: Uri, mimeType: String?, catalog: List<BookFile>): BookFile? {
    var displayName: String? = null
    var size: Long? = null
    var lastModifiedMs = 0L
    // 프로바이더마다 제공하는 컬럼이 달라 전체 컬럼을 조회하고 있는 것만 읽는다. 조회 실패 시 기본값 사용
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }
                    ?.let { displayName = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }
                    ?.let { size = c.getLong(it) }
                // SAF 문서 URI일 때만 있는 컬럼 (밀리초)
                c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf { it >= 0 && !c.isNull(it) }
                    ?.let { lastModifiedMs = c.getLong(it) }
            }
        }
    }
    // 표시 이름이 없으면 URI 끝부분을 쓴다 — 문서 ID "primary:a.epub"이면 "a.epub"
    val name = displayName ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "book"
    val extension = externalBookExtension(name, mimeType ?: context.contentResolver.getType(uri)) ?: return null

    // 권한이 없거나 파일이 사라졌으면 리더에서 복사하다 실패하므로 미리 열어 본다
    fun canRead(u: Uri) =
        runCatching { context.contentResolver.openInputStream(u)?.use { true } ?: false }.getOrDefault(false)

    // 서재 목록(catalog)은 폴더 제거·파일 삭제 후 다음 스캔 전까지 실제와 어긋날 수 있으므로
    // 서재 책도 읽을 수 있을 때만 쓰고, 아니면 외부 URI로 연다
    findLibraryMatch(catalog, name, size)
        ?.takeIf { it.contentUri.isNotEmpty() && canRead(Uri.parse(it.contentUri)) }
        ?.let { return it }

    if (!canRead(uri)) return null

    return BookFile(
        name = name,
        path = "",
        contentUri = uri.toString(),
        extension = extension,
        size = size ?: 0L,
        dateAdded = 0L,
        dateModified = lastModifiedMs / 1000  // BookFile은 초 단위
    )
}
