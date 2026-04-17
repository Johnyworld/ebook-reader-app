package com.rotein.ebookreader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.TocItem
import com.rotein.ebookreader.flattenToc
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun TocPopup(
    tocItems: List<TocItem>,
    bookTitle: String,
    currentChapterTitle: String,
    totalBookPages: Int,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val flatItems = remember(tocItems) { flattenToc(tocItems) }
    val density = LocalDensity.current
    val itemHeightDp = 45 // Row(44.dp) + HorizontalDivider(1.dp)
    var listHeightPx by remember { mutableStateOf(0) }
    val listHeightDp = with(density) { listHeightPx.toDp().value.toInt() }
    val itemsPerPage = if (listHeightDp > 0) maxOf(1, listHeightDp / itemHeightDp) else 20
    val totalPages = maxOf(1, (flatItems.size + itemsPerPage - 1) / itemsPerPage)
    var currentPage by remember { mutableStateOf(0) }
    if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    val startIndex = currentPage * itemsPerPage
    val pageItems = flatItems.drop(startIndex).take(itemsPerPage)

    FullScreenPopup {
            PopupHeaderBar(title = "목차: $bookTitle", onBack = onDismiss)
            Column(modifier = Modifier.weight(1f).onSizeChanged { listHeightPx = it.height }) {
                if (flatItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("목차를 불러오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val pageNumberWidth = 40.dp
                    val startPadding = EreaderSpacing.L
                    val gapWidth = EreaderSpacing.S
                    val lineSpacing = 16.dp
                    pageItems.forEachIndexed { index, item ->
                        val lineColor = EreaderColors.Gray
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { onNavigate(item.href) }
                                .testTag("tocItem_${startIndex + index}")
                                .then(
                                    if (item.depth > 0) {
                                        Modifier.drawBehind {
                                            val baseX = startPadding.toPx() + pageNumberWidth.toPx() + gapWidth.toPx()
                                            for (d in 1..item.depth) {
                                                val lineX = baseX + (d - 1) * lineSpacing.toPx()
                                                drawLine(
                                                    color = lineColor,
                                                    start = Offset(lineX, 0f),
                                                    end = Offset(lineX, size.height),
                                                    strokeWidth = 1.dp.toPx()
                                                )
                                            }
                                        }
                                    } else Modifier
                                )
                                .padding(
                                    start = startPadding,
                                    end = EreaderSpacing.L,
                                    top = EreaderSpacing.M,
                                    bottom = EreaderSpacing.M
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(gapWidth)
                        ) {
                            Box(
                                modifier = Modifier.width(pageNumberWidth),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (item.page > 0) {
                                    Text(
                                        "${item.page}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EreaderColors.DarkGray
                                    )
                                }
                            }
                            Text(
                                item.label,
                                style = if (item.depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                color = EreaderColors.Black,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = (item.depth * lineSpacing.value).dp)
                            )
                        }
                        HorizontalDivider(color = EreaderColors.Gray)
                    }
                }
            }
            PaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                centerText = "${currentPage + 1}/$totalPages (${flatItems.size}건)",
                onPrevious = { currentPage-- },
                onNext = { currentPage++ },
                modifier = Modifier.padding(bottom = EreaderSpacing.L),
            )
    }
}
