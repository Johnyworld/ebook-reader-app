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
    CREATED_DESC(R.string.sort_created_desc),
    CREATED_ASC(R.string.sort_created_asc),
    PAGE_ASC(R.string.sort_page_asc)
}

class AnnotationSortStore(private val prefName: String) {
    private val keyOrder = "sort_order"

    fun load(context: Context): BookmarkSortOrder {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        return BookmarkSortOrder.entries.firstOrNull { it.name == prefs.getString(keyOrder, null) }
            ?: BookmarkSortOrder.CREATED_DESC
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
    LAST_READ(R.string.sort_last_read),
    DATE_ADDED(R.string.sort_date_added),
    TITLE(R.string.sort_title),
    AUTHOR(R.string.sort_author);

    /** 각 필드의 기본 정렬 방향 */
    val defaultDescending: Boolean get() = this == DATE_ADDED || this == LAST_READ
}

enum class FilterMode(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    FAVORITE(R.string.filter_favorite),
    HIDDEN(R.string.filter_hidden)
}

/** 홈 목록 보기 방식: 전체 평면 목록 / 실제 폴더 구조 */
enum class ViewMode(@StringRes val labelRes: Int) {
    LIST(R.string.view_list),
    FOLDER(R.string.view_folder)
}

/** 보기 방식을 기기에 저장 — 다음 실행에도 유지 (폴더 위치는 저장하지 않음) */
object ViewModeStore {
    private const val PREF_NAME = "view_mode_pref"
    private const val KEY_MODE = "view_mode"

    fun load(context: Context): ViewMode {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return ViewMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ViewMode.LIST
    }

    fun save(context: Context, mode: ViewMode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

/** 폴더 보기 설정 — 폴더를 맨 위로 정렬할지 (기본: 맨 위, 끄면 폴더와 책을 섞어 정렬) */
object FolderSettingsStore {
    private const val PREF_NAME = "folder_settings_pref"
    private const val KEY_FOLDERS_FIRST = "folders_first"

    fun loadFoldersFirst(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_FOLDERS_FIRST, true)

    fun saveFoldersFirst(context: Context, foldersFirst: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FOLDERS_FIRST, foldersFirst)
            .apply()
    }
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

enum class SystemFontFilter(@StringRes val labelRes: Int) {
    DEVICE(R.string.font_filter_device),
    SYSTEM(R.string.font_filter_system),
    ALL(R.string.font_filter_all)
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

data class FontFileMaps(
    val system: Map<String, String>,
    val device: Map<String, String>,
    val all: Map<String, String>
)

fun getFontFileMaps(context: Context): FontFileMaps {
    val system = mutableMapOf<String, String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        SystemFonts.getAvailableFonts().forEach { font ->
            val file = font.file ?: return@forEach
            val name = extractFontDisplayName(file.nameWithoutExtension)
            if (name.isNotBlank() && !system.containsKey(name)) {
                system[name] = file.absolutePath
            }
        }
    }
    val device = mutableMapOf<String, String>()
    FontScanner.scanDeviceFonts(context).forEach { (name, path) ->
        if (!system.containsKey(name) && !device.containsKey(name)) {
            device[name] = path
        }
    }
    val all = system + device
    return FontFileMaps(system, device, all)
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

internal fun extractFontDisplayName(fileName: String): String {
    // CamelCase → "Camel Case", 하이픈을 공백으로
    return fileName
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .replace("-", " ")
        .trim()
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

/**
 * 4분할 페이지 넘김: 각 칸을 탭했을 때 다음 페이지(true)로 넘길지, 이전 페이지(false)로 넘길지.
 * 저장·순환을 위해 좌상/우상/좌하/우하 순서의 4비트 마스크(다음=1)로 변환할 수 있다.
 */
data class QuadPageFlip(
    val topLeft: Boolean,
    val topRight: Boolean,
    val bottomLeft: Boolean,
    val bottomRight: Boolean
) {
    fun toMask(): Int =
        (if (topLeft) 8 else 0) or (if (topRight) 4 else 0) or (if (bottomLeft) 2 else 0) or (if (bottomRight) 1 else 0)

    // 설정 행의 좌우 화살표로 16개 조합을 순환한다
    fun next(): QuadPageFlip = fromMask(toMask() + 1)
    fun prev(): QuadPageFlip = fromMask(toMask() - 1)

    companion object {
        // 기본값: 이전/다음, 다음/다음
        val DEFAULT = QuadPageFlip(topLeft = false, topRight = true, bottomLeft = true, bottomRight = true)

        // 하위 4비트만 사용하므로 음수·16 이상도 순환된 값으로 해석된다
        fun fromMask(mask: Int): QuadPageFlip {
            val m = mask and 0xF
            return QuadPageFlip(
                topLeft = m and 8 != 0,
                topRight = m and 4 != 0,
                bottomLeft = m and 2 != 0,
                bottomRight = m and 1 != 0
            )
        }
    }
}

enum class PageTapAction { PREV, NEXT, CENTER }

/**
 * 화면 탭 위치(x, y)를 페이지 넘김 동작으로 변환한다.
 * quad가 null이면 2분할(pageFlip) 기준, 아니면 4분할 기준으로 판정한다.
 * 4분할은 가로 가운데 1/3을 메뉴 영역으로 두고, 좌우 1/3 영역을 세로 절반으로 나눈다.
 */
fun resolvePageTap(x: Float, y: Float, w: Float, h: Float, pageFlip: ReaderPageFlip, quad: QuadPageFlip?): PageTapAction {
    val toAction = { isNext: Boolean -> if (isNext) PageTapAction.NEXT else PageTapAction.PREV }
    if (quad != null) {
        val top = y < h / 2f
        return when {
            x < w / 3f -> toAction(if (top) quad.topLeft else quad.bottomLeft)
            x > w * 2f / 3f -> toAction(if (top) quad.topRight else quad.bottomRight)
            else -> PageTapAction.CENTER
        }
    }
    return when (pageFlip) {
        ReaderPageFlip.LR_PREV_NEXT -> when {
            x < w / 3f -> PageTapAction.PREV
            x > w * 2f / 3f -> PageTapAction.NEXT
            else -> PageTapAction.CENTER
        }
        ReaderPageFlip.LR_NEXT_PREV -> when {
            x < w / 3f -> PageTapAction.NEXT
            x > w * 2f / 3f -> PageTapAction.PREV
            else -> PageTapAction.CENTER
        }
        ReaderPageFlip.TB_PREV_NEXT -> when {
            y < h / 3f -> PageTapAction.PREV
            y > h * 2f / 3f -> PageTapAction.NEXT
            else -> PageTapAction.CENTER
        }
        ReaderPageFlip.TB_NEXT_PREV -> when {
            y < h / 3f -> PageTapAction.NEXT
            y > h * 2f / 3f -> PageTapAction.PREV
            else -> PageTapAction.CENTER
        }
    }
}

// 볼륨 키로 페이지 넘기기 설정
enum class VolumeKeyAction(@StringRes val labelRes: Int) {
    UP_PREV_DOWN_NEXT(R.string.volume_key_up_prev),
    UP_NEXT_DOWN_PREV(R.string.volume_key_up_next),
    OFF(R.string.volume_key_off)
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
    // true면 2분할(pageFlip) 대신 4분할(quadPageFlip)로 페이지를 넘긴다
    val pageFlipQuad: Boolean = false,
    val quadPageFlip: QuadPageFlip = QuadPageFlip.DEFAULT,
    val leftInfo: ReaderBottomInfo = ReaderBottomInfo.NONE,
    val rightInfo: ReaderBottomInfo = ReaderBottomInfo.PAGE,
    val dualPage: Boolean = false,
    val volumeKey: VolumeKeyAction = VolumeKeyAction.UP_PREV_DOWN_NEXT,
    // 글꼴 이름별 두께 단계 (기본 0인 글꼴은 저장하지 않음)
    val fontWeights: Map<String, Int> = emptyMap()
)

fun ReaderSettings.layoutFingerprint(): String =
    "$fontName|$fontSize|$lineHeight|$paragraphSpacing|$paddingVertical|$paddingHorizontal|$dualPage|${textAlign.name}" +
        // 두께는 가변 글꼴에서 글자 폭을 바꾸므로 포함한다. 기본이면 기존 캐시와 호환되도록 생략.
        (if (fontWeightLevel != 0) "|w$fontWeightLevel" else "")

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
            pageFlipQuad = prefs.getBoolean("pageFlipQuad", false),
            quadPageFlip = QuadPageFlip.fromMask(prefs.getInt("quadPageFlip", QuadPageFlip.DEFAULT.toMask())),
            leftInfo = ReaderBottomInfo.entries.firstOrNull { it.name == prefs.getString("leftInfo", null) } ?: ReaderBottomInfo.NONE,
            rightInfo = ReaderBottomInfo.entries.firstOrNull { it.name == prefs.getString("rightInfo", null) } ?: ReaderBottomInfo.PAGE,
            dualPage = prefs.getBoolean("dualPage", false),
            volumeKey = VolumeKeyAction.entries.firstOrNull { it.name == prefs.getString("volumeKey", null) } ?: VolumeKeyAction.UP_PREV_DOWN_NEXT,
            fontWeights = decodeFontWeights(prefs.getString("fontWeights", null))
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
            .putBoolean("pageFlipQuad", settings.pageFlipQuad)
            .putInt("quadPageFlip", settings.quadPageFlip.toMask())
            .putString("leftInfo", settings.leftInfo.name)
            .putString("rightInfo", settings.rightInfo.name)
            .putBoolean("dualPage", settings.dualPage)
            .putString("volumeKey", settings.volumeKey.name)
            .putString("fontWeights", encodeFontWeights(settings.fontWeights))
            .apply()
    }
}
