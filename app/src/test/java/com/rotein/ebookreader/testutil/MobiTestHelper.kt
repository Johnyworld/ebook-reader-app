package com.rotein.ebookreader.testutil

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

object MobiTestHelper {

    fun createMinimalMobi(
        title: String = "Test Mobi Book",
        author: String = "Test Author",
        publisher: String = "Test Publisher",
        date: String = "2024-01-01",
        includeCover: Boolean = true
    ): File {
        val record0 = buildRecord0(title, author, publisher, date, includeCover)
        val coverRecord = if (includeCover) minimalJpeg() else null

        val numRecords = if (coverRecord != null) 2 else 1
        val headerSize = 78 + numRecords * 8
        val record0Offset = headerSize + 2 // +2 gap bytes
        val record1Offset = record0Offset + record0.size

        val buf = ByteArrayOutputStream()

        // PalmDB Header (78 bytes)
        val nameBytes = title.toByteArray(Charsets.ISO_8859_1).copyOf(32)
        buf.write(nameBytes)
        buf.write(ByteArray(16)) // attributes, version, dates
        buf.write(ByteArray(12)) // modificationNumber, appInfo, sortInfo
        buf.write("BOOK".toByteArray()) // type
        buf.write("MOBI".toByteArray()) // creator
        buf.write(ByteArray(8)) // uniqueIdSeed, nextRecordListId
        buf.write(byteArrayOf((numRecords shr 8).toByte(), numRecords.toByte())) // numRecords

        // Record entries
        buf.write(intToBytes(record0Offset))
        buf.write(ByteArray(4))
        if (coverRecord != null) {
            buf.write(intToBytes(record1Offset))
            buf.write(ByteArray(4))
        }

        buf.write(ByteArray(2)) // gap

        // Record 0
        buf.write(record0)

        // Record 1 (cover)
        if (coverRecord != null) {
            buf.write(coverRecord)
        }

        val file = File.createTempFile("test_mobi_", ".mobi")
        file.deleteOnExit()
        file.writeBytes(buf.toByteArray())
        return file
    }

    private fun buildRecord0(
        title: String,
        author: String,
        publisher: String,
        date: String,
        includeCover: Boolean
    ): ByteArray {
        val exthRecords = ByteArrayOutputStream()
        var exthRecordCount = 0

        fun addExthRecord(type: Int, value: String) {
            val data = value.toByteArray(Charsets.UTF_8)
            val length = data.size + 8
            exthRecords.write(intToBytes(type))
            exthRecords.write(intToBytes(length))
            exthRecords.write(data)
            exthRecordCount++
        }

        addExthRecord(100, author)
        addExthRecord(101, publisher)
        addExthRecord(106, date)
        addExthRecord(503, title)

        if (includeCover) {
            // type 201 = coverOffset = 0 (firstImageRecord + 0 = record 1)
            exthRecords.write(intToBytes(201))
            exthRecords.write(intToBytes(12)) // length = 12 (8 header + 4 data)
            exthRecords.write(intToBytes(0))
            exthRecordCount++
        }

        val exthData = exthRecords.toByteArray()

        val exthBlock = ByteArrayOutputStream()
        exthBlock.write("EXTH".toByteArray())
        exthBlock.write(intToBytes(12 + exthData.size))
        exthBlock.write(intToBytes(exthRecordCount))
        exthBlock.write(exthData)
        val exthBytes = exthBlock.toByteArray()

        // mobiHeaderLength = 고정 헤더 크기 (EXTH 제외)
        // 파서에서 exthStart = 16 + mobiHeaderLength 를 사용하므로
        // mobiHeaderLength = MOBI 식별자(4) + 나머지 고정 필드들의 합
        // 오프셋 16~132 까지 = 116바이트 → mobiHeaderLength = 116
        val mobiHeaderLength = 116

        val titleBytes = title.toByteArray(Charsets.UTF_8)
        // fullNameOffset: record0 시작 기준
        // record0 구조: 16(PalmDOC) + mobiHeaderLength(116) + exthBytes + titleBytes
        val fullNameOffset = 16 + mobiHeaderLength + exthBytes.size

        val record0 = ByteArrayOutputStream()

        // PalmDOC header (16 bytes, offset 0~15)
        record0.write(ByteArray(16))

        // "MOBI" (offset 16)
        record0.write("MOBI".toByteArray())
        // mobiHeaderLength (offset 20)
        record0.write(intToBytes(mobiHeaderLength))
        // Mobi type (offset 24)
        record0.write(intToBytes(2))
        // Encoding (offset 28) - 65001 = UTF-8
        record0.write(intToBytes(65001))
        // Unique-ID (offset 32)
        record0.write(intToBytes(0))
        // File version (offset 36)
        record0.write(intToBytes(6))
        // Padding offsets 40~83 (44 bytes)
        record0.write(ByteArray(44))
        // Full name offset (offset 84)
        record0.write(intToBytes(fullNameOffset))
        // Full name length (offset 88)
        record0.write(intToBytes(titleBytes.size))
        // Locale + input/output language (offset 92, 12 bytes)
        record0.write(ByteArray(12))
        // Min version (offset 104)
        record0.write(intToBytes(6))
        // First image record (offset 108) → record index 1
        record0.write(intToBytes(1))
        // Padding offsets 112~127 (16 bytes)
        record0.write(ByteArray(16))
        // EXTH flags (offset 128) - bit 6 = has EXTH
        record0.write(intToBytes(0x40))
        // EXTH block (offset 132 = 16 + mobiHeaderLength)
        record0.write(exthBytes)
        // Full name
        record0.write(titleBytes)

        return record0.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).putInt(value).array()

    private fun minimalJpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0xFF.toByte(), 0xD9.toByte()
    )
}
