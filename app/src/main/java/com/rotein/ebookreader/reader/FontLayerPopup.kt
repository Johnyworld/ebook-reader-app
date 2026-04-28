package com.rotein.ebookreader.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.FONT_EPUB_ORIGINAL
import com.rotein.ebookreader.FONT_SYSTEM
import com.rotein.ebookreader.FontSortOrder
import com.rotein.ebookreader.ImportedFontStore
import com.rotein.ebookreader.READER_BUILTIN_FONT_NAMES
import com.rotein.ebookreader.SystemFontFilter
import com.rotein.ebookreader.SystemFontSortOrder
import com.rotein.ebookreader.fontDisplayName
import com.rotein.ebookreader.getFontFileMaps
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.EreaderTabBar
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import java.io.File

@Composable
internal fun FontLayerPopup(
    currentFontName: String,
    onSelect: (String) -> Unit,
    onFontChanged: (String) -> Unit = {},
    onFontImported: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value.toInt() }
    var selectedTab by remember { mutableStateOf(0) }
    var fontSortOrder by remember { mutableStateOf(FontSortOrder.NAME_ASC) }
    var systemFontSortOrder by remember { mutableStateOf(SystemFontSortOrder.NAME_ASC) }
    var systemFontFilter by remember { mutableStateOf(SystemFontFilter.DEVICE) }
    var currentPage by remember { mutableStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var confirmImportFont by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDeleteFont by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember { mutableStateOf(ImportedFontStore.load(context)) }

    val itemHeightDp = 64
    val dividerHeightDp = 1
    val filterBarHeightDp = 48
    val headerHeightDp = 56 + 1 + filterBarHeightDp + 1 // header with tabs(56) + divider(1) + filter bar(48) + divider(1)
    val paginationHeightDp = 72
    val contentHeightDp = screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp
    // N개 아이템 + (N-1)개 구분선: N * 64 + (N-1) * 1 = N * 65 - 1
    val itemsPerPage = maxOf(1, (contentHeightDp + dividerHeightDp) / (itemHeightDp + dividerHeightDp))

    val pinnedFonts = listOf(FONT_EPUB_ORIGINAL, FONT_SYSTEM)
    val appFonts = remember(fontSortOrder, importedFonts) {
        val all = READER_BUILTIN_FONT_NAMES + importedFonts.map { it.name }
        when (fontSortOrder) {
            FontSortOrder.NAME_ASC -> all.sortedBy { it }
            FontSortOrder.NAME_DESC -> all.sortedByDescending { it }
            FontSortOrder.CREATED_DESC -> (READER_BUILTIN_FONT_NAMES + importedFonts.reversed().map { it.name })
            FontSortOrder.IMPORTED -> all
        }
    }

    val fontMaps = remember { getFontFileMaps() }
    val systemFonts = remember(systemFontSortOrder, systemFontFilter, importedFonts) {
        val importedNames = importedFonts.map { it.name }.toSet()
        val sourceMap = when (systemFontFilter) {
            SystemFontFilter.DEVICE -> fontMaps.device
            SystemFontFilter.SYSTEM -> fontMaps.system
            SystemFontFilter.ALL -> fontMaps.all
        }
        sourceMap.keys.filter { it !in importedNames }.let { keys ->
            when (systemFontSortOrder) {
                SystemFontSortOrder.NAME_ASC -> keys.sortedBy { it }
                SystemFontSortOrder.NAME_DESC -> keys.sortedByDescending { it }
            }
        }
    }

    val currentItems: List<String> = run {
        val items = if (selectedTab == 0) pinnedFonts + appFonts else systemFonts
        if (searchQuery.isBlank()) items
        else {
            val q = searchQuery.trim().lowercase()
            items.filter { fontDisplayName(it).lowercase().contains(q) }
        }
    }
    val totalPages = maxOf(1, (currentItems.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = currentItems.drop(currentPage * itemsPerPage).take(itemsPerPage)

    LaunchedEffect(selectedTab) {
        currentPage = 0
        searchQuery = ""
        isSearchActive = false
    }
    LaunchedEffect(systemFontFilter, searchQuery) { currentPage = 0 }
    LaunchedEffect(currentItems.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    confirmImportFont?.let { (fontName, filePath) ->
        AlertDialog(
            onDismissRequest = { confirmImportFont = null },
            title = { Text(stringResource(R.string.font_import_title)) },
            text = { Text(stringResource(R.string.font_import_message, fontName)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val srcFile = File(filePath)
                        val destFile = File(ImportedFontStore.getDir(context), srcFile.name)
                        if (!destFile.exists()) srcFile.copyTo(destFile)
                        ImportedFontStore.add(context, fontName, destFile.absolutePath)
                        importedFonts = ImportedFontStore.load(context)
                        onFontImported()
                        // 가져오기 완료 후 글꼴 탭으로 전환하고 해당 글꼴 선택
                        selectedTab = 0
                        onFontChanged(fontName)
                    } catch (e: Exception) { /* ignore */ }
                    confirmImportFont = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportFont = null }) { Text(stringResource(R.string.no)) }
            }
        )
    }

    confirmDeleteFont?.let { fontName ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFont = null },
            title = { Text(stringResource(R.string.font_delete_title)) },
            text = { Text(stringResource(R.string.font_delete_message, fontName)) },
            confirmButton = {
                TextButton(onClick = {
                    ImportedFontStore.remove(context, fontName)
                    importedFonts = ImportedFontStore.load(context)
                    onFontImported()
                    if (fontName == currentFontName) onFontChanged(FONT_EPUB_ORIGINAL)
                    confirmDeleteFont = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFont = null }) { Text(stringResource(R.string.no)) }
            }
        )
    }

    FullScreenPopup {
            // Header: 뒤로가기 + 탭
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = EreaderSpacing.XS)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                }
                EreaderTabBar(
                    tabs = listOf(stringResource(R.string.font_tab_fonts), stringResource(R.string.font_tab_import)),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    height = 56.dp,
                )
            }
            HorizontalDivider(color = EreaderColors.Black)
            // 필터/정렬 바 (검색 활성 시 검색 블록으로 전환)
            Box(
                modifier = Modifier.fillMaxWidth().height(filterBarHeightDp.dp)
            ) {
                // 베이스 레이어: 돋보기 + 정렬/필터
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = EreaderSpacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = EreaderColors.DarkGray
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    if (selectedTab == 0) {
                        EreaderDropdownMenu(
                            items = FontSortOrder.entries.toList(),
                            selectedItem = fontSortOrder,
                            onSelect = { fontSortOrder = it },
                            label = { stringResource(it.labelRes) },
                        )
                    } else {
                        EreaderDropdownMenu(
                            items = SystemFontFilter.entries.toList(),
                            selectedItem = systemFontFilter,
                            onSelect = { systemFontFilter = it },
                            label = { stringResource(it.labelRes) },
                        )
                        EreaderDropdownMenu(
                            items = SystemFontSortOrder.entries.toList(),
                            selectedItem = systemFontSortOrder,
                            onSelect = { systemFontSortOrder = it },
                            label = { stringResource(it.labelRes) },
                        )
                    }
                }
                // 오버레이 레이어: 검색 활성 시 전체를 덮음
                if (isSearchActive) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(EreaderColors.White)
                            .padding(horizontal = EreaderSpacing.XS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = EreaderColors.Black
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = EreaderFontSize.L.copy(color = EreaderColors.Black),
                            cursorBrush = SolidColor(EreaderColors.Black),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_font_hint),
                                            style = EreaderFontSize.L,
                                            color = EreaderColors.DarkGray
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchActive = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close_search),
                                tint = EreaderColors.DarkGray
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = EreaderColors.Gray)

            // Content
            Box(modifier = Modifier.weight(1f)) {
                if (currentItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (selectedTab == 0) R.string.font_none else R.string.font_none_device),
                            style = EreaderFontSize.M
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, fontName ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
                            val isImported = selectedTab == 0 && importedFonts.any { it.name == fontName }
                            val filePath = if (selectedTab == 0) {
                                importedFonts.firstOrNull { it.name == fontName }?.filePath
                            } else {
                                fontMaps.all[fontName]
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clickable {
                                        if (selectedTab == 0) {
                                            onSelect(fontName)
                                        } else {
                                            val path = fontMaps.all[fontName] ?: ""
                                            if (path.isNotEmpty()) confirmImportFont = Pair(fontName, path)
                                        }
                                    }
                                    .padding(start = EreaderSpacing.L, end = if (isImported) EreaderSpacing.XS else EreaderSpacing.L),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        fontDisplayName(fontName),
                                        style = EreaderFontSize.M,
                                    )
                                    if (filePath != null) {
                                        Text(
                                            filePath.substringBeforeLast("/"),
                                            style = EreaderFontSize.M,
                                            color = EreaderColors.DarkGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (selectedTab == 0 && fontName == currentFontName) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                if (isImported) {
                                    IconButton(
                                        onClick = { confirmDeleteFont = fontName },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            Column {
                if (currentItems.isNotEmpty()) {
                    PaginationBar(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        centerText = stringResource(R.string.pagination_format, currentPage + 1, totalPages, currentItems.size),
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
                }
            }
    }
}
