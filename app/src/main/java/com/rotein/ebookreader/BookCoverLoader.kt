package com.rotein.ebookreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val bytes = when (extension) {
                "epub" -> EpubMetadataParser.extractCover(path)
                "mobi" -> MobiMetadataParser.extractCover(path)
                else -> null
            } ?: return@withContext null

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null

            cache.put(path, bitmap)
            bitmap
        }
    }
}
