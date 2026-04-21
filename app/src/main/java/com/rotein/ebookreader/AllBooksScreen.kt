package com.rotein.ebookreader

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.net.toUri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import com.rotein.ebookreader.ui.theme.EreaderColors
import com.rotein.ebookreader.ui.theme.EreaderFontSize
import com.rotein.ebookreader.ui.theme.EreaderSpacing
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.PaginationBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AllBooksScreen(
    onBookClick: (BookFile) -> Unit,
    modifier: Modifier = Modifier,
    onLoadComplete: () -> Unit = {},
    refreshKey: Any? = Unit
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
    var covers by remember { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }


    var readingProgressMap by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    // DB에서 읽은 시각 + 즐겨찾기/숨기기/진행률 로드
    LaunchedEffect(refreshKey) {
        val records = withContext(Dispatchers.IO) { dao.getAll() }
        lastReadTimes = records.associate { it.bookPath to it.lastReadAt }
        favorites = records.filter { it.isFavorite }.map { it.bookPath }.toSet()
        hiddenBooks = records.filter { it.isHidden }.map { it.bookPath }.toSet()
        readingProgressMap = records.filter { it.readingProgress > 0f }.associate { it.bookPath to it.readingProgress }
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
            val bookList = if (BookCache.books != null) {
                BookCache.books!!
            } else {
                isLoading = true
                val scanned = withContext(Dispatchers.IO) { FileScanner.scanBooks(context) }
                BookCache.books = scanned
                isLoading = false
                scanned
            }
            books = bookList
            // 스플래시 동안 커버를 미리 로드한 후 완료 통지
            val newCovers = withContext(Dispatchers.IO) {
                val result = mutableMapOf<String, Bitmap?>()
                bookList.forEach { book ->
                    val bitmap = BookCoverLoader.getCached(book.path)
                        ?: BookCoverLoader.load(book.path, book.extension)
                    result[book.path] = bitmap
                }
                result
            }
            covers = covers + newCovers
            onLoadComplete()
        } else {
            onLoadComplete()
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

        var currentPage by remember { mutableIntStateOf(0) }
        var targetPage by remember { mutableIntStateOf(0) }

        // 필터/검색 변경 시 페이지 초기화
        LaunchedEffect(processedBooks.size) {
            currentPage = 0
            targetPage = 0
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(EreaderSpacing.XL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
                    ) {
                        Text(stringResource(R.string.permission_description))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                                manageStorageLauncher.launch(intent)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }

                isLoading -> {
                    Text(stringResource(R.string.loading_files), modifier = Modifier.align(Alignment.Center))
                }

                processedBooks.isEmpty() -> {
                    Text(
                        stringResource(R.string.empty),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val itemHeightDp = 81 // BookItem(80dp) + Divider(1dp)
                    val paginationBarHeightDp = 56
                    val availableHeightDp = this.maxHeight.value.toInt() - paginationBarHeightDp
                    val itemsPerPage = (availableHeightDp / itemHeightDp).coerceAtLeast(1)
                    val totalPages = ((processedBooks.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
                    val safePage = currentPage.coerceIn(0, totalPages - 1)
                    val startIndex = safePage * itemsPerPage
                    val pageItems = processedBooks.subList(
                        startIndex,
                        (startIndex + itemsPerPage).coerceAtMost(processedBooks.size)
                    )

                    // targetPage 변경 시 커버를 먼저 로드한 뒤 페이지 전환
                    LaunchedEffect(targetPage, itemsPerPage, processedBooks) {
                        if (targetPage == currentPage) return@LaunchedEffect
                        val tStart = (targetPage * itemsPerPage).coerceAtMost(processedBooks.size)
                        val tEnd = ((targetPage + 1) * itemsPerPage).coerceAtMost(processedBooks.size)
                        val newCovers = mutableMapOf<String, Bitmap?>()
                        for (i in tStart until tEnd) {
                            val book = processedBooks[i]
                            if (book.path !in covers) {
                                val bitmap = BookCoverLoader.getCached(book.path)
                                    ?: BookCoverLoader.load(book.path, book.extension)
                                newCovers[book.path] = bitmap
                            }
                        }
                        if (newCovers.isNotEmpty()) {
                            covers = covers + newCovers
                        }
                        currentPage = targetPage
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f)) {
                            pageItems.forEach { book ->
                                val isHidden = book.path in hiddenBooks
                                val isFavorite = book.path in favorites
                                BookItem(
                                    book = book,
                                    cover = covers[book.path],
                                    isFavorite = isFavorite,
                                    isHidden = isHidden,
                                    readingProgress = readingProgressMap[book.path] ?: 0f,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        scope.launch(Dispatchers.IO) {
                                            dao.upsertLastReadAt(book.path, now)
                                        }
                                        onBookClick(book)
                                    },
                                    onToggleFavorite = if (isHidden && !isFavorite) null else {{
                                        val newValue = !isFavorite
                                        favorites = if (newValue) favorites + book.path else favorites - book.path
                                        scope.launch(Dispatchers.IO) {
                                            dao.upsertFavorite(book.path, newValue)
                                        }
                                    }},
                                    onToggleHidden = {
                                        val newValue = !isHidden
                                        hiddenBooks = if (newValue) hiddenBooks + book.path else hiddenBooks - book.path
                                        scope.launch(Dispatchers.IO) {
                                            dao.upsertHidden(book.path, newValue)
                                        }
                                    }
                                )
                                HorizontalDivider(color = EreaderColors.Gray)
                            }
                        }

                        PaginationBar(
                            currentPage = safePage,
                            totalPages = totalPages,
                            centerText = stringResource(R.string.pagination_books_format, safePage + 1, totalPages, processedBooks.size),
                            onPrevious = { targetPage = safePage - 1 },
                            onNext = { targetPage = safePage + 1 }
                        )
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
            .padding(top = 4.dp)
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
                    contentDescription = stringResource(R.string.search),
                    tint = EreaderColors.DarkGray
                )
            }

            Box(modifier = Modifier.weight(1f))

            // 필터 드롭다운
            EreaderDropdownMenu(
                items = FilterMode.entries.toList(),
                selectedItem = filterMode,
                onSelect = { onFilterChange(it) },
                label = { stringResource(it.labelRes) },
            )

            // 정렬 필드 드롭다운
            EreaderDropdownMenu(
                items = SortField.entries.toList(),
                selectedItem = sortPref.field,
                onSelect = { onSortChange(sortPref.copy(field = it)) },
                label = { stringResource(it.labelRes) },
            )

            // 언어 선택 드롭다운
            val appLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language
            val currentLocale = appLocale ?: java.util.Locale.getDefault().language
            val languageOptions = listOf(
                "en" to "English",
                "ko" to "한국어",
                "ja" to "日本語",
                "zh" to "中文",
                "es" to "Español",
            )
            val currentLanguageCode = languageOptions.firstOrNull { it.first == currentLocale }?.first ?: languageOptions.first().first

            EreaderDropdownMenu(
                items = languageOptions,
                selectedItem = languageOptions.first { it.first == currentLanguageCode },
                onSelect = { (code, _) ->
                    val localeList = LocaleListCompat.forLanguageTags(code)
                    AppCompatDelegate.setApplicationLocales(localeList)
                },
                label = { it.second },
                trigger = { onClick ->
                    Text(
                        text = stringResource(R.string.language),
                        style = EreaderFontSize.M,
                        modifier = Modifier
                            .clickable { onClick() }
                            .padding(horizontal = EreaderSpacing.M, vertical = EreaderSpacing.S)
                    )
                }
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
                        .testTag("searchInput")
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = EreaderFontSize.L.copy(
                        color = EreaderColors.Black
                    ),
                    cursorBrush = SolidColor(EreaderColors.Black),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_books_hint),
                                    style = EreaderFontSize.L,
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
                        contentDescription = stringResource(R.string.close_search),
                        tint = EreaderColors.DarkGray
                    )
                }
            }
        }
    }

    HorizontalDivider(color = EreaderColors.Black)
}

@Composable
private fun BookItem(
    book: BookFile,
    cover: Bitmap?,
    isFavorite: Boolean,
    isHidden: Boolean,
    readingProgress: Float,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onToggleHidden: () -> Unit
) {
    val displayTitle = book.metadata?.title ?: book.name.substringBeforeLast('.')
    val author = book.metadata?.author

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("bookItem_${book.name}")
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
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (book.extension == "pdf") {
                    Text(
                        text = "PDF",
                        color = EreaderColors.White,
                        style = EreaderFontSize.S,
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
                    style = EreaderFontSize.S,
                    color = EreaderColors.DarkGray
                )
            }
            if (isFavorite) {
                Canvas(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-7).dp, y = (-7).dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val outerR = w / 2f
                    val innerR = outerR * 0.38f
                    val starPath = Path().apply {
                        for (i in 0 until 5) {
                            val outerAngle = Math.toRadians(-90.0 + i * 72.0)
                            val innerAngle = Math.toRadians(-90.0 + i * 72.0 + 36.0)
                            val ox = cx + outerR * kotlin.math.cos(outerAngle).toFloat()
                            val oy = cy + outerR * kotlin.math.sin(outerAngle).toFloat()
                            val ix = cx + innerR * kotlin.math.cos(innerAngle).toFloat()
                            val iy = cy + innerR * kotlin.math.sin(innerAngle).toFloat()
                            if (i == 0) moveTo(ox, oy) else lineTo(ox, oy)
                            lineTo(ix, iy)
                        }
                        close()
                    }
                    drawPath(starPath, Color.White, style = Stroke(width = 1.dp.toPx()))
                    drawPath(starPath, Color.Black, style = Fill)
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = EreaderFontSize.L,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (author != null) {
                Text(
                    text = author,
                    style = EreaderFontSize.M,
                    color = EreaderColors.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(EreaderColors.Gray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(readingProgress.coerceIn(0f, 1f))
                            .background(EreaderColors.Black)
                    )
                }
                Text(
                    text = "${(readingProgress * 100).toInt()}%",
                    style = EreaderFontSize.XS,
                    color = EreaderColors.DarkGray
                )
            }
        }


        val favoriteLabel = if (isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite)
        val hiddenLabel = if (isHidden) stringResource(R.string.unhide) else stringResource(R.string.hide)
        val menuItems = buildList {
            if (onToggleFavorite != null) {
                add(favoriteLabel to onToggleFavorite)
            }
            add(hiddenLabel to onToggleHidden)
        }
        EreaderDropdownMenu(
            items = menuItems,
            onSelect = { it.second() },
            label = { it.first },
            trigger = { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.menu),
                        tint = EreaderColors.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
    }
}
