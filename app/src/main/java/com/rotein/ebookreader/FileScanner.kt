package com.rotein.ebookreader

import android.content.Context
import android.provider.MediaStore

object FileScanner {

    private val SUPPORTED_EXTENSIONS = setOf("epub", "txt", "mobi", "pdf")

    fun scanBooks(context: Context): List<BookFile> {
        val books = mutableListOf<BookFile>()
        val uri = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selectionParts = SUPPORTED_EXTENSIONS.map {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        }
        val selection = selectionParts.joinToString(" OR ")
        val selectionArgs = SUPPORTED_EXTENSIONS.map { "%.${it}" }.toTypedArray()

        context.contentResolver.query(
            uri, projection, selection, selectionArgs, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val path = cursor.getString(pathCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val dateModified = cursor.getLong(dateModifiedCol)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val metadata = extractMetadata(path, ext)
                    books.add(BookFile(name, path, ext, size, dateAdded, dateModified, metadata))
                }
            }
        }

        return books
    }

    private fun extractMetadata(path: String, extension: String): BookMetadata? = when (extension) {
        "epub" -> EpubMetadataParser.parse(path)
        "mobi" -> MobiMetadataParser.parse(path)
        "pdf" -> PdfMetadataParser.parse(path)
        else -> null
    }
}
