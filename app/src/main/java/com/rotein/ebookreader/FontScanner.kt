package com.rotein.ebookreader

import android.content.Context
import androidx.documentfile.provider.DocumentFile

object FontScanner {

    private val FONT_EXTENSIONS = setOf("ttf", "otf")

    /** SAF 선택 폴더에서 폰트 파일 스캔 */
    fun scanDeviceFonts(context: Context): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val folderUris = FolderUriStore.load(context)
        for (folderUri in folderUris) {
            val tree = DocumentFile.fromTreeUri(context, folderUri) ?: continue
            scanDirectory(context, tree, result)
        }
        return result
    }

    private fun scanDirectory(context: Context, dir: DocumentFile, result: MutableMap<String, String>) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectory(context, file, result)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in FONT_EXTENSIONS) {
                    val displayName = extractFontDisplayName(name.substringBeforeLast('.'))
                    if (displayName.isNotBlank() && !result.containsKey(displayName)) {
                        // 폰트는 로컬 경로가 필요하므로 캐시 복사
                        try {
                            val localPath = BookFileCache.ensureCached(
                                context, file.uri, name, file.length()
                            )
                            result[displayName] = localPath
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }
}
