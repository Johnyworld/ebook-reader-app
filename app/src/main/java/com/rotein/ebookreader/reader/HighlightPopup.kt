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
import com.rotein.ebookreader.Highlight
import com.rotein.ebookreader.HighlightSortStore
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HighlightPopup(
    highlights: List<Highlight>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int> = emptyMap(),
    onNavigate: (cfi: String) -> Unit,
    onDelete: (Highlight) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 88
    val headerHeightDp = 56
    val paginationHeightDp = 72
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(HighlightSortStore.load(context)) }
    val sortedHighlights = remember(highlights, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.CREATED_ASC -> highlights.sortedBy { it.createdAt }
            BookmarkSortOrder.CREATED_DESC -> highlights.sortedByDescending { it.createdAt }
            BookmarkSortOrder.PAGE_ASC -> highlights.sortedBy { it.page.takeIf { p -> p > 0 } ?: cfiToPage(it.cfi, spinePageOffsets, cfiPageMap) }
        }
    }
    val totalPages = maxOf(1, (sortedHighlights.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = sortedHighlights.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(sortOrder) { HighlightSortStore.save(context, sortOrder) }
    LaunchedEffect(sortedHighlights.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    FullScreenPopup {
            PopupHeaderBar(title = "하이라이트", onBack = onDismiss) {
                EreaderDropdownMenu(
                    items = BookmarkSortOrder.entries.toList(),
                    selectedItem = sortOrder,
                    onSelect = { sortOrder = it },
                    label = { it.label },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (highlights.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("하이라이트가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, highlight ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
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
                                        val highlightPage = highlight.page.takeIf { it > 0 } ?: cfiToPage(highlight.cfi, spinePageOffsets, cfiPageMap)
                                        if (highlightPage > 0) {
                                            Text("p.$highlightPage", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = EreaderColors.Black)
                                        }
                                        Text(
                                            highlight.chapterTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EreaderColors.DarkGray,
                                            modifier = Modifier.weight(1f).padding(start = if (highlightPage > 0) EreaderSpacing.S else 0.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
                                        )
                                    }
                                    Text(
                                        highlight.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = EreaderSpacing.XS)
                                    )
                                    Text(dateFormat.format(Date(highlight.createdAt)), style = MaterialTheme.typography.labelSmall, color = EreaderColors.DarkGray)
                                }
                                IconButton(onClick = { onDelete(highlight) }) {
                                    Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Column {
                if (sortedHighlights.isNotEmpty()) {
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = "${currentPage + 1}/$totalPages (${sortedHighlights.size}건)",
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
                }
            }
    }
}
