package com.rotein.ebookreader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotein.ebookreader.R
import com.rotein.ebookreader.ui.components.FullScreenPopup
import com.rotein.ebookreader.ui.components.PopupHeaderBar
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing

@Composable
internal fun MemoEditorScreen(
    selectedText: String,
    initialNote: String,
    onSave: (note: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null
) {
    var note by remember { mutableStateOf(initialNote) }

    FullScreenPopup {
            PopupHeaderBar(title = stringResource(R.string.memo), onBack = onCancel) {
                TextButton(onClick = { onSave(note) }) {
                    Text(stringResource(R.string.save), color = EreaderColors.Black)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(EreaderSpacing.L),
                verticalArrangement = Arrangement.spacedBy(EreaderSpacing.L)
            ) {
                if (selectedText.isNotEmpty()) {
                    Text(
                        selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EreaderColors.Gray, RoundedCornerShape(4.dp))
                            .padding(EreaderSpacing.M)
                    )
                }
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, EreaderColors.Gray, RoundedCornerShape(4.dp))
                        .padding(EreaderSpacing.M),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = EreaderColors.Black),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) {
                                Text(stringResource(R.string.memo_placeholder), style = MaterialTheme.typography.bodyMedium, color = EreaderColors.Gray)
                            }
                            inner()
                        }
                    }
                )
            }
            if (onDelete != null || onNavigate != null) {
                HorizontalDivider(color = EreaderColors.Black)
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.delete), color = EreaderColors.Black)
                        }
                    }
                    if (onDelete != null && onNavigate != null) {
                        Box(Modifier.width(1.dp).height(20.dp).background(EreaderColors.Black))
                    }
                    if (onNavigate != null) {
                        TextButton(onClick = onNavigate, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.go_to_page), color = EreaderColors.Black)
                        }
                    }
                }
            }
    }
}
