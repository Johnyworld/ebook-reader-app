package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test

class FolderBrowserTest {

    private val rootA = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
    private val rootB = "content://com.android.externalstorage.documents/tree/primary%3AComics%2FMarvel"

    /** 테스트용 책 — 제목은 파일 이름, 정렬 키는 인자로 지정 */
    private fun book(
        name: String,
        root: String = rootA,
        folder: String = "",
        dateAdded: Long = 0L,
        author: String? = null
    ) = BookFile(
        name = "$name.epub",
        path = "",
        contentUri = "$root/document/$name",
        extension = "epub",
        size = 1L,
        dateAdded = dateAdded,
        dateModified = dateAdded,
        metadata = BookMetadata(name, author, null, null, null, null),
        rootUri = root,
        folderPath = folder
    )

    // --- childFolderPath ---

    @Test
    fun `childFolderPath - 루트 바로 아래면 이름만`() {
        assertEquals("SF", childFolderPath("", "SF"))
    }

    @Test
    fun `childFolderPath - 하위면 슬래시로 연결`() {
        assertEquals("SF/고전", childFolderPath("SF", "고전"))
    }

    @Test
    fun `childFolderPath - 이름은 NFC로 정규화`() {
        val nfd = java.text.Normalizer.normalize("고전", java.text.Normalizer.Form.NFD)
        assertEquals("SF/고전", childFolderPath("SF", nfd))
    }

    // --- rootDisplayName ---

    @Test
    fun `rootDisplayName - 트리 문서 ID의 마지막 폴더 이름`() {
        assertEquals("Books", rootDisplayName(rootA))
        assertEquals("Marvel", rootDisplayName(rootB))
    }

    @Test
    fun `rootDisplayName - 저장소 최상위면 볼륨 이름`() {
        assertEquals("primary", rootDisplayName("content://com.android.externalstorage.documents/tree/primary%3A"))
    }

    @Test
    fun `rootDisplayName - 트리 URI가 아니면 원문 그대로`() {
        assertEquals("content://test", rootDisplayName("content://test"))
    }

    // --- rootUrisOf ---

    @Test
    fun `rootUrisOf - 빈 rootUri는 제외하고 중복 없이`() {
        val books = listOf(book("a"), book("b"), book("c", root = rootB), book("d", root = ""))
        assertEquals(listOf(rootA, rootB), rootUrisOf(books))
    }

    // --- locationOf ---

    @Test
    fun `locationOf - 책의 루트와 폴더`() {
        assertEquals(FolderLocation.Folder(rootA, "SF"), locationOf(book("a", folder = "SF")))
    }

    @Test
    fun `locationOf - rootUri가 없으면 Top`() {
        assertEquals(FolderLocation.Top, locationOf(book("a", root = "")))
    }

    // --- normalizeLocation ---

    @Test
    fun `normalizeLocation - 루트가 하나면 Top은 그 루트 내부`() {
        assertEquals(FolderLocation.Folder(rootA, ""), normalizeLocation(FolderLocation.Top, listOf(rootA)))
    }

    @Test
    fun `normalizeLocation - 루트가 여러 개면 Top 그대로`() {
        assertEquals(FolderLocation.Top, normalizeLocation(FolderLocation.Top, listOf(rootA, rootB)))
    }

    // --- sortBooks ---

    @Test
    fun `sortBooks - 제목 오름차순`() {
        val books = listOf(book("다"), book("가"), book("나"))
        assertEquals(listOf("가", "나", "다"), sortBooks(books, SortField.TITLE, emptyMap()).map { it.metadata?.title })
    }

    @Test
    fun `sortBooks - 추가일 내림차순`() {
        val books = listOf(book("a", dateAdded = 1), book("b", dateAdded = 3), book("c", dateAdded = 2))
        assertEquals(listOf("b", "c", "a"), sortBooks(books, SortField.DATE_ADDED, emptyMap()).map { it.metadata?.title })
    }

    @Test
    fun `sortBooks - 최근 읽음 내림차순, 읽은 적 없으면 뒤로`() {
        val a = book("a"); val b = book("b"); val c = book("c")
        val times = mapOf(a.bookKey() to 10L, c.bookKey() to 20L)
        assertEquals(listOf("c", "a", "b"), sortBooks(listOf(a, b, c), SortField.LAST_READ, times).map { it.metadata?.title })
    }

    // --- buildFolderEntries ---

    /** 결과를 읽기 쉬운 문자열 목록으로 — 폴더는 "[이름 N]", 책은 제목 */
    private fun List<FolderEntry>.labels(): List<String> = map {
        when (it) {
            is FolderEntry.Folder -> "[${it.name} ${it.bookCount}]"
            is FolderEntry.Book -> it.book.metadata?.title ?: it.book.name
        }
    }

    private fun entries(
        books: List<BookFile>,
        location: FolderLocation,
        field: SortField = SortField.TITLE,
        lastReadTimes: Map<String, Long> = emptyMap(),
        foldersFirst: Boolean = true
    ): List<FolderEntry> {
        val roots = rootUrisOf(books)
        return buildFolderEntries(books, location, roots, roots.associateWith { rootDisplayName(it) }, field, lastReadTimes, foldersFirst)
    }

    @Test
    fun `루트가 하나면 Top은 루트 내부 - 하위 폴더 먼저, 그다음 직속 책`() {
        val books = listOf(
            book("직속"),
            book("sf1", folder = "SF"),
            book("sf2", folder = "SF/고전"),
            book("역사1", folder = "역사")
        )
        assertEquals(listOf("[SF 2]", "[역사 1]", "직속"), entries(books, FolderLocation.Top).labels())
    }

    @Test
    fun `폴더 안 - 바로 아래 하위 폴더와 직속 책만`() {
        val books = listOf(
            book("sf1", folder = "SF"),
            book("고전1", folder = "SF/고전"),
            book("고전2", folder = "SF/고전/러시아"),
            book("역사1", folder = "역사")
        )
        val result = entries(books, FolderLocation.Folder(rootA, "SF"))
        assertEquals(listOf("[고전 2]", "sf1"), result.labels())
        assertEquals(FolderLocation.Folder(rootA, "SF/고전"), (result[0] as FolderEntry.Folder).location)
    }

    @Test
    fun `이름이 접두사인 형제 폴더는 섞이지 않는다`() {
        val books = listOf(book("a", folder = "SF"), book("b", folder = "SF2"))
        assertEquals(listOf("a"), entries(books, FolderLocation.Folder(rootA, "SF")).labels())
    }

    @Test
    fun `루트가 여러 개면 Top은 루트 목록 + rootUri 없는 책`() {
        val books = listOf(book("a"), book("b", root = rootB, folder = "X"), book("레거시", root = ""))
        val result = entries(books, FolderLocation.Top)
        assertEquals(listOf("[Books 1]", "[Marvel 1]", "레거시"), result.labels())
        assertEquals(FolderLocation.Folder(rootB, ""), (result[1] as FolderEntry.Folder).location)
    }

    @Test
    fun `책이 없는 폴더는 나오지 않는다 - 입력 책 목록이 이미 필터된 결과`() {
        val books = listOf(book("a", folder = "SF"))
        assertEquals(listOf("[SF 1]"), entries(books, FolderLocation.Top).labels())
    }

    @Test
    fun `정렬 제목순 - 폴더도 이름 오름차순`() {
        val books = listOf(book("x", folder = "다"), book("y", folder = "가"), book("z", folder = "나"))
        assertEquals(listOf("[가 1]", "[나 1]", "[다 1]"), entries(books, FolderLocation.Top, SortField.TITLE).labels())
    }

    @Test
    fun `정렬 저자순 - 폴더는 이름 오름차순`() {
        val books = listOf(book("x", folder = "나"), book("y", folder = "가"))
        assertEquals(listOf("[가 1]", "[나 1]"), entries(books, FolderLocation.Top, SortField.AUTHOR).labels())
    }

    @Test
    fun `정렬 추가일순 - 폴더 안 가장 최근 추가일 내림차순`() {
        val books = listOf(
            book("a1", folder = "A", dateAdded = 5),
            book("b1", folder = "B", dateAdded = 1),
            book("b2", folder = "B/깊은", dateAdded = 9)
        )
        assertEquals(listOf("[B 2]", "[A 1]"), entries(books, FolderLocation.Top, SortField.DATE_ADDED).labels())
    }

    @Test
    fun `정렬 최근 읽음순 - 폴더 안 가장 최근 읽은 시각 내림차순, 같으면 이름순`() {
        val a = book("a", folder = "A"); val b = book("b", folder = "B"); val c = book("c", folder = "C")
        val times = mapOf(b.bookKey() to 100L)
        assertEquals(
            listOf("[B 1]", "[A 1]", "[C 1]"),
            entries(listOf(a, b, c), FolderLocation.Top, SortField.LAST_READ, times).labels()
        )
    }

    // --- parentOf ---

    @Test
    fun `parentOf - Top의 상위는 없음`() {
        assertNull(parentOf(FolderLocation.Top, listOf(rootA, rootB)))
    }

    @Test
    fun `parentOf - 루트가 하나면 루트의 상위는 없음`() {
        assertNull(parentOf(FolderLocation.Folder(rootA, ""), listOf(rootA)))
    }

    @Test
    fun `parentOf - 루트가 여러 개면 루트의 상위는 Top`() {
        assertEquals(FolderLocation.Top, parentOf(FolderLocation.Folder(rootA, ""), listOf(rootA, rootB)))
    }

    @Test
    fun `parentOf - 하위 폴더는 한 단계 위`() {
        assertEquals(FolderLocation.Folder(rootA, "SF"), parentOf(FolderLocation.Folder(rootA, "SF/고전"), listOf(rootA)))
        assertEquals(FolderLocation.Folder(rootA, ""), parentOf(FolderLocation.Folder(rootA, "SF"), listOf(rootA)))
    }

    // --- resolveLocation ---

    @Test
    fun `resolveLocation - 책이 있으면 그대로`() {
        val books = listOf(book("a", folder = "SF/고전"))
        val loc = FolderLocation.Folder(rootA, "SF")
        assertEquals(loc, resolveLocation(loc, books, listOf(rootA)))
    }

    @Test
    fun `resolveLocation - 비었으면 책이 남은 가장 가까운 상위로`() {
        val books = listOf(book("a", folder = "SF"))
        assertEquals(
            FolderLocation.Folder(rootA, "SF"),
            resolveLocation(FolderLocation.Folder(rootA, "SF/고전/러시아"), books, listOf(rootA))
        )
    }

    @Test
    fun `resolveLocation - 상위가 없을 때까지 비었으면 Top`() {
        // 루트가 하나면 최상위는 항상 루트 내부로 정규화된다
        assertEquals(FolderLocation.Folder(rootA, ""), resolveLocation(FolderLocation.Folder(rootA, "SF"), emptyList(), listOf(rootA)))
    }

    @Test
    fun `resolveLocation - 루트가 여러 개이고 비었으면 Top`() {
        assertEquals(FolderLocation.Top, resolveLocation(FolderLocation.Folder(rootB, "X"), emptyList(), listOf(rootA, rootB)))
    }

    // --- 보강 엣지 케이스 ---

    @Test
    fun `루트가 하나여도 rootUri 없는 책은 최상위에 책으로 표시`() {
        val books = listOf(book("a", root = rootA), book("레거시", root = ""))
        val result = buildFolderEntries(books, FolderLocation.Top, listOf(rootA), mapOf(rootA to "Books"), SortField.TITLE, emptyMap())
        assertEquals(listOf("a", "레거시"), result.labels())
    }

    @Test
    fun `루트가 없으면 레거시 책만 Top에 책으로 표시`() {
        val books = listOf(book("b", root = ""), book("a", root = ""))
        val result = buildFolderEntries(books, FolderLocation.Top, emptyList(), emptyMap(), SortField.TITLE, emptyMap())
        assertEquals(listOf("a", "b"), result.labels())
    }

    @Test
    fun `루트가 둘이어도 필터된 책이 한 루트에만 있으면 그 루트만 표시`() {
        val books = listOf(book("a", root = rootA), book("b", root = rootA))
        val result = buildFolderEntries(
            books, FolderLocation.Top, listOf(rootA, rootB),
            mapOf(rootA to "Books", rootB to "Marvel"), SortField.TITLE, emptyMap()
        )
        assertEquals(listOf("[Books 2]"), result.labels())
    }

    @Test
    fun `루트 목록의 책 수는 하위 폴더 책을 포함`() {
        val books = listOf(book("a", root = rootA), book("b", root = rootB, folder = "X/Y"))
        assertEquals(listOf("[Books 1]", "[Marvel 1]"), entries(books, FolderLocation.Top).labels())
    }

    @Test
    fun `rootDisplayName - 한글 이름`() {
        assertEquals("책", rootDisplayName("content://com.android.externalstorage.documents/tree/primary%3A%EC%B1%85"))
    }

    @Test
    fun `rootDisplayName - 플러스 문자는 보존`() {
        assertEquals("C++", rootDisplayName("content://com.android.externalstorage.documents/tree/primary%3AC%2B%2B"))
        assertEquals("A+B", rootDisplayName("content://com.android.externalstorage.documents/tree/primary%3AA+B"))
    }

    @Test
    fun `정렬 추가일순 - 날짜가 같으면 폴더 이름 오름차순`() {
        val books = listOf(book("x", folder = "나", dateAdded = 5), book("y", folder = "가", dateAdded = 5))
        assertEquals(listOf("[가 1]", "[나 1]"), entries(books, FolderLocation.Top, SortField.DATE_ADDED).labels())
    }

    // --- breadcrumb ---

    @Test
    fun `breadcrumb - 루트가 하나면 루트 이름 없이 폴더 경로만`() {
        assertEquals("SF / 고전", breadcrumb(FolderLocation.Folder(rootA, "SF/고전"), listOf(rootA), mapOf(rootA to "Books")))
    }

    @Test
    fun `breadcrumb - 루트가 여러 개면 루트 이름부터`() {
        val roots = listOf(rootA, rootB)
        val names = mapOf(rootA to "Books", rootB to "Marvel")
        assertEquals("Marvel / X", breadcrumb(FolderLocation.Folder(rootB, "X"), roots, names))
        assertEquals("Books", breadcrumb(FolderLocation.Folder(rootA, ""), roots, names))
    }

    @Test
    fun `breadcrumb - Top은 빈 문자열`() {
        assertEquals("", breadcrumb(FolderLocation.Top, listOf(rootA), mapOf(rootA to "Books")))
    }

    // --- 폴더를 맨 위로 정렬 해제 (폴더와 책 섞어 정렬) ---

    @Test
    fun `섞어 정렬 제목순 - 폴더 이름과 책 제목을 함께 오름차순`() {
        val books = listOf(book("하하"), book("가나"), book("x", folder = "다다"))
        assertEquals(
            listOf("가나", "[다다 1]", "하하"),
            entries(books, FolderLocation.Top, SortField.TITLE, foldersFirst = false).labels()
        )
    }

    @Test
    fun `섞어 정렬 저자순 - 폴더 이름을 저자처럼 비교, 저자 없는 책은 맨 뒤`() {
        val books = listOf(
            book("b1", author = "하"),
            book("b2", author = "가"),
            book("b3"),
            book("x", folder = "나")
        )
        assertEquals(
            listOf("b2", "[나 1]", "b1", "b3"),
            entries(books, FolderLocation.Top, SortField.AUTHOR, foldersFirst = false).labels()
        )
    }

    @Test
    fun `섞어 정렬 추가일순 - 폴더 안 최신 추가일과 책 추가일을 함께 내림차순`() {
        val books = listOf(book("a", dateAdded = 5), book("c", dateAdded = 1), book("x", folder = "F", dateAdded = 3))
        assertEquals(
            listOf("a", "[F 1]", "c"),
            entries(books, FolderLocation.Top, SortField.DATE_ADDED, foldersFirst = false).labels()
        )
    }

    @Test
    fun `섞어 정렬 최근 읽음순 - 폴더 안 최근 읽은 시각과 책 읽은 시각을 함께 내림차순`() {
        val a = book("a"); val c = book("c"); val inF = book("x", folder = "F")
        val times = mapOf(a.bookKey() to 30L, inF.bookKey() to 20L, c.bookKey() to 10L)
        assertEquals(
            listOf("a", "[F 1]", "c"),
            entries(listOf(c, inF, a), FolderLocation.Top, SortField.LAST_READ, times, foldersFirst = false).labels()
        )
    }

    @Test
    fun `섞어 정렬 - 값이 같으면 폴더가 먼저`() {
        val books = listOf(book("같음"), book("x", folder = "같음"))
        assertEquals(
            listOf("[같음 1]", "같음"),
            entries(books, FolderLocation.Top, SortField.TITLE, foldersFirst = false).labels()
        )
    }
}
