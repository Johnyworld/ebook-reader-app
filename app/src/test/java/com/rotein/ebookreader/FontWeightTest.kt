package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class FontWeightTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- 테스트용 최소 sfnt 파일 생성 ---

    /** 16.16 고정소수점 */
    private fun fixed(v: Float): Int = (v * 65536).toInt()

    /**
     * 테이블 목록과 (선택적으로) fvar 테이블만 가진 최소 글꼴 파일을 만든다.
     * axes: (태그, 최소, 기본, 최대)
     */
    private fun buildFont(
        sfntVersion: Int = 0x00010000,
        extraTags: List<String> = listOf("head"),
        axes: List<Quad>? = null
    ): ByteArray {
        val tags = extraTags + (if (axes != null) listOf("fvar") else emptyList())
        val headerSize = 12 + 16 * tags.size

        // fvar 본문
        val fvar = ByteArrayOutputStream().also { bos ->
            if (axes == null) return@also
            DataOutputStream(bos).apply {
                writeShort(1); writeShort(0)   // major/minor
                writeShort(16)                 // axesArrayOffset
                writeShort(2)                  // reserved
                writeShort(axes.size)          // axisCount
                writeShort(20)                 // axisSize
                writeShort(0); writeShort(0)   // instanceCount/instanceSize
                axes.forEach { a ->
                    writeBytes(a.tag)
                    writeInt(fixed(a.min)); writeInt(fixed(a.def)); writeInt(fixed(a.max))
                    writeShort(0); writeShort(256)
                }
            }
        }.toByteArray()

        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(sfntVersion)
            writeShort(tags.size)
            writeShort(0); writeShort(0); writeShort(0)
            tags.forEach { tag ->
                writeBytes(tag)
                writeInt(0)
                if (tag == "fvar") { writeInt(headerSize); writeInt(fvar.size) }
                else { writeInt(0); writeInt(0) }
            }
            write(fvar)
        }
        return out.toByteArray()
    }

    private data class Quad(val tag: String, val min: Float, val def: Float, val max: Float)

    private fun fontFile(bytes: ByteArray): File = tmp.newFile().also { it.writeBytes(bytes) }

    // --- readWeightAxis ---

    @Test
    fun `readWeightAxis - fvar가 없는 일반 글꼴은 null`() {
        assertNull(readWeightAxis(fontFile(buildFont())))
    }

    @Test
    fun `readWeightAxis - wght 축의 범위를 읽는다`() {
        val file = fontFile(buildFont(axes = listOf(Quad("wght", 100f, 400f, 900f))))
        assertEquals(WeightAxis(100f, 900f), readWeightAxis(file))
    }

    @Test
    fun `readWeightAxis - 여러 축 중 wght만 찾는다`() {
        val file = fontFile(buildFont(axes = listOf(Quad("wdth", 75f, 100f, 125f), Quad("wght", 300f, 400f, 700f))))
        assertEquals(WeightAxis(300f, 700f), readWeightAxis(file))
    }

    @Test
    fun `readWeightAxis - fvar는 있지만 wght 축이 없으면 null`() {
        val file = fontFile(buildFont(axes = listOf(Quad("wdth", 75f, 100f, 125f))))
        assertNull(readWeightAxis(file))
    }

    @Test
    fun `readWeightAxis - OTF(CFF) 글꼴도 읽는다`() {
        val file = fontFile(buildFont(sfntVersion = 0x4F54544F, axes = listOf(Quad("wght", 200f, 400f, 800f))))
        assertEquals(WeightAxis(200f, 800f), readWeightAxis(file))
    }

    @Test
    fun `readWeightAxis - 글꼴이 아닌 파일이나 손상된 파일은 null`() {
        assertNull(readWeightAxis(fontFile("not a font".toByteArray())))
        assertNull(readWeightAxis(fontFile(ByteArray(0))))
        // 테이블 목록이 파일 끝을 넘어가는 경우
        assertNull(readWeightAxis(fontFile(buildFont(axes = listOf(Quad("wght", 100f, 400f, 900f))).copyOf(20))))
    }

    @Test
    fun `readWeightAxis - 존재하지 않는 파일은 null`() {
        assertNull(readWeightAxis(File(tmp.root, "missing.ttf")))
    }

    @Test
    fun `readWeightAxis - 실제 가변 글꼴(Pretendard Variable)`() {
        val file = File("src/main/res/font/pretendard_variable.ttf")
        assertEquals(WeightAxis(45f, 930f), readWeightAxis(file))
    }

    // --- availableFontWeightLevels ---

    @Test
    fun `availableFontWeightLevels - 가변 글꼴이 아니면 기본과 굵게만`() {
        assertEquals(listOf(0, 1, 2, 3), availableFontWeightLevels(null))
    }

    @Test
    fun `availableFontWeightLevels - 축 범위가 넓으면 100부터 900까지 (-3부터 +5)`() {
        assertEquals(listOf(-3, -2, -1, 0, 1, 2, 3, 4, 5), availableFontWeightLevels(WeightAxis(100f, 900f)))
        // 100~900을 넘는 축이어도 CSS 굵기 100~900 안에서만
        assertEquals(listOf(-3, -2, -1, 0, 1, 2, 3, 4, 5), availableFontWeightLevels(WeightAxis(1f, 1000f)))
    }

    @Test
    fun `availableFontWeightLevels - 축 범위 밖의 단계는 제외`() {
        // 300~600 → 400 + 100*단계 가 범위 안인 -1..2
        assertEquals(listOf(-1, 0, 1, 2), availableFontWeightLevels(WeightAxis(300f, 600f)))
    }

    @Test
    fun `availableFontWeightLevels - 기본은 축 범위와 무관하게 항상 포함`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), availableFontWeightLevels(WeightAxis(500f, 900f)))
    }

    // --- effectiveFontWeightLevel ---

    @Test
    fun `effectiveFontWeightLevel - 지원하는 단계면 그대로`() {
        assertEquals(-2, effectiveFontWeightLevel(-2, listOf(-2, -1, 0, 1)))
    }

    @Test
    fun `effectiveFontWeightLevel - 지원하지 않는 단계면 기본`() {
        assertEquals(0, effectiveFontWeightLevel(-2, listOf(0, 1, 2, 3)))
    }

    // --- 글꼴별 두께 저장 ---

    @Test
    fun `fontWeightLevel - 저장값이 없으면 기본`() {
        assertEquals(0, ReaderSettings(fontName = "A").fontWeightLevel)
    }

    @Test
    fun `withFontWeightLevel - 현재 글꼴에만 저장된다`() {
        val s = ReaderSettings(fontName = "A").withFontWeightLevel(2)
        assertEquals(2, s.fontWeightLevel)
        assertEquals(0, s.copy(fontName = "B").fontWeightLevel)
        // 다른 글꼴로 바꿨다가 돌아와도 유지
        assertEquals(2, s.copy(fontName = "B").copy(fontName = "A").fontWeightLevel)
    }

    @Test
    fun `withFontWeightLevel - 기본으로 되돌리면 맵에서 제거`() {
        val s = ReaderSettings(fontName = "A").withFontWeightLevel(2).withFontWeightLevel(0)
        assertTrue(s.fontWeights.isEmpty())
    }

    @Test
    fun `encode decode - 왕복 변환`() {
        val map = mapOf("A" to 2, "나눔 \"고딕\"|x" to -1)
        assertEquals(map, decodeFontWeights(encodeFontWeights(map)))
    }

    @Test
    fun `decodeFontWeights - null이나 잘못된 값이면 빈 맵`() {
        assertEquals(emptyMap<String, Int>(), decodeFontWeights(null))
        assertEquals(emptyMap<String, Int>(), decodeFontWeights("{broken"))
    }

    // --- fontWeightForJs ---

    @Test
    fun `fontWeightForJs - 파일 없는 글꼴은 굵게만 적용되고 얇게는 기본으로`() {
        assertEquals(2 to "", fontWeightForJs(ReaderSettings(fontName = "A").withFontWeightLevel(2), ""))
        assertEquals(0 to "", fontWeightForJs(ReaderSettings(fontName = "A").withFontWeightLevel(-2), ""))
    }

    @Test
    fun `fontWeightForJs - 가변 글꼴은 얇게와 wght 범위를 넘긴다`() {
        val s = ReaderSettings(fontName = "P").withFontWeightLevel(-2)
        assertEquals(-2 to "45 930", fontWeightForJs(s, "src/main/res/font/pretendard_variable.ttf"))
    }

    // --- layoutFingerprint ---

    @Test
    fun `layoutFingerprint - 두께가 기본이면 기존 형식 그대로`() {
        assertEquals("A|16|1.5|0|20|20|false|JUSTIFY", ReaderSettings(fontName = "A").layoutFingerprint())
    }

    @Test
    fun `layoutFingerprint - 현재 글꼴 두께가 있으면 뒤에 붙는다`() {
        val s = ReaderSettings(fontName = "A").withFontWeightLevel(-1)
        assertEquals("A|16|1.5|0|20|20|false|JUSTIFY|w-1", s.layoutFingerprint())
        // 다른 글꼴의 두께는 영향 없음
        assertEquals("B|16|1.5|0|20|20|false|JUSTIFY", s.copy(fontName = "B").layoutFingerprint())
    }
}
