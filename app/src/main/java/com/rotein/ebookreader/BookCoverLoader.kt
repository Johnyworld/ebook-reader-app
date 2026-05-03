package com.rotein.ebookreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
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

    fun getCached(path: String): Bitmap? = cache.get(path)

    suspend fun load(path: String, extension: String): Bitmap? {
        cache.get(path)?.let { return it }

        return withContext(Dispatchers.IO) {
            val bitmap = when (extension) {
                "epub" -> EpubMetadataParser.extractCover(path)?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
                "mobi" -> MobiMetadataParser.extractCover(path)?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
                "pdf" -> renderPdfFirstPage(path)
                else -> null
            } ?: return@withContext null

            cache.put(path, bitmap)
            bitmap
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
