package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing

data class ActionItem(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun ActionPopup(
    selectionY: Float,
    selectionBottom: Float,
    selectionCx: Float,
    actions: List<ActionItem>,
    onDismiss: () -> Unit,
    usePopup: Boolean = false,
) {
    val popupHeightDp = 48.dp
    val marginDp = 8.dp
    val yDp = selectionY.dp
    val bottomDp = selectionBottom.dp
    val cxDp = selectionCx.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val bottomSafeDp = 28.dp
    val showAbove = yDp > popupHeightDp + marginDp
    val offsetY = if (showAbove) {
        yDp - popupHeightDp - marginDp
    } else {
        (bottomDp + marginDp).coerceAtMost(screenHeightDp - popupHeightDp - bottomSafeDp)
    }

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var popupWidthDp by remember { mutableStateOf(0.dp) }

    val inner: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().let { if (!usePopup) it.zIndex(10f) else it }.pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
            Row(
                modifier = Modifier
                    .onSizeChanged { popupWidthDp = with(density) { it.width.toDp() } }
                    .alpha(if (popupWidthDp == 0.dp) 0f else 1f)
                    .offset(
                        x = (cxDp - popupWidthDp / 2).coerceIn(marginDp, screenWidthDp - popupWidthDp - marginDp),
                        y = offsetY
                    )
                    .border(1.dp, EreaderColors.Black, RoundedCornerShape(8.dp))
                    .background(EreaderColors.White, RoundedCornerShape(8.dp))
                    .padding(horizontal = EreaderSpacing.XS)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEachIndexed { index, action ->
                    TextButton(onClick = action.onClick) {
                        Text(action.label, color = EreaderColors.Black, fontSize = 14.sp)
                    }
                    if (index < actions.lastIndex) {
                        Box(Modifier.width(1.dp).height(20.dp).background(EreaderColors.Black))
                    }
                }
            }
        }
    }

    if (usePopup) Popup(onDismissRequest = onDismiss) { inner() } else inner()
}
