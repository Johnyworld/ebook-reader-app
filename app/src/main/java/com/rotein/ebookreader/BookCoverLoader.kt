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

    // 힙 메모리의 1/8을 커버 캐시로 사용 (KB 단위)
    private val cache: LruCache<String, Bitmap> = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        object : LruCache<String, Bitmap>(maxKb / 8) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
    }

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
            val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fd.close()
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
