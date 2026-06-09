package com.rotein.ebookreader

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * SAF content URI의 파일을 로컬 캐시에 복사한다.
 * 메타데이터 파싱과 뷰어 열기 시 로컬 파일 경로가 필요할 때 사용.
 */
object BookFileCache {

    private const val CACHE_DIR = "books"

    /**
     * content URI를 로컬 캐시 파일로 복사하고 경로를 반환한다.
     * 이미 캐시된 파일이 있고 크기가 동일하면 재사용한다.
     */
    fun ensureCached(context: Context, contentUri: Uri, fileName: String, fileSize: Long): String {
        val cacheDir = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
        // URI 해시 + 원본 확장자로 안정적 파일명 생성
        val ext = fileName.substringAfterLast('.', "bin")
        val hash = contentUri.toString().hashCode().toUInt().toString(16)
        val cached = File(cacheDir, "$hash.$ext")

        // 캐시 히트: 크기가 같으면 재사용
        if (cached.exists() && cached.length() == fileSize) {
            return cached.absolutePath
        }

        // 캐시 미스: 복사
        context.contentResolver.openInputStream(contentUri)?.use { input ->
            cached.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open URI: $contentUri")

        return cached.absolutePath
    }

    /**
     * 메타데이터 파싱용 임시 파일 복사. 사용 후 삭제 필요.
     */
    fun copyToTemp(context: Context, contentUri: Uri, fileName: String): File {
        val ext = fileName.substringAfterLast('.', "bin")
        val temp = File.createTempFile("meta_", ".$ext", context.cacheDir)
        context.contentResolver.openInputStream(contentUri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open URI: $contentUri")
        return temp
    }
}
