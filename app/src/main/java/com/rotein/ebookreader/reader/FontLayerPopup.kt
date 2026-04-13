package com.rotein.ebookreader.reader

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.FONT_EPUB_ORIGINAL
import com.rotein.ebookreader.FONT_SYSTEM
import com.rotein.ebookreader.FontSortOrder
import com.rotein.ebookreader.ImportedFontStore
import com.rotein.ebookreader.READER_BUILTIN_FONT_NAMES
import com.rotein.ebookreader.SystemFontSortOrder
import com.rotein.ebookreader.fontDisplayName
import com.rotein.ebookreader.getSystemFontFileMap
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.EreaderTabBar
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PaginationBar
import com.rotein.ebookreader.ui.theme.EreaderColors
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
    var currentPage by remember { mutableStateOf(0) }
    var confirmImportFont by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmDeleteFont by remember { mutableStateOf<String?>(null) }
    var importedFonts by remember { mutableStateOf(ImportedFontStore.load(context)) }

    val itemHeightDp = 56
    val headerHeightDp = 56 + 48 + 2 // header(56) + tabs(48) + tab underline(2)
    val paginationHeightDp = 72
    val itemsPerPage = maxOf(1, (screenHeightDp - statusBarHeightDp - headerHeightDp - paginationHeightDp) / itemHeightDp)

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

    val systemFontFileMap = remember { getSystemFontFileMap() }
    val systemFonts = remember(systemFontSortOrder, importedFonts) {
        val importedNames = importedFonts.map { it.name }.toSet()
        systemFontFileMap.keys.filter { it !in importedNames }.let { keys ->
            when (systemFontSortOrder) {
                SystemFontSortOrder.NAME_ASC -> keys.sortedBy { it }
                SystemFontSortOrder.NAME_DESC -> keys.sortedByDescending { it }
            }
        }
    }

    val currentItems: List<String> = if (selectedTab == 0) pinnedFonts + appFonts else systemFonts
    val totalPages = maxOf(1, (currentItems.size + itemsPerPage - 1) / itemsPerPage)
    val pageItems = currentItems.drop(currentPage * itemsPerPage).take(itemsPerPage)

    LaunchedEffect(selectedTab) { currentPage = 0 }
    LaunchedEffect(currentItems.size) {
        if (currentPage >= totalPages) currentPage = maxOf(0, totalPages - 1)
    }

    confirmImportFont?.let { (fontName, filePath) ->
        AlertDialog(
            onDismissRequest = { confirmImportFont = null },
            title = { Text("글꼴 가져오기") },
            text = { Text("${fontName} 글꼴을 가져올까요?") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val srcFile = File(filePath)
                        val destFile = File(ImportedFontStore.getDir(context), srcFile.name)
                        if (!destFile.exists()) srcFile.copyTo(destFile)
                        ImportedFontStore.add(context, fontName, destFile.absolutePath)
                        importedFonts = ImportedFontStore.load(context)
                        onFontImported()
                    } catch (e: Exception) { /* ignore */ }
                    confirmImportFont = null
                }) { Text("예") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportFont = null }) { Text("아니오") }
            }
        )
    }

    confirmDeleteFont?.let { fontName ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFont = null },
            title = { Text("글꼴 삭제") },
            text = { Text("${fontName} 글꼴을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    ImportedFontStore.remove(context, fontName)
                    importedFonts = ImportedFontStore.load(context)
                    onFontImported()
                    if (fontName == currentFontName) onFontChanged(FONT_EPUB_ORIGINAL)
                    confirmDeleteFont = null
                }) { Text("예") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFont = null }) { Text("아니오") }
            }
        )
    }

    FullScreenPopup {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = EreaderSpacing.XS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text("글꼴", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selectedTab == 0) {
                    EreaderDropdownMenu(
                        items = FontSortOrder.entries.toList(),
                        selectedItem = fontSortOrder,
                        onSelect = { fontSortOrder = it },
                        label = { it.label },
                    )
                } else {
                    EreaderDropdownMenu(
                        items = SystemFontSortOrder.entries.toList(),
                        selectedItem = systemFontSortOrder,
                        onSelect = { systemFontSortOrder = it },
                        label = { it.label },
                    )
                }
            }
            EreaderTabBar(
                tabs = listOf("글꼴", "글꼴 가져오기"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            HorizontalDivider(color = EreaderColors.Black)

            // Content
            Box(modifier = Modifier.weight(1f)) {
                if (currentItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedTab == 0) "글꼴이 없습니다." else "기기에 글꼴이 없습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        pageItems.forEachIndexed { index, fontName ->
                            if (index > 0) HorizontalDivider(color = EreaderColors.Gray)
                            val isImported = selectedTab == 0 && importedFonts.any { it.name == fontName }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clickable {
                                        if (selectedTab == 0) {
                                            onSelect(fontName)
                                        } else {
                                            val filePath = systemFontFileMap[fontName] ?: ""
                                            if (filePath.isNotEmpty()) confirmImportFont = Pair(fontName, filePath)
                                        }
                                    }
                                    .padding(start = EreaderSpacing.L, end = if (isImported) EreaderSpacing.XS else EreaderSpacing.L),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    fontDisplayName(fontName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedTab == 0 && fontName == currentFontName) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                if (isImported) {
                                    IconButton(
                                        onClick = { confirmDeleteFont = fontName },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(16.dp))
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
                        centerText = "${currentPage + 1}/$totalPages (${currentItems.size}개)",
                        onPrevious = { currentPage-- },
                        onNext = { currentPage++ },
                        modifier = Modifier.padding(bottom = EreaderSpacing.L),
                    )
                }
            }
    }
}
