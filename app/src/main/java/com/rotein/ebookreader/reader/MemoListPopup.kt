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
import androidx.compose.material.icons.filled.ModeComment
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
import com.rotein.ebookreader.Memo
import com.rotein.ebookreader.MemoSortStore
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun MemoListPopup(
    memos: List<Memo>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (Memo) -> Unit,
    onEdit: (Memo) -> Unit,
    onDelete: (Memo) -> Unit,
    onDismiss: () -> Unit
) {
    AnnotationListPopup(
        title = stringResource(R.string.memo),
        items = memos,
        spinePageOffsets = spinePageOffsets,
        cfiPageMap = cfiPageMap,
        sortStore = MemoSortStore,
        itemHeightDp = 112,
        emptyText = stringResource(R.string.no_memos),
        onDismiss = onDismiss
    ) { memo, page, dateStr ->
        Row(
            modifier = Modifier.fillMaxWidth().height(112.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f).fillMaxHeight()
                    .clickable { onNavigate(memo); onEdit(memo) }
                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = EreaderSpacing.XS),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page > 0) {
                        Text("p.$page", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = EreaderColors.Black)
                    }
                    Text(
                        memo.chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = EreaderColors.DarkGray,
                        modifier = Modifier.weight(1f).padding(start = if (page > 0) EreaderSpacing.S else 0.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                    )
                }
                Text(
                    memo.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                )
                if (memo.note.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.XS),
                        modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                    ) {
                        Icon(Icons.Default.ModeComment, contentDescription = null, modifier = Modifier.size(12.dp), tint = EreaderColors.DarkGray)
                        Text(memo.note, style = MaterialTheme.typography.bodySmall, color = EreaderColors.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = EreaderColors.DarkGray)
            }
            IconButton(onClick = { onDelete(memo) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp))
            }
        }
    }
}
