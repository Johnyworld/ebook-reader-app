package com.rotein.ebookreader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.rotein.ebookreader.QuadPageFlip
import com.rotein.ebookreader.R
import com.rotein.ebookreader.ReaderPageFlip
import com.rotein.ebookreader.ReaderSettings
import com.rotein.ebookreader.ui.components.EreaderTabBar
import com.rotein.ebookreader.ui.components.ReaderClickableValue
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

/**
 * 페이지 넘김 설정 필드.
 * 팝오버 맨 위에서 2분할/4분할을 고르고, 2분할은 기존 4개 프리셋 목록, 4분할은 칸마다 이전/다음을 토글하는 2×2 격자를 보여준다.
 * 좌우 화살표는 2분할이면 4개 프리셋, 4분할이면 16개 조합을 순환한다.
 */
@Composable
internal fun PageFlipSelectorField(settings: ReaderSettings, onSettingsChange: (ReaderSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = ReaderPageFlip.entries
    val currentIndex = options.indexOf(settings.pageFlip)

    // 화살표: 현재 분할 방식 안에서만 순환
    val cycle = { step: Int ->
        if (settings.pageFlipQuad) {
            val q = settings.quadPageFlip
            onSettingsChange(settings.copy(quadPageFlip = if (step > 0) q.next() else q.prev()))
        } else {
            onSettingsChange(settings.copy(pageFlip = options[(currentIndex + step + options.size) % options.size]))
        }
    }

    Row(
        modifier = Modifier.width(160.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { cycle(-1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val label = if (settings.pageFlipQuad) quadPageFlipLabel(settings.quadPageFlip) else stringResource(settings.pageFlip.labelRes)
            ReaderClickableValue(label, { expanded = true }, Modifier.fillMaxWidth(), maxLines = 2)
            if (expanded) {
                Popup(
                    popupPositionProvider = AboveAnchorPositionProvider,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    PageFlipPopover(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onDismiss = { expanded = false }
                    )
                }
            }
        }
        IconButton(onClick = { cycle(1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

// 4분할 값 표시: 윗줄 좌상/우상, 아랫줄 좌하/우하
@Composable
private fun quadPageFlipLabel(quad: QuadPageFlip): String {
    val prev = stringResource(R.string.page_flip_prev)
    val next = stringResource(R.string.page_flip_next)
    val l = { isNext: Boolean -> if (isNext) next else prev }
    return "${l(quad.topLeft)}/${l(quad.topRight)}\n${l(quad.bottomLeft)}/${l(quad.bottomRight)}"
}

@Composable
private fun PageFlipPopover(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .background(EreaderColors.White)
            .border(1.dp, EreaderColors.Black)
    ) {
        // 분할 방식 선택 (바꿔도 팝오버는 열어 둔다)
        EreaderTabBar(
            tabs = listOf(stringResource(R.string.page_flip_split_two), stringResource(R.string.page_flip_split_four)),
            selectedIndex = if (settings.pageFlipQuad) 1 else 0,
            onSelect = { onSettingsChange(settings.copy(pageFlipQuad = it == 1)) },
            height = 44.dp
        )
        HorizontalDivider(color = EreaderColors.Black)
        if (settings.pageFlipQuad) {
            QuadPageFlipGrid(settings.quadPageFlip) { onSettingsChange(settings.copy(quadPageFlip = it)) }
        } else {
            // 2분할: 기존 프리셋 목록. 고르면 닫는다.
            ReaderPageFlip.entries.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSettingsChange(settings.copy(pageFlip = item))
                            onDismiss()
                        }
                        .padding(start = EreaderSpacing.L, end = EreaderSpacing.M, top = EreaderSpacing.M, bottom = EreaderSpacing.M)
                ) {
                    Text(
                        text = stringResource(item.labelRes),
                        style = EreaderFontSize.M,
                        color = EreaderColors.Black,
                        modifier = Modifier.weight(1f)
                    )
                    if (item == settings.pageFlip) {
                        Spacer(modifier = Modifier.width(EreaderSpacing.L))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EreaderColors.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (index < ReaderPageFlip.entries.lastIndex) {
                    HorizontalDivider(color = EreaderColors.Gray)
                }
            }
        }
    }
}

// 4분할: 화면 배치대로 2×2 격자. 칸을 누를 때마다 이전↔다음이 바뀐다.
@Composable
private fun QuadPageFlipGrid(quad: QuadPageFlip, onChange: (QuadPageFlip) -> Unit) {
    val prev = stringResource(R.string.page_flip_prev)
    val next = stringResource(R.string.page_flip_next)

    @Composable
    fun Cell(isNext: Boolean, modifier: Modifier, onToggle: () -> Unit) {
        ReaderClickableValue(if (isNext) next else prev, onToggle, modifier.height(56.dp))
    }

    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Cell(quad.topLeft, Modifier.weight(1f)) { onChange(quad.copy(topLeft = !quad.topLeft)) }
        VerticalDivider(color = EreaderColors.Gray, modifier = Modifier.fillMaxHeight())
        Cell(quad.topRight, Modifier.weight(1f)) { onChange(quad.copy(topRight = !quad.topRight)) }
    }
    HorizontalDivider(color = EreaderColors.Gray)
    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Cell(quad.bottomLeft, Modifier.weight(1f)) { onChange(quad.copy(bottomLeft = !quad.bottomLeft)) }
        VerticalDivider(color = EreaderColors.Gray, modifier = Modifier.fillMaxHeight())
        Cell(quad.bottomRight, Modifier.weight(1f)) { onChange(quad.copy(bottomRight = !quad.bottomRight)) }
    }
}

// 팝오버를 값 바로 위에, 가로로는 값 가운데에 맞춰 띄운다 (화면 밖으로 나가지 않게 보정)
private object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
