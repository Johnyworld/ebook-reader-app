package com.rotein.ebookreader

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rotein.ebookreader.ui.theme.EreaderColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.rotein.ebookreader.ui.theme.EbookReaderAppTheme

class MainActivity : ComponentActivity() {
    var currentEpubWebView: WebView? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    currentEpubWebView?.evaluateJavascript("window._prev()", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    currentEpubWebView?.evaluateJavascript("window._next()", null)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        setContent {
            EbookReaderAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var currentBook by remember { mutableStateOf<BookFile?>(null) }
    var showSplash by remember { mutableStateOf(true) }
    var splashMinTimeElapsed by remember { mutableStateOf(false) }
    var fileScanComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800)
        splashMinTimeElapsed = true
    }

    LaunchedEffect(splashMinTimeElapsed, fileScanComplete) {
        if (splashMinTimeElapsed && fileScanComplete) {
            showSplash = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 실제 콘텐츠 (스플래시 뒤에서 로딩 진행)
        AllBooksScreen(
            onBookClick = { currentBook = it },
            onLoadComplete = { fileScanComplete = true },
            refreshKey = currentBook
        )

        // 뷰어 오버레이
        if (currentBook != null) {
            BookReaderScreen(
                book = currentBook!!,
                onClose = { currentBook = null },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 스플래시 오버레이
        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EreaderColors.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ebook-reader",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = EreaderColors.Black
                )
            }
        }
    }
}
