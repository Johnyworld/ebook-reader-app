package com.rotein.ebookreader

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.Normalizer

object FileScanner {

    private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf")
    private const val MAX_SCAN_DEPTH = 30 // 심볼릭 링크 순환으로 인한 StackOverflow 방지

    /** 경로/크기/수정시간만 빠르게 수집하는 경량 파일 정보 */
    private data class FileSnapshot(
        val path: String,
        val size: Long,
        val lastModified: Long
    )

    /** 전체 스캔 — 최초 실행 시 사용 */
    fun scanBooks(@Suppress("UNUSED_PARAMETER") context: Context): List<BookFile> {
        val root = Environment.getExternalStorageDirectory()
        val books = mutableListOf<BookFile>()
        scanDirectory(root, books, 0)
        return books
    }

    /**
     * diff 기반 갱신 — 기존 캐시와 비교하여 변경분만 처리
     * 새로 추가된 파일만 메타데이터를 추출하고, 삭제된 파일은 목록에서 제거한다.
     */
    fun refreshBooks(existing: List<BookFile>): List<BookFile> {
        val root = Environment.getExternalStorageDirectory()

        // 1) 경량 스캔: 경로/크기/수정시간만 수집
        val snapshots = mutableListOf<FileSnapshot>()
        scanDirectoryLightweight(root, snapshots, 0)
        val snapshotByPath = snapshots.associateBy { it.path }

        // 2) 기존 캐시를 path 기준으로 인덱싱
        val existingByPath = existing.associateBy { it.path }

        // 3) 기존 항목 분류: 변경 없으면 유지, 변경되었으면 재추출 대상
        val kept = mutableListOf<BookFile>()
        val modifiedPaths = mutableListOf<String>()
        for (book in existing) {
            val snapshot = snapshotByPath[book.path] ?: continue // 삭제된 파일은 skip
            if (book.size == snapshot.size && book.dateModified == snapshot.lastModified / 1000) {
                kept.add(book)
            } else {
                modifiedPaths.add(book.path)
            }
        }

        // 4) 새로 추가된 파일 + 변경된 파일 모두 메타데이터 추출
        val newPaths = snapshotByPath.keys - existingByPath.keys
        val pathsToExtract = newPaths + modifiedPaths
        val added = pathsToExtract.map { path ->
            val file = File(path)
            val ext = file.extension.lowercase()
            val metadata = extractMetadata(path, ext)
            val normalizedName = Normalizer.normalize(file.name, Normalizer.Form.NFC)
            BookFile(
                name = normalizedName,
                path = path,
                extension = ext,
                size = file.length(),
                dateAdded = file.lastModified() / 1000,
                dateModified = file.lastModified() / 1000,
                metadata = metadata
            )
        }

        return kept + added
    }

    /** 경로/크기/수정시간만 수집하는 경량 디렉토리 탐색 */
    private fun scanDirectoryLightweight(dir: File, result: MutableList<FileSnapshot>, depth: Int) {
        if (depth > MAX_SCAN_DEPTH) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.startsWith(".")) continue
                scanDirectoryLightweight(file, result, depth + 1)
            } else {
                val ext = file.extension.lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(FileSnapshot(file.absolutePath, file.length(), file.lastModified()))
                }
            }
        }
    }

    private fun scanDirectory(dir: File, result: MutableList<BookFile>, depth: Int) {
        if (depth > MAX_SCAN_DEPTH) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.startsWith(".")) continue
                scanDirectory(file, result, depth + 1)
            } else {
                val ext = file.extension.lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val metadata = extractMetadata(file.absolutePath, ext)
                    // macOS NFD 파일명 등으로 인한 폰트 폴백 방지
                    val normalizedName = Normalizer.normalize(file.name, Normalizer.Form.NFC)
                    result.add(
                        BookFile(
                            name = normalizedName,
                            path = file.absolutePath,
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

    private fun extractMetadata(path: String, extension: String): BookMetadata? = when (extension) {
        "epub" -> EpubMetadataParser.parse(path)
        "pdf" -> PdfMetadataParser.parse(path)
        else -> null
    }
}
