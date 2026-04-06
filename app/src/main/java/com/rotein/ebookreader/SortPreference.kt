package com.rotein.ebookreader

import android.content.Context
import android.graphics.fonts.SystemFonts
import android.os.Build

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

val READER_FONT_FAMILIES = listOf("기본", "나눔고딕", "나눔명조", "본고딕", "본명조")

const val FONT_EPUB_ORIGINAL = "epub_original"

fun getSystemFontFamilies(): List<String> {
    val families = mutableListOf("시스템 폰트", FONT_EPUB_ORIGINAL)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        SystemFonts.getAvailableFonts()
            .mapNotNull { it.file?.nameWithoutExtension }
            .map { extractFontFamilyName(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .forEach { families.add(it) }
    }
    return families
}

fun getSystemFontFileMap(): Map<String, String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyMap()
    val result = mutableMapOf<String, String>()
    SystemFonts.getAvailableFonts().forEach { font ->
        val file = font.file ?: return@forEach
        val name = extractFontFamilyName(file.nameWithoutExtension)
        if (name.isNotBlank() && !result.containsKey(name)) {
            result[name] = file.absolutePath
        }
    }
    return result
}

private fun extractFontFamilyName(fileName: String): String {
    val weightStyleSuffixes = listOf(
        "-Regular", "-Bold", "-Italic", "-Light", "-Medium", "-SemiBold",
        "-ExtraBold", "-Black", "-Thin", "-Condensed", "-Expanded",
        "-BoldItalic", "-LightItalic", "-MediumItalic", "-ThinItalic",
        "-Variable", "-VF"
    )
    var name = fileName
    for (suffix in weightStyleSuffixes) {
        if (name.endsWith(suffix, ignoreCase = true)) {
            name = name.dropLast(suffix.length)
            break
        }
    }
    // CamelCase → "Camel Case"
    return name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").trim()
}

enum class ReaderTextAlign(val label: String) {
    JUSTIFY("양쪽"), LEFT("왼쪽"), RIGHT("오른쪽"), CENTER("가운데")
}

enum class ReaderPageFlip(val label: String) {
    LR_PREV_NEXT("좌우\n이전/다음"),
    LR_NEXT_PREV("좌우\n다음/이전"),
    TB_PREV_NEXT("상하\n이전/다음"),
    TB_NEXT_PREV("상하\n다음/이전")
}

enum class ReaderBottomInfo(val label: String) {
    NONE("없음"),
    BOOK_TITLE("책 제목"),
    CHAPTER_TITLE("챕터 제목"),
    PAGE("페이지 수"),
    CLOCK("시계"),
    PROGRESS("독서 진행률")
}

data class ReaderSettings(
    val fontIndex: Int = 0,
    val fontSize: Int = 16,
    val textAlign: ReaderTextAlign = ReaderTextAlign.JUSTIFY,
    val lineHeight: Float = 1.5f,
    val paragraphSpacing: Int = 0,
    val paddingVertical: Int = 20,
    val paddingHorizontal: Int = 20,
    val pageFlip: ReaderPageFlip = ReaderPageFlip.LR_PREV_NEXT,
    val leftInfo: ReaderBottomInfo = ReaderBottomInfo.NONE,
    val rightInfo: ReaderBottomInfo = ReaderBottomInfo.PAGE,
    val dualPage: Boolean = false
)

object ReaderSettingsStore {
    private const val PREF_NAME = "reader_settings"

    fun load(context: Context): ReaderSettings {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return ReaderSettings(
            fontIndex = prefs.getInt("fontIndex", 0).coerceIn(0, READER_FONT_FAMILIES.lastIndex),
            fontSize = prefs.getInt("fontSize", 16),
            textAlign = ReaderTextAlign.entries.firstOrNull { it.name == prefs.getString("textAlign", null) } ?: ReaderTextAlign.JUSTIFY,
            lineHeight = prefs.getFloat("lineHeight", 1.5f),
            paragraphSpacing = prefs.getInt("paragraphSpacing", 0),
            paddingVertical = prefs.getInt("paddingVertical", 20),
            paddingHorizontal = prefs.getInt("paddingHorizontal", 20),
            pageFlip = ReaderPageFlip.entries.firstOrNull { it.name == prefs.getString("pageFlip", null) } ?: ReaderPageFlip.LR_PREV_NEXT,
            leftInfo = ReaderBottomInfo.entries.firstOrNull { it.name == prefs.getString("leftInfo", null) } ?: ReaderBottomInfo.NONE,
            rightInfo = ReaderBottomInfo.entries.firstOrNull { it.name == prefs.getString("rightInfo", null) } ?: ReaderBottomInfo.PAGE,
            dualPage = prefs.getBoolean("dualPage", false)
        )
    }

    fun save(context: Context, settings: ReaderSettings) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt("fontIndex", settings.fontIndex)
            .putInt("fontSize", settings.fontSize)
            .putString("textAlign", settings.textAlign.name)
            .putFloat("lineHeight", settings.lineHeight)
            .putInt("paragraphSpacing", settings.paragraphSpacing)
            .putInt("paddingVertical", settings.paddingVertical)
            .putInt("paddingHorizontal", settings.paddingHorizontal)
            .putString("pageFlip", settings.pageFlip.name)
            .putString("leftInfo", settings.leftInfo.name)
            .putString("rightInfo", settings.rightInfo.name)
            .putBoolean("dualPage", settings.dualPage)
            .apply()
    }
}
