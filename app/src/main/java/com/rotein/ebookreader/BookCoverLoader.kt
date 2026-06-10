package com.rotein.ebookreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /**
     * content URI가 있는 BookFile의 커버를 로드한다.
     * 임시 파일로 복사 → 커버 추출 → 즉시 삭제하여 디스크 사용을 최소화한다.
     */
    suspend fun loadFromBook(context: Context, book: BookFile): Bitmap? {
        val key = book.bookKey()
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val bitmap = if (book.contentUri.isNotEmpty()) {
                // content URI: 임시 파일 복사 → 커버 추출 → 삭제
                try {
                    val temp = BookFileCache.copyToTemp(context, Uri.parse(book.contentUri), book.name)
                    try {
                        extractCoverFromPath(temp.absolutePath, book.extension)
                    } finally {
                        temp.delete()
                    }
                } catch (_: Exception) { null }
            } else {
                // 레거시 로컬 경로
                try { extractCoverFromPath(book.path, book.extension) } catch (_: Exception) { null }
            }

            bitmap?.also { cache.put(key, it) }
        }
    }

    /** 로컬 파일 경로에서 확장자에 맞는 커버를 추출한다 */
    private fun extractCoverFromPath(path: String, extension: String): Bitmap? {
        return when (extension) {
            "epub" -> EpubMetadataParser.extractCover(path)?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
            "mobi" -> MobiMetadataParser.extractCover(path)?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
            "pdf" -> renderPdfFirstPage(path)
            else -> null
        }
    }

    private fun renderPdfFirstPage(path: String): Bitmap? {
        return try {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
