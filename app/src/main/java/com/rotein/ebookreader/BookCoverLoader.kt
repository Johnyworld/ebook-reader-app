package com.rotein.ebookreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BookCoverLoader {

    // 힙 메모리의 1/8과 16MB 중 작은 값을 커버 캐시로 사용 (KB 단위)
    private const val MAX_CACHE_KB = 16 * 1024 // 16MB — e-ink 기기의 제한된 RAM 고려
    private val cache: LruCache<String, Bitmap> = run {
        val heapFraction = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
        object : LruCache<String, Bitmap>(minOf(heapFraction, MAX_CACHE_KB)) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
    }

    // 썸네일 디스크 캐시 디렉토리 (cacheDir 하위 — 공간 부족 시 시스템이 정리할 수 있음)
    private const val DISK_CACHE_DIR = "covers"

    /**
     * BookFile의 커버 썸네일을 로드한다.
     * 메모리 캐시 → 디스크 썸네일 캐시 → 파일에서 추출(축소 디코딩) 순으로 시도하고,
     * 새로 추출한 썸네일은 디스크에 저장해 다음 실행부터 재추출하지 않는다.
     */
    suspend fun loadFromBook(context: Context, book: BookFile): Bitmap? {
        val key = book.bookKey()
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val diskFile = diskCacheFile(context, book)
            val bitmap = (if (diskFile.exists()) BitmapFactory.decodeFile(diskFile.absolutePath) else null)
                ?: extractThumbnail(context, book)?.also { saveToDisk(it, diskFile) }

            bitmap?.also { cache.put(key, it) }
        }
    }

    private fun diskCacheFile(context: Context, book: BookFile): File {
        val dir = File(context.cacheDir, DISK_CACHE_DIR).also { it.mkdirs() }
        val name = CoverThumbnail.diskCacheKey(book.bookKey(), book.size, book.dateModified)
        return File(dir, "$name.png")
    }

    /** 임시 파일에 쓴 뒤 이름을 바꿔, 쓰는 도중 종료되어도 깨진 썸네일이 남지 않게 한다 */
    private fun saveToDisk(bitmap: Bitmap, file: File) {
        try {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(file)) temp.delete()
        } catch (_: Exception) { }
    }

    /** 파일에서 커버를 추출해 썸네일 크기로 만든다 */
    private fun extractThumbnail(context: Context, book: BookFile): Bitmap? {
        if (book.contentUri.isEmpty()) {
            // 레거시 로컬 경로
            return try { extractCoverFromPath(book.path, book.extension) } catch (_: Exception) { null }
        }
        return try {
            val uri = Uri.parse(book.contentUri)
            val pfd = if (book.extension in setOf("epub", "pdf")) BookFileCache.openSeekable(context, uri) else null
            if (pfd != null) {
                // 파일 복사 없이 디스크립터에서 커버만 읽는다
                pfd.use {
                    when (book.extension) {
                        "epub" -> it.channel().use { channel ->
                            EpubMetadataParser.extractCover(channel)?.let(::decodeThumbnail)
                        }
                        else -> renderPdfFirstPage(it)
                    }
                }
            } else {
                // 일반 파일이 아닌 경우: 임시 파일 복사 → 커버 추출 → 삭제
                val temp = BookFileCache.copyToTemp(context, uri, book.name)
                try {
                    extractCoverFromPath(temp.absolutePath, book.extension)
                } finally {
                    temp.delete()
                }
            }
        } catch (_: Exception) { null }
    }

    /** 로컬 파일 경로에서 확장자에 맞는 커버를 추출한다 */
    private fun extractCoverFromPath(path: String, extension: String): Bitmap? {
        return when (extension) {
            "epub" -> EpubMetadataParser.extractCover(path)?.let(::decodeThumbnail)
            "mobi" -> MobiMetadataParser.extractCover(path)?.let(::decodeThumbnail)
            "pdf" -> ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use {
                renderPdfFirstPage(it)
            }
            else -> null
        }
    }

    /** 이미지 바이트를 썸네일 목표 높이에 맞춰 축소 디코딩한다 (원본 해상도로 디코딩하지 않음) */
    private fun decodeThumbnail(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = CoverThumbnail.calculateInSampleSize(
                bounds.outWidth, bounds.outHeight, CoverThumbnail.TARGET_HEIGHT
            )
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** PDF 첫 페이지를 썸네일 크기로 렌더링한다. PdfRenderer가 닫힐 때 fd도 함께 닫힌다 */
    private fun renderPdfFirstPage(fd: ParcelFileDescriptor): Bitmap? {
        return try {
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val (width, height) = CoverThumbnail.scaledSize(
                        page.width, page.height, CoverThumbnail.TARGET_HEIGHT
                    )
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    val matrix = Matrix().apply {
                        setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
