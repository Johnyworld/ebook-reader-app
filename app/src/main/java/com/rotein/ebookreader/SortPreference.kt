package com.rotein.ebookreader

import android.content.Context
import android.graphics.fonts.SystemFonts
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class BookmarkSortOrder(@StringRes val labelRes: Int) {
    CREATED_ASC(R.string.sort_created_asc),
    CREATED_DESC(R.string.sort_created_desc),
    PAGE_ASC(R.string.sort_page_asc)
}

class AnnotationSortStore(private val prefName: String) {
    private val keyOrder = "sort_order"

    fun load(context: Context): BookmarkSortOrder {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        return BookmarkSortOrder.entries.firstOrNull { it.name == prefs.getString(keyOrder, null) }
            ?: BookmarkSortOrder.CREATED_ASC
    }

    fun save(context: Context, order: BookmarkSortOrder) {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
            .putString(keyOrder, order.name)
            .apply()
    }
}

val BookmarkSortStore = AnnotationSortStore("bookmark_sort_pref")
val MemoSortStore = AnnotationSortStore("memo_sort_pref")
val HighlightSortStore = AnnotationSortStore("highlight_sort_pref")

enum class SortField(@StringRes val labelRes: Int) {
    TITLE(R.string.sort_title),
    AUTHOR(R.string.sort_author),
    DATE_ADDED(R.string.sort_date_added),
    LAST_READ(R.string.sort_last_read);

    /** 각 필드의 기본 정렬 방향 */
    val defaultDescending: Boolean get() = this == DATE_ADDED || this == LAST_READ
}

enum class FilterMode(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    FAVORITE(R.string.filter_favorite),
    HIDDEN(R.string.filter_hidden)
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

val READER_BUILTIN_FONT_NAMES = emptyList<String>()

const val FONT_EPUB_ORIGINAL = "epub_original"
const val FONT_SYSTEM = "시스템 글꼴"

@Composable
fun fontDisplayName(fontName: String): String = when (fontName) {
    FONT_EPUB_ORIGINAL -> stringResource(R.string.font_epub_original)
    FONT_SYSTEM -> stringResource(R.string.font_system)
    else -> fontName
}

enum class FontSortOrder(@StringRes val labelRes: Int) {
    NAME_ASC(R.string.font_sort_name_asc),
    NAME_DESC(R.string.font_sort_name_desc),
    CREATED_DESC(R.string.font_sort_latest),
    IMPORTED(R.string.font_sort_imported)
}

enum class SystemFontSortOrder(@StringRes val labelRes: Int) {
    NAME_ASC(R.string.font_sort_name_asc),
    NAME_DESC(R.string.font_sort_name_desc)
}

object ImportedFontStore {
    private const val PREF_NAME = "imported_fonts"
    private const val KEY_FONTS = "font_entries"

    data class FontEntry(val name: String, val filePath: String)

    fun load(context: Context): List<FontEntry> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FONTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                FontEntry(obj.getString("name"), obj.getString("path"))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun add(context: Context, name: String, filePath: String) {
        val current = load(context).toMutableList()
        if (current.none { it.name == name }) current.add(FontEntry(name, filePath))
        val arr = JSONArray()
        current.forEach { arr.put(JSONObject().put("name", it.name).put("path", it.filePath)) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FONTS, arr.toString()).apply()
    }

    fun remove(context: Context, name: String) {
        val current = load(context).filter { it.name != name }
        val arr = JSONArray()
        current.forEach { arr.put(JSONObject().put("name", it.name).put("path", it.filePath)) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FONTS, arr.toString()).apply()
    }

    fun contains(context: Context, name: String): Boolean = load(context).any { it.name == name }

    fun getDir(context: Context): File = File(context.filesDir, "fonts").also { it.mkdirs() }
}

fun getSystemFontFamilies(context: Context): List<String> {
    val families = mutableListOf(context.getString(R.string.font_system_fonts), FONT_EPUB_ORIGINAL)
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

internal fun extractFontFamilyName(fileName: String): String {
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

enum class ReaderTextAlign(@StringRes val labelRes: Int) {
    JUSTIFY(R.string.align_justify), LEFT(R.string.align_left), RIGHT(R.string.align_right), CENTER(R.string.align_center)
}

enum class ReaderPageFlip(@StringRes val labelRes: Int) {
    LR_PREV_NEXT(R.string.page_flip_lr_prev_next),
    LR_NEXT_PREV(R.string.page_flip_lr_next_prev),
    TB_PREV_NEXT(R.string.page_flip_tb_prev_next),
    TB_NEXT_PREV(R.string.page_flip_tb_next_prev)
}

enum class ReaderBottomInfo(@StringRes val labelRes: Int) {
    NONE(R.string.bottom_info_none),
    BOOK_TITLE(R.string.bottom_info_book_title),
    CHAPTER_TITLE(R.string.bottom_info_chapter_title),
    PAGE(R.string.bottom_info_page),
    CLOCK(R.string.bottom_info_clock),
    PROGRESS(R.string.bottom_info_progress)
}

data class ReaderSettings(
    val fontName: String = FONT_EPUB_ORIGINAL,
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

fun ReaderSettings.layoutFingerprint(): String =
    "$fontName|$fontSize|$lineHeight|$paragraphSpacing|$paddingVertical|$paddingHorizontal|$dualPage|${textAlign.name}"

object ReaderSettingsStore {
    private const val PREF_NAME = "reader_settings"

    fun load(context: Context): ReaderSettings {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return ReaderSettings(
            fontName = prefs.getString("fontName", FONT_EPUB_ORIGINAL) ?: FONT_EPUB_ORIGINAL,
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
            .putString("fontName", settings.fontName)
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
