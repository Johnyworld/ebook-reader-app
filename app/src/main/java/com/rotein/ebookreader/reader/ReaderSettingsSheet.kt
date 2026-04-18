package com.rotein.ebookreader.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.FONT_EPUB_ORIGINAL
import com.rotein.ebookreader.FONT_SYSTEM
import com.rotein.ebookreader.ImportedFontStore
import com.rotein.ebookreader.READER_BUILTIN_FONT_NAMES
import com.rotein.ebookreader.ReaderBottomInfo
import com.rotein.ebookreader.ReaderPageFlip
import com.rotein.ebookreader.ReaderSettings
import com.rotein.ebookreader.fontDisplayName
import com.rotein.ebookreader.ui.components.EreaderTabBar
import com.rotein.ebookreader.ui.components.ReaderCycleSelectorField
import com.rotein.ebookreader.ui.components.ReaderSettingRow
import com.rotein.ebookreader.ui.components.ReaderStepperField
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun ReaderMenuItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
            .padding(horizontal = EreaderSpacing.L),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
}




// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
internal fun ReaderSettingsBottomSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenFontPopup: () -> Unit = {},
    isPdf: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabLabels = if (isPdf) listOf(stringResource(R.string.tab_viewer)) else listOf(stringResource(R.string.tab_glyph), stringResource(R.string.tab_margin), stringResource(R.string.tab_viewer))

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EreaderTabBar(
                tabs = tabLabels,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                trailingContent = {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
        HorizontalDivider(color = EreaderColors.Black)

        Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
            if (isPdf) {
                ReaderViewerTab(settings, onSettingsChange)
            } else {
                when (selectedTab) {
                    0 -> ReaderGlyphTab(settings, onSettingsChange, onOpenFontPopup)
                    1 -> ReaderMarginTab(settings, onSettingsChange)
                    2 -> ReaderViewerTab(settings, onSettingsChange)
                }
            }
        }
    }
}

@Composable
private fun ReaderGlyphTab(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onOpenFontPopup: () -> Unit = {}
) {
    val context = LocalContext.current
    val importedFonts = remember { ImportedFontStore.load(context) }
    val allFonts = remember(importedFonts) {
        listOf(FONT_EPUB_ORIGINAL, FONT_SYSTEM) + READER_BUILTIN_FONT_NAMES + importedFonts.map { it.name }
    }
    val currentIndex = allFonts.indexOf(settings.fontName).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingRow(stringResource(R.string.font_label)) {
            Row(
                modifier = Modifier.width(160.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSettingsChange(settings.copy(fontName = allFonts[(currentIndex - 1 + allFonts.size) % allFonts.size])) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(
                    fontDisplayName(settings.fontName),
                    modifier = Modifier.weight(1f).clickable { onOpenFontPopup() },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { onSettingsChange(settings.copy(fontName = allFonts[(currentIndex + 1) % allFonts.size])) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow(stringResource(R.string.setting_font_size)) {
            ReaderStepperField(
                value = settings.fontSize.toString(),
                onDecrement = { if (settings.fontSize > 8) onSettingsChange(settings.copy(fontSize = settings.fontSize - 1)) },
                onIncrement = { if (settings.fontSize < 32) onSettingsChange(settings.copy(fontSize = settings.fontSize + 1)) }
            )
        }
    }
}

@Composable
private fun ReaderMarginTab(settings: ReaderSettings, onSettingsChange: (ReaderSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingRow(stringResource(R.string.setting_line_height)) {
            ReaderStepperField(
                value = "%.1f".format(settings.lineHeight),
                onDecrement = { if (settings.lineHeight > 1.0f) onSettingsChange(settings.copy(lineHeight = (Math.round(settings.lineHeight * 10) - 1) / 10f)) },
                onIncrement = { if (settings.lineHeight < 3.0f) onSettingsChange(settings.copy(lineHeight = (Math.round(settings.lineHeight * 10) + 1) / 10f)) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow(stringResource(R.string.setting_paragraph_spacing)) {
            ReaderStepperField(
                value = settings.paragraphSpacing.toString(),
                onDecrement = { if (settings.paragraphSpacing > 0) onSettingsChange(settings.copy(paragraphSpacing = settings.paragraphSpacing - 1)) },
                onIncrement = { if (settings.paragraphSpacing < 40) onSettingsChange(settings.copy(paragraphSpacing = settings.paragraphSpacing + 1)) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow(stringResource(R.string.setting_padding_vertical)) {
            ReaderStepperField(
                value = settings.paddingVertical.toString(),
                onDecrement = { if (settings.paddingVertical > -16) onSettingsChange(settings.copy(paddingVertical = settings.paddingVertical - 2)) },
                onIncrement = { if (settings.paddingVertical < 80) onSettingsChange(settings.copy(paddingVertical = settings.paddingVertical + 2)) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow(stringResource(R.string.setting_padding_horizontal)) {
            ReaderStepperField(
                value = settings.paddingHorizontal.toString(),
                onDecrement = { if (settings.paddingHorizontal > -16) onSettingsChange(settings.copy(paddingHorizontal = settings.paddingHorizontal - 2)) },
                onIncrement = { if (settings.paddingHorizontal < 80) onSettingsChange(settings.copy(paddingHorizontal = settings.paddingHorizontal + 2)) }
            )
        }
    }
}

@Composable
private fun ReaderViewerTab(settings: ReaderSettings, onSettingsChange: (ReaderSettings) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderSettingRow(stringResource(R.string.setting_page_flip)) {
            ReaderCycleSelectorField(
                options = ReaderPageFlip.entries,
                selected = settings.pageFlip,
                onSelect = { onSettingsChange(settings.copy(pageFlip = it)) },
                labelFor = { stringResource(it.labelRes) },
                forceAbove = true
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow(stringResource(R.string.setting_left_info)) {
            ReaderCycleSelectorField(
                options = ReaderBottomInfo.entries,
                selected = settings.leftInfo,
                onSelect = { onSettingsChange(settings.copy(leftInfo = it)) },
                labelFor = { stringResource(it.labelRes) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        ReaderSettingRow(stringResource(R.string.setting_right_info)) {
            ReaderCycleSelectorField(
                options = ReaderBottomInfo.entries,
                selected = settings.rightInfo,
                onSelect = { onSettingsChange(settings.copy(rightInfo = it)) },
                labelFor = { stringResource(it.labelRes) }
            )
        }
        HorizontalDivider(color = EreaderColors.Gray)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.setting_dual_page),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.dualPage,
                onCheckedChange = { onSettingsChange(settings.copy(dualPage = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = EreaderColors.White,
                    checkedTrackColor = EreaderColors.Black,
                    uncheckedThumbColor = EreaderColors.Black,
                    uncheckedTrackColor = EreaderColors.White,
                    uncheckedBorderColor = EreaderColors.Black
                )
            )
        }
    }
}
