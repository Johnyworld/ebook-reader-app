package com.rotein.ebookreader.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.rotein.ebookreader.R
import com.rotein.ebookreader.CenteredMessage
import com.rotein.ebookreader.LoadingIndicator
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun TxtViewer(path: String, onCenterTap: () -> Unit) {
    var text by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            text = withContext(Dispatchers.IO) { File(path).readText() }
        } catch (_: Exception) {
            error = true
        }
    }

    when {
        error -> CenteredMessage(stringResource(R.string.error_cannot_read))
        text == null -> LoadingIndicator()
        else -> Text(
            text = text!!,
            style = EreaderFontSize.M,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onCenterTap) {
                    detectTapGestures { offset ->
                        if (offset.x > size.width / 3f && offset.x < size.width * 2f / 3f) {
                            onCenterTap()
                        }
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(EreaderSpacing.L)
        )
    }
}
