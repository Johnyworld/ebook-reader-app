package com.rotein.ebookreader

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rotein.ebookreader.ui.theme.EbookReaderAppTheme

class MainActivity : AppCompatActivity() {
    var currentEpubWebView: WebView? = null
    private var isReady = false

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
        WebView.setWebContentsDebuggingEnabled(
            0 != applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
        )
        splashScreen.setKeepOnScreenCondition { !isReady }
        splashScreen.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        setContent {
            EbookReaderAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        onLoadComplete = { isReady = true }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onLoadComplete: () -> Unit = {}) {
    var currentBook by remember { mutableStateOf<BookFile?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AllBooksScreen(
            onBookClick = { currentBook = it },
            onLoadComplete = onLoadComplete,
            refreshKey = currentBook
        )

        if (currentBook != null) {
            BookReaderScreen(
                book = currentBook!!,
                onClose = { currentBook = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
