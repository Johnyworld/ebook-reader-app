package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
fun <T> EreaderDropdownMenu(
    items: List<T>,
    selectedItem: T? = null,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    popupWidth: Dp? = null,
    popupAlignment: Alignment = Alignment.TopEnd,
    forceAbove: Boolean = false,
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }

    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var buttonPositionY by remember { mutableStateOf(0f) }
    var buttonHeightPx by remember { mutableStateOf(0f) }
    var dropdownHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            buttonPositionY = coords.positionInRoot().y
            buttonHeightPx = coords.size.height.toFloat()
        }
    ) {
        if (trigger != null) {
            trigger { expanded = true; currentPage = 0 }
        } else {
            TextButton(onClick = { expanded = true; currentPage = 0 }) {
                Text(
                    text = label(selectedItem ?: items.first()),
                    style = EreaderFontSize.M,
                    color = EreaderColors.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (expanded) {
            val itemHeightPx = with(density) { 44.dp.toPx() }
            val paginationHeightPx = with(density) { 44.dp.toPx() }
            val maxDropdownHeightPx = screenHeightPx * 0.6f
            val needsPagination = items.size * itemHeightPx > maxDropdownHeightPx
            val itemsPerPage = if (needsPagination) {
                ((maxDropdownHeightPx - paginationHeightPx) / itemHeightPx).toInt().coerceAtLeast(1)
            } else {
                items.size
            }
            val totalPages = if (needsPagination) {
                (items.size + itemsPerPage - 1) / itemsPerPage
            } else 1
            val pageStart = currentPage * itemsPerPage
            val pageEnd = minOf(pageStart + itemsPerPage, items.size)
            val visibleItems = items.subList(pageStart, pageEnd)

            val estimatedDropdownHeightPx = if (needsPagination) maxDropdownHeightPx.toInt() else (items.size * itemHeightPx).toInt()
            val spaceBelow = screenHeightPx - buttonPositionY - buttonHeightPx
            val showAbove = forceAbove || spaceBelow < estimatedDropdownHeightPx
            val offsetY = if (showAbove) {
                -(if (dropdownHeightPx > 0) dropdownHeightPx else estimatedDropdownHeightPx)
            } else {
                buttonHeightPx.toInt()
            }

            Popup(
                alignment = popupAlignment,
                offset = IntOffset(0, offsetY),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                val widthModifier = if (popupWidth != null) Modifier.width(popupWidth) else Modifier.width(IntrinsicSize.Max)
                Column(
                    modifier = widthModifier
                        .background(EreaderColors.White)
                        .border(1.dp, EreaderColors.Black)
                        .onGloballyPositioned { dropdownHeightPx = it.size.height }
                ) {
                    visibleItems.forEachIndexed { index, item ->
                        val isSelected = selectedItem != null && item == selectedItem
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(item)
                                    expanded = false
                                }
                                .padding(start = EreaderSpacing.L, end = EreaderSpacing.M, top = EreaderSpacing.M, bottom = EreaderSpacing.M)
                        ) {
                            Text(
                                text = label(item),
                                style = EreaderFontSize.M,
                                color = EreaderColors.Black,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(EreaderSpacing.L))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = EreaderColors.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (index < visibleItems.lastIndex || needsPagination) {
                            HorizontalDivider(color = EreaderColors.Gray)
                        }
                    }
                    if (needsPagination) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                modifier = Modifier.size(44.dp),
                                enabled = currentPage > 0
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (currentPage > 0) EreaderColors.Black else EreaderColors.DarkGray
                                )
                            }
                            Text(
                                "${currentPage + 1} / $totalPages",
                                style = EreaderFontSize.S,
                                color = EreaderColors.Black
                            )
                            IconButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                modifier = Modifier.size(44.dp),
                                enabled = currentPage < totalPages - 1
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (currentPage < totalPages - 1) EreaderColors.Black else EreaderColors.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
