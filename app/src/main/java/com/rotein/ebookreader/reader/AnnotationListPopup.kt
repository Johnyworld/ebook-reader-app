package com.rotein.ebookreader.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.rotein.ebookreader.R
import com.rotein.ebookreader.AnnotationItem
import com.rotein.ebookreader.AnnotationSortStore
import com.rotein.ebookreader.BookmarkSortOrder
import com.rotein.ebookreader.cfiToPage
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import java.util.Locale

@Composable
internal fun <T : AnnotationItem> AnnotationListPopup(
    title: String,
    items: List<T>,
    spinePageOffsets: Map<Int, Int>,
    cfiPageMap: Map<String, Int>,
    sortStore: AnnotationSortStore,
    itemHeightDp: Int = 88,
    emptyText: String = "",
    onDismiss: () -> Unit,
    itemContent: @Composable (item: T, page: Int, dateStr: String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // 콘텐츠 영역의 실제 높이를 측정하여 페이지당 아이템 수 계산
    var contentHeightDp by remember { mutableStateOf(0) }
    val itemsPerPage = if (contentHeightDp > 0) maxOf(1, contentHeightDp / itemHeightDp) else 1
    var currentPage by remember { mutableStateOf(0) }
    var sortOrder by remember { mutableStateOf(sortStore.load(context)) }
    val sortedItems = remember(items, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.CREATED_ASC -> items.sortedBy { it.createdAt }
            BookmarkSortOrder.CREATED_DESC -> items.sortedByDescending { it.createdAt }
            BookmarkSortOrder.PAGE_ASC -> items.sortedBy { it.page.takeIf { p -> p > 0 } ?: cfiToPage(it.cfi, spinePageOffsets, cfiPageMap) }
        }
    }
    val totalPages = maxOf(1, (sortedItems.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = sortedItems.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val dateFormat = remember {
        java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
    }

    LaunchedEffect(sortOrder) { sortStore.save(context, sortOrder) }
    LaunchedEffect(sortedItems.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    FullScreenPopup {
        PopupHeaderBar(title = title, onBack = onDismiss) {
            EreaderDropdownMenu(
                items = BookmarkSortOrder.entries.toList(),
                selectedItem = sortOrder,
                onSelect = { sortOrder = it },
                label = { stringResource(it.labelRes) },
            )
        }

        Box(modifier = Modifier.weight(1f).onSizeChanged {
            contentHeightDp = with(density) { it.height.toDp().value.toInt() }
        }) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, style = EreaderFontSize.M)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    pageItems.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
                        val itemPage = item.page.takeIf { it > 0 } ?: cfiToPage(item.cfi, spinePageOffsets, cfiPageMap)
                        itemContent(item, itemPage, dateFormat.format(java.time.Instant.ofEpochMilli(item.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()))
                    }
                }
            }
        }

        Column {
            if (sortedItems.isNotEmpty()) {
                PaginationBar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    centerText = stringResource(R.string.pagination_format, currentPage + 1, totalPages, sortedItems.size),
                    onPrevious = { currentPage-- },
                    onNext = { currentPage++ },
                    modifier = Modifier.padding(bottom = EreaderSpacing.L),
                )
            }
        }
    }
}
