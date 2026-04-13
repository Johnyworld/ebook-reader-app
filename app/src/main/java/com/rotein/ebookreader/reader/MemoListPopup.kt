package com.rotein.ebookreader.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.BookmarkSortOrder
import com.rotein.ebookreader.cfiToPage
import com.rotein.ebookreader.Memo
import com.rotein.ebookreader.MemoSortStore
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import java.text.SimpleDateFormat
import java.util.Date

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
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 112
    val headerHeightDp = 56
    val paginationHeightDp = 72
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(MemoSortStore.load(context)) }
    val sortedMemos = remember(memos, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.CREATED_ASC -> memos.sortedBy { it.createdAt }
            BookmarkSortOrder.CREATED_DESC -> memos.sortedByDescending { it.createdAt }
            BookmarkSortOrder.PAGE_ASC -> memos.sortedBy { it.page.takeIf { p -> p > 0 } ?: cfiToPage(it.cfi, spinePageOffsets, cfiPageMap) }
        }
    }
    val totalPages = maxOf(1, (sortedMemos.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = sortedMemos.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault()) }

    LaunchedEffect(sortOrder) { MemoSortStore.save(context, sortOrder) }
    LaunchedEffect(sortedMemos.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    FullScreenPopup {
            PopupHeaderBar(title = "메모", onBack = onDismiss) {
                EreaderDropdownMenu(
                    items = BookmarkSortOrder.entries.toList(),
                    selectedItem = sortOrder,
                    onSelect = { sortOrder = it },
                    label = { it.label },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (memos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("메모가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, memo ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
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
                                        val memoPage = memo.page.takeIf { it > 0 } ?: cfiToPage(memo.cfi, spinePageOffsets, cfiPageMap)
                                        if (memoPage > 0) {
                                            Text("p.$memoPage", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = EreaderColors.Black)
                                        }
                                        Text(
                                            memo.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EreaderColors.DarkGray,
                                            modifier = Modifier.weight(1f).padding(start = if (memoPage > 0) EreaderSpacing.S else 0.dp),
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
                                            Text(memo.note, style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text(dateFormat.format(Date(memo.createdAt)), style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray)
                                }
                                IconButton(onClick = { onDelete(memo) }) {
                                    Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Column {
                if (sortedMemos.isNotEmpty()) {
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = "${currentPage + 1}/$totalPages (${sortedMemos.size}건)",
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
                }
            }
    }
}
