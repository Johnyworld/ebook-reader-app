package com.rotein.ebookreader

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.RandomAccessFile

object PdfMetadataParser {

    fun parse(path: String): BookMetadata? = try {
        val content = readTail(path, 8192)
        val info = findInfoDictionary(content)
        if (info != null) {
            BookMetadata(
                title = extractField(info, "Title"),
                author = extractField(info, "Author"),
                language = null,
                publisher = null,
                publishedDate = extractField(info, "CreationDate"),
                description = extractField(info, "Subject")
            )
        } else null
    } catch (_: Exception) {
        null
    }

    private fun readTail(path: String, maxBytes: Int): String {
        RandomAccessFile(path, "r").use { raf ->
            val len = raf.length()
            val start = maxOf(0L, len - maxBytes)
            raf.seek(start)
            val buf = ByteArray((len - start).toInt())
            raf.readFully(buf)
            return String(buf, Charsets.ISO_8859_1)
        }
    }

    /**
     * PDF 파일 끝부분에서 Info dictionary를 찾아 반환한다.
     * trailer의 /Info 참조 → 해당 indirect object의 << ... >> 내용을 추출.
     */
    private fun findInfoDictionary(content: String): String? {
        // trailer에서 /Info N 0 R 형태로 참조된 object 번호 찾기
        val trailerMatch = Regex("/Info\\s+(\\d+)\\s+\\d+\\s+R").find(content)
        if (trailerMatch != null) {
            val objNum = trailerMatch.groupValues[1]
            // N 0 obj ... endobj 에서 dictionary 추출
            val objPattern = Regex("$objNum\\s+0\\s+obj\\s*<<(.+?)>>", RegexOption.DOT_MATCHES_ALL)
            val objMatch = objPattern.find(content)
            if (objMatch != null) return objMatch.groupValues[1]
        }

        // cross-reference stream 방식이면 trailer가 없을 수 있음
        // 마지막 << ... >> 에서 /Info 가 있는 블록을 직접 찾기
        val inlinePattern = Regex("<<([^>]*?/Info[^>]*?)>>")
        val inlineMatch = inlinePattern.findAll(content).lastOrNull()
        if (inlineMatch != null) {
            val innerRef = Regex("/Info\\s+(\\d+)\\s+\\d+\\s+R").find(inlineMatch.groupValues[1])
            if (innerRef != null) {
                val objNum = innerRef.groupValues[1]
                val objPattern = Regex("$objNum\\s+0\\s+obj\\s*<<(.+?)>>", RegexOption.DOT_MATCHES_ALL)
                val objMatch = objPattern.find(content)
                if (objMatch != null) return objMatch.groupValues[1]
            }
        }

        return null
    }

    private fun extractField(info: String, key: String): String? {
        // /Key (value) 형태 — PDF literal string
        val parenPattern = Regex("/$key\\s*\\((.+?)\\)")
        parenPattern.find(info)?.let { return decodeString(it.groupValues[1]) }

        // /Key <hex> 형태 — PDF hex string
        val hexPattern = Regex("/$key\\s*<([0-9A-Fa-f]+)>")
        hexPattern.find(info)?.let { return decodeHexString(it.groupValues[1]) }

        return null
    }

    private fun decodeString(raw: String): String? {
        // UTF-16BE BOM으로 시작하면 유니코드
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).takeIf { it.isNotBlank() }
        }
        // PDFDocEncoding (Latin-1 호환)
        return raw.takeIf { it.isNotBlank() }
    }

    private fun decodeHexString(hex: String): String? {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        // UTF-16BE BOM 체크
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).takeIf { it.isNotBlank() }
        }
        return String(bytes, Charsets.ISO_8859_1).takeIf { it.isNotBlank() }
    }
}
