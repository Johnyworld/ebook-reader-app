package com.rotein.ebookreader

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    private fun hideStatusBarIfNoCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
                val hasCutout = insets.displayCutout != null
                if (!hasCutout) {
                    WindowCompat.getInsetsController(window, view).apply {
                        hide(WindowInsetsCompat.Type.statusBars())
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
                ViewCompat.onApplyWindowInsets(view, insets)
            }
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
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
        hideStatusBarIfNoCutout()
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 홈 화면은 시스템 인셋 패딩 적용
        Box(modifier = modifier.fillMaxSize()) {
            AllBooksScreen(
                onBookClick = { currentBook = it },
                onLoadComplete = onLoadComplete,
                refreshKey = currentBook
            )
        }

        // 리더 화면은 전체화면 (시스템 인셋 패딩 미적용)
        if (currentBook != null) {
            BookReaderScreen(
                book = currentBook!!,
                onClose = { currentBook = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
