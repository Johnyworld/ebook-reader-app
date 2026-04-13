package com.rotein.ebookreader.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val density = LocalDensity.current
    val itemHeightDp = 49 // top(12) + bottom(12) padding + bodyLarge line height
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp().value.toInt() }
    val itemsPerPage = maxOf(1, (screenHeightDp - 56 - bottomBarHeightDp) / itemHeightDp)
    val totalPages = maxOf(1, (flatItems.size + itemsPerPage - 1) / itemsPerPage)
    var currentPage by remember { mutableStateOf(0) }
    val pageItems = flatItems.drop(currentPage * itemsPerPage).take(itemsPerPage)

    FullScreenPopup {
            PopupHeaderBar(title = "목차: $bookTitle", onBack = onDismiss)
            Column(modifier = Modifier.weight(1f)) {
                if (flatItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("목차를 불러오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    pageItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(item.href) }
                                .padding(
                                    start = EreaderSpacing.L,
                                    end = EreaderSpacing.L,
                                    top = EreaderSpacing.M,
                                    bottom = EreaderSpacing.M
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.S)
                        ) {
                            Box(
                                modifier = Modifier.width(40.dp),
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
                                color = if (item.label == currentChapterTitle) EreaderColors.Black else EreaderColors.Black,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = (item.depth * 16).dp)
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
                modifier = Modifier.padding(bottom = EreaderSpacing.L).onSizeChanged { bottomBarHeightPx = it.height },
            )
    }
}
