package com.rotein.ebookreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rotein.ebookreader.R
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing

/**
 * 페이지 이동 모달 다이얼로그.
 * 숫자 입력 필드와 +/- 버튼으로 원하는 페이지를 선택한 뒤 이동한다.
 * Popup 기반으로 구현하여 scrim/dim/fade 애니메이션 없음 (e-ink 최적화).
 */
@Composable
fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onNavigate: (Int) -> Unit,
) {
    var pageText by remember { mutableStateOf(currentPage.toString()) }

    // 페이지 범위 제한 헬퍼
    fun clampedPage(value: Int) = value.coerceIn(1, totalPages)

    // 현재 유효 페이지 값 (빈 입력이면 현재 페이지 유지)
    fun effectivePage() = pageText.toIntOrNull()?.let { clampedPage(it) } ?: currentPage

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // 전체 화면을 덮는 투명 터치 영역 (바깥 클릭 시 닫기)
        val dialogMaxWidth = if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 480.dp else 320.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
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
                Column(
                    modifier = Modifier
                        .padding(EreaderSpacing.L)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 제목
                    Text(
                        stringResource(R.string.go_to_page),
                        style = EreaderFontSize.L
                    )

                    Spacer(Modifier.height(EreaderSpacing.L))

                    // 숫자 입력 필드
                    TextField(
                        value = pageText,
                        onValueChange = { text ->
                            // 숫자만 허용, 빈 문자열도 허용 (이동 시점에 파싱)
                            pageText = text.filter { it.isDigit() }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = EreaderFontSize.L.copy(textAlign = TextAlign.Center),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = EreaderColors.Black,
                            unfocusedIndicatorColor = EreaderColors.Gray,
                            cursorColor = EreaderColors.Black
                        ),
                        modifier = Modifier.widthIn(max = 120.dp),
                        suffix = {
                            Text(
                                " / $totalPages",
                                style = EreaderFontSize.M,
                                color = EreaderColors.DarkGray
                            )
                        }
                    )

                    Spacer(Modifier.height(EreaderSpacing.L))

                    // +/- 버튼 그리드 (감소 왼쪽, 증가 오른쪽)
                    val steps = listOf(1, 5, 10, 50, 100)
                    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.S)
                    ) {
                        // 감소 버튼 (왼쪽)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(EreaderSpacing.S)
                        ) {
                            if (isLandscape) {
                                // 가로모드: 2열 배치
                                steps.chunked(2).forEach { chunk ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.S)) {
                                        chunk.forEach { step ->
                                            Box(Modifier.weight(1f)) {
                                                StepButton(label = "-$step") {
                                                    val result = clampedPage(effectivePage() - step)
                                                    pageText = result.toString()
                                                }
                                            }
                                        }
                                        // 홀수 개일 때 빈 공간 채우기
                                        if (chunk.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                steps.forEach { step ->
                                    StepButton(label = "-$step") {
                                        val result = clampedPage(effectivePage() - step)
                                        pageText = result.toString()
                                    }
                                }
                            }
                        }
                        // 증가 버튼 (오른쪽)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(EreaderSpacing.S)
                        ) {
                            if (isLandscape) {
                                steps.chunked(2).forEach { chunk ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.S)) {
                                        chunk.forEach { step ->
                                            Box(Modifier.weight(1f)) {
                                                StepButton(label = "+$step") {
                                                    val result = clampedPage(effectivePage() + step)
                                                    pageText = result.toString()
                                                }
                                            }
                                        }
                                        if (chunk.size < 2) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                steps.forEach { step ->
                                    StepButton(label = "+$step") {
                                        val result = clampedPage(effectivePage() + step)
                                        pageText = result.toString()
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(EreaderSpacing.L))

                    // 하단 버튼: 취소 / 이동
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                stringResource(R.string.cancel),
                                color = EreaderColors.Black,
                                style = EreaderFontSize.M
                            )
                        }
                        Spacer(Modifier.width(EreaderSpacing.S))
                        TextButton(onClick = {
                            onNavigate(effectivePage())
                        }) {
                            Text(
                                stringResource(R.string.move),
                                color = EreaderColors.Black,
                                style = EreaderFontSize.M
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * +/- 스텝 버튼 (e-ink 최적화: 보더, 흰색 배경, 애니메이션 없음)
 */
@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EreaderColors.Gray, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = EreaderSpacing.S),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = EreaderFontSize.M, color = EreaderColors.Black)
    }
}
