package com.rotein.ebookreader

import com.rotein.ebookreader.testutil.EpubTestHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
class EpubMetadataParserTest {

    private val tempFiles = mutableListOf<File>()

    private fun createEpub(
        title: String = "Test Book",
        author: String = "Test Author",
        includeCover: Boolean = true
    ): File {
        val file = EpubTestHelper.createMinimalEpub(
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
        val epub = createEpub(title = "나의 책", author = "홍길동")
        val metadata = EpubMetadataParser.parse(epub.absolutePath)

        assertNotNull(metadata)
        assertEquals("나의 책", metadata!!.title)
        assertEquals("홍길동", metadata.author)
        assertEquals("en", metadata.language)
        assertEquals("Test Publisher", metadata.publisher)
        assertEquals("2024-01-01", metadata.publishedDate)
        assertEquals("A test book", metadata.description)
    }

    @Test
    fun `parse - 존재하지 않는 파일`() {
        assertNull(EpubMetadataParser.parse("/nonexistent/file.epub"))
    }

    @Test
    fun `extractCover - 커버 이미지 추출`() {
        val epub = createEpub(includeCover = true)
        val cover = EpubMetadataParser.extractCover(epub.absolutePath)

        assertNotNull(cover)
        assertTrue(cover!!.isNotEmpty())
        assertEquals(0xFF.toByte(), cover[0])
        assertEquals(0xD8.toByte(), cover[1])
    }

    @Test
    fun `extractCover - 커버 없는 EPUB`() {
        val epub = createEpub(includeCover = false)
        val cover = EpubMetadataParser.extractCover(epub.absolutePath)
        assertNull(cover)
    }

    // --- FileChannel 기반 (SAF 파일 디스크립터에서 복사 없이 읽기) ---

    @Test
    fun `parse(channel) - 경로 기반과 같은 메타데이터`() {
        val epub = createEpub(title = "채널 책", author = "홍길동")
        val fromChannel = RandomAccessFile(epub, "r").use { EpubMetadataParser.parse(it.channel) }
        assertEquals(EpubMetadataParser.parse(epub.absolutePath), fromChannel)
        assertEquals("채널 책", fromChannel!!.title)
    }

    @Test
    fun `extractCover(channel) - 경로 기반과 같은 커버 바이트`() {
        val epub = createEpub(includeCover = true)
        val fromChannel = RandomAccessFile(epub, "r").use { EpubMetadataParser.extractCover(it.channel) }
        assertArrayEquals(EpubMetadataParser.extractCover(epub.absolutePath), fromChannel)
    }

    @Test
    fun `extractCover(channel) - 커버 없는 EPUB`() {
        val epub = createEpub(includeCover = false)
        assertNull(RandomAccessFile(epub, "r").use { EpubMetadataParser.extractCover(it.channel) })
    }

    @Test
    fun `parse(channel) - zip이 아닌 파일이면 null`() {
        val file = File.createTempFile("broken_", ".epub").also { tempFiles.add(it) }
        file.writeText("not a zip")
        assertNull(RandomAccessFile(file, "r").use { EpubMetadataParser.parse(it.channel) })
    }
}
