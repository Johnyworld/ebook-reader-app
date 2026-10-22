package com.rotein.ebookreader

import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * 도서 목록 커버 썸네일 관련 계산.
 * 목록의 커버는 44x60dp로 작게 표시되므로 원본 해상도로 디코딩/렌더링할 필요가 없다.
 */
object CoverThumbnail {

    /** 썸네일 목표 높이(px) — 60dp 표시 크기의 고밀도 화면 대응 여유분 포함 */
    const val TARGET_HEIGHT = 240

    /**
     * BitmapFactory.Options.inSampleSize 계산.
     * 디코딩 결과 높이가 targetHeight 이상을 유지하는 가장 큰 2의 거듭제곱을 반환한다.
     */
    fun calculateInSampleSize(width: Int, height: Int, targetHeight: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    /** PDF 페이지를 targetHeight 높이로 렌더링할 비트맵 크기 (비율 유지, 확대하지 않음) */
    fun scaledSize(width: Int, height: Int, targetHeight: Int): Pair<Int, Int> {
        if (height <= targetHeight) return width to height
        val scale = targetHeight.toFloat() / height
        return (width * scale).roundToInt().coerceAtLeast(1) to targetHeight
    }

    /**
     * 디스크 썸네일 캐시 파일명 키.
     * 파일 크기/수정 시각을 포함해, 같은 경로의 파일이 교체되면 새 썸네일을 만들도록 한다.
     */
    fun diskCacheKey(bookKey: String, size: Long, dateModified: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$bookKey|$size|$dateModified".toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
