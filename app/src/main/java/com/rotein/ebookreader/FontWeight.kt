package com.rotein.ebookreader

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** 가변 글꼴의 wght 축 범위 */
data class WeightAxis(val min: Float, val max: Float)

/** 두께 단계 0(기본)에 해당하는 CSS font-weight */
internal const val FONT_WEIGHT_BASE = 400

/** 단계 하나당 font-weight 변화량 (가변 글꼴) */
internal const val FONT_WEIGHT_STEP = 100

/** 가변 글꼴의 두께 단계 범위: CSS font-weight 100~900 */
private val VARIABLE_FONT_WEIGHT_LEVELS = -3..5

/** 일반 글꼴의 두께 단계 범위: 기본 + text-stroke 굵게 3단계 */
private val STROKE_FONT_WEIGHT_LEVELS = 0..3

/**
 * 글꼴 파일(TTF/OTF)의 fvar 테이블에서 wght 축 범위를 읽는다.
 * 가변 글꼴이 아니거나 읽을 수 없는 파일이면 null.
 * 파일 전체가 아니라 테이블 목록과 fvar 테이블만 읽는다.
 */
internal fun readWeightAxis(file: File): WeightAxis? = try {
    RandomAccessFile(file, "r").use { raf ->
        val sfntVersion = raf.readInt()
        // TrueType(0x00010000), CFF('OTTO'), 구형 Apple('true')만 지원
        if (sfntVersion != 0x00010000 && sfntVersion != 0x4F54544F && sfntVersion != 0x74727565) return null
        val numTables = raf.readUnsignedShort()

        // 테이블 목록(각 16바이트)에서 fvar 위치를 찾는다
        var fvarOffset = -1L
        for (i in 0 until numTables) {
            raf.seek(12L + 16L * i)
            val tag = raf.readInt()
            raf.readInt() // checksum
            val offset = raf.readInt().toLong() and 0xFFFFFFFFL
            if (tag == 0x66766172) { fvarOffset = offset; break } // 'fvar'
        }
        if (fvarOffset < 0) return null

        // fvar 헤더: major, minor, axesArrayOffset, reserved, axisCount, axisSize
        raf.seek(fvarOffset + 4)
        val axesArrayOffset = raf.readUnsignedShort()
        raf.readUnsignedShort()
        // 손상된 파일에서 과도한 반복을 막기 위해 축 개수를 제한한다 (실제 글꼴은 대개 5개 이하)
        val axisCount = raf.readUnsignedShort().coerceAtMost(64)
        val axisSize = raf.readUnsignedShort()

        // 축 레코드: tag, min, default, max (16.16 고정소수점)
        for (i in 0 until axisCount) {
            raf.seek(fvarOffset + axesArrayOffset + axisSize.toLong() * i)
            val tag = raf.readInt()
            val min = raf.readInt() / 65536f
            raf.readInt() // default
            val max = raf.readInt() / 65536f
            if (tag == 0x77676874) return WeightAxis(min, max) // 'wght'
        }
        null
    }
} catch (e: Exception) {
    // 존재하지 않는 파일, 파일 끝 초과(EOFException) 등
    null
}

/**
 * 글꼴이 지원하는 두께 단계 목록.
 * - 가변 글꼴: font-weight 100~900 중 wght 축 범위 안인 단계 (기본 400은 항상 포함)
 * - 그 외: 기본과 굵게(text-stroke)만
 */
internal fun availableFontWeightLevels(axis: WeightAxis?): List<Int> {
    if (axis == null) return STROKE_FONT_WEIGHT_LEVELS.toList()
    return VARIABLE_FONT_WEIGHT_LEVELS.filter { level ->
        level == 0 || (FONT_WEIGHT_BASE + FONT_WEIGHT_STEP * level).toFloat() in axis.min..axis.max
    }
}

/** 저장된 단계를 현재 글꼴이 지원하지 않으면 기본(0)으로 적용한다. 저장값은 바꾸지 않는다. */
internal fun effectiveFontWeightLevel(stored: Int, levels: List<Int>): Int =
    if (stored in levels) stored else 0

/** 현재 글꼴에 저장된 두께 단계 */
val ReaderSettings.fontWeightLevel: Int
    get() = fontWeights[fontName] ?: 0

/** 현재 글꼴의 두께 단계만 바꾼 설정. 기본(0)이면 맵에서 제거한다. */
fun ReaderSettings.withFontWeightLevel(level: Int): ReaderSettings =
    copy(fontWeights = if (level == 0) fontWeights - fontName else fontWeights + (fontName to level))

/** 글꼴별 두께 맵 → SharedPreferences 저장용 JSON 문자열 */
internal fun encodeFontWeights(map: Map<String, Int>): String = JSONObject(map).toString()

/** SharedPreferences의 JSON 문자열 → 글꼴별 두께 맵. 잘못된 값이면 빈 맵. */
internal fun decodeFontWeights(json: String?): Map<String, Int> {
    if (json == null) return emptyMap()
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.getInt(it) }
    } catch (e: Exception) {
        emptyMap()
    }
}
