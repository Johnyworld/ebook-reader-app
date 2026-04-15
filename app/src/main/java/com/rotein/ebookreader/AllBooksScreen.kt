package com.rotein.ebookreader

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AllBooksScreen(
    onBookClick: (BookFile) -> Unit,
    onLoadComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { BookDatabase.getInstance(context).bookReadRecordDao() }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var books by remember { mutableStateOf(BookCache.books ?: emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortPref by remember { mutableStateOf(SortPreferenceStore.load(context)) }
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var lastReadTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // 정렬 설정 변경 시 기기에 저장
    LaunchedEffect(sortPref) {
        SortPreferenceStore.save(context, sortPref)
    }

    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hiddenBooks by remember { mutableStateOf<Set<String>>(emptySet()) }

    // DB에서 읽은 시각 + 즐겨찾기/숨기기 로드
    LaunchedEffect(Unit) {
        val records = withContext(Dispatchers.IO) { dao.getAll() }
        lastReadTimes = records.associate { it.bookPath to it.lastReadAt }
        favorites = records.filter { it.isFavorite }.map { it.bookPath }.toSet()
        hiddenBooks = records.filter { it.isHidden }.map { it.bookPath }.toSet()
    }

    val processedBooks = remember(books, searchQuery, sortPref, lastReadTimes, hiddenBooks, favorites, filterMode) {
        // 0) 필터 모드 적용
        val visible = when (filterMode) {
            FilterMode.ALL -> books.filter { it.path !in hiddenBooks }
            FilterMode.FAVORITE -> books.filter { it.path in favorites && it.path !in hiddenBooks }
            FilterMode.HIDDEN -> books.filter { it.path in hiddenBooks }
        }
        // 1) 검색 필터
        val filtered = if (searchQuery.isBlank()) visible
        else {
            val q = searchQuery.trim().lowercase()
            visible.filter { book ->
                val title = (book.metadata?.title ?: book.name).lowercase()
                val author = book.metadata?.author?.lowercase() ?: ""
                title.contains(q) || author.contains(q) || book.name.lowercase().contains(q)
            }
        }
        // 2) 정렬 (방향은 필드별 기본값 적용)
        val comparator: Comparator<BookFile> = when (sortPref.field) {
            SortField.TITLE -> compareBy { (it.metadata?.title ?: it.name).lowercase() }
            SortField.AUTHOR -> compareBy { it.metadata?.author?.lowercase() ?: "\uFFFF" }
            SortField.DATE_ADDED -> compareBy { it.dateAdded }
            SortField.LAST_READ -> compareBy { lastReadTimes[it.path] ?: 0L }
        }
        val sorted = filtered.sortedWith(comparator)
        if (sortPref.field.defaultDescending) sorted.reversed() else sorted
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            if (BookCache.books != null) {
                onLoadComplete()
            } else {
                isLoading = true
                val scanned = withContext(Dispatchers.IO) { FileScanner.scanBooks(context) }
                BookCache.books = scanned
                books = scanned
                isLoading = false
                onLoadComplete()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            sortPref = sortPref,
            filterMode = filterMode,
            onSearchClick = { isSearchActive = true },
            onQueryChange = { searchQuery = it },
            onSearchClear = {
                searchQuery = ""
                isSearchActive = false
            },
            onSortChange = { sortPref = it },
            onFilterChange = { filterMode = it }
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(EreaderSpacing.XL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
                    ) {
                        Text("기기 내 파일을 검색하려면\n저장소 접근 권한이 필요합니다.")
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                manageStorageLauncher.launch(intent)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }) {
                            Text("권한 허용")
                        }
                    }
                }

                isLoading -> {
                    Text("파일 검색 중...", modifier = Modifier.align(Alignment.Center))
                }

                processedBooks.isEmpty() -> {
                    Text(
                        if (searchQuery.isBlank()) "epub, txt, mobi, pdf 파일을 찾을 수 없습니다."
                        else "\"$searchQuery\"에 해당하는 파일이 없습니다.",
                        modifier = Modifier.align(Alignment.Center).padding(EreaderSpacing.XL)
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(processedBooks) { book ->
                            BookItem(
                                book = book,
                                isFavorite = book.path in favorites,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    scope.launch(Dispatchers.IO) {
                                        dao.upsertLastReadAt(book.path, now)
                                    }
                                    lastReadTimes = lastReadTimes + (book.path to now)
                                    onBookClick(book)
                                },
                                onToggleFavorite = {
                                    val newValue = book.path !in favorites
                                    favorites = if (newValue) favorites + book.path else favorites - book.path
                                    scope.launch(Dispatchers.IO) {
                                        dao.upsertFavorite(book.path, newValue)
                                    }
                                },
                                onHide = {
                                    hiddenBooks = hiddenBooks + book.path
                                    scope.launch(Dispatchers.IO) {
                                        dao.upsertHidden(book.path, true)
                                    }
                                }
                            )
                            HorizontalDivider(color = EreaderColors.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    sortPref: SortPreference,
    filterMode: FilterMode,
    onSearchClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSortChange: (SortPreference) -> Unit,
    onFilterChange: (FilterMode) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // 베이스 레이어: 돋보기 아이콘 + 정렬 컨트롤
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = EreaderSpacing.XS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "검색",
                    tint = EreaderColors.DarkGray
                )
            }

            Box(modifier = Modifier.weight(1f))

            // 필터 드롭다운
            EreaderDropdownMenu(
                items = FilterMode.entries.toList(),
                selectedItem = filterMode,
                onSelect = { onFilterChange(it) },
                label = { it.label },
            )

            // 정렬 필드 드롭다운
            EreaderDropdownMenu(
                items = SortField.entries.toList(),
                selectedItem = sortPref.field,
                onSelect = { onSortChange(sortPref.copy(field = it)) },
                label = { it.label },
            )

        }

        // 오버레이 레이어: 검색 활성 시 전체 행을 덮음
        if (isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EreaderColors.White)
                    .padding(horizontal = EreaderSpacing.XS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = EreaderColors.Black
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = EreaderColors.Black
                    ),
                    cursorBrush = SolidColor(EreaderColors.Black),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "책 제목, 저자 검색...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = EreaderColors.DarkGray
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(onClick = onSearchClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "검색 닫기",
                        tint = EreaderColors.DarkGray
                    )
                }
            }
        }
    }

    HorizontalDivider(color = EreaderColors.Gray)
}

@Composable
private fun BookItem(
    book: BookFile,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit
) {
    val displayTitle = book.metadata?.title ?: book.name.substringBeforeLast('.')
    val author = book.metadata?.author

    var cover by remember(book.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.path) {
        cover = BookCoverLoader.load(book.path, book.extension)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = EreaderSpacing.L, top = 10.dp, bottom = 10.dp, end = EreaderSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 60.dp)
                .background(EreaderColors.Gray),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (book.extension == "pdf") {
                    Text(
                        text = "PDF",
                        color = EreaderColors.White,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp)
                            .background(EreaderColors.Black)
                            .padding(horizontal = 4.dp)
                    )
                }
            } else {
                Text(
                    text = book.extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = EreaderColors.DarkGray
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (author != null) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = EreaderColors.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (cover == null) {
            Text(
                text = book.extension.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = EreaderColors.Black
            )
        }

        EreaderDropdownMenu(
            items = listOf(
                (if (isFavorite) "즐겨찾기 해제" else "즐겨찾기") to onToggleFavorite,
                "숨기기" to onHide
            ),
            onSelect = { it.second() },
            label = { it.first },
            trigger = { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "메뉴",
                        tint = EreaderColors.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
    }
}
