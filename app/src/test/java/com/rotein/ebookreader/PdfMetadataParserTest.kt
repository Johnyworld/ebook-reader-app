package com.rotein.ebookreader

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfMetadataParserTest {

    private val tempFiles = mutableListOf<File>()

    private fun createPdf(
        title: String? = "Test PDF",
        author: String? = "Test Author",
        subject: String? = "A test document",
        creationDate: String? = "D:20240101120000"
    ): File {
        val file = File.createTempFile("test_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)

        // 최소한의 PDF 구조: Info dictionary를 가진 PDF
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        // Object 1: Info dictionary
        sb.append("1 0 obj\n<<")
        if (title != null) sb.append(" /Title ($title)")
        if (author != null) sb.append(" /Author ($author)")
        if (subject != null) sb.append(" /Subject ($subject)")
        if (creationDate != null) sb.append(" /CreationDate ($creationDate)")
        sb.append(" >>\nendobj\n")
        // Object 2: Catalog (minimal)
        sb.append("2 0 obj\n<< /Type /Catalog >>\nendobj\n")
        // Trailer referencing Info
        sb.append("trailer\n<< /Info 1 0 R /Root 2 0 R >>\n")
        sb.append("%%EOF\n")

        file.writeText(sb.toString())
        return file
    }

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `parse - 정상 메타데이터 추출`() {
        val pdf = createPdf(title = "My Book", author = "John Doe", subject = "A test document")
        val metadata = PdfMetadataParser.parse(pdf.absolutePath)

        assertNotNull(metadata)
        assertEquals("My Book", metadata!!.title)
        assertEquals("John Doe", metadata.author)
        assertEquals("A test document", metadata.description)
    }

    @Test
    fun `parse - title만 있는 경우`() {
        val pdf = createPdf(title = "Only Title", author = null, subject = null, creationDate = null)
        val metadata = PdfMetadataParser.parse(pdf.absolutePath)

        assertNotNull(metadata)
        assertEquals("Only Title", metadata!!.title)
        assertNull(metadata.author)
        assertNull(metadata.description)
    }

    @Test
    fun `parse - 존재하지 않는 파일`() {
        assertNull(PdfMetadataParser.parse("/nonexistent/file.pdf"))
    }

    @Test
    fun `parse - 빈 파일`() {
        val file = File.createTempFile("empty_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)
        file.writeBytes(ByteArray(0))
        assertNull(PdfMetadataParser.parse(file.absolutePath))
    }

    @Test
    fun `parse - PDF가 아닌 파일`() {
        val file = File.createTempFile("notpdf_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)
        file.writeText("This is not a PDF file at all.")
        assertNull(PdfMetadataParser.parse(file.absolutePath))
    }

    @Test
    fun `parse - hex string 메타데이터`() {
        val file = File.createTempFile("hex_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)

        // UTF-16BE BOM (FEFF) + "AB" (0041 0042)
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Title <FEFF00410042> >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Catalog >>\nendobj\n")
        sb.append("trailer\n<< /Info 1 0 R /Root 2 0 R >>\n")
        sb.append("%%EOF\n")
        file.writeText(sb.toString())

        val metadata = PdfMetadataParser.parse(file.absolutePath)
        assertNotNull(metadata)
        assertEquals("AB", metadata!!.title)
    }

    @Test
    fun `parse - CreationDate 추출`() {
        val pdf = createPdf(creationDate = "D:20240315")
        val metadata = PdfMetadataParser.parse(pdf.absolutePath)

        assertNotNull(metadata)
        assertEquals("D:20240315", metadata!!.publishedDate)
    }

    @Test
    fun `parse - UTF-16BE 이스케이프된 괄호 포함 제목`() {
        val file = File.createTempFile("utf16_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)

        // "패" = U+D328, UTF-16BE 바이트 D3 28 → 28은 '('이므로 PDF에서 \(로 이스케이프됨
        // 테스트 제목: "패스" (U+D328 U+C2A4)
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val titleBytes = bom + byteArrayOf(0xD3.toByte(), 0x28, 0xC2.toByte(), 0xA4.toByte())
        // PDF literal string에서 0x28='('은 \(로, 0x5C='\'은 \\로 이스케이프
        val escaped = buildString {
            for (b in titleBytes) {
                val c = (b.toInt() and 0xFF).toChar()
                when (c) {
                    '(', ')' -> { append('\\'); append(c) }
                    '\\' -> append("\\\\")
                    else -> append(c)
                }
            }
        }

        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Title ($escaped) >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Catalog >>\nendobj\n")
        sb.append("trailer\n<< /Info 1 0 R /Root 2 0 R >>\n")
        sb.append("%%EOF\n")
        file.writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1))

        val metadata = PdfMetadataParser.parse(file.absolutePath)
        assertNotNull(metadata)
        assertEquals("패스", metadata!!.title)
    }

    @Test
    fun `parse - Info dictionary가 파일 앞부분에 있는 큰 파일`() {
        val file = File.createTempFile("bigpdf_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)

        // Info dict를 파일 앞에 두고, 중간에 큰 패딩, trailer를 파일 끝에 배치
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Title (Front Title) /Author (Front Author) >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Catalog >>\nendobj\n")
        // 8KB 이상의 패딩으로 Info dict가 tail 범위 밖에 놓이게 함
        sb.append("% ")
        repeat(20000) { sb.append('X') }
        sb.append("\n")
        sb.append("trailer\n<< /Info 1 0 R /Root 2 0 R >>\n")
        sb.append("%%EOF\n")
        file.writeText(sb.toString())

        val metadata = PdfMetadataParser.parse(file.absolutePath)
        assertNotNull(metadata)
        assertEquals("Front Title", metadata!!.title)
        assertEquals("Front Author", metadata.author)
    }

    @Test
    fun `parse - Info dictionary 없는 PDF`() {
        val file = File.createTempFile("noinfo_", ".pdf")
        file.deleteOnExit()
        tempFiles.add(file)

        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog >>\nendobj\n")
        sb.append("trailer\n<< /Root 1 0 R >>\n")
        sb.append("%%EOF\n")
        file.writeText(sb.toString())

        assertNull(PdfMetadataParser.parse(file.absolutePath))
    }
}
