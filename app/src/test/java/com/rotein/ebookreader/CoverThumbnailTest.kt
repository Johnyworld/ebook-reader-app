package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class CoverThumbnailTest {

    // --- calculateInSampleSize: 목표 높이 이상을 유지하는 가장 큰 2의 거듭제곱 ---

    @Test
    fun `큰 이미지 - 목표 높이 이상을 유지하는 최대 배율`() {
        // 1200 / 4 = 300 >= 240, 1200 / 8 = 150 < 240
        assertEquals(4, CoverThumbnail.calculateInSampleSize(811, 1200, 240))
    }

    @Test
    fun `목표 높이의 정확히 2배 - 배율 2`() {
        assertEquals(2, CoverThumbnail.calculateInSampleSize(300, 480, 240))
    }

    @Test
    fun `목표보다 작은 이미지 - 축소하지 않음`() {
        assertEquals(1, CoverThumbnail.calculateInSampleSize(100, 150, 240))
    }

    @Test
    fun `크기를 알 수 없는 이미지(0 이하) - 축소하지 않음`() {
        assertEquals(1, CoverThumbnail.calculateInSampleSize(0, 0, 240))
        assertEquals(1, CoverThumbnail.calculateInSampleSize(-1, -1, 240))
    }

    @Test
    fun `매우 큰 이미지 - 큰 배율`() {
        // 9600 / 32 = 300 >= 240, / 64 = 150 < 240
        assertEquals(32, CoverThumbnail.calculateInSampleSize(6000, 9600, 240))
    }

    // --- scaledSize: PDF 첫 페이지를 목표 높이로 렌더링할 크기 (확대는 하지 않음) ---

    @Test
    fun `A4 페이지 - 목표 높이로 비율 유지 축소`() {
        assertEquals(170 to 240, CoverThumbnail.scaledSize(595, 842, 240))
    }

    @Test
    fun `목표보다 작은 페이지 - 원본 크기 유지`() {
        assertEquals(100 to 150, CoverThumbnail.scaledSize(100, 150, 240))
    }

    @Test
    fun `아주 좁은 페이지 - 너비는 최소 1`() {
        assertEquals(1 to 240, CoverThumbnail.scaledSize(1, 10_000, 240))
    }

    // --- diskCacheKey: 같은 책·같은 파일 상태면 같은 키, 파일이 바뀌면 다른 키 ---

    @Test
    fun `같은 입력이면 같은 키`() {
        val a = CoverThumbnail.diskCacheKey("content://a", 100L, 1L)
        val b = CoverThumbnail.diskCacheKey("content://a", 100L, 1L)
        assertEquals(a, b)
    }

    @Test
    fun `크기나 수정 시각이 바뀌면 다른 키`() {
        val base = CoverThumbnail.diskCacheKey("content://a", 100L, 1L)
        assertNotEquals(base, CoverThumbnail.diskCacheKey("content://a", 101L, 1L))
        assertNotEquals(base, CoverThumbnail.diskCacheKey("content://a", 100L, 2L))
        assertNotEquals(base, CoverThumbnail.diskCacheKey("content://b", 100L, 1L))
    }

    @Test
    fun `키는 파일명으로 안전한 16진수 문자열`() {
        val key = CoverThumbnail.diskCacheKey("content://x/한글 책.epub", 1L, 1L)
        assertTrue(key.matches(Regex("[0-9a-f]+")))
        assertEquals(32, key.length)
    }
}
