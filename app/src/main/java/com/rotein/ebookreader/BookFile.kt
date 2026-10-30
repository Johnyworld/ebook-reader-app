package com.rotein.ebookreader

data class BookFile(
    val name: String,
    val path: String,
    val contentUri: String = "",  // SAF content URI 문자열
    val extension: String,
    val size: Long,
    val dateAdded: Long,      // MediaStore DATE_ADDED (초 단위)
    val dateModified: Long,   // MediaStore DATE_MODIFIED (초 단위) — 읽은 순 proxy
    val metadata: BookMetadata? = null,
    val rootUri: String = "",     // 책이 속한 루트(사용자가 선택한 SAF 트리 폴더) URI. 레거시 경로 책은 빈 문자열
    val folderPath: String = ""   // 루트 기준 상대 폴더 경로('/' 구분). 루트 바로 아래 책은 빈 문자열
)

/** DB 키로 사용하는 안정적 식별자: SAF URI 또는 기존 절대 경로 */
fun BookFile.bookKey(): String = contentUri.ifEmpty { path }

/**
 * 파일 내용의 버전 식별자: 크기 + 수정 시각.
 * 같은 경로(URI)에 다른 파일이 들어오면 값이 달라지므로, 파일 내용에서 파생된 캐시의 무효화 기준으로 쓴다.
 */
fun BookFile.fileVersion(): String = fileVersionOf(size, dateModified)

/** [size]는 바이트, [dateModifiedSec]는 초 단위 수정 시각 */
fun fileVersionOf(size: Long, dateModifiedSec: Long): String = "$size:$dateModifiedSec"
