package com.rotein.ebookreader

import android.content.Context

enum class BookmarkSortOrder(val label: String) {
    CREATED_ASC("등록순"),
    CREATED_DESC("최신순"),
    PAGE_ASC("페이지순")
}

object BookmarkSortStore {
    private const val PREF_NAME = "bookmark_sort_pref"
    private const val KEY_ORDER = "sort_order"

    fun load(context: Context): BookmarkSortOrder {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return BookmarkSortOrder.entries.firstOrNull { it.name == prefs.getString(KEY_ORDER, null) }
            ?: BookmarkSortOrder.CREATED_ASC
    }

    fun save(context: Context, order: BookmarkSortOrder) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.name)
            .apply()
    }
}

object MemoSortStore {
    private const val PREF_NAME = "memo_sort_pref"
    private const val KEY_ORDER = "sort_order"

    fun load(context: Context): BookmarkSortOrder {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return BookmarkSortOrder.entries.firstOrNull { it.name == prefs.getString(KEY_ORDER, null) }
            ?: BookmarkSortOrder.CREATED_ASC
    }

    fun save(context: Context, order: BookmarkSortOrder) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.name)
            .apply()
    }
}

object HighlightSortStore {
    private const val PREF_NAME = "highlight_sort_pref"
    private const val KEY_ORDER = "sort_order"

    fun load(context: Context): BookmarkSortOrder {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return BookmarkSortOrder.entries.firstOrNull { it.name == prefs.getString(KEY_ORDER, null) }
            ?: BookmarkSortOrder.CREATED_ASC
    }

    fun save(context: Context, order: BookmarkSortOrder) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.name)
            .apply()
    }
}

enum class SortField(val label: String) {
    TITLE("제목순"),
    AUTHOR("작가순"),
    DATE_ADDED("담은순"),
    LAST_READ("읽은순");

    /** 각 필드의 기본 정렬 방향 */
    val defaultDescending: Boolean get() = this == DATE_ADDED || this == LAST_READ
}

data class SortPreference(
    val field: SortField = SortField.LAST_READ
)

object SortPreferenceStore {
    private const val PREF_NAME = "sort_pref"
    private const val KEY_FIELD = "sort_field"

    fun load(context: Context): SortPreference {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val field = SortField.entries.firstOrNull { it.name == prefs.getString(KEY_FIELD, null) }
            ?: SortField.LAST_READ
        return SortPreference(field)
    }

    fun save(context: Context, pref: SortPreference) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FIELD, pref.field.name)
            .apply()
    }
}
