# 홈 폴더 보기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈(도서 리스트)에서 SAF로 선택한 폴더의 실제 디렉토리 구조를 그대로 탐색하는 "폴더 보기"를 추가한다.

**Architecture:** 스캔 시 각 책의 루트 URI와 상대 폴더 경로를 `BookFile`/`book_catalog`에 저장한다. 폴더 목록·정렬·상위 이동 등은 순수 함수 파일 `FolderBrowser.kt`에서 계산하고(TDD), `AllBooksScreen`은 그 결과(`List<FolderEntry>`)를 기존 페이지네이션으로 그린다.

**Tech Stack:** Kotlin, Jetpack Compose(BOM 2025.02 = Compose 1.7), Room(DB v19 → v20), SAF `DocumentFile`, JUnit4 + Robolectric(단위), Compose UI Test(인스트루먼트)

**Spec:** `docs/superpowers/specs/2026-10-30-home-folder-view-design.md`

---

## 프로젝트 규칙 (CLAUDE.md) — 반드시 지킬 것

- 답변·주석은 한국어. 코드 의미를 알기 쉽게 주석을 단다.
- **태스크마다 커밋하지 않는다.** 기능이 완전히 동작하는 것을 확인한 뒤, 코드 리뷰 에이전트 리뷰 → 인스트루먼트 테스트 실행 여부를 사용자에게 질문 → 커밋 순서로 마지막 태스크에서 한 번만 커밋한다.
- 커밋 메시지에 Co-Authored-By를 넣지 않는다.
- e-ink 규칙: 애니메이션 없음, 배경은 흰색만, 글자는 검정(필요 시 회색).
- 인접 코드를 "개선"하지 않는다. 변경은 이 기능에 필요한 줄만.

## 명령어

모든 명령은 워크트리 루트(`/Users/rotein/dev/ebook-reader-app/.claude/worktrees/font-weight-setting-04dad5`)에서 실행한다.

- 단위 테스트 하나: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
- 단위 테스트 전체: `./gradlew :app:testDebugUnitTest`
- 컴파일 확인: `./gradlew :app:compileDebugKotlin`
- 인스트루먼트 테스트 하나(기기 연결 필요): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rotein.ebookreader.AllBooksFolderViewTest`

## 파일 구조

| 파일 | 작업 | 책임 |
|---|---|---|
| `app/src/main/java/com/rotein/ebookreader/FolderBrowser.kt` | 생성 | `FolderLocation`/`FolderEntry` 타입과 폴더 보기 계산용 순수 함수 전부 |
| `app/src/test/java/com/rotein/ebookreader/FolderBrowserTest.kt` | 생성 | 위 순수 함수 단위 테스트 |
| `app/src/main/java/com/rotein/ebookreader/BookFile.kt` | 수정 | `rootUri`, `folderPath` 필드 추가 |
| `app/src/main/java/com/rotein/ebookreader/BookCatalog.kt` | 수정 | 엔티티 컬럼 2개 + 매핑 |
| `app/src/main/java/com/rotein/ebookreader/BookDatabase.kt` | 수정 | `MIGRATION_19_20`, version 20 |
| `app/src/test/java/com/rotein/ebookreader/BookCatalogTest.kt` | 수정 | 새 필드 왕복 테스트 |
| `app/src/test/java/com/rotein/ebookreader/migration/MigrationTest.kt` | 수정 | 19 → 20 테스트 |
| `app/src/main/java/com/rotein/ebookreader/FileScanner.kt` | 수정 | 재귀 중 경로 전달, diff 스캔 kept 항목 경로 갱신 |
| `app/src/main/java/com/rotein/ebookreader/SortPreference.kt` | 수정 | `ViewMode` enum + `ViewModeStore` |
| `app/src/test/java/com/rotein/ebookreader/SortPreferenceTest.kt` | 수정 | `ViewMode` 매핑 테스트 |
| `app/src/main/res/values*/strings.xml` (5개 로케일) | 수정 | `view_list`, `view_folder`, `folder_book_count`, `go_up` |
| `app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt` | 수정 | 보기 드롭다운, 경로 줄, `FolderItem`, entries 기반 페이지네이션, 리더 복귀 |
| `app/src/androidTest/java/com/rotein/ebookreader/AllBooksFolderViewTest.kt` | 생성 | 폴더 보기 UI 인스트루먼트 테스트 |

## 스펙과 다른 점 (구현 제약)

- 경로 줄의 "앞쪽 말줄임"(`TextOverflow.StartEllipsis`)은 Compose 1.8부터 지원된다. 이 프로젝트는 1.7이므로 **끝쪽 말줄임(`TextOverflow.Ellipsis`)** 을 쓴다. 스펙 문서의 해당 문장도 Task 9에서 함께 고친다.
- 루트 목록은 `FolderUriStore.load()`가 아니라 **책들의 `rootUri`에서 도출**한다(`rootUrisOf`). 인스트루먼트 테스트의 가짜 폴더 URI는 persistable 권한이 없어 `load()`에서 걸러지기 때문이고, 책이 하나도 없는 루트를 보여줄 이유도 없다.

---

### Task 0: 작업 브랜치 준비

`claude/page-flip-quad`는 이미 main에 머지됐다(#33). main에서 새 브랜치를 딴다. 아직 커밋하지 않은 스펙·계획 문서(untracked)는 브랜치 전환 후에도 그대로 남는다.

- [ ] **Step 1: 작업 트리 상태 확인**

Run: `git status --short`
Expected: `?? docs/superpowers/` 외에 변경 없음

- [ ] **Step 2: 최신 main에서 브랜치 생성**

```bash
git fetch origin
git switch -c claude/home-folder-view origin/main
```

Expected: `Switched to a new branch 'claude/home-folder-view'`

---

### Task 1: BookFile·카탈로그에 폴더 필드 추가 + DB 마이그레이션 19 → 20

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookFile.kt`
- Modify: `app/src/main/java/com/rotein/ebookreader/BookCatalog.kt`
- Modify: `app/src/main/java/com/rotein/ebookreader/BookDatabase.kt` (MIGRATION_18_19 아래, `ALL_MIGRATIONS`, `@Database`)
- Test: `app/src/test/java/com/rotein/ebookreader/BookCatalogTest.kt`
- Test: `app/src/test/java/com/rotein/ebookreader/migration/MigrationTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성 — BookCatalogTest**

`BookCatalogTest` 클래스 맨 끝(마지막 `}` 앞)에 추가:

```kotlin
    @Test
    fun `폴더 정보 - 엔트리로 변환 후 복원하면 rootUri와 folderPath가 유지된다`() {
        val book = safBook.copy(
            rootUri = "content://com.android.externalstorage.documents/tree/primary%3ABooks",
            folderPath = "SF/고전"
        )
        val entry = book.toCatalogEntry()
        assertEquals(book.rootUri, entry.rootUri)
        assertEquals("SF/고전", entry.folderPath)
        assertEquals(book, entry.toBookFile())
    }
```

- [ ] **Step 2: 실패하는 테스트 작성 — MigrationTest**

`MigrationTest`의 import에 `import com.rotein.ebookreader.MIGRATION_19_20` 추가. `migration 18 to 19` 테스트 바로 아래에 추가:

```kotlin
    @Test
    fun `migration 19 to 20 - book_catalog에 폴더 컬럼 추가 및 기존 데이터 보존`() {
        val db = createDatabase(19) { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `book_catalog` (`bookKey` TEXT NOT NULL, `name` TEXT NOT NULL, `path` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `extension` TEXT NOT NULL, `size` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL, `hasMetadata` INTEGER NOT NULL, `title` TEXT, `author` TEXT, `language` TEXT, `publisher` TEXT, `publishedDate` TEXT, `description` TEXT, PRIMARY KEY(`bookKey`))")
        }
        db.execSQL("INSERT INTO book_catalog VALUES ('content://a', 'a.epub', '', 'content://a', 'epub', 100, 1, 2, 1, '제목', NULL, NULL, NULL, NULL, NULL)")

        MIGRATION_19_20.migrate(db)

        val cursor = db.query("SELECT * FROM book_catalog")
        assertTrue(cursor.moveToFirst())
        assertEquals("제목", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        // 기존 항목은 폴더를 모르므로 빈 문자열 → 다음 diff 스캔에서 채워진다
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("rootUri")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("folderPath")))
        cursor.close()
        db.close()
    }
```

- [ ] **Step 3: 테스트가 컴파일 실패하는지 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.BookCatalogTest"`
Expected: FAIL — `Unresolved reference: rootUri` / `MIGRATION_19_20`

- [ ] **Step 4: BookFile에 필드 추가**

`BookFile.kt`의 `data class BookFile`을 다음으로 교체(필드는 맨 뒤에 추가해 기존 생성자 호출을 깨지 않는다):

```kotlin
data class BookFile(
    val name: String,
    val path: String,
    val contentUri: String = "",  // SAF content URI 문자열
    val extension: String,
    val size: Long,
    val dateAdded: Long,      // MediaStore DATE_ADDED (초 단위)
    val dateModified: Long,   // MediaStore DATE_MODIFIED (초 단위) — 읽은 순 proxy
    val metadata: BookMetadata? = null,
    val rootUri: String = "",     // 책이 속한 루트(사용자가 선택한 SAF 트리 폴더) URI. 레거시 경로 책은 빈 문자열
    val folderPath: String = ""   // 루트 기준 상대 폴더 경로('/' 구분). 루트 바로 아래 책은 빈 문자열
)
```

- [ ] **Step 5: BookCatalogEntry 컬럼과 매핑 추가**

`BookCatalog.kt`에서 `BookCatalogEntry`의 `val description: String?` 뒤에 추가:

```kotlin
    val description: String?,
    val rootUri: String = "",     // BookFile.rootUri
    val folderPath: String = ""   // BookFile.folderPath
)
```

`toCatalogEntry()`의 `description = metadata?.description` 뒤에 `,` 를 붙이고 추가:

```kotlin
    description = metadata?.description,
    rootUri = rootUri,
    folderPath = folderPath
)
```

`toBookFile()`의 `metadata = ...` 블록 뒤에 `,` 를 붙이고 추가:

```kotlin
    metadata = if (hasMetadata) {
        BookMetadata(title, author, language, publisher, publishedDate, description)
    } else null,
    rootUri = rootUri,
    folderPath = folderPath
)
```

- [ ] **Step 6: 마이그레이션 추가**

`BookDatabase.kt`에서 `MIGRATION_18_19` 정의 바로 아래에 추가:

```kotlin
internal val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 홈 폴더 보기를 위한 루트 URI·상대 폴더 경로 컬럼
        database.execSQL("ALTER TABLE book_catalog ADD COLUMN rootUri TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE book_catalog ADD COLUMN folderPath TEXT NOT NULL DEFAULT ''")
    }
}
```

`ALL_MIGRATIONS` 마지막 줄을 `MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20` 로 바꾸고, `@Database(... version = 19)`를 `version = 20`으로 바꾼다.

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.BookCatalogTest" --tests "com.rotein.ebookreader.migration.MigrationTest"`
Expected: PASS. 특히 기존 `migration 17 to 18 - ... Room 스키마 검증` 테스트는 이제 v20까지 올라가며 Room 스키마 검증을 하므로, 엔티티와 마이그레이션 SQL이 어긋나면 여기서 실패한다.

---

### Task 2: FolderBrowser — 타입, 경로 헬퍼, 루트 이름

**Files:**
- Create: `app/src/main/java/com/rotein/ebookreader/FolderBrowser.kt`
- Test: `app/src/test/java/com/rotein/ebookreader/FolderBrowserTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`FolderBrowserTest.kt` 생성:

```kotlin
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
        dateAdded: Long = 0L
    ) = BookFile(
        name = "$name.epub",
        path = "",
        contentUri = "$root/document/$name",
        extension = "epub",
        size = 1L,
        dateAdded = dateAdded,
        dateModified = dateAdded,
        metadata = BookMetadata(name, null, null, null, null, null),
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
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: FAIL — `Unresolved reference: childFolderPath` 등

- [ ] **Step 3: 구현**

`FolderBrowser.kt` 생성:

```kotlin
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
    return path.substringAfterLast('/').ifEmpty { docId.substringBefore(':') }
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: PASS (11 tests)

---

### Task 3: FolderBrowser — 책 정렬 함수 분리 (`sortBooks`)

`AllBooksScreen`의 책 정렬 로직을 폴더 보기에서도 그대로 써야 하므로 순수 함수로 옮긴다. 동작은 기존과 완전히 같게 유지한다.

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/FolderBrowser.kt`
- Test: `app/src/test/java/com/rotein/ebookreader/FolderBrowserTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`FolderBrowserTest` 끝에 추가:

```kotlin
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: FAIL — `Unresolved reference: sortBooks`

- [ ] **Step 3: 구현**

`FolderBrowser.kt` 끝에 추가 (`AllBooksScreen.processedBooks`의 2) 정렬 단계와 같은 코드):

```kotlin
/** 책 정렬 — 방향은 필드별 기본값(SortField.defaultDescending)을 따른다 */
fun sortBooks(books: List<BookFile>, field: SortField, lastReadTimes: Map<String, Long>): List<BookFile> {
    val comparator: Comparator<BookFile> = when (field) {
        SortField.TITLE -> compareBy { (it.metadata?.title ?: it.name).lowercase() }
        SortField.AUTHOR -> compareBy { it.metadata?.author?.lowercase() ?: "￿" }
        SortField.DATE_ADDED -> compareBy { it.dateAdded }
        SortField.LAST_READ -> compareBy { lastReadTimes[it.bookKey()] ?: 0L }
    }
    val sorted = books.sortedWith(comparator)
    return if (field.defaultDescending) sorted.reversed() else sorted
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: PASS

---

### Task 4: FolderBrowser — 폴더 목록 계산 (`buildFolderEntries`)

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/FolderBrowser.kt`
- Test: `app/src/test/java/com/rotein/ebookreader/FolderBrowserTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`FolderBrowserTest` 끝에 추가:

```kotlin
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
        lastReadTimes: Map<String, Long> = emptyMap()
    ): List<FolderEntry> {
        val roots = rootUrisOf(books)
        return buildFolderEntries(books, location, roots, roots.associateWith { rootDisplayName(it) }, field, lastReadTimes)
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: FAIL — `Unresolved reference: buildFolderEntries`

- [ ] **Step 3: 구현**

`FolderBrowser.kt` 끝에 추가:

```kotlin
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
    lastReadTimes: Map<String, Long>
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
            directBooks = inLoc.filter { it.folderPath == loc.path }
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

    return groups.sortedWith(folderComparator).map { it.entry } +
        sortBooks(directBooks, sortField, lastReadTimes).map { FolderEntry.Book(it) }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: PASS

---

### Task 5: FolderBrowser — 상위 이동, 위치 보정, 경로 문자열

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/FolderBrowser.kt`
- Test: `app/src/test/java/com/rotein/ebookreader/FolderBrowserTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`FolderBrowserTest` 끝에 추가:

```kotlin
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
        assertEquals(FolderLocation.Top, resolveLocation(FolderLocation.Folder(rootA, "SF"), emptyList(), listOf(rootA)))
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: FAIL — `Unresolved reference: parentOf`

- [ ] **Step 3: 구현**

`FolderBrowser.kt` 끝에 추가:

```kotlin
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
 * 책이 남아 있는 가장 가까운 상위 위치로 보정한다.
 */
fun resolveLocation(location: FolderLocation, books: List<BookFile>, rootUris: List<String>): FolderLocation {
    var loc = location
    while (loc is FolderLocation.Folder && books.none { isInFolder(it, loc as FolderLocation.Folder) }) {
        loc = parentOf(loc, rootUris) ?: return FolderLocation.Top
    }
    return loc
}

/** 경로 줄에 표시할 문자열 — "SF / 고전". 루트가 여러 개면 루트 이름부터 */
fun breadcrumb(location: FolderLocation, rootUris: List<String>, rootNames: Map<String, String>): String = when (location) {
    FolderLocation.Top -> ""
    is FolderLocation.Folder -> {
        val rootPart = if (rootUris.size > 1) listOf(rootNames[location.rootUri] ?: location.rootUri) else emptyList()
        (rootPart + location.path.split('/').filter { it.isNotEmpty() }).joinToString(" / ")
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FolderBrowserTest"`
Expected: PASS (전체 FolderBrowserTest)

---

### Task 6: FileScanner — 스캔 시 폴더 경로 기록

`FileScanner`는 `DocumentFile`에 의존해 단위 테스트가 없다. 경로 조합은 Task 2의 `childFolderPath`(테스트됨)를 쓰고, 동작은 Task 10의 실기기 확인으로 검증한다.

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/FileScanner.kt`

- [ ] **Step 1: FileSnapshot에 필드 추가**

```kotlin
    /** SAF content URI에서 경량 파일 정보 */
    private data class FileSnapshot(
        val uri: String,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val rootUri: String,     // 속한 루트 URI
        val folderPath: String   // 루트 기준 상대 폴더 경로
    )
```

- [ ] **Step 2: scanBooks / refreshBooks에서 루트 URI와 빈 경로로 시작**

`scanBooks`의 호출을 다음으로:

```kotlin
            scanDirectory(context, tree, folderUri.toString(), "", books, 0)
```

`refreshBooks` 1)의 호출을 다음으로:

```kotlin
            scanDirectoryLightweight(tree, folderUri.toString(), "", snapshots, 0)
```

- [ ] **Step 3: refreshBooks — 유지 항목의 폴더 정보를 스냅샷으로 갱신, 추가 항목에 폴더 정보 기록**

3)의 `kept.add(book)`을 다음으로:

```kotlin
                // 폴더 정보는 스냅샷 기준으로 갱신 — DB v20 이전 캐시(빈 경로)도 재스캔 없이 채워진다
                kept.add(book.copy(rootUri = snapshot.rootUri, folderPath = snapshot.folderPath))
```

4)의 `BookFile(...)` 생성에서 `metadata = metadata` 뒤에 추가:

```kotlin
                metadata = metadata,
                rootUri = snapshot.rootUri,
                folderPath = snapshot.folderPath
            )
```

- [ ] **Step 4: scanDirectory — 경로를 넘기며 재귀**

`scanDirectory` 전체를 다음으로 교체:

```kotlin
    /** DocumentFile 기반 재귀 디렉토리 탐색 — 전체 스캔용. folderPath는 루트 기준 현재 폴더 경로 */
    private fun scanDirectory(
        context: Context,
        dir: DocumentFile,
        rootUri: String,
        folderPath: String,
        result: MutableList<BookFile>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectory(context, file, rootUri, childFolderPath(folderPath, name), result, depth + 1)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    val uri = file.uri
                    val metadata = extractMetadataFromUri(context, uri, name, ext)
                    val normalizedName = Normalizer.normalize(name, Normalizer.Form.NFC)
                    result.add(
                        BookFile(
                            name = normalizedName,
                            path = "",
                            contentUri = uri.toString(),
                            extension = ext,
                            size = file.length(),
                            dateAdded = file.lastModified() / 1000,
                            dateModified = file.lastModified() / 1000,
                            metadata = metadata,
                            rootUri = rootUri,
                            folderPath = folderPath
                        )
                    )
                }
            }
        }
    }
```

- [ ] **Step 5: scanDirectoryLightweight — 경로를 넘기며 재귀**

`scanDirectoryLightweight` 전체를 다음으로 교체:

```kotlin
    /** 경량 디렉토리 탐색 — diff 스캔용. folderPath는 루트 기준 현재 폴더 경로 */
    private fun scanDirectoryLightweight(
        dir: DocumentFile,
        rootUri: String,
        folderPath: String,
        result: MutableList<FileSnapshot>,
        depth: Int
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                scanDirectoryLightweight(file, rootUri, childFolderPath(folderPath, name), result, depth + 1)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(FileSnapshot(file.uri.toString(), name, file.length(), file.lastModified(), rootUri, folderPath))
                }
            }
        }
    }
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 7: 보기 모드(ViewMode) 설정과 문자열 리소스

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/SortPreference.kt` (`FilterMode` 아래)
- Modify: `app/src/main/res/values/strings.xml`, `values-ko`, `values-ja`, `values-zh`, `values-es`
- Test: `app/src/test/java/com/rotein/ebookreader/SortPreferenceTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`SortPreferenceTest`의 `FilterMode - labelRes 매핑 확인` 테스트 아래에 추가:

```kotlin
    // --- ViewMode ---

    @Test
    fun `ViewMode - 목록과 폴더 두 가지, labelRes 매핑 확인`() {
        assertEquals(listOf(ViewMode.LIST, ViewMode.FOLDER), ViewMode.entries.toList())
        assertEquals(R.string.view_list, ViewMode.LIST.labelRes)
        assertEquals(R.string.view_folder, ViewMode.FOLDER.labelRes)
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.SortPreferenceTest"`
Expected: FAIL — `Unresolved reference: ViewMode`

- [ ] **Step 3: 문자열 추가**

각 `strings.xml`에서 `filter_all` 줄 바로 위에 다음 4줄을 추가한다.

`values/strings.xml`:
```xml
    <string name="view_list">List</string>
    <string name="view_folder">Folders</string>
    <string name="folder_book_count">%1$d books</string>
    <string name="go_up">Up</string>
```

`values-ko/strings.xml`:
```xml
    <string name="view_list">목록</string>
    <string name="view_folder">폴더</string>
    <string name="folder_book_count">%1$d권</string>
    <string name="go_up">상위 폴더</string>
```

`values-ja/strings.xml`:
```xml
    <string name="view_list">リスト</string>
    <string name="view_folder">フォルダ</string>
    <string name="folder_book_count">%1$d冊</string>
    <string name="go_up">上へ</string>
```

`values-zh/strings.xml`:
```xml
    <string name="view_list">列表</string>
    <string name="view_folder">文件夹</string>
    <string name="folder_book_count">%1$d本</string>
    <string name="go_up">上一级</string>
```

`values-es/strings.xml`:
```xml
    <string name="view_list">Lista</string>
    <string name="view_folder">Carpetas</string>
    <string name="folder_book_count">%1$d libros</string>
    <string name="go_up">Subir</string>
```

- [ ] **Step 4: ViewMode와 저장소 추가**

`SortPreference.kt`에서 `enum class FilterMode { ... }` 블록 바로 아래에 추가:

```kotlin
/** 홈 목록 보기 방식: 전체 평면 목록 / 실제 폴더 구조 */
enum class ViewMode(@StringRes val labelRes: Int) {
    LIST(R.string.view_list),
    FOLDER(R.string.view_folder)
}

/** 보기 방식을 기기에 저장 — 다음 실행에도 유지 (폴더 위치는 저장하지 않음) */
object ViewModeStore {
    private const val PREF_NAME = "view_mode_pref"
    private const val KEY_MODE = "view_mode"

    fun load(context: Context): ViewMode {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return ViewMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ViewMode.LIST
    }

    fun save(context: Context, mode: ViewMode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.SortPreferenceTest"`
Expected: PASS

---

### Task 8: AllBooksScreen — 폴더 보기 UI

UI 코드라 구현 후 Task 9의 인스트루먼트 테스트로 검증한다. 아래 단계는 `AllBooksScreen.kt`만 수정한다.

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt`

- [ ] **Step 1: import 추가**

기존 import 블록에 추가(알파벳 정렬은 기존 파일도 지키지 않으므로 근처에 추가하면 된다):

```kotlin
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Folder
```

- [ ] **Step 2: 상태 추가 — 보기 모드와 현재 폴더 위치**

`var lastReadTimes by remember { ... }` 줄 아래에 추가:

```kotlin
    var viewMode by remember { mutableStateOf(ViewModeStore.load(context)) }
    // 폴더 보기에서 현재 보고 있는 위치 — 메모리에만 두어 앱 재시작 시 최상위에서 시작한다
    var location by remember { mutableStateOf<FolderLocation>(FolderLocation.Top) }
```

`LaunchedEffect(sortPref) { SortPreferenceStore.save(context, sortPref) }` 블록 아래에 추가:

```kotlin
    // 보기 방식 변경 시 기기에 저장
    LaunchedEffect(viewMode) {
        ViewModeStore.save(context, viewMode)
    }
```

- [ ] **Step 3: processedBooks를 필터 단계와 검색·정렬 단계로 분리하고 entries 계산 추가**

기존 `val processedBooks = remember(...) { ... }` 블록 전체(135~161행)를 다음으로 교체:

```kotlin
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
    LaunchedEffect(visibleBooks, rootUris) {
        location = resolveLocation(location, visibleBooks, rootUris)
    }

    // 화면에 그릴 항목 — 목록 보기는 책만, 폴더 보기는 하위 폴더 + 직속 책
    val entries: List<FolderEntry> = remember(inFolderView, processedBooks, visibleBooks, location, rootUris, rootNames, sortPref, lastReadTimes) {
        if (inFolderView) buildFolderEntries(visibleBooks, location, rootUris, rootNames, sortPref.field, lastReadTimes)
        else processedBooks.map { FolderEntry.Book(it) }
    }
    // 한 단계 위 위치 — null이면 경로 줄과 뒤로가기 처리를 하지 않는다
    val parentLocation = if (inFolderView) parentOf(location, rootUris) else null
```

- [ ] **Step 4: TopBar에 보기 모드 전달**

`TopBar(` 호출 인자에 `filterMode = filterMode,` 아래로 `viewMode = viewMode,` 를, `onFilterChange = { filterMode = it },` 아래로 다음을 추가:

```kotlin
            onViewModeChange = { viewMode = it },
```

`private fun TopBar(` 파라미터에 `filterMode: FilterMode,` 아래 `viewMode: ViewMode,` 를, `onFilterChange: (FilterMode) -> Unit,` 아래 `onViewModeChange: (ViewMode) -> Unit,` 를 추가한다.

TopBar 본문의 `// 필터 드롭다운` 바로 위에 추가:

```kotlin
            // 보기 방식 드롭다운 (목록 / 폴더)
            EreaderDropdownMenu(
                items = ViewMode.entries.toList(),
                selectedItem = viewMode,
                onSelect = { onViewModeChange(it) },
                label = { stringResource(it.labelRes) },
            )
```

- [ ] **Step 5: 페이지 상태 위치 이동 + 경로 줄 + 뒤로가기**

`TopBar(...)` 호출 바로 아래에 있던 다음 블록을

```kotlin
        var currentPage by remember { mutableIntStateOf(0) }
        var targetPage by remember { mutableIntStateOf(0) }

        // 필터/검색 변경 시 페이지 초기화
        LaunchedEffect(processedBooks.size) {
            currentPage = 0
            targetPage = 0
        }
```

다음으로 교체:

```kotlin
        var currentPage by remember { mutableIntStateOf(0) }
        var targetPage by remember { mutableIntStateOf(0) }

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
                path = breadcrumb(location, rootUris, rootNames),
                onUp = { moveTo(parentLocation) }
            )
        }
        BackHandler(enabled = parentLocation != null) {
            parentLocation?.let { moveTo(it) }
        }
```

- [ ] **Step 6: 빈 목록 판단과 페이지 계산을 entries 기준으로**

`processedBooks.isEmpty() -> {` 를 `entries.isEmpty() -> {` 로 바꾼다.

`BoxWithConstraints` 안의 다음 두 줄과 `pageItems` 계산을

```kotlin
                    val totalPages = ((processedBooks.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
                    ...
                    val pageItems = processedBooks.subList(
                        startIndex,
                        (startIndex + itemsPerPage).coerceAtMost(processedBooks.size)
                    )
```

다음으로 바꾼다(`itemHeightDp`는 FolderItem도 같은 높이라 그대로 둔다):

```kotlin
                    val totalPages = ((entries.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
                    ...
                    val pageItems = entries.subList(
                        startIndex,
                        (startIndex + itemsPerPage).coerceAtMost(entries.size)
                    )
```

- [ ] **Step 7: 표지 로딩을 책 항목만 대상으로**

`LaunchedEffect(pageItems) { ... }` 블록의 첫 줄을 다음으로:

```kotlin
                        val missing = pageItems.filterIsInstance<FolderEntry.Book>().map { it.book }
                            .filter { it.bookKey() !in covers }
```

`LaunchedEffect(targetPage, itemsPerPage, processedBooks) { ... }` 블록 전체를 다음으로 교체:

```kotlin
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
```

- [ ] **Step 8: 리더 복귀 시 책이 있는 폴더·페이지로 이동**

`LaunchedEffect(jumpToBookKey, processedBooks, itemsPerPage) { ... }` 블록 전체를 다음으로 교체:

```kotlin
                    // 방금 연 책이 재정렬된 목록에서 있는 페이지로 이동
                    // 리더가 목록을 가리고 있는 동안 처리되므로, 돌아왔을 때 이동 과정이 보이지 않는다
                    // (목록에 없으면 — 검색/필터로 제외된 경우 등 — 현재 페이지 유지)
                    LaunchedEffect(jumpToBookKey, entries, itemsPerPage, location) {
                        val key = jumpToBookKey ?: return@LaunchedEffect
                        // 폴더 보기: 먼저 그 책이 있는 폴더로 이동하고, 항목이 다시 계산된 뒤 페이지를 찾는다
                        if (inFolderView) {
                            val target = visibleBooks.firstOrNull { it.bookKey() == key }?.let { locationOf(it) }
                            if (target != null && normalizeLocation(target, rootUris) != normalizeLocation(location, rootUris)) {
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
```

- [ ] **Step 9: 항목 렌더링 — 폴더와 책 분기**

`pageItems.forEach { book -> ... }` 블록을 다음 구조로 바꾼다. `is FolderEntry.Book` 분기 안의 `BookItem(...)` 호출은 기존 코드를 **그대로** 옮긴다(`val book = entry.book` 한 줄만 추가).

```kotlin
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
                                            // ... 기존 인자 그대로 ...
                                        )
                                    }
                                }
                                HorizontalDivider(color = EreaderColors.Gray)
                            }
```

`PaginationBar`의 `centerText`에서 `processedBooks.size`를 `entries.size`로 바꾼다.

- [ ] **Step 10: FolderPathBar, FolderItem 컴포저블 추가**

`private fun BookItem(` 정의 바로 위에 추가:

```kotlin
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
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = EreaderColors.Black,
                modifier = Modifier.size(36.dp)
            )
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
```

- [ ] **Step 11: 컴파일 및 기존 단위 테스트 확인**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 전체 단위 테스트 PASS

---

### Task 9: 인스트루먼트 테스트 + 스펙 문구 수정

**Files:**
- Create: `app/src/androidTest/java/com/rotein/ebookreader/AllBooksFolderViewTest.kt`
- Modify: `docs/superpowers/specs/2026-10-30-home-folder-view-design.md`

- [ ] **Step 1: 인스트루먼트 테스트 작성**

`ReturnFromReaderPageTest`와 같은 방식으로 `AllBooksScreen`만 띄운다(더미 책은 실제 파일이 없어 diff 스캔이 목록을 비우므로, 로드 후 `BookCache.books = null`).

```kotlin
package com.rotein.ebookreader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 홈 폴더 보기: 폴더 진입·상위 이동·검색 시 평면 목록·리더 복귀 시 폴더 유지를 확인한다.
 * 루트 하나("Books") 아래에 직속 책 1권과 SF 폴더 책 1권을 둔다.
 */
@RunWith(AndroidJUnit4::class)
class AllBooksFolderViewTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = "content://com.android.externalstorage.documents/tree/primary%3ABooks"

    private fun book(title: String, folder: String) = BookFile(
        name = "$title.epub",
        path = "",
        contentUri = "$root/document/$title",
        extension = "epub",
        size = 1024L,
        dateAdded = 1L,
        dateModified = 1L,
        metadata = BookMetadata(title, null, null, null, null, null),
        rootUri = root,
        folderPath = folder
    )

    private val books = listOf(book("직속 책", ""), book("SF 책", "SF"))

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setup() {
        BookDatabase.getInstance(context).clearAllTables()
        BookCache.books = books
        context.getSharedPreferences("folder_uris", Context.MODE_PRIVATE)
            .edit().putStringSet("selected_uris", setOf(root)).commit()
        // 폴더 보기로 시작
        context.getSharedPreferences("view_mode_pref", Context.MODE_PRIVATE)
            .edit().putString("view_mode", ViewMode.FOLDER.name).commit()
    }

    @After
    fun cleanup() {
        BookCache.books = null
        BookDatabase.getInstance(context).clearAllTables()
        context.getSharedPreferences("folder_uris", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("view_mode_pref", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private var openedBook by mutableStateOf<BookFile?>(null)

    /** 화면을 띄우고 목록 로드 후 캐시를 비워 가짜 폴더의 diff 스캔이 목록을 비우지 않게 한다 */
    private fun launch() {
        rule.setContent {
            AllBooksScreen(onBookClick = { openedBook = it }, refreshKey = openedBook)
        }
        rule.waitUntil(5000) {
            rule.onAllNodesWithText("직속 책").fetchSemanticsNodes().isNotEmpty()
        }
        BookCache.books = null
    }

    private fun nodeCount(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun topShowsFolderAndDirectBooksOnly() {
        launch()
        rule.onNodeWithTag("folderItem_SF").assertIsDisplayed()
        rule.onNodeWithText("직속 책").assertIsDisplayed()
        assertEquals(0, nodeCount("SF 책"))
        // 루트가 하나라 최상위에서는 경로 줄이 없다
        assertEquals(0, rule.onAllNodesWithContentDescription(context.getString(R.string.go_up)).fetchSemanticsNodes().size)
    }

    @Test
    fun enterFolderThenGoUpWithPathBar() {
        launch()
        rule.onNodeWithTag("folderItem_SF").performClick()
        rule.onNodeWithText("SF 책").assertIsDisplayed()
        rule.onNodeWithTag("folderPathBar").assertIsDisplayed()
        assertEquals(0, nodeCount("직속 책"))

        rule.onNodeWithContentDescription(context.getString(R.string.go_up)).performClick()
        rule.onNodeWithText("직속 책").assertIsDisplayed()
        rule.onNodeWithTag("folderItem_SF").assertIsDisplayed()
    }

    @Test
    fun searchShowsFlatResultsAcrossFolders() {
        launch()
        rule.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
        rule.onNodeWithTag("searchInput").performTextInput("SF 책")
        rule.onNodeWithText("SF 책").assertIsDisplayed()
    }

    @Test
    fun returningFromReaderStaysInFolder() {
        launch()
        rule.onNodeWithTag("folderItem_SF").performClick()
        rule.onNodeWithText("SF 책").performClick()
        rule.waitForIdle()
        assertEquals("SF 책", openedBook?.metadata?.title)

        // 리더를 닫고 복귀 — 같은 폴더가 그대로 보여야 한다
        rule.runOnIdle { openedBook = null }
        rule.waitForIdle()
        rule.onNodeWithText("SF 책").assertIsDisplayed()
        rule.onNodeWithTag("folderPathBar").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 스펙 문구 수정**

`docs/superpowers/specs/2026-10-30-home-folder-view-design.md`의 UI 섹션에서
`텍스트는 한 줄, 넘치면 앞쪽 말줄임` 을
`텍스트는 한 줄, 넘치면 끝쪽 말줄임 (앞쪽 말줄임 StartEllipsis는 Compose 1.8부터라 현재 1.7에서 불가)` 로 바꾸고,
데이터 섹션 끝에 다음 문장을 추가한다:
`루트 목록은 FolderUriStore가 아니라 책들의 rootUri에서 도출한다(rootUrisOf). 책이 없는 루트는 표시하지 않는다.`

- [ ] **Step 4: 인스트루먼트 테스트 실행 여부를 사용자에게 질문**

CLAUDE.md 규칙: 커밋 전 인스트루먼트 테스트 실행 여부를 묻는다. 사용자가 원하면 실행:

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rotein.ebookreader.AllBooksFolderViewTest`
Expected: 4 tests PASS

기존 홈 화면 테스트 회귀도 함께 권장: `AllBooksScreenTest`, `AllBooksScreenExtendedTest`, `ReturnFromReaderPageTest`, `HomeToReaderFlowTest`.

---

### Task 10: 실기기 확인, 코드 리뷰, 커밋

- [ ] **Step 1: 실기기(또는 에뮬레이터) 수동 확인**

디버그 빌드를 설치하고(`./gradlew :app:installDebug`, 실기기는 `com.eera.debug`로 나란히 설치됨) 다음을 확인한다:

1. 업그레이드 설치 직후(DB v19 캐시 존재) 앱 실행 → 목록 즉시 표시 → 폴더 보기로 바꾸면 diff 스캔 후 실제 폴더 구조가 나타난다
2. 하위 폴더 진입, 경로 줄 ←, 시스템 뒤로가기로 상위 이동. 최상위에서 뒤로가기는 기존 동작(앱 종료/백그라운드)
3. 폴더 안 책을 열고 리더에서 뒤로가기 → 리더만 닫히고(폴더 상위 이동이 일어나지 않음) 같은 폴더·페이지로 복귀
4. 정렬 4종에서 폴더 순서, 즐겨찾기/숨김 필터에서 빈 폴더 숨김
5. 검색어 입력 시 평면 목록, 검색 닫으면 원래 폴더로
6. 한글 폴더 이름 표시, 루트를 2개 이상 추가했을 때 최상위에 루트 목록
7. 앱 완전 종료 후 재실행 → 보기 모드는 유지, 위치는 최상위
8. 상단 바에 드롭다운이 3개(보기/필터/정렬)가 되므로, 좁은 화면(폰 세로)에서 검색 아이콘·케밥 메뉴와 겹치거나 잘리지 않는지. 겹치면 사용자와 배치를 상의한다

- [ ] **Step 2: 코드 리뷰 에이전트 실행**

`superpowers:code-reviewer` 에이전트로 `git diff origin/main` 전체를 스펙 대비 리뷰한다. 지적 사항은 `superpowers:receiving-code-review` 방식으로 검토 후 반영하고, 반영 후 단위 테스트를 다시 돌린다.

- [ ] **Step 3: 커밋 (사용자 확인 후)**

```bash
git add app/src/main/java/com/rotein/ebookreader/FolderBrowser.kt \
  app/src/main/java/com/rotein/ebookreader/BookFile.kt \
  app/src/main/java/com/rotein/ebookreader/BookCatalog.kt \
  app/src/main/java/com/rotein/ebookreader/BookDatabase.kt \
  app/src/main/java/com/rotein/ebookreader/FileScanner.kt \
  app/src/main/java/com/rotein/ebookreader/SortPreference.kt \
  app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-ko/strings.xml \
  app/src/main/res/values-ja/strings.xml app/src/main/res/values-zh/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/test/java/com/rotein/ebookreader/FolderBrowserTest.kt \
  app/src/test/java/com/rotein/ebookreader/BookCatalogTest.kt \
  app/src/test/java/com/rotein/ebookreader/SortPreferenceTest.kt \
  app/src/test/java/com/rotein/ebookreader/migration/MigrationTest.kt \
  app/src/androidTest/java/com/rotein/ebookreader/AllBooksFolderViewTest.kt \
  docs/superpowers/specs/2026-10-30-home-folder-view-design.md \
  docs/superpowers/plans/2026-10-30-home-folder-view.md
git commit -m "feat: 홈에서 실제 폴더 구조를 탐색하는 폴더 보기 추가"
```

Co-Authored-By 줄은 넣지 않는다.
