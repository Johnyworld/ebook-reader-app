package com.rotein.ebookreader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReaderModelsTest {

    // --- parseSearchResults ---

    @Test
    fun `parseSearchResults - 정상 JSON 파싱`() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("cfi", "epubcfi(/6/4!/4/2)")
                put("excerpt", "hello world")
                put("chapter", "Chapter 1")
                put("page", 3)
                put("spineIndex", 1)
                put("charPos", 100)
            })
            put(JSONObject().apply {
                put("cfi", "epubcfi(/6/6!/4/2)")
                put("excerpt", "foo bar")
            })
        }

        val results = parseSearchResults(json)

        assertEquals(2, results.size)
        assertEquals("epubcfi(/6/4!/4/2)", results[0].cfi)
        assertEquals("hello world", results[0].excerpt)
        assertEquals("Chapter 1", results[0].chapter)
        assertEquals(3, results[0].page)
        assertEquals(1, results[0].spineIndex)
        assertEquals(100, results[0].charPos)

        assertEquals("epubcfi(/6/6!/4/2)", results[1].cfi)
        assertEquals("foo bar", results[1].excerpt)
        assertEquals("", results[1].chapter)
        assertEquals(0, results[1].page)
        assertEquals(-1, results[1].spineIndex)
        assertEquals(-1, results[1].charPos)
    }

    @Test
    fun `parseSearchResults - 빈 배열`() {
        val results = parseSearchResults(JSONArray())
        assertTrue(results.isEmpty())
    }

    // --- parseTocJson ---

    @Test
    fun `parseTocJson - 중첩 TOC 파싱`() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("label", "Part 1")
                put("href", "part1.xhtml")
                put("page", 1)
                put("subitems", JSONArray().apply {
                    put(JSONObject().apply {
                        put("label", "Chapter 1")
                        put("href", "ch1.xhtml")
                        put("page", 2)
                    })
                })
            })
        }

        val items = parseTocJson(json)

        assertEquals(1, items.size)
        assertEquals("Part 1", items[0].label)
        assertEquals("part1.xhtml", items[0].href)
        assertEquals(0, items[0].depth)
        assertEquals(1, items[0].page)
        assertEquals(1, items[0].subitems.size)
        assertEquals("Chapter 1", items[0].subitems[0].label)
        assertEquals(1, items[0].subitems[0].depth)
    }

    @Test
    fun `parseTocJson - 빈 배열`() {
        val items = parseTocJson(JSONArray())
        assertTrue(items.isEmpty())
    }

    // --- flattenToc ---

    @Test
    fun `flattenToc - 중첩 구조를 플랫으로 변환`() {
        val items = listOf(
            TocItem("Part 1", "p1.xhtml", 0, 1, listOf(
                TocItem("Ch 1", "c1.xhtml", 1, 2),
                TocItem("Ch 2", "c2.xhtml", 1, 5)
            )),
            TocItem("Part 2", "p2.xhtml", 0, 10)
        )

        val flat = flattenToc(items)

        assertEquals(4, flat.size)
        assertEquals("Part 1", flat[0].label)
        assertEquals("Ch 1", flat[1].label)
        assertEquals("Ch 2", flat[2].label)
        assertEquals("Part 2", flat[3].label)
    }

    @Test
    fun `flattenToc - 빈 목록`() {
        assertTrue(flattenToc(emptyList()).isEmpty())
    }

    // --- recalcSearchPages ---

    @Test
    fun `recalcSearchPages - 페이지 재계산`() {
        val results = listOf(
            SearchResultItem("cfi1", "text1", "ch1", 0, spineIndex = 0, charPos = 50),
            SearchResultItem("cfi2", "text2", "ch1", 0, spineIndex = 1, charPos = 200)
        )
        val spinePageOffsets = mapOf(0 to 0, 1 to 5)
        // spine 0: 페이지 경계 [0, 100, 200] → charPos 50은 인덱스 0
        // spine 1: 페이지 경계 [0, 150, 300] → charPos 200은 인덱스 1
        val charPageBreaksJson = JSONObject().apply {
            put("0", JSONArray(listOf(0, 100, 200)))
            put("1", JSONArray(listOf(0, 150, 300)))
        }.toString()

        val recalculated = recalcSearchPages(results, spinePageOffsets, charPageBreaksJson)

        assertEquals(0 + 0 + 1, recalculated[0].page)  // baseOffset(0) + pageWithin(0) + 1 = 1
        assertEquals(5 + 1 + 1, recalculated[1].page)   // baseOffset(5) + pageWithin(1) + 1 = 7
    }

    @Test
    fun `recalcSearchPages - 빈 결과`() {
        val results = recalcSearchPages(emptyList(), emptyMap(), "{}")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `recalcSearchPages - 빈 charPageBreaksJson`() {
        val results = listOf(SearchResultItem("cfi", "text", "ch", 5, 0, 100))
        val unchanged = recalcSearchPages(results, mapOf(0 to 0), "")
        assertEquals(5, unchanged[0].page) // 원래 page 유지
    }

    @Test
    fun `recalcSearchPages - spineIndex가 음수면 건너뜀`() {
        val results = listOf(SearchResultItem("cfi", "text", "ch", 5, spineIndex = -1, charPos = 100))
        val charBreaks = JSONObject().apply { put("0", JSONArray(listOf(0, 50))) }.toString()
        val unchanged = recalcSearchPages(results, mapOf(0 to 0), charBreaks)
        assertEquals(5, unchanged[0].page) // 원래 page 유지
    }
}
