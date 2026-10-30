# 홈 폴더 보기 설계

## 목표

홈(도서 리스트)에서 사용자가 SAF로 선택한 폴더의 실제 디렉토리 구조를 그대로 탐색할 수 있게 한다.
eera가 별도의 가상 폴더를 관리하지 않는다 (SAF 권한이 읽기 전용이고, 파일 정리는 PC/Calibre/동기화 등 앱 밖에서 이뤄지기 때문).

## 용어

- **루트**: 케밥 메뉴의 폴더 관리에서 사용자가 선택한 SAF 트리 폴더 (`FolderUriStore`)
- **목록 보기**: 현재의 평면 도서 목록
- **폴더 보기**: 이번에 추가하는 폴더 탐색 화면

## 사용자 동작

| 항목 | 동작 |
|---|---|
| 보기 전환 | 상단 바 필터·정렬 옆에 "목록 / 폴더" 드롭다운. 선택 값은 저장되어 다음 실행에도 유지 |
| 최상위 | 루트가 1개면 그 루트 내부를 바로 표시. 여러 개면 루트 폴더 목록 표시 |
| 항목 순서 | 하위 폴더가 항상 책보다 위. 폴더끼리도 현재 정렬 기준을 따름 (아래 "정렬" 참고) |
| 폴더 항목 | 폴더 아이콘 + 이름 + "N권"(하위 폴더 포함 책 수). 누르면 진입하고 페이지는 0으로 |
| 상위 이동 | 폴더 안에서는 상단 바 아래에 경로 줄 `←  SF / 고전`. ← 또는 시스템 뒤로가기로 한 단계 위로. 최상위에서는 경로 줄 숨김 |
| 검색 | 검색어가 있으면 폴더와 무관하게 전체 도서를 평면 목록으로 표시 (기존 동작) |
| 필터 | 전체/즐겨찾기/숨김 필터는 폴더 보기에도 적용. 필터를 통과한 책이 없는 폴더는 표시하지 않음 |
| 리더에서 복귀 | 방금 읽은 책이 있는 폴더와 페이지로 이동 (#32 동작의 폴더 버전) |
| 앱 재시작 | 보기 모드만 유지. 폴더 위치는 저장하지 않고 최상위에서 시작 |

### 정렬

폴더가 책보다 항상 위에 오고, 폴더끼리는 다음 키로 정렬한다. 방향은 해당 정렬 필드의 기본 방향(`SortField.defaultDescending`)을 따른다.

| 정렬 필드 | 폴더 정렬 키 |
|---|---|
| 제목 | 폴더 이름 |
| 저자 | 폴더 이름 |
| 추가일 | 폴더 안(하위 포함) 책 중 가장 큰 `dateAdded` |
| 최근 읽음 | 폴더 안(하위 포함) 책 중 가장 큰 마지막 읽은 시각 (없으면 0) |

## 데이터

### BookFile / BookCatalogEntry

두 필드를 추가한다.

- `rootUri: String` — 책이 속한 루트의 트리 URI 문자열
- `folderPath: String` — 루트 기준 상대 디렉토리 경로. `/`로 구분. 루트 바로 아래 책은 `""`

기본값은 `""`. `rootUri`가 빈 책(레거시 절대 경로 책 등)은 폴더 보기 최상위에 책으로 표시한다.

### DB 마이그레이션 19 → 20

`book_catalog` 테이블에 `rootUri TEXT NOT NULL DEFAULT ''`, `folderPath TEXT NOT NULL DEFAULT ''` 컬럼 추가.

### FileScanner

- `scanDirectory`, `scanDirectoryLightweight`가 재귀하면서 현재 상대 경로를 인자로 넘기고, 결과(`BookFile`, `FileSnapshot`)에 `rootUri`·`folderPath`를 채운다.
- `refreshBooks`에서 변경이 없어 유지하는 책(kept)에도 스냅샷의 `rootUri`·`folderPath`를 복사한다. 이로써 마이그레이션 직후 경로가 빈 기존 캐시 항목이 전체 재스캔 없이 다음 diff 스캔에서 채워진다.

루트 목록은 FolderUriStore가 아니라 책들의 rootUri에서 도출한다(rootUrisOf). 책이 없는 루트는 표시하지 않는다.

## 순수 로직: `FolderBrowser.kt` (신규)

```kotlin
sealed interface FolderLocation {
    data object Top : FolderLocation
    data class Folder(val rootUri: String, val path: String) : FolderLocation
}

sealed interface FolderEntry {
    data class Folder(val location: FolderLocation.Folder, val name: String, val bookCount: Int) : FolderEntry
    data class Book(val book: BookFile) : FolderEntry
}
```

- `buildFolderEntries(books, location, rootUris, rootNames, sortField, lastReadTimes): List<FolderEntry>`
  - `books`는 필터가 이미 적용된 목록
  - 폴더는 책의 `folderPath`로부터 도출하므로 빈 폴더는 자연히 제외
  - `location == Top`이고 루트가 1개면 `Folder(root, "")`로 취급. 루트가 1개일 때 `rootUri`가 빈 책도 해당 루트의 최상위에 직접 표시한다
  - 루트가 여러 개인 `Top`: 각 루트를 `Folder(root, "")` 항목으로, 이름은 `rootNames[root]`
  - 책 항목 정렬은 기존 `processedBooks` 정렬과 동일
- `parentOf(location, rootUris): FolderLocation?` — 상위 위치. 루트가 1개일 때 루트의 상위, 그리고 `Top`의 상위는 `null`. 루트가 여러 개일 때 루트의 상위는 `Top`
- `resolveLocation(location, books, rootUris): FolderLocation` — 정규화된 위치를 반환한다. 루트가 1개면 `Top`은 `Folder(root, "")`로 정규화된다. 해당 위치에 표시할 책이 없으면(폴더가 사라졌거나 필터로 비었음) 책이 있는 가장 가까운 상위로 보정
- `locationOf(book): FolderLocation` — 책이 들어 있는 폴더 위치 (리더 복귀용)

## UI: `AllBooksScreen`

- `ViewMode { LIST, FOLDER }` enum과 저장소 추가 (`SortPreferenceStore`와 같은 SharedPreferences 방식)
- 상단 바에 보기 모드 드롭다운 (`EreaderDropdownMenu` 재사용)
- 상태: `var location by remember { mutableStateOf<FolderLocation>(FolderLocation.Top) }`
- `entries` 계산
  - 목록 보기이거나 검색어가 있으면 `processedBooks.map(FolderEntry::Book)`
  - 폴더 보기면 `buildFolderEntries(...)`
- 페이지네이션, 빈 목록 표시, 표지 로딩, `jumpToBookKey` 처리 기준을 `processedBooks`에서 `entries`로 변경. 표지는 `FolderEntry.Book` 항목만 로딩
- 경로 줄 컴포저블: 폴더 보기이고 `parentOf(location) != null`일 때만 표시. 텍스트는 한 줄, 넘치면 끝쪽 말줄임 (앞쪽 말줄임 StartEllipsis는 Compose 1.8부터라 현재 1.7에서 불가)
- `BackHandler(enabled = 상위가 있을 때)`로 한 단계 위로
- `FolderItem` 컴포저블: `BookItem`과 같은 81dp(80dp + 구분선) 높이로 페이지당 항목 수 계산 유지
- 폴더 진입·상위 이동 시 `currentPage`, `targetPage`를 0으로
- 리더 복귀: `jumpToBookKey`가 있고 폴더 보기면, 먼저 `location = locationOf(book)`로 맞춘 뒤 `entries`에서 인덱스를 찾아 페이지 이동
- 재스캔·필터 변경 후 `location = resolveLocation(location, filteredBooks, rootUris)`
- e-ink 규칙: 애니메이션 없음, 흰 배경, 텍스트는 검정/회색

## 범위 밖

- 겹치는 루트(예: `Books`와 `Books/SF`를 둘 다 선택)로 인한 중복 표시 — 기존에도 있는 문제
- 폴더별 정렬 설정, 폴더 표지 미리보기, 앱 재시작 후 폴더 위치 복원

## 테스트

### 단위 테스트 (TDD)

- `FolderBrowserTest`
  - 하위 폴더와 직속 책 분리, `bookCount`가 하위 포함 개수
  - 루트 1개일 때 `Top`이 루트 내부를 표시, 루트 여러 개일 때 루트 목록 표시
  - 정렬 필드 4종별 폴더 순서, 폴더가 항상 책보다 앞
  - 필터 후 책이 없는 폴더는 미표시
  - `parentOf`, `resolveLocation`, `locationOf`
  - `rootUri`가 빈 책은 최상위에 표시
- `BookCatalogTest`: 새 필드 매핑 왕복
- 마이그레이션 19 → 20 테스트 (`app/src/test/.../migration`)

### 인스트루먼트 테스트

- 폴더 보기 전환 후 폴더 진입, 경로 줄 표시, 뒤로가기로 상위 이동
- 검색어 입력 시 평면 목록 전환
- 폴더 안의 책을 열었다 돌아오면 같은 폴더·페이지 표시
