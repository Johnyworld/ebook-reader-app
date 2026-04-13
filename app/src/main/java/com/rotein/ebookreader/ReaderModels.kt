package com.rotein.ebookreader

import org.json.JSONArray

data class TocItem(
    val label: String,
    val href: String,
    val depth: Int,
    val page: Int = 0,
    val subitems: List<TocItem> = emptyList()
)

data class SearchResultItem(
    val cfi: String,
    val excerpt: String,
    val chapter: String = "",
    val page: Int = 0,
    val spineIndex: Int = -1,
    val charPos: Int = -1
)

internal fun parseSearchResults(json: JSONArray): List<SearchResultItem> {
    val items = mutableListOf<SearchResultItem>()
    for (i in 0 until json.length()) {
        val obj = json.getJSONObject(i)
        items.add(SearchResultItem(
            cfi = obj.optString("cfi", ""),
            excerpt = obj.optString("excerpt", ""),
            chapter = obj.optString("chapter", ""),
            page = obj.optInt("page", 0),
            spineIndex = obj.optInt("spineIndex", -1),
            charPos = obj.optInt("charPos", -1)
        ))
    }
    return items
}

internal fun recalcSearchPages(
    results: List<SearchResultItem>,
    spinePageOffsets: Map<Int, Int>,
    charPageBreaksJson: String
): List<SearchResultItem> {
    if (results.isEmpty() || charPageBreaksJson.isEmpty()) return results
    val breaksMap = try {
        val obj = org.json.JSONObject(charPageBreaksJson)
        val map = mutableMapOf<Int, List<Int>>()
        obj.keys().forEach { key ->
            val arr = obj.getJSONArray(key)
            val list = mutableListOf<Int>()
            for (i in 0 until arr.length()) list.add(arr.getInt(i))
            map[key.toInt()] = list
        }
        map
    } catch (_: Exception) { return results }

    return results.map { r ->
        if (r.spineIndex < 0 || r.charPos < 0) return@map r
        val breaks = breaksMap[r.spineIndex]
        val baseOffset = spinePageOffsets[r.spineIndex] ?: 0
        val pageWithin = if (breaks != null && breaks.size > 1) {
            var pw = 0
            for (bi in breaks.indices.reversed()) {
                if (r.charPos >= breaks[bi]) { pw = bi; break }
            }
            pw
        } else 0
        r.copy(page = baseOffset + pageWithin + 1)
    }
}

internal fun parseTocJson(json: JSONArray, depth: Int = 0): List<TocItem> {
    val items = mutableListOf<TocItem>()
    for (i in 0 until json.length()) {
        val obj = json.getJSONObject(i)
        val subitems = if (obj.has("subitems")) parseTocJson(obj.getJSONArray("subitems"), depth + 1) else emptyList()
        items.add(TocItem(
            label = obj.getString("label"),
            href = obj.getString("href"),
            depth = depth,
            page = if (obj.has("page")) obj.getInt("page") else 0,
            subitems = subitems
        ))
    }
    return items
}

internal fun flattenToc(items: List<TocItem>): List<TocItem> {
    val result = mutableListOf<TocItem>()
    for (item in items) {
        result.add(item)
        result.addAll(flattenToc(item.subitems))
    }
    return result
}
