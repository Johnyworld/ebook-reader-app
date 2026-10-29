package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class PageFlipTest {

    // --- QuadPageFlip 마스크 변환 ---

    @Test
    fun `QuadPageFlip - 기본값은 이전-다음, 다음-다음`() {
        val q = QuadPageFlip.DEFAULT
        assertFalse(q.topLeft)
        assertTrue(q.topRight)
        assertTrue(q.bottomLeft)
        assertTrue(q.bottomRight)
    }

    @Test
    fun `QuadPageFlip - 마스크 왕복 변환 16개 모두 일치`() {
        for (mask in 0 until 16) {
            assertEquals(mask, QuadPageFlip.fromMask(mask).toMask())
        }
    }

    @Test
    fun `QuadPageFlip - 비트 순서는 좌상, 우상, 좌하, 우하`() {
        assertEquals(QuadPageFlip(topLeft = true, topRight = false, bottomLeft = false, bottomRight = false), QuadPageFlip.fromMask(8))
        assertEquals(QuadPageFlip(topLeft = false, topRight = true, bottomLeft = false, bottomRight = false), QuadPageFlip.fromMask(4))
        assertEquals(QuadPageFlip(topLeft = false, topRight = false, bottomLeft = true, bottomRight = false), QuadPageFlip.fromMask(2))
        assertEquals(QuadPageFlip(topLeft = false, topRight = false, bottomLeft = false, bottomRight = true), QuadPageFlip.fromMask(1))
    }

    @Test
    fun `QuadPageFlip - 범위 밖 마스크는 하위 4비트만 사용`() {
        assertEquals(QuadPageFlip.fromMask(1), QuadPageFlip.fromMask(17))
        assertEquals(QuadPageFlip.fromMask(15), QuadPageFlip.fromMask(-1))
    }

    @Test
    fun `QuadPageFlip - next, prev는 16개를 순환`() {
        assertEquals(QuadPageFlip.fromMask(0), QuadPageFlip.fromMask(15).next())
        assertEquals(QuadPageFlip.fromMask(15), QuadPageFlip.fromMask(0).prev())
        assertEquals(QuadPageFlip.fromMask(8), QuadPageFlip.fromMask(7).next())
    }

    // --- resolvePageTap: 2분할 ---

    private val w = 300f
    private val h = 600f

    @Test
    fun `2분할 좌우 이전-다음`() {
        assertEquals(PageTapAction.PREV, resolvePageTap(10f, 300f, w, h, ReaderPageFlip.LR_PREV_NEXT, null))
        assertEquals(PageTapAction.NEXT, resolvePageTap(290f, 300f, w, h, ReaderPageFlip.LR_PREV_NEXT, null))
        assertEquals(PageTapAction.CENTER, resolvePageTap(150f, 300f, w, h, ReaderPageFlip.LR_PREV_NEXT, null))
    }

    @Test
    fun `2분할 좌우 다음-이전`() {
        assertEquals(PageTapAction.NEXT, resolvePageTap(10f, 300f, w, h, ReaderPageFlip.LR_NEXT_PREV, null))
        assertEquals(PageTapAction.PREV, resolvePageTap(290f, 300f, w, h, ReaderPageFlip.LR_NEXT_PREV, null))
    }

    @Test
    fun `2분할 상하 이전-다음`() {
        assertEquals(PageTapAction.PREV, resolvePageTap(150f, 10f, w, h, ReaderPageFlip.TB_PREV_NEXT, null))
        assertEquals(PageTapAction.NEXT, resolvePageTap(150f, 590f, w, h, ReaderPageFlip.TB_PREV_NEXT, null))
        assertEquals(PageTapAction.CENTER, resolvePageTap(10f, 300f, w, h, ReaderPageFlip.TB_PREV_NEXT, null))
    }

    @Test
    fun `2분할 상하 다음-이전`() {
        assertEquals(PageTapAction.NEXT, resolvePageTap(150f, 10f, w, h, ReaderPageFlip.TB_NEXT_PREV, null))
        assertEquals(PageTapAction.PREV, resolvePageTap(150f, 590f, w, h, ReaderPageFlip.TB_NEXT_PREV, null))
    }

    // --- resolvePageTap: 4분할 ---

    @Test
    fun `4분할 - 각 칸이 설정대로 동작`() {
        // 좌상=이전, 우상=다음, 좌하=다음, 우하=이전
        val quad = QuadPageFlip(topLeft = false, topRight = true, bottomLeft = true, bottomRight = false)
        assertEquals(PageTapAction.PREV, resolvePageTap(10f, 10f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
        assertEquals(PageTapAction.NEXT, resolvePageTap(290f, 10f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
        assertEquals(PageTapAction.NEXT, resolvePageTap(10f, 590f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
        assertEquals(PageTapAction.PREV, resolvePageTap(290f, 590f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
    }

    @Test
    fun `4분할 - 가로 가운데 3분의 1은 위아래 모두 메뉴`() {
        val quad = QuadPageFlip.DEFAULT
        assertEquals(PageTapAction.CENTER, resolvePageTap(150f, 10f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
        assertEquals(PageTapAction.CENTER, resolvePageTap(150f, 590f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
    }

    @Test
    fun `4분할 - 세로 절반을 경계로 위아래 구분`() {
        val quad = QuadPageFlip(topLeft = false, topRight = false, bottomLeft = true, bottomRight = true)
        assertEquals(PageTapAction.PREV, resolvePageTap(10f, 299f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
        assertEquals(PageTapAction.NEXT, resolvePageTap(10f, 300f, w, h, ReaderPageFlip.LR_PREV_NEXT, quad))
    }
}
