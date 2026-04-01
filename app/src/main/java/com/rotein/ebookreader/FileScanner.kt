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
            MediaStore.Files.FileColumns.SIZE
        )

        // LIKE 조건으로 각 확장자 필터링
        val selectionParts = SUPPORTED_EXTENSIONS.map {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        }
        val selection = selectionParts.joinToString(" OR ")
        val selectionArgs = SUPPORTED_EXTENSIONS.map { "%.${it}" }.toTypedArray()

        context.contentResolver.query(
            uri, projection, selection, selectionArgs,
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val path = cursor.getString(pathCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    books.add(BookFile(name, path, ext, size))
                }
            }
        }

        return books
    }
}
