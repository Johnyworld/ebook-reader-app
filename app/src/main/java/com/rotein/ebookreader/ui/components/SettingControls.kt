package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
fun ReaderSettingRow(label: String, field: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = EreaderSpacing.L),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = EreaderFontSize.M, modifier = Modifier.weight(1f))
        field()
    }
}

@Composable
fun ReaderStepperField(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    tag: String? = null
) {
    Row(
        modifier = Modifier.width(160.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDecrement, modifier = Modifier.size(40.dp).let { if (tag != null) it.testTag("${tag}Decrement") else it }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            value,
            style = EreaderFontSize.M,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(40.dp).let { if (tag != null) it.testTag("${tag}Increment") else it }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun <T> ReaderCycleSelectorField(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelFor: @Composable (T) -> String,
    forceAbove: Boolean = false,
    // false면 양 끝에서 반대편으로 넘어가지 않고 화살표가 비활성화된다
    wrap: Boolean = true
) {
    val currentIndex = options.indexOf(selected)

    Row(
        modifier = Modifier.width(160.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onSelect(options[(currentIndex - 1 + options.size) % options.size]) },
            modifier = Modifier.size(40.dp),
            enabled = wrap || currentIndex > 0
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            EreaderDropdownMenu(
                items = options,
                selectedItem = selected,
                onSelect = onSelect,
                label = labelFor,
                popupWidth = 160.dp,
                popupAlignment = Alignment.TopStart,
                forceAbove = forceAbove,
                trigger = { onClick -> ReaderClickableValue(labelFor(selected), onClick, Modifier.fillMaxWidth(), maxLines = 2) }
            )
        }
        IconButton(
            onClick = { onSelect(options[(currentIndex + 1) % options.size]) },
            modifier = Modifier.size(40.dp),
            enabled = wrap || currentIndex < options.size - 1
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 눌러서 목록·팝업을 여는 설정 값. 숫자 스텝퍼 값과 구분되도록 글자 아래에 점선 밑줄을 긋는다.
 * 여러 줄로 표시될 때는 줄마다 글자 폭만큼 밑줄을 긋는다.
 */
@Composable
fun ReaderClickableValue(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(
        // 탭 영역은 최소 48dp 높이를 보장한다 (밑줄 좌표는 Text가 내용 폭일 때를 전제로 하므로 Text에는 폭을 주지 않는다)
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = EreaderFontSize.M,
            color = EreaderColors.Black,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier.drawBehind {
                val l = layout ?: return@drawBehind
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
                val stroke = 1.dp.toPx()
                for (line in 0 until l.lineCount) {
                    // 글자 기준선보다 살짝 아래에 긋는다
                    val y = l.getLineBaseline(line) + 3.dp.toPx()
                    drawLine(
                        color = EreaderColors.Black,
                        start = Offset(l.getLineLeft(line), y),
                        end = Offset(l.getLineRight(line), y),
                        strokeWidth = stroke,
                        pathEffect = dash
                    )
                }
            }
        )
    }
}
