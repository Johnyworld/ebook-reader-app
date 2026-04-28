package com.rotein.ebookreader.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.Bookmark
import com.rotein.ebookreader.BookmarkSortStore
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun BookmarkPopup(
    bookmarks: List<Bookmark>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (cfi: String) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    AnnotationListPopup(
        title = stringResource(R.string.bookmark),
        items = bookmarks,
        spinePageOffsets = spinePageOffsets,
        cfiPageMap = cfiPageMap,
        sortStore = BookmarkSortStore,
        itemHeightDp = 88,
        emptyText = stringResource(R.string.no_bookmarks),
        onDismiss = onDismiss
    ) { bookmark, page, dateStr ->
        Row(
            modifier = Modifier.fillMaxWidth().height(88.dp).testTag("bookmarkItem"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onNavigate(bookmark.cfi) }
                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = EreaderSpacing.XS),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page > 0) {
                        Text(
                            "p.$page",
                            style = EreaderFontSize.M.copy(fontWeight = FontWeight.Bold),
                            color = EreaderColors.Black,
                            modifier = Modifier.testTag("bookmarkPageText")
                        )
                    }
                    Text(
                        bookmark.chapterTitle,
                        style = EreaderFontSize.M,
                        color = EreaderColors.DarkGray,
                        modifier = Modifier.weight(1f).padding(start = if (page > 0) EreaderSpacing.S else 0.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
                Text(
                    bookmark.excerpt,
                    style = EreaderFontSize.M,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                )
                Text(
                    dateStr,
                    style = EreaderFontSize.M,
                    color = EreaderColors.DarkGray
                )
            }
            IconButton(onClick = { onDelete(bookmark) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
