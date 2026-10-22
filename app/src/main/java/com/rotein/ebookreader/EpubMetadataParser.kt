package com.rotein.ebookreader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.nio.channels.FileChannel
import java.util.zip.ZipFile

object EpubMetadataParser {

    /** EPUB 컨테이너 엔트리 접근 추상화 — 로컬 경로(ZipFile)와 FileChannel(ChannelZipReader) 공용 */
    private interface EpubArchive {
        fun size(name: String): Long?
        fun read(name: String): ByteArray?
    }

    private class ZipFileArchive(private val zip: ZipFile) : EpubArchive {
        override fun size(name: String): Long? = zip.getEntry(name)?.size
        override fun read(name: String): ByteArray? =
            zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
    }

    private class ChannelArchive(private val reader: ChannelZipReader) : EpubArchive {
        override fun size(name: String): Long? = reader.size(name)
        override fun read(name: String): ByteArray? = reader.read(name)
    }

    fun parse(path: String): BookMetadata? = try {
        ZipFile(path).use { zip -> parse(ZipFileArchive(zip)) }
    } catch (e: Exception) {
        null
    }

    /** SAF 파일 디스크립터의 채널에서 파일 복사 없이 메타데이터를 읽는다 */
    fun parse(channel: FileChannel): BookMetadata? = try {
        parse(ChannelArchive(ChannelZipReader(channel)))
    } catch (e: Exception) {
        null
    }

    private fun parse(archive: EpubArchive): BookMetadata? {
        val opfPath = findOpfPath(archive) ?: return null
        return parseOpf(archive, opfPath)
    }

    private const val MAX_COVER_SIZE = 10L * 1024 * 1024 // 10MB — 비정상적으로 큰 이미지 방지

    fun extractCover(path: String): ByteArray? = try {
        ZipFile(path).use { zip -> extractCover(ZipFileArchive(zip)) }
    } catch (e: Exception) {
        null
    }

    /** SAF 파일 디스크립터의 채널에서 파일 복사 없이 커버 이미지를 읽는다 */
    fun extractCover(channel: FileChannel): ByteArray? = try {
        extractCover(ChannelArchive(ChannelZipReader(channel)))
    } catch (e: Exception) {
        null
    }

    private fun extractCover(archive: EpubArchive): ByteArray? {
        val opfPath = findOpfPath(archive) ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")
        val coverHref = findCoverHref(archive, opfPath) ?: return null
        val coverPath = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
        val size = archive.size(coverPath) ?: return null
        if (size > MAX_COVER_SIZE) return null
        return archive.read(coverPath)
    }

    private fun findOpfPath(archive: EpubArchive): String? {
        val bytes = archive.read("META-INF/container.xml") ?: return null
        return ByteArrayInputStream(bytes).use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return@use parser.getAttributeValue(null, "full-path")
                }
                event = parser.next()
            }
            null
        }
    }

    /**
     * OPF manifest에서 커버 이미지 href를 찾는다.
     * 우선순위:
     *   1. EPUB3 - properties="cover-image" 속성
     *   2. EPUB2 - <meta name="cover" content="id"/> → manifest item
     *   3. id가 "cover"인 manifest item
     *   4. href에 "cover"가 포함된 이미지 파일
     */
    private fun findCoverHref(archive: EpubArchive, opfPath: String): String? {
        val bytes = archive.read(opfPath) ?: return null
        return ByteArrayInputStream(bytes).use { stream ->
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)

        var coverId: String? = null
        val manifestItems = mutableMapOf<String, String>() // id -> href
        var coverImageHref: String? = null                 // EPUB3 properties="cover-image"

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfterLast(':')) {
                    "meta" -> {
                        if (parser.getAttributeValue(null, "name") == "cover") {
                            coverId = parser.getAttributeValue(null, "content")
                        }
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val properties = parser.getAttributeValue(null, "properties") ?: ""
                        if (href.isNotEmpty()) {
                            if (id.isNotEmpty()) manifestItems[id] = href
                            if (properties.contains("cover-image")) coverImageHref = href
                        }
                    }
                }
            }
            event = parser.next()
        }

        coverImageHref
            ?: coverId?.let { manifestItems[it] }
            ?: manifestItems["cover"]
            ?: manifestItems.values.firstOrNull { href ->
                href.contains("cover", ignoreCase = true) &&
                (href.endsWith(".jpg", true) || href.endsWith(".jpeg", true) || href.endsWith(".png", true))
            }
        } // use
    }

    private fun parseOpf(archive: EpubArchive, opfPath: String): BookMetadata? {
        val bytes = archive.read(opfPath) ?: return null
        return ByteArrayInputStream(bytes).use { stream ->
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)

        var title: String? = null
        var author: String? = null
        var language: String? = null
        var publisher: String? = null
        var date: String? = null
        var description: String? = null
        var currentTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.substringAfterLast(':')
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) when (currentTag) {
                        "title" -> if (title == null) title = text
                        "creator" -> if (author == null) author = text
                        "language" -> if (language == null) language = text
                        "publisher" -> if (publisher == null) publisher = text
                        "date" -> if (date == null) date = text
                        "description" -> if (description == null) description = text
                    }
                }
                XmlPullParser.END_TAG -> currentTag = null
            }
            event = parser.next()
        }

        BookMetadata(title, author, language, publisher, date, description)
        } // use
    }
}
