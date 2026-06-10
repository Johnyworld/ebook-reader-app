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
        val lastModified: Long
    )

    /**
     * 전체 스캔 — 선택된 폴더들의 전자책 파일을 탐색하고 메타데이터를 추출한다.
     * 각 파일을 임시 복사 → 메타데이터 파싱 → 임시 파일 삭제 순으로 처리.
     */
    fun scanBooks(context: Context): List<BookFile> {
        val folderUris = FolderUriStore.load(context)
        val books = mutableListOf<BookFile>()
        for (folderUri in folderUris) {
            val tree = DocumentFile.fromTreeUri(context, folderUri) ?: continue
            scanDirectory(context, tree, books, 0)
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
            scanDirectoryLightweight(tree, snapshots, 0)
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
                kept.add(book)
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
                metadata = metadata
            )
        }

        return kept + added
    }

    /** DocumentFile 기반 재귀 디렉토리 탐색 — 전체 스캔용 */
    private fun scanDirectory(
        context: Context,
        dir: DocumentFile,
        result: MutableList<BookFile>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectory(context, file, result, depth + 1)
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
                            metadata = metadata
                        )
                    )
                }
            }
        }
    }

    /** 경량 디렉토리 탐색 — diff 스캔용 */
    private fun scanDirectoryLightweight(
        dir: DocumentFile,
        result: MutableList<FileSnapshot>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectoryLightweight(file, result, depth + 1)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(FileSnapshot(file.uri.toString(), name, file.length(), file.lastModified()))
                }
            }
        }
    }

    /**
     * SAF URI에서 메타데이터 추출.
     * 임시 파일로 복사 → 기존 파서로 파싱 → 임시 파일 삭제.
     */
    private fun extractMetadataFromUri(
        context: Context,
        uri: Uri,
        fileName: String,
        extension: String
    ): BookMetadata? {
        if (extension !in setOf("epub", "pdf")) return null
        return try {
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
        } catch (_: Exception) {
            null
        }
    }
}
