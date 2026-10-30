package com.rotein.ebookreader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.Normalizer

object FileScanner {

    private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf")
    private const val MAX_SCAN_DEPTH = 30

    /** SAF content URI에서 경량 파일 정보 */
    private data class FileSnapshot(
        val uri: String,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val rootUri: String,     // 속한 루트 URI
        val folderPath: String   // 루트 기준 상대 폴더 경로
    )

    /**
     * 전체 스캔 — 선택된 폴더들의 전자책 파일을 탐색하고 메타데이터를 추출한다.
     */
    fun scanBooks(context: Context): List<BookFile> {
        val folderUris = FolderUriStore.load(context)
        val books = mutableListOf<BookFile>()
        for (folderUri in folderUris) {
            val tree = DocumentFile.fromTreeUri(context, folderUri) ?: continue
            scanDirectory(context, tree, folderUri.toString(), "", books, 0)
        }
        return books
    }

    /**
     * diff 기반 갱신 — 기존 캐시와 비교하여 변경분만 처리.
     * 새로 추가된 파일만 메타데이터를 추출하고, 삭제된 파일은 목록에서 제거한다.
     */
    fun refreshBooks(context: Context, existing: List<BookFile>): List<BookFile> {
        val folderUris = FolderUriStore.load(context)

        // 1) 경량 스캔: URI/이름/크기/수정시간만 수집
        val snapshots = mutableListOf<FileSnapshot>()
        for (folderUri in folderUris) {
            val tree = DocumentFile.fromTreeUri(context, folderUri) ?: continue
            scanDirectoryLightweight(tree, folderUri.toString(), "", snapshots, 0)
        }
        val snapshotByUri = snapshots.associateBy { it.uri }

        // 2) 기존 캐시를 contentUri 기준으로 인덱싱
        val existingByUri = existing.associateBy { it.contentUri }

        // 3) 기존 항목 분류
        val kept = mutableListOf<BookFile>()
        val modifiedUris = mutableListOf<String>()
        for (book in existing) {
            val snapshot = snapshotByUri[book.contentUri] ?: continue // 삭제된 파일은 skip
            if (book.size == snapshot.size && book.dateModified == snapshot.lastModified / 1000) {
                // 폴더 정보는 스냅샷 기준으로 갱신 — DB v20 이전 캐시(빈 경로)도 재스캔 없이 채워진다
                kept.add(book.copy(rootUri = snapshot.rootUri, folderPath = snapshot.folderPath))
            } else {
                modifiedUris.add(book.contentUri)
            }
        }

        // 4) 새로 추가된 파일 + 변경된 파일 메타데이터 추출
        val newUris = snapshotByUri.keys - existingByUri.keys
        val urisToExtract = newUris + modifiedUris
        val added = urisToExtract.mapNotNull { uriStr ->
            val snapshot = snapshotByUri[uriStr] ?: return@mapNotNull null
            val uri = Uri.parse(uriStr)
            val ext = snapshot.name.substringAfterLast('.', "").lowercase()
            val metadata = extractMetadataFromUri(context, uri, snapshot.name, ext)
            val normalizedName = Normalizer.normalize(snapshot.name, Normalizer.Form.NFC)
            BookFile(
                name = normalizedName,
                path = "",
                contentUri = uriStr,
                extension = ext,
                size = snapshot.size,
                dateAdded = snapshot.lastModified / 1000,
                dateModified = snapshot.lastModified / 1000,
                metadata = metadata,
                rootUri = snapshot.rootUri,
                folderPath = snapshot.folderPath
            )
        }

        return kept + added
    }

    /** DocumentFile 기반 재귀 디렉토리 탐색 — 전체 스캔용. folderPath는 루트 기준 현재 폴더 경로 */
    private fun scanDirectory(
        context: Context,
        dir: DocumentFile,
        rootUri: String,
        folderPath: String,
        result: MutableList<BookFile>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectory(context, file, rootUri, childFolderPath(folderPath, name), result, depth + 1)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val uri = file.uri
                    val metadata = extractMetadataFromUri(context, uri, name, ext)
                    val normalizedName = Normalizer.normalize(name, Normalizer.Form.NFC)
                    result.add(
                        BookFile(
                            name = normalizedName,
                            path = "",
                            contentUri = uri.toString(),
                            extension = ext,
                            size = file.length(),
                            dateAdded = file.lastModified() / 1000,
                            dateModified = file.lastModified() / 1000,
                            metadata = metadata,
                            rootUri = rootUri,
                            folderPath = folderPath
                        )
                    )
                }
            }
        }
    }

    /** 경량 디렉토리 탐색 — diff 스캔용. folderPath는 루트 기준 현재 폴더 경로 */
    private fun scanDirectoryLightweight(
        dir: DocumentFile,
        rootUri: String,
        folderPath: String,
        result: MutableList<FileSnapshot>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectoryLightweight(file, rootUri, childFolderPath(folderPath, name), result, depth + 1)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(FileSnapshot(file.uri.toString(), name, file.length(), file.lastModified(), rootUri, folderPath))
                }
            }
        }
    }

    /**
     * SAF URI에서 메타데이터 추출.
     * 파일 디스크립터에서 필요한 부분만 직접 읽고(파일 복사 없음),
     * 일반 파일이 아닌 경우에만 임시 파일로 복사 → 기존 파서로 파싱 → 임시 파일 삭제.
     */
    private fun extractMetadataFromUri(
        context: Context,
        uri: Uri,
        fileName: String,
        extension: String
    ): BookMetadata? {
        if (extension !in setOf("epub", "pdf")) return null
        return try {
            // 1) 파일 디스크립터에서 필요한 부분만 직접 읽기 (파일 복사 없음)
            val pfd = BookFileCache.openSeekable(context, uri)
            val fromChannel = pfd?.use {
                it.channel().use { channel ->
                    when (extension) {
                        "epub" -> EpubMetadataParser.parse(channel)
                        "pdf" -> PdfMetadataParser.parse(channel)
                        else -> null
                    }
                }
            }
            // 2) 채널 방식 결과가 없으면 임시 파일 복사 후 경로 기반 파서로 재시도
            //    - 일반 파일이 아닌 경우(클라우드 프로바이더 등)
            //    - EPUB은 메타데이터가 없어도 빈 객체를 반환하므로 null은 파싱 실패(ZIP64 등 미지원 형식) → 재시도
            //    - PDF는 두 방식의 파싱 로직이 같으므로 재시도하지 않음
            if (fromChannel != null || (extension == "pdf" && pfd != null)) {
                fromChannel
            } else {
                val temp = BookFileCache.copyToTemp(context, uri, fileName)
                try {
                    when (extension) {
                        "epub" -> EpubMetadataParser.parse(temp.absolutePath)
                        "pdf" -> PdfMetadataParser.parse(temp.absolutePath)
                        else -> null
                    }
                } finally {
                    temp.delete()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
