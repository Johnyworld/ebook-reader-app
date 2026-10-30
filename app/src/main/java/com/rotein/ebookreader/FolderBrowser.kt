package com.rotein.ebookreader

import java.net.URLDecoder
import java.text.Normalizer

/** 폴더 보기에서 현재 보고 있는 위치 */
sealed interface FolderLocation {
    /** 최상위 — 루트가 하나면 그 루트 내부, 여러 개면 루트 목록을 보여준다 */
    data object Top : FolderLocation

    /** 특정 루트 아래의 폴더. path는 루트 기준 상대 경로이고 ""는 루트 자신 */
    data class Folder(val rootUri: String, val path: String) : FolderLocation
}

/** 폴더 보기 목록의 한 칸 — 하위 폴더 또는 책 */
sealed interface FolderEntry {
    /** bookCount는 하위 폴더까지 포함한 책 수 */
    data class Folder(val location: FolderLocation.Folder, val name: String, val bookCount: Int) : FolderEntry
    data class Book(val book: BookFile) : FolderEntry
}

/** 상위 폴더 경로에 하위 폴더 이름을 붙인다. 한글 자모 분리(NFD)를 막기 위해 NFC로 정규화 */
fun childFolderPath(parent: String, name: String): String {
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFC)
    return if (parent.isEmpty()) normalized else "$parent/$normalized"
}

/**
 * 루트(SAF 트리 URI)의 표시 이름.
 * 트리 문서 ID("primary:Books/SF")의 마지막 폴더 이름을 쓰고, 저장소 최상위("primary:")면 볼륨 이름을 쓴다.
 */
fun rootDisplayName(rootUri: String): String {
    val encoded = rootUri.substringAfterLast("/tree/", "")
    if (encoded.isEmpty()) return rootUri
    // URLDecoder는 '+'를 공백으로 바꾸므로 미리 이스케이프해 원래 '+'를 보존한다
    val docId = URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
    val path = docId.substringAfter(':', docId)
    // 한글 자모 분리(NFD)를 막기 위해 NFC로 정규화
    return Normalizer.normalize(path.substringAfterLast('/').ifEmpty { docId.substringBefore(':') }, Normalizer.Form.NFC)
}

/** 책 목록에 등장하는 루트들 (빈 rootUri 제외, 등장 순서 유지) */
fun rootUrisOf(books: List<BookFile>): List<String> =
    books.map { it.rootUri }.filter { it.isNotEmpty() }.distinct()

/** 책이 들어 있는 폴더 위치 — 레거시 경로 책(rootUri 없음)은 최상위 */
fun locationOf(book: BookFile): FolderLocation =
    if (book.rootUri.isEmpty()) FolderLocation.Top else FolderLocation.Folder(book.rootUri, book.folderPath)

/** 루트가 하나면 Top을 그 루트 내부로 바꾼다 — 같은 화면을 가리키는 위치를 하나로 맞추기 위함 */
fun normalizeLocation(location: FolderLocation, rootUris: List<String>): FolderLocation =
    if (location == FolderLocation.Top && rootUris.size == 1) FolderLocation.Folder(rootUris[0], "") else location

/** 책 정렬 — 방향은 필드별 기본값(SortField.defaultDescending)을 따른다 */
fun sortBooks(books: List<BookFile>, field: SortField, lastReadTimes: Map<String, Long>): List<BookFile> {
    val comparator: Comparator<BookFile> = when (field) {
        SortField.TITLE -> compareBy { (it.metadata?.title ?: it.name).lowercase() }
        SortField.AUTHOR -> compareBy { it.metadata?.author?.lowercase() ?: "\uFFFF" }
        SortField.DATE_ADDED -> compareBy { it.dateAdded }
        SortField.LAST_READ -> compareBy { lastReadTimes[it.bookKey()] ?: 0L }
    }
    val sorted = books.sortedWith(comparator)
    return if (field.defaultDescending) sorted.reversed() else sorted
}

/** 책이 해당 폴더(하위 폴더 포함) 안에 있는지 */
private fun isInFolder(book: BookFile, folder: FolderLocation.Folder): Boolean =
    book.rootUri == folder.rootUri &&
        (folder.path.isEmpty() || book.folderPath == folder.path || book.folderPath.startsWith(folder.path + "/"))

/** 폴더 항목과 그 안(하위 포함)의 책들 — 정렬 키 계산용 */
private class FolderGroup(val entry: FolderEntry.Folder, val books: List<BookFile>)

/**
 * 폴더 보기 목록을 만든다: 하위 폴더(현재 정렬 기준) → 직속 책(현재 정렬 기준) 순서.
 * [books]는 필터(전체/즐겨찾기/숨김)가 이미 적용된 목록이며, 폴더는 책에서 도출하므로 책이 없는 폴더는 나오지 않는다.
 */
fun buildFolderEntries(
    books: List<BookFile>,
    location: FolderLocation,
    rootUris: List<String>,
    rootNames: Map<String, String>,
    sortField: SortField,
    lastReadTimes: Map<String, Long>,
    foldersFirst: Boolean = true  // false면 폴더와 책을 같은 기준으로 섞어 정렬
): List<FolderEntry> {
    val groups: List<FolderGroup>
    val directBooks: List<BookFile>
    when (val loc = normalizeLocation(location, rootUris)) {
        // 루트가 여러 개(또는 0개)인 최상위: 루트 폴더들 + 루트 정보가 없는 레거시 책
        FolderLocation.Top -> {
            groups = rootUris.mapNotNull { root ->
                val inRoot = books.filter { it.rootUri == root }
                if (inRoot.isEmpty()) null
                else FolderGroup(
                    FolderEntry.Folder(FolderLocation.Folder(root, ""), rootNames[root] ?: root, inRoot.size),
                    inRoot
                )
            }
            directBooks = books.filter { it.rootUri.isEmpty() }
        }
        is FolderLocation.Folder -> {
            val inLoc = books.filter { isInFolder(it, loc) }
            // 루트가 하나면 그 루트 내부가 최상위이므로, 루트 정보가 없는 레거시 책도 최상위 책으로 함께 보여준다
            val legacyBooks = if (loc.path.isEmpty() && rootUris.size == 1) books.filter { it.rootUri.isEmpty() } else emptyList()
            directBooks = inLoc.filter { it.folderPath == loc.path } + legacyBooks
            // 더 깊은 책들을 "바로 아래 하위 폴더" 단위로 묶는다
            val prefixLength = if (loc.path.isEmpty()) 0 else loc.path.length + 1
            groups = inLoc.filter { it.folderPath != loc.path }
                .groupBy { it.folderPath.substring(prefixLength).substringBefore('/') }
                .map { (name, groupBooks) ->
                    val childPath = if (loc.path.isEmpty()) name else "${loc.path}/$name"
                    FolderGroup(
                        FolderEntry.Folder(FolderLocation.Folder(loc.rootUri, childPath), name, groupBooks.size),
                        groupBooks
                    )
                }
        }
    }

    // 폴더 정렬: 제목·저자순은 이름 오름차순, 날짜·읽음순은 폴더 안 최댓값 내림차순(같으면 이름순)
    val byName = compareBy<FolderGroup> { it.entry.name.lowercase() }
    val folderComparator: Comparator<FolderGroup> = when (sortField) {
        SortField.TITLE, SortField.AUTHOR -> byName
        SortField.DATE_ADDED -> compareByDescending<FolderGroup> { g -> g.books.maxOf { it.dateAdded } }.then(byName)
        SortField.LAST_READ -> compareByDescending<FolderGroup> { g ->
            g.books.maxOf { lastReadTimes[it.bookKey()] ?: 0L }
        }.then(byName)
    }

    val sortedFolders = groups.sortedWith(folderComparator)
    val sortedBooks = sortBooks(directBooks, sortField, lastReadTimes)
    if (foldersFirst) return sortedFolders.map { it.entry } + sortedBooks.map { FolderEntry.Book(it) }

    // 섞어 정렬: 두 목록은 이미 같은 기준·방향으로 정렬돼 있으므로 병합한다.
    // 책끼리의 순서는 목록 보기와 그대로 유지되고, 값이 같으면 폴더가 먼저 온다.
    // 폴더의 비교값 — 제목·저자순은 폴더 이름(저자순에서도 이름을 저자처럼 비교), 날짜·읽음순은 폴더 안 최댓값
    fun folderSortKey(g: FolderGroup): Comparable<*> = when (sortField) {
        SortField.TITLE, SortField.AUTHOR -> g.entry.name.lowercase()
        SortField.DATE_ADDED -> g.books.maxOf { it.dateAdded }
        SortField.LAST_READ -> g.books.maxOf { lastReadTimes[it.bookKey()] ?: 0L }
    }
    // 책의 비교값 — sortBooks와 같은 기준
    fun bookSortKey(b: BookFile): Comparable<*> = when (sortField) {
        SortField.TITLE -> (b.metadata?.title ?: b.name).lowercase()
        SortField.AUTHOR -> b.metadata?.author?.lowercase() ?: "\uFFFF"
        SortField.DATE_ADDED -> b.dateAdded
        SortField.LAST_READ -> lastReadTimes[b.bookKey()] ?: 0L
    }

    val merged = mutableListOf<FolderEntry>()
    var fi = 0
    var bi = 0
    while (fi < sortedFolders.size || bi < sortedBooks.size) {
        val takeFolder = when {
            bi >= sortedBooks.size -> true
            fi >= sortedFolders.size -> false
            else -> {
                val cmp = compareValues(folderSortKey(sortedFolders[fi]), bookSortKey(sortedBooks[bi]))
                if (sortField.defaultDescending) cmp >= 0 else cmp <= 0
            }
        }
        if (takeFolder) merged += sortedFolders[fi++].entry else merged += FolderEntry.Book(sortedBooks[bi++])
    }
    return merged
}

/** 한 단계 위 위치. 더 올라갈 곳이 없으면 null (경로 줄·뒤로가기 표시 여부 판단에 사용) */
fun parentOf(location: FolderLocation, rootUris: List<String>): FolderLocation? = when (location) {
    FolderLocation.Top -> null
    is FolderLocation.Folder -> when {
        location.path.isNotEmpty() -> FolderLocation.Folder(location.rootUri, location.path.substringBeforeLast('/', ""))
        // 루트가 하나면 루트 자신이 최상위
        rootUris.size <= 1 -> null
        else -> FolderLocation.Top
    }
}

/**
 * 현재 위치에 보여줄 책이 없으면(재스캔으로 폴더가 사라졌거나 필터로 비었음)
 * 책이 남아 있는 가장 가까운 상위 위치로 보정한다. 결과는 항상 normalizeLocation을 거친다.
 */
fun resolveLocation(location: FolderLocation, books: List<BookFile>, rootUris: List<String>): FolderLocation {
    var loc = location
    while (loc is FolderLocation.Folder && books.none { isInFolder(it, loc as FolderLocation.Folder) }) {
        loc = parentOf(loc, rootUris) ?: return normalizeLocation(FolderLocation.Top, rootUris)
    }
    return normalizeLocation(loc, rootUris)
}

/** 경로 줄에 표시할 문자열 — "SF / 고전". 루트가 여러 개면 루트 이름부터 */
fun breadcrumb(location: FolderLocation, rootUris: List<String>, rootNames: Map<String, String>): String = when (location) {
    FolderLocation.Top -> ""
    is FolderLocation.Folder -> {
        val rootPart = if (rootUris.size > 1) listOf(rootNames[location.rootUri] ?: location.rootUri) else emptyList()
        (rootPart + location.path.split('/').filter { it.isNotEmpty() }).joinToString(" / ")
    }
}
