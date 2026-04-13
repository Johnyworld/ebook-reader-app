package com.rotein.ebookreader

import com.rotein.ebookreader.testutil.MobiTestHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MobiMetadataParserTest {

    private val tempFiles = mutableListOf<File>()

    private fun createMobi(
        title: String = "Test Mobi Book",
        author: String = "Test Author",
        includeCover: Boolean = true
    ): File {
        val file = MobiTestHelper.createMinimalMobi(
            title = title,
            author = author,
            includeCover = includeCover
        )
        tempFiles.add(file)
        return file
    }

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `parse - 정상 메타데이터 추출`() {
        val mobi = createMobi(title = "나의 모비책", author = "홍길동")
        val metadata = MobiMetadataParser.parse(mobi.absolutePath)

        assertNotNull(metadata)
        assertEquals("나의 모비책", metadata!!.title)
        assertEquals("홍길동", metadata.author)
        assertEquals("Test Publisher", metadata.publisher)
        assertEquals("2024-01-01", metadata.publishedDate)
    }

    @Test
    fun `parse - 존재하지 않는 파일`() {
        assertNull(MobiMetadataParser.parse("/nonexistent/file.mobi"))
    }

    @Test
    fun `parse - 너무 작은 파일`() {
        val file = File.createTempFile("tiny_", ".mobi")
        file.deleteOnExit()
        tempFiles.add(file)
        file.writeBytes(ByteArray(10))
        assertNull(MobiMetadataParser.parse(file.absolutePath))
    }

    @Test
    fun `extractCover - 커버 이미지 추출`() {
        val mobi = createMobi(includeCover = true)
        val cover = MobiMetadataParser.extractCover(mobi.absolutePath)

        assertNotNull(cover)
        assertTrue(cover!!.isNotEmpty())
        assertEquals(0xFF.toByte(), cover[0])
        assertEquals(0xD8.toByte(), cover[1])
    }

    @Test
    fun `extractCover - 커버 없는 MOBI`() {
        val mobi = createMobi(includeCover = false)
        val cover = MobiMetadataParser.extractCover(mobi.absolutePath)
        assertNull(cover)
    }
}
