package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    centerText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPrev = currentPage > 0
    val hasNext = currentPage < totalPages - 1

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = hasPrev) { onPrevious() }
                .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.XS)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.height(16.dp),
                tint = if (hasPrev) EreaderColors.Black else EreaderColors.DarkGray
            )
            Text(
                stringResource(R.string.previous),
                style = EreaderFontSize.M,
                color = if (hasPrev) EreaderColors.Black else EreaderColors.DarkGray
            )
        }
        Text(
            centerText,
            style = EreaderFontSize.M
        )
        Row(
            modifier = Modifier
                .clickable(enabled = hasNext) { onNext() }
                .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.XS)
        ) {
            Text(
                stringResource(R.string.next),
                style = EreaderFontSize.M,
                color = if (hasNext) EreaderColors.Black else EreaderColors.DarkGray
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.height(16.dp),
                tint = if (hasNext) EreaderColors.Black else EreaderColors.DarkGray
            )
        }
    }
}
