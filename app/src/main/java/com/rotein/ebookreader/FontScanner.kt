package com.rotein.ebookreader

import android.os.Environment
import java.io.File

object FontScanner {

    private val FONT_EXTENSIONS = setOf("ttf", "otf")

    fun scanDeviceFonts(): Map<String, String> {
        val root = Environment.getExternalStorageDirectory()
        return scanFontFiles(root)
    }

    internal fun scanFontFiles(root: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        scanDirectory(root, result)
        return result
    }

    private fun scanDirectory(dir: File, result: MutableMap<String, String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.startsWith(".")) continue
                scanDirectory(file, result)
            } else {
                val ext = file.extension.lowercase()
                if (ext in FONT_EXTENSIONS) {
                    val name = extractFontFamilyName(file.nameWithoutExtension)
                    if (name.isNotBlank() && !result.containsKey(name)) {
                        result[name] = file.absolutePath
                    }
                }
            }
        }
    }
}
