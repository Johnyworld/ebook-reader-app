package com.rotein.ebookreader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rotein.ebookreader.ui.theme.EreaderFontSize

internal fun cfiToPage(cfi: String, spinePageOffsets: Map<Int, Int>, cfiPageMap: Map<String, Int> = emptyMap()): Int {
    if (cfi.isEmpty()) return 0
    cfiPageMap[cfi]?.let { return it }
    if (spinePageOffsets.isEmpty()) return 0
    val step = Regex("/6/(\\d+)").find(cfi)?.groupValues?.get(1)?.toIntOrNull() ?: return 0
    val spineIndex = (step / 2 - 1).coerceAtLeast(0)
    return (spinePageOffsets[spineIndex] ?: 0) + 1
}

internal fun readerBottomInfoText(
    info: ReaderBottomInfo,
    book: BookFile,
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    readingProgress: Float,
    currentTime: String
): String? = when (info) {
    ReaderBottomInfo.NONE -> null
    ReaderBottomInfo.BOOK_TITLE -> book.metadata?.title ?: book.name
    ReaderBottomInfo.CHAPTER_TITLE -> chapterTitle.ifEmpty { null }
    ReaderBottomInfo.PAGE -> if (totalPages > 0) "$currentPage / $totalPages" else null
    ReaderBottomInfo.CLOCK -> currentTime.ifEmpty { null }
    ReaderBottomInfo.PROGRESS -> "${(readingProgress * 100).toInt()}%"
}

internal fun fontFamilyForJs(fontName: String): String = when (fontName) {
    FONT_EPUB_ORIGINAL -> FONT_EPUB_ORIGINAL
    FONT_SYSTEM -> ""
    else -> fontName
}

internal fun String.escapeCfiForJs(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

/** JS 문자열 리터럴 안에 안전하게 삽입하기 위한 이스케이프 */
internal fun String.escapeForJs(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("<", "\\u003c")  // </script> 탈출 방지
        .replace(">", "\\u003e")

@Composable
internal fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
internal fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "읽는 중...", style = EreaderFontSize.L)
    }
}
