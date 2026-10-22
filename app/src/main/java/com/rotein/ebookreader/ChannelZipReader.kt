package com.rotein.ebookreader

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * FileChannel 위에서 동작하는 최소 ZIP 리더.
 *
 * SAF content URI는 로컬 경로가 없어 java.util.zip.ZipFile을 쓸 수 없다.
 * 파일 전체를 임시 파일로 복사하는 대신, 파일 끝의 중앙 디렉터리만 읽어 엔트리 위치를 파악한 뒤
 * 필요한 엔트리만 위치 지정 읽기(pread)로 꺼낸다. 위치 지정 읽기만 사용하므로 채널의 position은 바꾸지 않는다.
 *
 * 지원: STORED(0), DEFLATED(8) / 미지원: ZIP64, 암호화 엔트리 → IOException
 */
class ChannelZipReader(private val channel: FileChannel) {

    private class Entry(
        val method: Int,
        val encrypted: Boolean,
        val compressedSize: Long,
        val size: Long,
        val localHeaderOffset: Long
    )

    // 엔트리 이름 → 위치 정보 (최초 접근 시 중앙 디렉터리를 한 번만 읽는다)
    private val entries: Map<String, Entry> by lazy { readCentralDirectory() }

    /** 엔트리의 압축 해제 크기. 엔트리가 없으면 null */
    fun size(name: String): Long? = entries[name]?.size

    /** 엔트리 내용을 압축 해제해 반환한다. 엔트리가 없으면 null */
    fun read(name: String): ByteArray? {
        val entry = entries[name] ?: return null
        if (entry.encrypted) throw IOException("Encrypted entry not supported: $name")
        // 손상/조작된 파일의 비정상 크기로 거대한 배열을 할당(OOM 크래시)하지 않도록 상한 검사
        if (entry.size > MAX_ENTRY_SIZE || entry.compressedSize > MAX_ENTRY_SIZE) {
            throw IOException("Entry too large: $name")
        }

        // 로컬 헤더의 이름/extra 길이는 중앙 디렉터리와 다를 수 있으므로 로컬 헤더에서 다시 읽는다
        val header = readAt(entry.localHeaderOffset, LOCAL_HEADER_SIZE)
        if (header.getInt(0) != LOCAL_HEADER_SIG) throw IOException("Bad local header: $name")
        val nameLength = header.getShort(26).toUnsigned()
        val extraLength = header.getShort(28).toUnsigned()
        val dataOffset = entry.localHeaderOffset + LOCAL_HEADER_SIZE + nameLength + extraLength

        val compressed = readAt(dataOffset, entry.compressedSize.toInt()).array()
        return when (entry.method) {
            METHOD_STORED -> compressed
            METHOD_DEFLATED -> inflate(compressed, entry.size.toInt())
            else -> throw IOException("Unsupported compression method ${entry.method}: $name")
        }
    }

    /** 파일 끝에서 EOCD 레코드를 찾고, 그것이 가리키는 중앙 디렉터리를 파싱한다 */
    private fun readCentralDirectory(): Map<String, Entry> {
        val fileSize = channel.size()
        if (fileSize < EOCD_SIZE) throw IOException("Not a zip file")

        // EOCD는 파일 끝에서 [22, 22 + 최대 주석 길이] 범위에 있다
        val tailLength = minOf(fileSize, (EOCD_SIZE + MAX_COMMENT_LENGTH).toLong()).toInt()
        val tail = readAt(fileSize - tailLength, tailLength)
        // 주석 안에 시그니처 바이트가 있을 수 있으므로, 주석 길이가 파일 끝과 정확히 맞는 위치만 EOCD로 인정
        var eocd = -1
        for (i in tailLength - EOCD_SIZE downTo 0) {
            if (tail.getInt(i) == EOCD_SIG &&
                i + EOCD_SIZE + tail.getShort(i + 20).toUnsigned() == tailLength
            ) { eocd = i; break }
        }
        if (eocd < 0) throw IOException("Not a zip file (EOCD not found)")

        val totalEntries = tail.getShort(eocd + 10).toUnsigned()
        val cdSize = tail.getInt(eocd + 12).toUnsignedLong()
        val cdOffset = tail.getInt(eocd + 16).toUnsignedLong()
        if (totalEntries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
            throw IOException("ZIP64 not supported")
        }
        if (cdSize > MAX_ENTRY_SIZE) throw IOException("Central directory too large")

        val cd = readAt(cdOffset, cdSize.toInt())
        val result = HashMap<String, Entry>(totalEntries)
        var p = 0
        repeat(totalEntries) {
            if (cd.getInt(p) != CD_SIG) throw IOException("Bad central directory entry")
            val flags = cd.getShort(p + 8).toUnsigned()
            val method = cd.getShort(p + 10).toUnsigned()
            val compressedSize = cd.getInt(p + 20).toUnsignedLong()
            val size = cd.getInt(p + 24).toUnsignedLong()
            val nameLength = cd.getShort(p + 28).toUnsigned()
            val extraLength = cd.getShort(p + 30).toUnsigned()
            val commentLength = cd.getShort(p + 32).toUnsigned()
            val localHeaderOffset = cd.getInt(p + 42).toUnsignedLong()
            // java.util.zip.ZipFile과 동일하게 엔트리 이름은 UTF-8로 해석
            val name = String(cd.array(), p + CD_HEADER_SIZE, nameLength, Charsets.UTF_8)
            result[name] = Entry(
                method = method,
                encrypted = flags and 0x1 != 0,
                compressedSize = compressedSize,
                size = size,
                localHeaderOffset = localHeaderOffset
            )
            p += CD_HEADER_SIZE + nameLength + extraLength + commentLength
        }
        return result
    }

    /** position 위치부터 length 바이트를 읽는다 (리틀 엔디언 버퍼) */
    private fun readAt(position: Long, length: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, position + buffer.position())
            if (n < 0) throw EOFException("Unexpected end of zip file")
        }
        return buffer
    }

    private fun inflate(compressed: ByteArray, size: Int): ByteArray {
        // nowrap 모드는 입력 끝에 더미 바이트가 하나 필요할 수 있다 (Inflater 문서 참고)
        val input = compressed.copyOf(compressed.size + 1)
        val inflater = Inflater(true)
        try {
            inflater.setInput(input)
            val out = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val n = inflater.inflate(out, offset, size - offset)
                if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                offset += n
            }
            if (offset != size) throw IOException("Inflated size mismatch: $offset != $size")
            return out
        } finally {
            inflater.end()
        }
    }

    private fun Short.toUnsigned(): Int = toInt() and 0xFFFF
    private fun Int.toUnsignedLong(): Long = toLong() and 0xFFFFFFFFL

    private companion object {
        const val LOCAL_HEADER_SIG = 0x04034b50
        const val CD_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50
        const val LOCAL_HEADER_SIZE = 30
        const val CD_HEADER_SIZE = 46
        const val EOCD_SIZE = 22
        const val MAX_COMMENT_LENGTH = 0xFFFF
        // 한 번에 메모리로 읽는 최대 크기 (EPUB의 OPF/커버 등은 이보다 훨씬 작다)
        const val MAX_ENTRY_SIZE = 64L * 1024 * 1024
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
    }
}
