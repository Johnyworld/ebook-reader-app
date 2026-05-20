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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
    var currentVolumeKeyAction: VolumeKeyAction = VolumeKeyAction.OFF
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
                // 볼륨 키로 페이지 넘기기 (리더 화면이 열려 있을 때만)
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val wv = currentEpubWebView ?: return super.dispatchKeyEvent(event)
                    when (currentVolumeKeyAction) {
                        VolumeKeyAction.UP_PREV_DOWN_NEXT -> { wv.evaluateJavascript("window._prev()", null); return true }
                        VolumeKeyAction.UP_NEXT_DOWN_PREV -> { wv.evaluateJavascript("window._next()", null); return true }
                        VolumeKeyAction.OFF -> {}
                    }
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val wv = currentEpubWebView ?: return super.dispatchKeyEvent(event)
                    when (currentVolumeKeyAction) {
                        VolumeKeyAction.UP_PREV_DOWN_NEXT -> { wv.evaluateJavascript("window._next()", null); return true }
                        VolumeKeyAction.UP_NEXT_DOWN_PREV -> { wv.evaluateJavascript("window._prev()", null); return true }
                        VolumeKeyAction.OFF -> {}
                    }
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
                // Scaffold의 insets 소비를 비활성화하여 하위 화면들이 직접 insets를 처리
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    HomeScreen(
                        // Scaffold의 insets 소비를 비활성화했으므로 직접 시스템 바 패딩 적용
                        modifier = Modifier.padding(innerPadding).statusBarsPadding().navigationBarsPadding(),
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
