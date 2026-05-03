package com.rotein.ebookreader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile

object EpubMetadataParser {

    fun parse(path: String): BookMetadata? = try {
        ZipFile(path).use { zip ->
            val opfPath = findOpfPath(zip) ?: return null
            parseOpf(zip, opfPath)
        }
    } catch (e: Exception) {
        null
    }

    private const val MAX_COVER_SIZE = 10L * 1024 * 1024 // 10MB — 비정상적으로 큰 이미지 방지

    fun extractCover(path: String): ByteArray? = try {
        ZipFile(path).use { zip ->
            val opfPath = findOpfPath(zip) ?: return null
            val opfDir = opfPath.substringBeforeLast('/', "")
            val coverHref = findCoverHref(zip, opfPath) ?: return null
            val coverPath = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
            val entry = zip.getEntry(coverPath) ?: return null
            if (entry.size > MAX_COVER_SIZE) return null
            zip.getInputStream(entry).use { it.readBytes() }
        }
    } catch (e: Exception) {
        null
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        return zip.getInputStream(entry).use { stream ->
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
    private fun findCoverHref(zip: ZipFile, opfPath: String): String? {
        val entry = zip.getEntry(opfPath) ?: return null
        return zip.getInputStream(entry).use { stream ->
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

    private fun parseOpf(zip: ZipFile, opfPath: String): BookMetadata? {
        val entry = zip.getEntry(opfPath) ?: return null
        return zip.getInputStream(entry).use { stream ->
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
