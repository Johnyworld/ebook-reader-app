package com.rotein.ebookreader

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class ChannelZipReaderTest {

    private val tempFiles = mutableListOf<File>()

    /** 엔트리 이름 → (내용, 압축 여부) 로 zip 파일 생성 */
    private fun createZip(entries: List<Triple<String, ByteArray, Boolean>>): File {
        val file = File.createTempFile("test_zip_", ".zip")
        tempFiles.add(file)
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((name, data, deflate) in entries) {
                val entry = ZipEntry(name)
                if (!deflate) {
                    // STORED 엔트리는 크기/CRC를 미리 지정해야 한다
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun <T> withReader(file: File, block: (ChannelZipReader) -> T): T =
        RandomAccessFile(file, "r").use { raf -> block(ChannelZipReader(raf.channel)) }

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `압축(DEFLATED) 엔트리를 원본 그대로 읽는다`() {
        val text = "<container>압축된 내용</container>".repeat(200).toByteArray()
        val zip = createZip(listOf(Triple("META-INF/container.xml", text, true)))
        withReader(zip) { assertArrayEquals(text, it.read("META-INF/container.xml")) }
    }

    @Test
    fun `비압축(STORED) 엔트리를 원본 그대로 읽는다`() {
        val data = "application/epub+zip".toByteArray()
        val zip = createZip(listOf(Triple("mimetype", data, false)))
        withReader(zip) { assertArrayEquals(data, it.read("mimetype")) }
    }

    @Test
    fun `여러 엔트리 중 원하는 엔트리만 읽는다`() {
        val a = Random(1).nextBytes(50_000)
        val b = Random(2).nextBytes(30_000)
        val zip = createZip(listOf(
            Triple("OEBPS/a.bin", a, true),
            Triple("OEBPS/b.bin", b, false),
            Triple("OEBPS/c.txt", "c".toByteArray(), true)
        ))
        withReader(zip) {
            assertArrayEquals(b, it.read("OEBPS/b.bin"))
            assertArrayEquals(a, it.read("OEBPS/a.bin"))
        }
    }

    @Test
    fun `없는 엔트리는 null을 반환한다`() {
        val zip = createZip(listOf(Triple("a.txt", "a".toByteArray(), true)))
        withReader(zip) { assertNull(it.read("b.txt")) }
    }

    @Test
    fun `size는 압축 해제 크기를 반환하고 없으면 null`() {
        val data = ByteArray(10_000) { 7 }
        val zip = createZip(listOf(Triple("img/cover.jpg", data, true)))
        withReader(zip) {
            assertEquals(10_000L, it.size("img/cover.jpg"))
            assertNull(it.size("nope"))
        }
    }

    @Test
    fun `한글 엔트리 이름을 찾을 수 있다`() {
        val data = "표지".toByteArray()
        val zip = createZip(listOf(Triple("OEBPS/Images/표지.jpg", data, true)))
        withReader(zip) { assertArrayEquals(data, it.read("OEBPS/Images/표지.jpg")) }
    }

    @Test(expected = IOException::class)
    fun `zip이 아닌 파일이면 IOException`() {
        val file = File.createTempFile("not_zip_", ".epub").also { tempFiles.add(it) }
        file.writeText("this is not a zip file")
        withReader(file) { it.read("mimetype") }
    }

    @Test
    fun `zip 주석 안에 EOCD 시그니처가 있어도 올바르게 읽는다`() {
        val data = "content".toByteArray()
        val file = File.createTempFile("comment_zip_", ".zip").also { tempFiles.add(it) }
        ZipOutputStream(file.outputStream()).use { zos ->
            // 주석에 EOCD 시그니처(PK\u0005\u0006)와 쓰레기 바이트를 넣는다
            zos.setComment("PK\u0005\u0006" + "x".repeat(30))
            zos.putNextEntry(ZipEntry("a.txt"))
            zos.write(data)
            zos.closeEntry()
        }
        withReader(file) { assertArrayEquals(data, it.read("a.txt")) }
    }

    @Test(expected = IOException::class)
    fun `중앙 디렉터리에 비정상적으로 큰 크기가 적혀 있으면 할당하지 않고 IOException`() {
        val zip = createZip(listOf(Triple("a.txt", "a".toByteArray(), true)))
        // 중앙 디렉터리 엔트리의 압축 해제 크기(+24)를 약 2GB로 조작
        val bytes = zip.readBytes()
        val cd = (bytes.size - 4 downTo 0).first {
            bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4b.toByte() &&
                bytes[it + 2] == 0x01.toByte() && bytes[it + 3] == 0x02.toByte()
        }
        val huge = 0x7FFFFFF0
        for (i in 0 until 4) bytes[cd + 24 + i] = (huge ushr (8 * i)).toByte()
        zip.writeBytes(bytes)
        withReader(zip) { it.read("a.txt") }
    }
}
