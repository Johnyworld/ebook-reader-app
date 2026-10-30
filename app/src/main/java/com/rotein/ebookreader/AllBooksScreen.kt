package com.rotein.ebookreader

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.StrokeJoin
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
    val catalogDao = remember { BookDatabase.getInstance(context).bookCatalogDao() }

    // 스캔 결과를 DB에 저장 — 다음 콜드 스타트 시 전체 재스캔 없이 즉시 표시하기 위함
    suspend fun saveCatalog(list: List<BookFile>) = withContext(Dispatchers.IO) {
        catalogDao.replaceAll(list.map { it.toCatalogEntry() })
    }

    var hasFolders by remember { mutableStateOf(FolderUriStore.hasAny(context)) }
    var folders by remember { mutableStateOf(FolderUriStore.load(context)) }
    var scanKey by remember { mutableIntStateOf(0) }  // 폴더 변경 시 재스캔 트리거
    val lifecycleOwner = LocalLifecycleOwner.current
    var books by remember { mutableStateOf(BookCache.books ?: emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortPref by remember { mutableStateOf(SortPreferenceStore.load(context)) }
    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var lastReadTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var viewMode by remember { mutableStateOf(ViewModeStore.load(context)) }
    // 폴더 보기에서 현재 보고 있는 위치 — 메모리에만 두어 앱 재시작 시 최상위에서 시작한다
    var location by remember { mutableStateOf<FolderLocation>(FolderLocation.Top) }

    // 정렬 설정 변경 시 기기에 저장
    LaunchedEffect(sortPref) {
        SortPreferenceStore.save(context, sortPref)
    }

    // 폴더 보기에서 폴더를 맨 위로 정렬할지 (끄면 폴더와 책을 섞어 정렬)
    var foldersFirst by remember { mutableStateOf(FolderSettingsStore.loadFoldersFirst(context)) }
    LaunchedEffect(foldersFirst) {
        FolderSettingsStore.saveFoldersFirst(context, foldersFirst)
    }

    // 보기 방식 변경 시 기기에 저장
    LaunchedEffect(viewMode) {
        ViewModeStore.save(context, viewMode)
    }

    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hiddenBooks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var covers by remember { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }


    var readingProgressMap by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    // 페이지를 이동해 보여줘야 할 책 — 책을 열 때 설정되고, 페이지 이동 후 비운다
    var jumpToBookKey by remember { mutableStateOf<String?>(null) }

    // DB에서 읽은 시각 + 즐겨찾기/숨기기/진행률 로드
    LaunchedEffect(refreshKey) {
        val records = withContext(Dispatchers.IO) { dao.getAll() }
        // 책을 열 때 화면에 먼저 반영한 읽은 시각이 DB 저장보다 앞설 수 있으므로 더 최근 값을 유지한다
        val dbLastReadTimes = records.associate { it.bookPath to it.lastReadAt }
        lastReadTimes = dbLastReadTimes + lastReadTimes.filter { (key, time) -> time > (dbLastReadTimes[key] ?: 0L) }
        favorites = records.filter { it.isFavorite }.map { it.bookPath }.toSet()
        hiddenBooks = records.filter { it.isHidden }.map { it.bookPath }.toSet()
        readingProgressMap = records.filter { it.readingProgress > 0f }.associate { it.bookPath to it.readingProgress }
    }

    // 0) 필터 모드 적용 — 폴더 보기도 이 결과를 쓴다
    val visibleBooks = remember(books, hiddenBooks, favorites, filterMode) {
        when (filterMode) {
            FilterMode.ALL -> books.filter { it.bookKey() !in hiddenBooks }
            FilterMode.FAVORITE -> books.filter { it.bookKey() in favorites && it.bookKey() !in hiddenBooks }
            FilterMode.HIDDEN -> books.filter { it.bookKey() in hiddenBooks }
        }
    }

    val processedBooks = remember(visibleBooks, searchQuery, sortPref, lastReadTimes) {
        // 1) 검색 필터
        val filtered = if (searchQuery.isBlank()) visibleBooks
        else {
            val q = searchQuery.trim().lowercase()
            visibleBooks.filter { book ->
                val title = (book.metadata?.title ?: book.name).lowercase()
                val author = book.metadata?.author?.lowercase() ?: ""
                title.contains(q) || author.contains(q) || book.name.lowercase().contains(q)
            }
        }
        // 2) 정렬 (방향은 필드별 기본값 적용)
        sortBooks(filtered, sortPref.field, lastReadTimes)
    }

    // 루트는 필터와 무관하게 전체 책에서 도출 — 필터를 바꿔도 최상위 구조가 바뀌지 않게 한다
    val rootUris = remember(books) { rootUrisOf(books) }
    val rootNames = remember(rootUris) { rootUris.associateWith { rootDisplayName(it) } }
    // 검색어가 있으면 폴더와 무관하게 전체 결과를 평면 목록으로 보여준다
    val inFolderView = viewMode == ViewMode.FOLDER && searchQuery.isBlank()

    // 재스캔·필터 변경으로 현재 폴더가 비면 책이 남은 가장 가까운 상위로 보정
    // 그리는 시점에 바로 계산해, 빈 화면이 한 프레임 그려지는 것(e-ink 추가 새로고침)을 막는다
    val effectiveLocation = remember(location, visibleBooks, rootUris) {
        resolveLocation(location, visibleBooks, rootUris)
    }
    // 상태도 보정값으로 맞춘다
    LaunchedEffect(effectiveLocation) {
        location = effectiveLocation
    }

    // 화면에 그릴 항목 — 목록 보기는 책만, 폴더 보기는 하위 폴더 + 직속 책
    val entries: List<FolderEntry> = remember(inFolderView, processedBooks, visibleBooks, effectiveLocation, rootUris, rootNames, sortPref, lastReadTimes, foldersFirst) {
        if (inFolderView) buildFolderEntries(visibleBooks, effectiveLocation, rootUris, rootNames, sortPref.field, lastReadTimes, foldersFirst)
        else processedBooks.map { FolderEntry.Book(it) }
    }
    // 한 단계 위 위치 — null이면 경로 줄과 뒤로가기 처리를 하지 않는다
    val parentLocation = if (inFolderView) parentOf(effectiveLocation, rootUris) else null

    // SAF 폴더 선택 런처
    val folderPickerLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            FolderUriStore.add(context, uri)
            // 캐시 무효화 + 재스캔 트리거
            BookCache.books = null
            hasFolders = true
            folders = FolderUriStore.load(context)
            scanKey++
        }
    }

    // 최초 로드 및 폴더 변경 시: 메모리 캐시 → DB에 저장된 목록 → 전체 스캔 순으로 사용
    // 스플래시는 스캔/커버 로드 완료를 기다리지 않고 바로 내린다 (스캔 중에는 로딩 문구 표시)
    LaunchedEffect(hasFolders, scanKey) {
        if (hasFolders) {
            val cached = BookCache.books
            if (cached != null) {
                books = cached
                onLoadComplete()
                return@LaunchedEffect
            }
            // 앱 프로세스가 새로 뜬 경우(scanKey == 0): DB에 저장된 목록을 즉시 표시하고 변경분만 diff 스캔
            // 폴더 추가/제거(scanKey > 0)는 저장된 목록이 폴더 구성과 맞지 않으므로 전체 스캔
            val saved = if (scanKey == 0) {
                withContext(Dispatchers.IO) { catalogDao.getAll().map { it.toBookFile() } }
            } else emptyList()
            if (saved.isNotEmpty()) {
                // 저장된 목록은 아직 diff 확인 전이므로 화면에만 표시하고 BookCache에는 넣지 않는다
                // (BookCache가 null인 동안 onResume/재진입 diff 스캔이 중복 실행되지 않고,
                //  Activity 재생성 시에도 다시 diff 스캔이 수행됨)
                books = saved
                onLoadComplete()
                val refreshed = withContext(Dispatchers.IO) { FileScanner.refreshBooks(context, saved) }
                withContext(Dispatchers.IO) { migrateBookPathsToUri(context, refreshed) }
                BookCache.books = refreshed
                books = refreshed
                saveCatalog(refreshed)
            } else {
                isLoading = true
                onLoadComplete()
                val scanned = withContext(Dispatchers.IO) { FileScanner.scanBooks(context) }
                // 기존 절대 경로 레코드를 SAF URI로 마이그레이션
                withContext(Dispatchers.IO) { migrateBookPathsToUri(context, scanned) }
                BookCache.books = scanned
                books = scanned
                isLoading = false
                saveCatalog(scanned)
            }
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
            val startScanKey = scanKey
            val refreshed = withContext(Dispatchers.IO) { FileScanner.refreshBooks(context, cached) }
            // 스캔 도중 폴더가 추가/제거되었으면 이전 폴더 구성의 결과이므로 버린다
            if (startScanKey != scanKey) return@repeatOnLifecycle
            // 미마이그레이션 레코드가 남아있으면 재시도
            withContext(Dispatchers.IO) { migrateBookPathsToUri(context, refreshed) }
            BookCache.books = refreshed
            books = refreshed
            saveCatalog(refreshed)
        }
    }

    // 화면 재진입 시 diff 스캔 (리더에서 돌아올 때)
    var prevRefreshKey by remember { mutableStateOf(refreshKey) }
    LaunchedEffect(refreshKey) {
        if (prevRefreshKey == refreshKey) return@LaunchedEffect
        prevRefreshKey = refreshKey
        val cached = BookCache.books ?: return@LaunchedEffect
        val startScanKey = scanKey
        val refreshed = withContext(Dispatchers.IO) { FileScanner.refreshBooks(context, cached) }
        // 스캔 도중 폴더가 추가/제거되었으면 이전 폴더 구성의 결과이므로 버린다
        if (startScanKey != scanKey) return@LaunchedEffect
        withContext(Dispatchers.IO) { migrateBookPathsToUri(context, refreshed) }
        BookCache.books = refreshed
        books = refreshed
        saveCatalog(refreshed)
    }

    Column(modifier = modifier.fillMaxSize()) {
        var currentPage by remember { mutableIntStateOf(0) }
        var targetPage by remember { mutableIntStateOf(0) }

        TopBar(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            sortPref = sortPref,
            filterMode = filterMode,
            viewMode = viewMode,
            foldersFirst = foldersFirst,
            folders = folders,
            onSearchClick = { isSearchActive = true },
            onQueryChange = { searchQuery = it },
            onSearchClear = {
                searchQuery = ""
                isSearchActive = false
            },
            onSortChange = { sortPref = it },
            onFilterChange = { filterMode = it },
            onViewModeChange = {
                viewMode = it
                // 보기 방식이 바뀌면 항목 수가 같아도 첫 페이지부터 보여준다
                currentPage = 0
                targetPage = 0
            },
            onFoldersFirstToggle = { foldersFirst = !foldersFirst },
            onAddFolder = { folderPickerLauncher.launch(null) },
            onRemoveFolder = { uri ->
                FolderUriStore.remove(context, uri)
                folders = FolderUriStore.load(context)
                hasFolders = FolderUriStore.hasAny(context)
                // 폴더 제거 후 도서 목록 다시 스캔
                BookCache.books = null
                scanKey++
            }
        )

        // 필터/검색/폴더 이동으로 항목 수가 바뀌면 페이지 초기화
        LaunchedEffect(entries.size) {
            currentPage = 0
            targetPage = 0
        }

        // 폴더 이동 — 항목 수가 같아도 첫 페이지부터 보이도록 페이지를 직접 초기화한다
        fun moveTo(newLocation: FolderLocation) {
            location = newLocation
            currentPage = 0
            targetPage = 0
        }

        // 폴더 안에서는 상위로 가는 경로 줄을 보여주고, 시스템 뒤로가기도 한 단계 위로 보낸다
        // (리더 화면은 이 화면보다 나중에 BackHandler를 등록하므로 리더가 열려 있을 때는 리더가 우선)
        if (parentLocation != null) {
            FolderPathBar(
                path = breadcrumb(effectiveLocation, rootUris, rootNames),
                onUp = { moveTo(parentLocation) }
            )
        }
        BackHandler(enabled = parentLocation != null) {
            parentLocation?.let { moveTo(it) }
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

                entries.isEmpty() -> {
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
                    val totalPages = ((entries.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
                    val safePage = currentPage.coerceIn(0, totalPages - 1)
                    val startIndex = safePage * itemsPerPage
                    val pageItems = entries.subList(
                        startIndex,
                        (startIndex + itemsPerPage).coerceAtMost(entries.size)
                    )

                    // 현재 페이지에 보이는 책 중 커버가 아직 없는 것만 로드 (전체 책 커버를 미리 로드하지 않음)
                    LaunchedEffect(pageItems) {
                        val missing = pageItems.filterIsInstance<FolderEntry.Book>().map { it.book }
                            .filter { it.bookKey() !in covers }
                        if (missing.isEmpty()) return@LaunchedEffect
                        val newCovers = mutableMapOf<String, Bitmap?>()
                        missing.forEach { book ->
                            newCovers[book.bookKey()] = BookCoverLoader.loadFromBook(context, book)
                        }
                        covers = covers + newCovers
                    }

                    // targetPage 변경 시 커버를 먼저 로드한 뒤 페이지 전환
                    LaunchedEffect(targetPage, itemsPerPage, entries) {
                        if (targetPage == currentPage) return@LaunchedEffect
                        val tStart = (targetPage * itemsPerPage).coerceAtMost(entries.size)
                        val tEnd = ((targetPage + 1) * itemsPerPage).coerceAtMost(entries.size)
                        val newCovers = mutableMapOf<String, Bitmap?>()
                        for (i in tStart until tEnd) {
                            // 폴더 항목은 표지가 없으므로 건너뛴다
                            val book = (entries[i] as? FolderEntry.Book)?.book ?: continue
                            if (book.bookKey() !in covers) {
                                newCovers[book.bookKey()] = BookCoverLoader.loadFromBook(context, book)
                            }
                        }
                        if (newCovers.isNotEmpty()) {
                            covers = covers + newCovers
                        }
                        currentPage = targetPage
                    }

                    // 방금 연 책이 재정렬된 목록에서 있는 페이지로 이동
                    // 리더가 목록을 가리고 있는 동안 처리되므로, 돌아왔을 때 이동 과정이 보이지 않는다
                    // (목록에 없으면 — 검색/필터로 제외된 경우 등 — 현재 페이지 유지)
                    LaunchedEffect(jumpToBookKey, entries, itemsPerPage, location) {
                        val key = jumpToBookKey ?: return@LaunchedEffect
                        // 폴더 보기: 먼저 그 책이 있는 폴더로 이동하고, 항목이 다시 계산된 뒤 페이지를 찾는다
                        if (inFolderView) {
                            val target = visibleBooks.firstOrNull { it.bookKey() == key }?.let { locationOf(it) }
                            if (target != null && normalizeLocation(target, rootUris) != normalizeLocation(effectiveLocation, rootUris)) {
                                location = target
                                return@LaunchedEffect
                            }
                        }
                        jumpToBookKey = null
                        val index = entries.indexOfFirst { it is FolderEntry.Book && it.book.bookKey() == key }
                        if (index < 0) return@LaunchedEffect
                        val page = index / itemsPerPage
                        currentPage = page
                        targetPage = page
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f)) {
                            pageItems.forEach { entry ->
                                when (entry) {
                                    is FolderEntry.Folder -> FolderItem(
                                        name = entry.name,
                                        bookCount = entry.bookCount,
                                        onClick = { moveTo(entry.location) }
                                    )
                                    is FolderEntry.Book -> {
                                        val book = entry.book
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
                                                // DB 저장을 기다리지 않고 읽은 시각을 바로 반영해 재정렬하고,
                                                // 방금 연 책이 있는 페이지로 이동시킨다 (리더가 덮은 뒤에 적용됨)
                                                lastReadTimes = lastReadTimes + (book.bookKey() to now)
                                                jumpToBookKey = book.bookKey()
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
                                    }
                                }
                                HorizontalDivider(color = EreaderColors.Gray)
                            }
                        }

                        PaginationBar(
                            currentPage = safePage,
                            totalPages = totalPages,
                            centerText = stringResource(R.string.pagination_books_format, safePage + 1, totalPages, entries.size),
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
    viewMode: ViewMode,
    foldersFirst: Boolean,
    folders: List<Uri>,
    onSearchClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSortChange: (SortPreference) -> Unit,
    onFilterChange: (FilterMode) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onFoldersFirstToggle: () -> Unit,
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

            // 보기 방식 드롭다운 (목록 / 폴더)
            EreaderDropdownMenu(
                items = ViewMode.entries.toList(),
                selectedItem = viewMode,
                onSelect = { onViewModeChange(it) },
                label = { stringResource(it.labelRes) },
            )

            // 필터 드롭다운
            EreaderDropdownMenu(
                items = FilterMode.entries.toList(),
                selectedItem = filterMode,
                onSelect = { onFilterChange(it) },
                label = { stringResource(it.labelRes) },
            )

            // 정렬 필드 드롭다운 — 폴더 보기에서는 맨 아래에 "폴더 설정" 섹션을 붙인다
            EreaderDropdownMenu(
                items = SortField.entries.toList(),
                selectedItem = sortPref.field,
                onSelect = { onSortChange(sortPref.copy(field = it)) },
                label = { stringResource(it.labelRes) },
                footer = if (viewMode == ViewMode.FOLDER) {
                    { FolderSortSettings(foldersFirst = foldersFirst, onToggle = onFoldersFirstToggle) }
                } else null,
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

/** 정렬 드롭다운 맨 아래의 "폴더 설정" 섹션 — 폴더를 맨 위로 정렬할지 토글한다 (드롭다운은 열린 채 유지) */
@Composable
private fun FolderSortSettings(foldersFirst: Boolean, onToggle: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.folder_settings),
            style = EreaderFontSize.S,
            color = EreaderColors.DarkGray,
            modifier = Modifier.padding(start = EreaderSpacing.L, end = EreaderSpacing.M, top = EreaderSpacing.S)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .testTag("foldersFirstToggle")
                .padding(start = EreaderSpacing.L, end = EreaderSpacing.M, top = EreaderSpacing.M, bottom = EreaderSpacing.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.folders_first),
                style = EreaderFontSize.M,
                color = EreaderColors.Black,
                modifier = Modifier.weight(1f)
            )
            // Material Checkbox는 체크 애니메이션이 있어 테두리 상자 + 체크 아이콘으로 직접 그린다
            Box(
                modifier = Modifier
                    .padding(start = EreaderSpacing.L)
                    .size(18.dp)
                    .border(1.5.dp, EreaderColors.Black),
                contentAlignment = Alignment.Center
            ) {
                if (foldersFirst) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = EreaderColors.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/** 폴더 보기에서 현재 경로와 상위 이동 버튼을 보여주는 줄. 줄 전체가 상위 이동 터치 영역 */
@Composable
private fun FolderPathBar(path: String, onUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onUp() }
            .testTag("folderPathBar")
            .padding(horizontal = EreaderSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.S)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.go_up),
            tint = EreaderColors.Black
        )
        // Compose 1.7에는 앞쪽 말줄임(StartEllipsis)이 없어 끝쪽 말줄임을 쓴다
        Text(
            text = path,
            style = EreaderFontSize.M,
            color = EreaderColors.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = EreaderColors.Gray)
}

/** 폴더 항목 — 페이지당 항목 수 계산이 맞도록 BookItem과 같은 80dp 높이 */
@Composable
private fun FolderItem(name: String, bookCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() }
            .testTag("folderItem_$name")
            .padding(start = EreaderSpacing.L, end = EreaderSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
    ) {
        // BookItem의 표지 자리와 같은 크기로 맞춰 제목 열이 정렬되게 한다
        Box(modifier = Modifier.size(width = 44.dp, height = 60.dp), contentAlignment = Alignment.Center) {
            // Material Outlined 폴더와 같은 모양을 24칸 격자에 직접 그린다
            // (벡터 아이콘은 선 두께를 바꿀 수 없어서, 원본 두께 2의 절반인 1로 그리기 위함)
            Canvas(modifier = Modifier.size(36.dp)) {
                val unit = size.width / 24f
                val folderPath = Path().apply {
                    moveTo(3f * unit, 5f * unit)
                    lineTo(9.6f * unit, 5f * unit)   // 탭 윗변
                    lineTo(11.6f * unit, 7f * unit)  // 탭 사선
                    lineTo(21f * unit, 7f * unit)
                    lineTo(21f * unit, 19f * unit)
                    lineTo(3f * unit, 19f * unit)
                    close()
                }
                drawPath(
                    path = folderPath,
                    color = EreaderColors.Black,
                    style = Stroke(width = 1f * unit, join = StrokeJoin.Round)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = EreaderFontSize.L,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.folder_book_count, bookCount),
                style = EreaderFontSize.M,
                color = EreaderColors.DarkGray
            )
        }
    }
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
