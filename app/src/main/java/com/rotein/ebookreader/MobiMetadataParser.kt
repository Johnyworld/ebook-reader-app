package com.rotein.ebookreader

import java.io.RandomAccessFile

/**
 * MOBI 파일 바이너리 포맷에서 메타데이터 및 커버 이미지를 추출.
 *
 * 파일 구조:
 *   PalmDB 헤더 (78 bytes)
 *   레코드 목록 (numRecords * 8 bytes)
 *   레코드 0 = PalmDOC 헤더 (16 bytes) + MOBI 헤더 + EXTH 블록 (선택)
 *   레코드 N = 이미지 레코드 (firstImageRecord 이후)
 */
object MobiMetadataParser {

    fun parse(path: String): BookMetadata? = try {
        val record0 = readRecord0(path) ?: return null
        extractMetadata(record0)
    } catch (e: Exception) {
        null
    }

    /**
     * MOBI 파일에서 커버 이미지 bytes를 추출.
     * EXTH type 201 (coverOffset) → firstImageRecord + coverOffset 번 레코드를 읽는다.
     * EXTH가 없으면 firstImageRecord 레코드를 커버로 사용한다.
     */
    fun extractCover(path: String): ByteArray? = try {
        RandomAccessFile(path, "r").use { raf ->
            val fileSize = raf.length()
            if (fileSize < 78 + 8) return null

            val palmHeader = ByteArray(78)
            raf.readFully(palmHeader)
            val numRecords = readUShort(palmHeader, 76)
            if (numRecords == 0) return null

            // 모든 레코드 오프셋 읽기
            val recordOffsets = LongArray(numRecords)
            repeat(numRecords) { i ->
                val entry = ByteArray(8)
                raf.readFully(entry)
                recordOffsets[i] = readUInt(entry, 0)
            }

            // 레코드 0 읽기 (MOBI 헤더 파싱용)
            val record0End = if (numRecords > 1) recordOffsets[1] else fileSize
            val record0Size = (record0End - recordOffsets[0]).coerceAtMost(65536L).toInt()
            if (record0Size < 132) return null

            val record0 = ByteArray(record0Size)
            raf.seek(recordOffsets[0])
            raf.readFully(record0)

            if (record0.copyOfRange(16, 20).toString(Charsets.ISO_8859_1) != "MOBI") return null

            val mobiHeaderLength = readInt(record0, 20)
            // firstImageRecord: MOBI 헤더 offset 0x5C = 92 → record0[16+92] = record0[108]
            val firstImageRecord = readInt(record0, 108)
            val exthFlags = readInt(record0, 128)

            // EXTH type 201 (coverOffset) 검색
            var coverOffset = 0
            if ((exthFlags and 0x40) != 0) {
                val exthStart = 16 + mobiHeaderLength
                if (exthStart + 12 <= record0.size) {
                    val exthId = record0.copyOfRange(exthStart, exthStart + 4).toString(Charsets.ISO_8859_1)
                    if (exthId == "EXTH") {
                        val recordCount = readInt(record0, exthStart + 8)
                        var pos = exthStart + 12
                        repeat(recordCount) {
                            if (pos + 8 > record0.size) return@repeat
                            val type = readInt(record0, pos)
                            val length = readInt(record0, pos + 4)
                            // EXTH 레코드 최소 길이는 8바이트 — 0 이하이면 무한 루프 방지
                            if (length < 8) return@repeat
                            // type 201 = cover offset (4 bytes data, total length = 12)
                            if (type == 201 && length == 12) {
                                coverOffset = readInt(record0, pos + 8)
                            }
                            pos += length
                        }
                    }
                }
            }

            val coverRecordIndex = firstImageRecord + coverOffset
            if (coverRecordIndex < 0 || coverRecordIndex >= numRecords) return null

            val coverStart = recordOffsets[coverRecordIndex]
            val coverEnd = if (coverRecordIndex + 1 < numRecords) recordOffsets[coverRecordIndex + 1] else fileSize
            val coverSize = (coverEnd - coverStart).coerceAtMost(5L * 1024 * 1024).toInt()
            if (coverSize <= 0) return null

            val coverBytes = ByteArray(coverSize)
            raf.seek(coverStart)
            raf.readFully(coverBytes)
            coverBytes
        }
    } catch (e: Exception) {
        null
    }

    private fun readRecord0(path: String): ByteArray? {
        RandomAccessFile(path, "r").use { raf ->
            val fileSize = raf.length()
            if (fileSize < 78 + 8) return null

            val palmHeader = ByteArray(78)
            raf.readFully(palmHeader)

            val numRecords = readUShort(palmHeader, 76)
            if (numRecords == 0) return null

            val rec0Entry = ByteArray(8)
            raf.readFully(rec0Entry)
            val record0Offset = readUInt(rec0Entry, 0)

            val record1Offset = if (numRecords > 1) {
                val rec1Entry = ByteArray(8)
                raf.readFully(rec1Entry)
                readUInt(rec1Entry, 0)
            } else {
                fileSize
            }

            val record0Size = minOf(record1Offset - record0Offset, 65536L, fileSize - record0Offset).toInt()
            if (record0Size < 132) return null

            val record0 = ByteArray(record0Size)
            raf.seek(record0Offset)
            raf.readFully(record0)
            return record0
        }
    }

    private fun extractMetadata(record0: ByteArray): BookMetadata? {
        if (record0.size < 20) return null
        val mobiId = record0.copyOfRange(16, 20).toString(Charsets.ISO_8859_1)
        if (mobiId != "MOBI") return null

        if (record0.size < 132) return null

        val mobiHeaderLength = readInt(record0, 20)
        val encoding = readInt(record0, 28)
        val charset = if (encoding == 65001) Charsets.UTF_8 else Charsets.ISO_8859_1

        val fullNameOffset = readInt(record0, 84)
        val fullNameLength = readInt(record0, 88)
        val exthFlags = readInt(record0, 128)

        val title = if (fullNameLength > 0 && fullNameOffset >= 0 &&
            fullNameOffset + fullNameLength <= record0.size
        ) {
            record0.copyOfRange(fullNameOffset, fullNameOffset + fullNameLength)
                .toString(charset).trim()
        } else null

        var author: String? = null
        var publisher: String? = null
        var date: String? = null
        var description: String? = null
        var updatedTitle: String? = null

        if ((exthFlags and 0x40) != 0) {
            val exthStart = 16 + mobiHeaderLength
            if (exthStart + 12 <= record0.size) {
                val exthId = record0.copyOfRange(exthStart, exthStart + 4).toString(Charsets.ISO_8859_1)
                if (exthId == "EXTH") {
                    val recordCount = readInt(record0, exthStart + 8)
                    var pos = exthStart + 12

                    repeat(recordCount) {
                        if (pos + 8 > record0.size) return@repeat
                        val type = readInt(record0, pos)
                        val length = readInt(record0, pos + 4)
                        // EXTH 레코드 최소 길이는 8바이트 — 0 이하이면 무한 루프 방지
                        if (length < 8) return@repeat
                        val dataLength = length - 8

                        if (dataLength > 0 && pos + length <= record0.size) {
                            val data = record0.copyOfRange(pos + 8, pos + 8 + dataLength)
                                .toString(charset).trim()
                            when (type) {
                                100 -> if (author == null) author = data
                                101 -> if (publisher == null) publisher = data
                                103 -> if (description == null) description = data
                                106 -> if (date == null) date = data
                                503 -> updatedTitle = data
                            }
                        }
                        pos += length
                    }
                }
            }
        }

        return BookMetadata(
            title = updatedTitle ?: title,
            author = author,
            language = null,
            publisher = publisher,
            publishedDate = date,
            description = description
        )
    }

    private fun readUShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readUInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)
}
