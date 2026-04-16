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
