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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.Highlight
import com.rotein.ebookreader.HighlightSortStore
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun HighlightPopup(
    highlights: List<Highlight>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (cfi: String) -> Unit,
    onDelete: (Highlight) -> Unit,
    onDismiss: () -> Unit
) {
    AnnotationListPopup(
        title = stringResource(R.string.highlight),
        items = highlights,
        spinePageOffsets = spinePageOffsets,
        cfiPageMap = cfiPageMap,
        sortStore = HighlightSortStore,
        itemHeightDp = 88,
        emptyText = stringResource(R.string.no_highlights),
        onDismiss = onDismiss
    ) { highlight, page, dateStr ->
        Row(
            modifier = Modifier.fillMaxWidth().height(88.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f).fillMaxHeight()
                    .clickable { onNavigate(highlight.cfi) }
                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = EreaderSpacing.XS),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page > 0) {
                        Text("p.$page", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = EreaderColors.Black)
                    }
                    Text(
                        highlight.chapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = EreaderColors.DarkGray,
                        modifier = Modifier.weight(1f).padding(start = if (page > 0) EreaderSpacing.S else 0.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                    )
                }
                Text(
                    highlight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                )
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray)
            }
            IconButton(onClick = { onDelete(highlight) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp))
            }
        }
    }
}
