package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rotein.ebookreader.ui.theme.EreaderColors

@Composable
fun FullScreenPopup(
    modifier: Modifier = Modifier,
    applyImePadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = EreaderColors.White) {
        Column(modifier = Modifier.fillMaxSize().then(if (applyImePadding) Modifier.imePadding() else Modifier)) {
            content()
        }
    }
}
