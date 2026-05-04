package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rotein.ebookreader.R
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

/**
 * 재사용 가능한 컨펌 다이얼로그.
 * Popup + Surface 기반으로 구현하여 scrim/dim/fade 애니메이션 없음 (e-ink 최적화).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.yes),
    dismissText: String = stringResource(R.string.no),
) {
    val dialogMaxWidth = if (LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE) 480.dp else 320.dp

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // 전체 화면을 덮는 투명 터치 영역 (바깥 클릭 시 닫기)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = EreaderColors.White,
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier
                    .widthIn(max = dialogMaxWidth)
                    .border(1.dp, EreaderColors.Black)
                    .clickable(enabled = false) {} // 내부 클릭 전파 차단
            ) {
                Column {
                    // 제목 + 메시지 영역
                    Column(modifier = Modifier.padding(EreaderSpacing.L)) {
                        Text(title, style = EreaderFontSize.L)
                        Spacer(Modifier.height(EreaderSpacing.M))
                        Text(message, style = EreaderFontSize.M)
                    }

                    // 구분선 + 버튼 영역
                    HorizontalDivider(color = EreaderColors.Black)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        // 취소 버튼
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onDismiss)
                                .padding(vertical = EreaderSpacing.M),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(dismissText, style = EreaderFontSize.M)
                        }
                        // 세로 구분선
                        VerticalDivider(color = EreaderColors.Black, modifier = Modifier.fillMaxHeight())
                        // 확인 버튼
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onConfirm)
                                .padding(vertical = EreaderSpacing.M),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(confirmText, style = EreaderFontSize.M)
                        }
                    }
                }
            }
        }
    }
}
