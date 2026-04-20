package com.rotein.ebookreader.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.SearchResultItem
import com.rotein.ebookreader.TocItem
import com.rotein.ebookreader.flattenToc
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun SearchPopup(
    searchResults: List<SearchResultItem>?,
    isSearching: Boolean,
    tocItems: List<TocItem>,
    initialQuery: String,
    onSearch: (String) -> Unit,
    onNavigate: (cfi: String, page: Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val tocPageMap = remember(tocItems) {
        flattenToc(tocItems).filter { it.page > 0 }.associate { it.label to it.page }
    }
    val sortedResults = remember(searchResults, tocPageMap) {
        searchResults?.sortedBy { r ->
            if (r.page > 0) r.page else (tocPageMap[r.chapter] ?: Int.MAX_VALUE)
        }
    }
    var query by remember { mutableStateOf(initialQuery) }
    var searchedQuery by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    var currentPage by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    val itemHeightDp = 80
    val headerHeightDp = 56
    val paginationHeightDp = 56
    val searchBarHeightDp = 45
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp - searchBarHeightDp) / itemHeightDp)
    val resultList = sortedResults ?: emptyList()
    val totalPages = maxOf(1, (resultList.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = resultList.drop(currentPage * itemsPerPage).take(itemsPerPage)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(searchedQuery) { currentPage = 0 }

    FullScreenPopup {
            PopupHeaderBar(title = stringResource(R.string.search_content), onBack = onDismiss)

            Box(modifier = Modifier.weight(1f)) {
                when {
                    searchResults == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.enter_search_query), style = EreaderFontSize.M)
                    }
                    isSearching && searchResults.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "검색 중...", style = EreaderFontSize.L)
                    }
                    searchResults.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_search_results), style = EreaderFontSize.M)
                    }
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, result ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
                            val effectivePage = if (result.page > 0) result.page else (tocPageMap[result.chapter] ?: 0)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clickable { onNavigate(result.cfi, effectivePage) }
                                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
                            ) {
                                if (result.chapter.isNotEmpty() || effectivePage > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = EreaderSpacing.XS),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            result.chapter,
                                            style = EreaderFontSize.S,
                                            color = EreaderColors.DarkGray,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (result.page > 0) {
                                            Text(
                                                "p.$effectivePage",
                                                style = EreaderFontSize.S,
                                                color = EreaderColors.DarkGray
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = buildAnnotatedString {
                                        val excerpt = result.excerpt.trim()
                                        val lowerExcerpt = excerpt.lowercase()
                                        val lowerQuery = searchedQuery.lowercase()
                                        var cursor = 0
                                        if (lowerQuery.isNotEmpty()) {
                                            var idx = lowerExcerpt.indexOf(lowerQuery)
                                            while (idx != -1) {
                                                append(excerpt.substring(cursor, idx))
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                    append(excerpt.substring(idx, idx + lowerQuery.length))
                                                }
                                                cursor = idx + lowerQuery.length
                                                idx = lowerExcerpt.indexOf(lowerQuery, cursor)
                                            }
                                        }
                                        append(excerpt.substring(cursor))
                                    },
                                    style = EreaderFontSize.M,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Column {
                if (!searchResults.isNullOrEmpty()) {
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = stringResource(R.string.pagination_format, currentPage + 1, totalPages, resultList.size),
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                    )
                }
                HorizontalDivider(color = EreaderColors.Black)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(start = EreaderSpacing.XS, end = EreaderSpacing.L),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = EreaderSpacing.M).size(24.dp),
                        tint = EreaderColors.Black
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = EreaderFontSize.L.copy(color = EreaderColors.Black),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.length >= 2) { val q = query.trim().replace(Regex("\\s+"), " "); searchedQuery = q; onSearch(q) }
                        }),
                        decorationBox = { innerTextField ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        stringResource(R.string.search_min_chars),
                                        style = EreaderFontSize.L,
                                        color = EreaderColors.DarkGray
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = ""; searchedQuery = ""; onClear() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier.size(20.dp),
                                tint = EreaderColors.Black
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, EreaderColors.Black, RoundedCornerShape(4.dp))
                            .clickable { if (query.length >= 2) { val q = query.trim().replace(Regex("\\s+"), " "); searchedQuery = q; onSearch(q) } }
                            .padding(horizontal = EreaderSpacing.L, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.search), style = EreaderFontSize.M, fontWeight = FontWeight.Bold)
                    }
                }
            }
    }
}
