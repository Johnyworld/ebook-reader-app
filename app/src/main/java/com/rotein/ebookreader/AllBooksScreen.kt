package com.rotein.ebookreader

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rotein.ebookreader.ui.components.EreaderDropdownMenu
import com.rotein.ebookreader.ui.components.PaginationBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// bookKey()는 BookFile.kt에 정의된 확장 함수를 사용

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

    var hasFolders by remember { mutableStateOf(FolderUriStore.hasAny(context)) }
    var folders by remember { mutableStateOf(FolderUriStore.load(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
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
            FilterMode.ALL -> books.filter { it.bookKey() !in hiddenBooks }
            FilterMode.FAVORITE -> books.filter { it.bookKey() in favorites && it.bookKey() !in hiddenBooks }
            FilterMode.HIDDEN -> books.filter { it.bookKey() in hiddenBooks }
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
            SortField.LAST_READ -> compareBy { lastReadTimes[it.bookKey()] ?: 0L }
        }
        val sorted = filtered.sortedWith(comparator)
        if (sortPref.field.defaultDescending) sorted.reversed() else sorted
    }

    // SAF 폴더 선택 런처
    val folderPickerLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            FolderUriStore.add(context, uri)
            hasFolders = true
            folders = FolderUriStore.load(context)
        }
    }

    // 최초 로드: 캐시가 있으면 그대로 사용, 없으면 전체 스캔
    LaunchedEffect(hasFolders) {
        if (hasFolders) {
            val cached = BookCache.books
            val bookList = if (cached != null) {
                cached
            } else {
                isLoading = true
                val scanned = withContext(Dispatchers.IO) { FileScanner.scanBooks(context) }
                // 기존 절대 경로 레코드를 SAF URI로 마이그레이션
                withContext(Dispatchers.IO) { migrateBookPathsToUri(context, scanned) }
                BookCache.books = scanned
                isLoading = false
                scanned
            }
            books = bookList
            val booksNeedingCovers = bookList.filter { it.bookKey() !in covers }
            if (booksNeedingCovers.isNotEmpty()) {
                val newCovers = withContext(Dispatchers.IO) {
                    val result = mutableMapOf<String, Bitmap?>()
                    booksNeedingCovers.forEach { book ->
                        result[book.bookKey()] = BookCoverLoader.loadFromBook(context, book)
                    }
                    result
                }
                covers = covers + newCovers
            }
            onLoadComplete()
        } else {
            onLoadComplete()
        }
    }

    // 앱 포그라운드 복귀 시 diff 스캔
    LaunchedEffect(lifecycleOwner) {
        var isFirstResume = true
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (isFirstResume) { isFirstResume = false; return@repeatOnLifecycle }
            val cached = BookCache.books ?: return@repeatOnLifecycle
            val refreshed = withContext(Dispatchers.IO) { FileScanner.refreshBooks(context, cached) }
            // 미마이그레이션 레코드가 남아있으면 재시도
            withContext(Dispatchers.IO) { migrateBookPathsToUri(context, refreshed) }
            BookCache.books = refreshed
            books = refreshed
            val booksNeedingCovers = refreshed.filter { it.bookKey() !in covers }
            if (booksNeedingCovers.isNotEmpty()) {
                val newCovers = withContext(Dispatchers.IO) {
                    val result = mutableMapOf<String, Bitmap?>()
                    booksNeedingCovers.forEach { book ->
                        result[book.bookKey()] = BookCoverLoader.loadFromBook(context, book)
                    }
                    result
                }
                covers = covers + newCovers
            }
        }
    }

    // 화면 재진입 시 diff 스캔 (리더에서 돌아올 때)
    var prevRefreshKey by remember { mutableStateOf(refreshKey) }
    LaunchedEffect(refreshKey) {
        if (prevRefreshKey == refreshKey) return@LaunchedEffect
        prevRefreshKey = refreshKey
        val cached = BookCache.books ?: return@LaunchedEffect
        val refreshed = withContext(Dispatchers.IO) { FileScanner.refreshBooks(context, cached) }
        withContext(Dispatchers.IO) { migrateBookPathsToUri(context, refreshed) }
        BookCache.books = refreshed
        books = refreshed
        val booksNeedingCovers = refreshed.filter { it.bookKey() !in covers }
        if (booksNeedingCovers.isNotEmpty()) {
            val newCovers = withContext(Dispatchers.IO) {
                val result = mutableMapOf<String, Bitmap?>()
                booksNeedingCovers.forEach { book ->
                    result[book.bookKey()] = BookCoverLoader.loadFromBook(context, book)
                }
                result
            }
            covers = covers + newCovers
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            sortPref = sortPref,
            filterMode = filterMode,
            folders = folders,
            onSearchClick = { isSearchActive = true },
            onQueryChange = { searchQuery = it },
            onSearchClear = {
                searchQuery = ""
                isSearchActive = false
            },
            onSortChange = { sortPref = it },
            onFilterChange = { filterMode = it },
            onAddFolder = { folderPickerLauncher.launch(null) },
            onRemoveFolder = { uri ->
                FolderUriStore.remove(context, uri)
                folders = FolderUriStore.load(context)
                hasFolders = FolderUriStore.hasAny(context)
                // 폴더 제거 후 도서 목록 다시 스캔
                BookCache.books = null
            }
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
                !hasFolders -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(EreaderSpacing.XL),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
                    ) {
                        Text(stringResource(R.string.folder_select_description))
                        Button(onClick = { folderPickerLauncher.launch(null) }) {
                            Text(stringResource(R.string.select_folder))
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
                            if (book.bookKey() !in covers) {
                                newCovers[book.bookKey()] = BookCoverLoader.loadFromBook(context, book)
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
                                val isHidden = book.bookKey() in hiddenBooks
                                val isFavorite = book.bookKey() in favorites
                                BookItem(
                                    book = book,
                                    cover = covers[book.bookKey()],
                                    isFavorite = isFavorite,
                                    isHidden = isHidden,
                                    readingProgress = readingProgressMap[book.bookKey()] ?: 0f,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        scope.launch(Dispatchers.IO) {
                                            dao.upsertLastReadAt(book.bookKey(), now)
                                        }
                                        onBookClick(book)
                                    },
                                    onToggleFavorite = if (isHidden && !isFavorite) null else {{
                                        val newValue = !isFavorite
                                        favorites = if (newValue) favorites + book.bookKey() else favorites - book.bookKey()
                                        scope.launch(Dispatchers.IO) {
                                            dao.upsertFavorite(book.bookKey(), newValue)
                                        }
                                    }},
                                    onToggleHidden = {
                                        val newValue = !isHidden
                                        hiddenBooks = if (newValue) hiddenBooks + book.bookKey() else hiddenBooks - book.bookKey()
                                        scope.launch(Dispatchers.IO) {
                                            dao.upsertHidden(book.bookKey(), newValue)
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
    folders: List<Uri>,
    onSearchClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSortChange: (SortPreference) -> Unit,
    onFilterChange: (FilterMode) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Uri) -> Unit
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
        // 베이스 레이어: 돋보기 아이콘 + 정렬 컨트롤 + 케밥 메뉴
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

            // 케밥 메뉴 (설정)
            KebabMenu(
                folders = folders,
                onAddFolder = onAddFolder,
                onRemoveFolder = onRemoveFolder
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

/** 케밥 메뉴: 언어 설정 + 폴더 관리 */
@Composable
private fun KebabMenu(
    folders: List<Uri>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Uri) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.settings),
                tint = EreaderColors.DarkGray
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = {
                    expanded = false
                    showLanguage = false
                    showFolders = false
                },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .background(EreaderColors.White)
                        .border(1.dp, EreaderColors.Black)
                ) {
                    if (!showLanguage && !showFolders) {
                        // 메인 메뉴
                        KebabMenuItem(
                            text = stringResource(R.string.language),
                            onClick = { showLanguage = true }
                        )
                        HorizontalDivider(color = EreaderColors.Gray)
                        KebabMenuItem(
                            text = stringResource(R.string.manage_folders),
                            onClick = { showFolders = true }
                        )
                    } else if (showLanguage) {
                        // 언어 선택 서브메뉴
                        KebabMenuItem(
                            text = "← ${stringResource(R.string.language)}",
                            onClick = { showLanguage = false }
                        )
                        HorizontalDivider(color = EreaderColors.Black)

                        val appLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language
                        val currentLocale = appLocale ?: java.util.Locale.getDefault().language
                        val languages = listOf(
                            "en" to "English",
                            "ko" to "한국어",
                            "ja" to "日本語",
                            "zh" to "中文",
                            "es" to "Español",
                        )
                        languages.forEachIndexed { index, (code, name) ->
                            val isSelected = code == currentLocale
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val localeList = LocaleListCompat.forLanguageTags(code)
                                        AppCompatDelegate.setApplicationLocales(localeList)
                                        expanded = false
                                        showLanguage = false
                                    }
                                    .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
                            ) {
                                Text(
                                    text = name,
                                    style = EreaderFontSize.M,
                                    color = EreaderColors.Black,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = EreaderColors.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (index < languages.lastIndex) {
                                HorizontalDivider(color = EreaderColors.Gray)
                            }
                        }
                    } else if (showFolders) {
                        // 폴더 관리 서브메뉴
                        KebabMenuItem(
                            text = "← ${stringResource(R.string.manage_folders)}",
                            onClick = { showFolders = false }
                        )
                        HorizontalDivider(color = EreaderColors.Black)

                        if (folders.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_folders),
                                style = EreaderFontSize.S,
                                color = EreaderColors.DarkGray,
                                modifier = Modifier.padding(EreaderSpacing.L)
                            )
                        } else {
                            folders.forEach { uri ->
                                // URI에서 마지막 경로 세그먼트를 표시명으로 사용
                                val displayName = uri.lastPathSegment
                                    ?.substringAfterLast(':')
                                    ?.substringAfterLast('/')
                                    ?: uri.toString()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = EreaderSpacing.L, end = EreaderSpacing.XS, top = EreaderSpacing.XS, bottom = EreaderSpacing.XS)
                                ) {
                                    Text(
                                        text = displayName,
                                        style = EreaderFontSize.S,
                                        color = EreaderColors.Black,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = {
                                            onRemoveFolder(uri)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = EreaderColors.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = EreaderColors.Gray)
                            }
                        }

                        // 폴더 추가 버튼
                        KebabMenuItem(
                            text = "+ ${stringResource(R.string.add_folder)}",
                            onClick = {
                                onAddFolder()
                                expanded = false
                                showFolders = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KebabMenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = EreaderFontSize.M,
        color = EreaderColors.Black,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = EreaderSpacing.L, vertical = EreaderSpacing.M)
    )
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
