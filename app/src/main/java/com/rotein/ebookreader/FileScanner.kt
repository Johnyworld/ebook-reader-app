package com.rotein.ebookreader

import android.content.Context
import android.os.Environment
import java.io.File

object FileScanner {

    private val SUPPORTED_EXTENSIONS = setOf("epub", "pdf")
    private const val MAX_SCAN_DEPTH = 30 // 심볼릭 링크 순환으로 인한 StackOverflow 방지

    fun scanBooks(@Suppress("UNUSED_PARAMETER") context: Context): List<BookFile> {
        val root = Environment.getExternalStorageDirectory()
        val books = mutableListOf<BookFile>()
        scanDirectory(root, books, 0)
        return books
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
                    result.add(
                        BookFile(
                            name = file.name,
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
