# 검색 결과 페이지 정확도 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 검색 결과의 page 번호를 북마크와 동일한 정확도로 표시한다.

**Architecture:** 스캔 시 각 spine 섹션을 렌더링하는 과정에서 페이지별 문자 offset 경계를 수집하여 `_spineCharPageBreaks`에 저장한다. 이 데이터를 DB에 캐싱하여 스캔 스킵 시에도 복원한다. 검색 시 기존 비율 추정 대신 이 테이블을 lookup하여 정확한 페이지를 결정한다.

**Tech Stack:** Kotlin (Room DB, WebView), JavaScript (epub.js scan/search)

---

## 변경 파일 개요

- **Modify:** `app/src/main/java/com/rotein/ebookreader/BookDatabase.kt` — `BookReadRecord`에 `cachedSpineCharPageBreaksJson` 컬럼 추가, migration 15, DAO 메서드 수정
- **Modify:** `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt` — JS scan 로직에서 문자 offset 수집, DB 캐시 저장/복원, 검색 시 lookup 사용

---

### Task 1: DB 스키마 변경 — `cachedSpineCharPageBreaksJson` 컬럼 추가

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookDatabase.kt`

- [ ] **Step 1: `BookReadRecord` entity에 컬럼 추가**

`BookDatabase.kt:18-27` — `BookReadRecord` data class에 필드 추가:

```kotlin
@Entity(tableName = "book_read_records")
data class BookReadRecord(
    @PrimaryKey val bookPath: String,
    val lastReadAt: Long,
    val lastCfi: String = "",
    val tocJson: String = "",
    val cachedTotalPages: Int = 0,
    val cachedSpinePageOffsetsJson: String = "",
    val cachedSpineCharPageBreaksJson: String = "",
    val cachedSettingsFingerprint: String = ""
)
```

- [ ] **Step 2: DAO 메서드 수정 — `updatePageScanCache`, `upsertPageScanCache`에 파라미터 추가**

`BookDatabase.kt:49-74` — 기존 두 메서드를 수정:

```kotlin
@Query("UPDATE book_read_records SET cachedTotalPages = :totalPages, cachedSpinePageOffsetsJson = :spinePageOffsetsJson, cachedSpineCharPageBreaksJson = :spineCharPageBreaksJson, cachedSettingsFingerprint = :fingerprint WHERE bookPath = :bookPath")
suspend fun updatePageScanCache(bookPath: String, totalPages: Int, spinePageOffsetsJson: String, spineCharPageBreaksJson: String, fingerprint: String)

@Transaction
suspend fun upsertPageScanCache(bookPath: String, totalPages: Int, spinePageOffsetsJson: String, spineCharPageBreaksJson: String, fingerprint: String) {
    insertIfNotExists(BookReadRecord(bookPath = bookPath, lastReadAt = 0L))
    updatePageScanCache(bookPath, totalPages, spinePageOffsetsJson, spineCharPageBreaksJson, fingerprint)
}
```

- [ ] **Step 3: MIGRATION_14_15 추가**

`BookDatabase.kt` — `MIGRATION_13_14` 아래에 추가:

```kotlin
private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE book_read_records ADD COLUMN cachedSpineCharPageBreaksJson TEXT NOT NULL DEFAULT ''")
    }
}
```

- [ ] **Step 4: Database version 및 migration 등록 업데이트**

`BookDatabase.kt:282` — version을 15로 변경:

```kotlin
@Database(entities = [BookReadRecord::class, Bookmark::class, Highlight::class, Memo::class], version = 15)
```

`BookDatabase.kt:298` — `.addMigrations()`에 `MIGRATION_14_15` 추가:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
```

- [ ] **Step 5: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookDatabase.kt
git commit -m "feat: DB에 spineCharPageBreaks 캐시 컬럼 추가 (migration 15)"
```

---

### Task 2: JS 스캔 로직 — 페이지별 문자 offset 경계 수집

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt` (JS 코드 영역)

핵심 아이디어: 스캔에서 각 spine 섹션을 `scanRendition.display(href)`로 렌더링한 직후, iframe 내 텍스트 노드를 walk하면서 각 페이지 경계의 문자 offset을 기록한다.

epub.js의 paginated 모드에서 `delta` (한 페이지 폭)와 각 텍스트 노드의 `getBoundingClientRect().left`를 비교하면 해당 텍스트가 몇 번째 페이지에 있는지 알 수 있다.

- [ ] **Step 1: JS 전역 변수 `_spineCharPageBreaks` 선언**

`BookReaderScreen.kt:2887` — `_totalVisualPages` 선언 아래에 추가:

```javascript
var _spineCharPageBreaks = {};
```

- [ ] **Step 2: 스캔 `next()` 함수 내에서 문자 offset 경계 수집**

`BookReaderScreen.kt:3076-3089` — `scanRendition.display(items[i].href).then(function() { ... })` 콜백 내부를, `_spinePageCounts` 계산 직후 다음 코드를 추가:

현재 코드:
```javascript
scanRendition.display(items[i].href).then(function() {
    var loc = scanRendition.currentLocation();
    _spinePageCounts[i] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
    _spinePageOffsets[i] = _runningOffset;
    var cfisForSpine = [];
    ...
```

변경 후:
```javascript
scanRendition.display(items[i].href).then(function() {
    var loc = scanRendition.currentLocation();
    _spinePageCounts[i] = (loc && loc.start && loc.start.displayed) ? loc.start.displayed.total : 1;
    _spinePageOffsets[i] = _runningOffset;
    try {
        var pageBreaks = [0];
        var sTotal = _spinePageCounts[i];
        if (sTotal > 1) {
            var sDelta = scanRendition.manager && scanRendition.manager.layout ? scanRendition.manager.layout.delta : 0;
            if (sDelta > 0) {
                var sIframe = scanDiv.querySelector('iframe');
                var sDoc = sIframe && (sIframe.contentDocument || (sIframe.contentWindow && sIframe.contentWindow.document));
                if (sDoc) {
                    var sBody = sDoc.body || sDoc.querySelector('body') || sDoc.documentElement;
                    if (sBody) {
                        var sWalker = sDoc.createTreeWalker(sBody, NodeFilter.SHOW_TEXT, null, false);
                        var sNd, charOffset = 0;
                        var lastPage = 0;
                        while ((sNd = sWalker.nextNode())) {
                            var len = sNd.textContent.length;
                            if (len === 0) continue;
                            try {
                                var range = sDoc.createRange();
                                range.selectNodeContents(sNd);
                                var rect = range.getBoundingClientRect();
                                if (rect.width > 0) {
                                    var nodePage = Math.floor(rect.left / sDelta);
                                    if (nodePage > lastPage) {
                                        for (var np = lastPage + 1; np <= nodePage; np++) {
                                            pageBreaks.push(charOffset);
                                        }
                                        lastPage = nodePage;
                                    }
                                }
                            } catch(re) {}
                            charOffset += len;
                        }
                    }
                }
            }
        }
        _spineCharPageBreaks[i] = pageBreaks;
    } catch(pe) {
        _spineCharPageBreaks[i] = [0];
    }
    var cfisForSpine = [];
    ...
```

이 로직은:
1. 각 텍스트 노드의 `getBoundingClientRect().left`를 `sDelta`(페이지 폭)로 나눠 페이지 번호를 결정
2. 페이지가 바뀌는 지점의 `charOffset`을 `pageBreaks` 배열에 push
3. 결과: `_spineCharPageBreaks[spineIndex] = [0, 312, 650, ...]` (각 페이지 시작 문자 offset)

- [ ] **Step 3: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: 스캔 시 spine별 페이지 문자 offset 경계 수집"
```

---

### Task 3: 스캔 데이터 DB 캐싱 및 복원

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`

- [ ] **Step 1: `onScanComplete`에서 `_spineCharPageBreaks`를 Kotlin으로 전달**

`BookReaderScreen.kt:3068` — `Android.onScanComplete` 호출에 `_spineCharPageBreaks` 추가:

현재:
```javascript
Android.onScanComplete(_totalVisualPages, _scanCurrentPage, JSON.stringify(_spinePageOffsets), JSON.stringify(_cfiPageMap));
```

변경:
```javascript
Android.onScanComplete(_totalVisualPages, _scanCurrentPage, JSON.stringify(_spinePageOffsets), JSON.stringify(_cfiPageMap), JSON.stringify(_spineCharPageBreaks));
```

- [ ] **Step 2: `EpubBridge.onScanComplete` 시그니처 업데이트**

`BookReaderScreen.kt:3501` 근처 — `onScanComplete` JavascriptInterface 메서드에 파라미터 추가:

현재:
```kotlin
@JavascriptInterface
fun onScanComplete(totalPages: Int, currentPage: Int, spinePageOffsetsJson: String, cfiPageMapJson: String) {
```

변경:
```kotlin
@JavascriptInterface
fun onScanComplete(totalPages: Int, currentPage: Int, spinePageOffsetsJson: String, cfiPageMapJson: String, spineCharPageBreaksJson: String) {
```

콜백 호출도 같이 업데이트.

- [ ] **Step 3: `onScanComplete` 콜백 시그니처를 통해 Kotlin Composable까지 전파**

`BookReaderScreen.kt:2227` 근처 — `onScanComplete` 파라미터 타입 수정:

현재:
```kotlin
onScanComplete: (totalPages: Int, spinePageOffsetsJson: String, cfiPageMapJson: String) -> Unit = { _, _, _ -> },
```

변경:
```kotlin
onScanComplete: (totalPages: Int, spinePageOffsetsJson: String, cfiPageMapJson: String, spineCharPageBreaksJson: String) -> Unit = { _, _, _, _ -> },
```

- [ ] **Step 4: `onScanComplete` 콜백 구현부에서 DB 캐싱**

`BookReaderScreen.kt:435` 근처 — `onScanComplete` 핸들러에서 `spineCharPageBreaksJson`을 DB에 저장:

현재 `dao.upsertPageScanCache` 호출에 파라미터 추가:
```kotlin
dao.upsertPageScanCache(book.path, scannedTotal, spinePageOffsetsJson, spineCharPageBreaksJson, fingerprint)
```

또한 `spineCharPageBreaksJson`을 JS에 주입할 수 있도록 상태로 보관:
```kotlin
var spineCharPageBreaksJson by remember(book.path) { mutableStateOf("") }
```

`onScanComplete` 핸들러 안에서:
```kotlin
spineCharPageBreaksJson = charPageBreaksJson
```

- [ ] **Step 5: 캐시 복원 시 `_spineCharPageBreaks`도 JS에 주입**

`BookReaderScreen.kt:366-372` — `onContentRendered`에서 캐시 복원하는 부분:

현재:
```kotlin
val js = "_spinePageOffsets=$jsonObj;_totalVisualPages=$totalPages;" +
    "if(_pendingLocation){reportLocation(_pendingLocation);_pendingLocation=null;}" +
    "else{var l=rendition.currentLocation();if(l&&l.start)reportLocation(l);}"
```

변경 — `_spineCharPageBreaks`도 함께 주입:
```kotlin
val charBreaksJs = if (spineCharPageBreaksJson.isNotEmpty()) "_spineCharPageBreaks=$spineCharPageBreaksJson;" else ""
val js = "_spinePageOffsets=$jsonObj;_totalVisualPages=$totalPages;$charBreaksJs" +
    "if(_pendingLocation){reportLocation(_pendingLocation);_pendingLocation=null;}" +
    "else{var l=rendition.currentLocation();if(l&&l.start)reportLocation(l);}"
```

캐시에서 로드하는 부분에서 `cachedSpineCharPageBreaksJson`도 읽어오기:
```kotlin
spineCharPageBreaksJson = record.cachedSpineCharPageBreaksJson
```

- [ ] **Step 6: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt app/src/main/java/com/rotein/ebookreader/BookDatabase.kt
git commit -m "feat: spineCharPageBreaks 스캔 데이터 DB 캐싱 및 복원"
```

---

### Task 4: 검색에서 비율 추정 대신 `_spineCharPageBreaks` lookup 사용

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt` (JS `_search` 함수)

- [ ] **Step 1: `_search` 함수 내 page 계산 로직 교체**

`BookReaderScreen.kt:3325-3329` — 현재 비율 추정 코드:

```javascript
var matchPage = 0;
if (_totalVisualPages > 0) {
    var sectionPages = _spinePageCounts[spineIndex] || 1;
    var pageWithin = Math.floor((pos / Math.max(fullText.length, 1)) * sectionPages);
    matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
}
```

변경:
```javascript
var matchPage = 0;
if (_totalVisualPages > 0) {
    var breaks = _spineCharPageBreaks[spineIndex];
    if (breaks && breaks.length > 1) {
        var pageWithin = 0;
        for (var bi = breaks.length - 1; bi >= 0; bi--) {
            if (pos >= breaks[bi]) { pageWithin = bi; break; }
        }
        matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
    } else {
        var sectionPages = _spinePageCounts[spineIndex] || 1;
        var pageWithin = Math.floor((pos / Math.max(fullText.length, 1)) * sectionPages);
        matchPage = (_spinePageOffsets[spineIndex] || 0) + pageWithin + 1;
    }
}
```

이 로직은:
1. `_spineCharPageBreaks`가 있으면 `pos`를 `breaks` 배열에서 이진(역순) 탐색하여 정확한 페이지 결정
2. 없으면 (스캔 전 검색) 기존 비율 추정을 fallback으로 유지

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "feat: 검색 결과 페이지를 문자 offset 테이블 기반으로 계산"
```

---

## 주의사항

1. **텍스트 노드 vs 렌더링 위치 차이:** `getBoundingClientRect`는 렌더링된 위치를 반환하므로, CSS로 인한 위치 변경(float, position 등)이 있으면 문자 offset 순서와 렌더링 순서가 다를 수 있다. 대부분의 EPUB에서는 문제없지만 복잡한 레이아웃에서는 드물게 오차가 발생할 수 있다.

2. **Range 대신 노드 단위:** 문자 하나하나의 위치를 재는 것은 비용이 크므로, 텍스트 노드 단위로 페이지를 판정한다. 하나의 텍스트 노드가 두 페이지에 걸치는 경우 `rect.left` 기준으로 첫 페이지에 속하게 된다. 이는 북마크의 정확도와 동등한 수준이다.

3. **스캔 전 검색:** `_spineCharPageBreaks`가 비어있으면 기존 비율 추정 fallback이 동작하므로, 스캔 완료 전에도 검색은 정상 작동한다.
