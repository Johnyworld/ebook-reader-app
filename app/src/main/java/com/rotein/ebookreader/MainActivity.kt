package com.rotein.ebookreader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rotein.ebookreader.ui.theme.EbookReaderAppTheme

class MainActivity : AppCompatActivity() {
    var currentEpubWebView: WebView? = null
    var currentVolumeKeyAction: VolumeKeyAction = VolumeKeyAction.OFF
    private var isReady = false
    // 외부 앱(파일 탐색기 등)에서 연 책 — HomeScreen이 리더로 연 뒤 비운다
    private var externalBook by mutableStateOf<BookFile?>(null)
    // 외부 파일을 확인하는 동안 스플래시를 유지해 서재가 잠깐 보였다가 리더로 바뀌지 않게 한다
    private var isResolvingExternal = false

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
        splashScreen.setKeepOnScreenCondition { !isReady || isResolvingExternal }
        // 화면 재생성(savedInstanceState != null)일 때는 다시 열지 않는다 — 서재에서 연 책과 같은 동작
        if (savedInstanceState == null) openExternalBookIfNeeded(intent)
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
                        onLoadComplete = { isReady = true },
                        externalBook = externalBook,
                        onExternalBookOpened = { externalBook = null }
                    )
                }
            }
        }
    }

    /**
     * ACTION_VIEW로 받은 책 파일을 확인해 [externalBook]에 넣는다.
     * 서재에 같은 책이 있으면 그 책으로 열어 기록을 이어서 쓴다. 열 수 없는 파일이면 안내 후 화면을 닫는다.
     */
    private fun openExternalBookIfNeeded(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // 최근 앱에서 다시 띄운 경우: 임시 읽기 권한이 이미 사라졌으므로 열지 않고 서재만 보여준다
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val uri = intent.data ?: return
        isResolvingExternal = true
        lifecycleScope.launch {
            val book = withContext(Dispatchers.IO) {
                runCatching {
                    val catalog = BookDatabase.getInstance(this@MainActivity).bookCatalogDao().getAll().map { it.toBookFile() }
                    resolveExternalBook(this@MainActivity, uri, intent.type, catalog)
                }.getOrNull()
            }
            if (book == null) {
                // 지원하지 않거나 읽을 수 없는 파일 — 안내 후 바로 파일 탐색기로 돌아간다
                // (형식을 알 수 없는 파일에도 연결 프로그램으로 등록되어 있어 실수로 고르는 경우가 있음)
                // isResolvingExternal은 일부러 false로 돌리지 않는다 — 닫히는 동안 서재가 잠깐 보이지 않게 스플래시 유지
                Toast.makeText(applicationContext, R.string.external_file_open_failed, Toast.LENGTH_LONG).show()
                // 이 화면만으로 새 태스크가 만들어졌으면 최근 앱 목록에 빈 항목이 남지 않게 태스크째 닫는다
                if (isTaskRoot) finishAndRemoveTask() else finish()
                return@launch
            }
            externalBook = book
            isResolvingExternal = false
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLoadComplete: () -> Unit = {},
    externalBook: BookFile? = null,
    onExternalBookOpened: () -> Unit = {}
) {
    var currentBook by remember { mutableStateOf<BookFile?>(null) }

    // 외부 앱에서 연 책을 리더로 연다 — 리더를 닫으면 서재가 보인다
    LaunchedEffect(externalBook) {
        if (externalBook != null) {
            currentBook = externalBook
            onExternalBookOpened()
        }
    }

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
