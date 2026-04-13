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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val tabs = listOf("기기 내 모든 책", "내 책장")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
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

    if (currentBook != null) {
        BookReaderScreen(
            book = currentBook!!,
            onClose = { currentBook = null },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 실제 콘텐츠 (스플래시 뒤에서 로딩 진행)
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = Color.Black,
                indicator = { tabPositions ->
                    val tab = tabPositions[selectedTabIndex]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.BottomStart)
                            .offset(x = tab.left)
                            .width(tab.width)
                            .height(2.dp)
                            .background(Color.Black)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        selectedContentColor = Color.Black,
                        unselectedContentColor = Color(0xFF888888)
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> AllBooksScreen(
                    onBookClick = { currentBook = it },
                    onLoadComplete = { fileScanComplete = true }
                )
                1 -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Text(text = tabs[1], modifier = Modifier.padding(16.dp))
                }
            }
        }

        // 스플래시 오버레이
        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ebook-reader",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
