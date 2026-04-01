package com.rotein.ebookreader

import android.content.Context

enum class SortField(val label: String) {
    TITLE("제목"),
    AUTHOR("작가명"),
    DATE_ADDED("담은 순"),
    LAST_READ("읽은 순");

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
