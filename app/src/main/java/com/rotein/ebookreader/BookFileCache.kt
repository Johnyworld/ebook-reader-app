package com.rotein.ebookreader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest

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
        // URI의 SHA-256 해시로 충돌 안전한 파일명 생성
        val ext = fileName.substringAfterLast('.', "bin")
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(contentUri.toString().toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
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
     * content URI를 복사하지 않고 읽기 전용 파일 디스크립터로 연다.
     * 위치 지정 읽기가 가능한 일반 파일일 때만 반환하고, 파이프 등(클라우드 프로바이더)이면 null을 반환한다.
     * → null이면 호출자는 copyToTemp 방식으로 대체한다. 반환된 디스크립터는 호출자가 닫아야 한다.
     */
    fun openSeekable(context: Context, contentUri: Uri): ParcelFileDescriptor? {
        val pfd = context.contentResolver.openFileDescriptor(contentUri, "r") ?: return null
        // statSize: 일반 파일이 아니면 -1
        if (pfd.statSize < 0) {
            pfd.close()
            return null
        }
        return pfd
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

/**
 * 디스크립터 위의 FileChannel. 위치 지정 읽기 전용으로 사용한다.
 * Android의 FileInputStream(FileDescriptor)는 fd를 소유하지 않으므로, fd는 ParcelFileDescriptor를 닫을 때 닫힌다.
 */
fun ParcelFileDescriptor.channel(): FileChannel = FileInputStream(fileDescriptor).channel
