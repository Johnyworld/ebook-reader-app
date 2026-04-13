package com.rotein.ebookreader.testutil

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubTestHelper {

    fun createMinimalEpub(
        title: String = "Test Book",
        author: String = "Test Author",
        language: String = "en",
        publisher: String = "Test Publisher",
        date: String = "2024-01-01",
        description: String = "A test book",
        includeCover: Boolean = true
    ): File {
        val file = File.createTempFile("test_epub_", ".epub")
        file.deleteOnExit()

        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray())
            zos.closeEntry()

            val coverManifest = if (includeCover) {
                """<item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""
            } else ""

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write("""<?xml version="1.0" encoding="UTF-8"?>
<package version="3.0" xmlns="http://www.idpf.org/2007/opf">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$title</dc:title>
    <dc:creator>$author</dc:creator>
    <dc:language>$language</dc:language>
    <dc:publisher>$publisher</dc:publisher>
    <dc:date>$date</dc:date>
    <dc:description>$description</dc:description>
  </metadata>
  <manifest>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    $coverManifest
  </manifest>
</package>""".toByteArray())
            zos.closeEntry()

            if (includeCover) {
                zos.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
                // Minimal valid JPEG: SOI + APP0 + EOI
                zos.write(byteArrayOf(
                    0xFF.toByte(), 0xD8.toByte(),
                    0xFF.toByte(), 0xE0.toByte(),
                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                    0xFF.toByte(), 0xD9.toByte()
                ))
                zos.closeEntry()
            }
        }

        return file
    }
}
