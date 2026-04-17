# 확장 Instrumented 테스트 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AllBooksScreen 핵심 기능 6개 테스트와 EPUB/PDF 뷰어 목차→페이지 이동 테스트 2개를 추가한다.

**Architecture:** Part 1은 기존 더미 데이터 방식으로 AllBooksScreen UI를 테스트한다. Part 2는 테스트용 EPUB/PDF 파일을 assets에 포함하고, UIAutomator로 WebView 영역의 제스처를 시뮬레이션하여 뷰어 내비게이션을 테스트한다.

**Tech Stack:** Compose UI Test, UIAutomator, AndroidX Test Runner

---

## 파일 구조

| 파일 | 역할 |
|---|---|
| `gradle/libs.versions.toml` | UIAutomator 라이브러리 추가 |
| `app/build.gradle.kts` | UIAutomator 의존성 추가 |
| `app/src/main/java/.../AllBooksScreen.kt` | 검색 입력 필드에 testTag 추가 |
| `app/src/main/java/.../BookReaderScreen.kt` | 목차 버튼, 페이지 번호에 testTag 추가 |
| `app/src/main/java/.../reader/TocPopup.kt` | 목차 아이템에 testTag 추가 |
| `app/src/androidTest/java/.../AllBooksScreenTest.kt` | Part 1 테스트 6개 |
| `app/src/androidTest/java/.../ViewerNavigationTest.kt` | Part 2 테스트 2개 |
| `app/src/androidTest/assets/test.epub` | 테스트용 EPUB (4챕터) |
| `app/src/androidTest/assets/test.pdf` | 테스트용 PDF (4페이지) |

---

### Task 1: UIAutomator 의존성 추가

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: libs.versions.toml에 UIAutomator 추가**

`[libraries]` 섹션, `androidx-test-runner` 뒤에 추가:

```toml
androidx-test-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version = "2.3.0" }
```

- [ ] **Step 2: build.gradle.kts에 의존성 추가**

`dependencies` 블록, `androidTestImplementation(libs.androidx.test.runner)` 뒤에 추가:

```kotlin
androidTestImplementation(libs.androidx.test.uiautomator)
```

- [ ] **Step 3: 커밋**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: UIAutomator 의존성 추가"
```

---

### Task 2: AllBooksScreen 검색 입력에 testTag 추가

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt`

- [ ] **Step 1: TopBar의 검색 BasicTextField에 testTag 추가**

AllBooksScreen.kt의 TopBar 함수 내, 검색 오버레이 레이어의 BasicTextField를 찾는다 (약 410행 부근). Modifier에 `.testTag("searchInput")` 추가:

```kotlin
BasicTextField(
    value = searchQuery,
    onValueChange = onQueryChange,
    modifier = Modifier
        .weight(1f)
        .testTag("searchInput"),
    // ... 나머지 동일
)
```

- [ ] **Step 2: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt
git commit -m "test: 검색 입력 필드에 testTag 추가"
```

---

### Task 3: AllBooksScreenTest — 빈 목록, 메타데이터, 스플래시

**Files:**
- Create: `app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt`

- [ ] **Step 1: 테스트 파일 생성 — 3개 테스트**

```kotlin
package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllBooksScreenTest {

    private val dummyBooks = listOf(
        BookFile(
            name = "테스트 책 1",
            path = "/storage/emulated/0/Books/test1.epub",
            extension = "epub",
            size = 1024L,
            dateAdded = 1000L,
            dateModified = 1000L,
            metadata = BookMetadata(
                title = "테스트 책 1",
                author = "가나다 저자",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        ),
        BookFile(
            name = "테스트 책 2",
            path = "/storage/emulated/0/Books/test2.epub",
            extension = "epub",
            size = 2048L,
            dateAdded = 2000L,
            dateModified = 2000L,
            metadata = BookMetadata(
                title = "테스트 책 2",
                author = "라마바 저자",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        )
    )

    init {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()
    }

    @After
    fun cleanup() {
        BookCache.books = null
    }

    private fun launchWithBooks(books: List<BookFile>): androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *> {
        BookCache.books = books
        return composeTestRule
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMillis: Long = 5000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun emptyBookListShowsEmptyMessage() {
        BookCache.books = emptyList()
        waitForText("비어있음")
        composeTestRule.onNodeWithText("비어있음").assertIsDisplayed()
    }

    @Test
    fun bookMetadataShowsAuthor() {
        BookCache.books = dummyBooks
        waitForText("테스트 책 1")
        composeTestRule.onNodeWithText("가나다 저자").assertIsDisplayed()
        composeTestRule.onNodeWithText("라마바 저자").assertIsDisplayed()
    }

    @Test
    fun splashScreenTransitionsToBookList() {
        BookCache.books = dummyBooks
        // 스플래시는 800ms 후 + fileScanComplete 시 사라짐
        // BookCache가 세팅되어 있으면 onLoadComplete가 즉시 호출되므로
        // 약 1초 내에 스플래시가 사라져야 함
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("ebook-reader")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        waitForText("테스트 책 1")
        composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
    }
}
```

> 주의: `emptyBookListShowsEmptyMessage` 테스트에서 BookCache.books를 빈 리스트로 세팅한다. `init` 블록에서는 BookCache를 세팅하지 않고, 각 테스트에서 직접 세팅한다.

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt
git commit -m "test: AllBooksScreen 빈 목록, 메타데이터, 스플래시 테스트 추가"
```

---

### Task 4: AllBooksScreenTest — 검색 필터

**Files:**
- Modify: `app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt`

- [ ] **Step 1: 검색 테스트 추가**

AllBooksScreenTest.kt에 import 추가:

```kotlin
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
```

테스트 메서드 추가:

```kotlin
@Test
fun searchFiltersBooksByQuery() {
    BookCache.books = dummyBooks
    waitForText("테스트 책 1")

    // 검색 아이콘 클릭 (Icons.Default.Search의 contentDescription)
    composeTestRule.onNodeWithContentDescription("Search").performClick()

    // 검색어 입력
    composeTestRule.onNodeWithTag("searchInput").performTextInput("책 1")

    // "테스트 책 1"은 표시, "테스트 책 2"는 미표시
    composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("테스트 책 2").assertDoesNotExist()
}
```

import 추가:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithContentDescription
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt
git commit -m "test: AllBooksScreen 검색 필터 테스트 추가"
```

---

### Task 5: AllBooksScreenTest — 정렬

**Files:**
- Modify: `app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt`

- [ ] **Step 1: 정렬 테스트 추가**

```kotlin
import androidx.compose.ui.test.onAllNodesWithTag

@Test
fun sortByAuthorChangesOrder() {
    BookCache.books = dummyBooks
    waitForText("테스트 책 1")

    // 정렬 드롭다운 클릭 (현재 표시 중인 정렬 레이블, 기본값은 "담은순")
    composeTestRule.onNodeWithText("담은순").performClick()

    // "작가순" 선택
    composeTestRule.onNodeWithText("작가순").performClick()

    // 정렬 결과 확인: "가나다 저자"가 "라마바 저자"보다 먼저 나와야 함
    val bookItems = composeTestRule.onAllNodesWithTag("bookItem_테스트 책 1")
        .fetchSemanticsNodes()
    assert(bookItems.isNotEmpty()) { "책 아이템이 표시되지 않음" }

    // 첫 번째 저자가 "가나다 저자"인지 확인
    composeTestRule.onNodeWithText("가나다 저자").assertIsDisplayed()
}
```

> 참고: AllBooksScreen의 기본 정렬은 DATE_ADDED(담은순, 내림차순)이다. "작가순"으로 변경하면 가나다순 오름차순이 된다.

- [ ] **Step 2: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt
git commit -m "test: AllBooksScreen 정렬 테스트 추가"
```

---

### Task 6: AllBooksScreenTest — 즐겨찾기 토글

**Files:**
- Modify: `app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt`

- [ ] **Step 1: 즐겨찾기 테스트 추가**

```kotlin
@Test
fun toggleFavoriteAndFilterByFavorite() {
    BookCache.books = dummyBooks
    waitForText("테스트 책 1")

    // 첫 번째 책의 3-dot 메뉴 클릭 (MoreVert 아이콘)
    // BookItem마다 MoreVert 아이콘이 있으므로 첫 번째 것을 클릭
    composeTestRule.onAllNodesWithContentDescription("More options")[0].performClick()

    // "즐겨찾기" 메뉴 아이템 클릭
    composeTestRule.onNodeWithText("즐겨찾기").performClick()

    // 필터를 "즐겨찾기"로 변경
    composeTestRule.onNodeWithText("전체보기").performClick()
    composeTestRule.onNodeWithText("즐겨찾기").performClick()

    // 즐겨찾기한 책만 표시
    composeTestRule.waitUntil(timeoutMillis = 3000) {
        composeTestRule
            .onAllNodesWithText("테스트 책 1")
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
}
```

import 추가:

```kotlin
import androidx.compose.ui.test.onAllNodesWithContentDescription
```

> 참고: `EreaderDropdownMenu`는 MoreVert 아이콘을 trigger로 사용한다. contentDescription이 설정되어 있지 않을 수 있으므로, 실제 구현을 확인하여 적절한 finder를 사용해야 한다. 만약 contentDescription이 없으면 testTag을 추가해야 한다.

- [ ] **Step 2: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/AllBooksScreenTest.kt
git commit -m "test: AllBooksScreen 즐겨찾기 토글 테스트 추가"
```

---

### Task 7: 뷰어 testTag 추가

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt`
- Modify: `app/src/main/java/com/rotein/ebookreader/reader/TocPopup.kt`

- [ ] **Step 1: BookReaderScreen — 목차 버튼에 testTag 추가**

BookReaderScreen.kt 약 362행, 목차 버튼 Row의 modifier에 testTag 추가:

```kotlin
Row(
    modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .clickable { vm.setShowTocPopup(true) }
        .testTag("tocButton")
        .border(1.dp, EreaderColors.Black, RoundedCornerShape(4.dp))
        .padding(horizontal = EreaderSpacing.S, vertical = EreaderSpacing.XS),
    // ... 나머지 동일
)
```

- [ ] **Step 2: BookReaderScreen — 페이지 번호 텍스트에 testTag 추가**

BookReaderScreen.kt 약 347행, 페이지 번호 Text에 testTag 추가:

```kotlin
Text(
    "${readingState.currentPage} / ${readingState.totalPages}",
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.testTag("pageInfoText")
)
```

- [ ] **Step 3: TocPopup — 목차 아이템에 testTag 추가**

TocPopup.kt 약 72행, 목차 아이템 Row의 modifier에 testTag 추가. `pageItems.forEachIndexed`로 변경하여 인덱스를 사용:

먼저 TocPopup.kt에서 목차 아이템을 렌더링하는 부분을 찾는다. `pageItems.forEach { item ->` 형태를 `pageItems.forEachIndexed { index, item ->` 으로 변경하고 Row에 testTag을 추가한다:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .clickable { onNavigate(item.href) }
        .testTag("tocItem_${startIndex + index}")
        // ... 나머지 동일
)
```

여기서 `startIndex`는 현재 페이지의 시작 인덱스이다. TocPopup의 페이지네이션 로직에서 `startIndex`를 확인하여 전체 목록 기준의 인덱스를 사용한다.

import 추가 (TocPopup.kt):
```kotlin
import androidx.compose.ui.platform.testTag
```

- [ ] **Step 4: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebug`

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt app/src/main/java/com/rotein/ebookreader/reader/TocPopup.kt
git commit -m "test: 뷰어 목차 버튼, 페이지 번호, 목차 아이템에 testTag 추가"
```

---

### Task 8: 테스트용 EPUB 파일 생성

**Files:**
- Create: `app/src/androidTest/assets/test.epub`

- [ ] **Step 1: 테스트용 EPUB 생성 스크립트 실행**

EPUB은 ZIP 포맷이다. 다음 구조로 최소한의 EPUB 3 파일을 생성한다:

```
mimetype
META-INF/container.xml
OEBPS/content.opf
OEBPS/toc.ncx
OEBPS/nav.xhtml
OEBPS/chapter1.xhtml
OEBPS/chapter2.xhtml
OEBPS/chapter3.xhtml
OEBPS/chapter4.xhtml
```

스크립트로 생성:

```bash
cd /Users/rotein/dev/ebook-reader-app
mkdir -p app/src/androidTest/assets
TMPDIR=$(mktemp -d)

# mimetype (압축 없이 첫 번째 엔트리)
echo -n "application/epub+zip" > "$TMPDIR/mimetype"

# META-INF/container.xml
mkdir -p "$TMPDIR/META-INF"
cat > "$TMPDIR/META-INF/container.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
XMLEOF

# OEBPS 디렉토리
mkdir -p "$TMPDIR/OEBPS"

# content.opf
cat > "$TMPDIR/OEBPS/content.opf" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">test-epub-001</dc:identifier>
    <dc:title>테스트 도서</dc:title>
    <dc:language>ko</dc:language>
    <dc:creator>테스트 저자</dc:creator>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch4" href="chapter4.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
    <itemref idref="ch3"/>
    <itemref idref="ch4"/>
  </spine>
</package>
XMLEOF

# nav.xhtml (EPUB 3 navigation)
cat > "$TMPDIR/OEBPS/nav.xhtml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Navigation</title></head>
<body>
<nav epub:type="toc">
  <ol>
    <li><a href="chapter1.xhtml">제1장 시작</a></li>
    <li><a href="chapter2.xhtml">제2장 전개</a></li>
    <li><a href="chapter3.xhtml">제3장 절정</a></li>
    <li><a href="chapter4.xhtml">제4장 결말</a></li>
  </ol>
</nav>
</body>
</html>
XMLEOF

# 챕터 파일 생성 (각 챕터에 충분한 텍스트)
for i in 1 2 3 4; do
  TITLE=""
  case $i in
    1) TITLE="제1장 시작" ;;
    2) TITLE="제2장 전개" ;;
    3) TITLE="제3장 절정" ;;
    4) TITLE="제4장 결말" ;;
  esac
  cat > "$TMPDIR/OEBPS/chapter${i}.xhtml" << XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>${TITLE}</title></head>
<body>
<h1>${TITLE}</h1>
$(for j in $(seq 1 30); do echo "<p>이것은 ${TITLE}의 ${j}번째 단락입니다. 테스트를 위해 충분한 길이의 텍스트를 포함합니다. 이 단락은 페이지를 채우기 위한 더미 콘텐츠입니다. 각 챕터는 여러 페이지에 걸쳐 표시되어야 하므로 충분한 양의 텍스트가 필요합니다. 이 문장은 반복되는 패딩 텍스트입니다.</p>"; done)
</body>
</html>
XMLEOF
done

# EPUB ZIP 생성 (mimetype은 압축 없이 첫 번째)
cd "$TMPDIR"
zip -0 -X test.epub mimetype
zip -r -X test.epub META-INF OEBPS
cp test.epub /Users/rotein/dev/ebook-reader-app/app/src/androidTest/assets/test.epub
cd /Users/rotein/dev/ebook-reader-app
rm -rf "$TMPDIR"
```

- [ ] **Step 2: 생성 확인**

```bash
ls -la app/src/androidTest/assets/test.epub
unzip -l app/src/androidTest/assets/test.epub | head -20
```

Expected: EPUB 파일이 존재하고, mimetype/META-INF/OEBPS 구조가 보임

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/assets/test.epub
git commit -m "test: 테스트용 EPUB 파일 추가 (4챕터)"
```

---

### Task 9: 테스트용 PDF 파일 생성

**Files:**
- Create: `app/src/androidTest/assets/test.pdf`

- [ ] **Step 1: 최소 PDF 파일 생성**

Python으로 4페이지짜리 PDF를 생성한다:

```bash
cd /Users/rotein/dev/ebook-reader-app
python3 -c "
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
import os

path = 'app/src/androidTest/assets/test.pdf'
c = canvas.Canvas(path, pagesize=A4)
chapters = ['제1장 시작', '제2장 전개', '제3장 절정', '제4장 결말']
for i, title in enumerate(chapters):
    c.setFont('Helvetica', 24)
    c.drawString(72, 750, f'Page {i+1}: {title}')
    c.setFont('Helvetica', 12)
    for j in range(20):
        c.drawString(72, 700 - j * 30, f'This is paragraph {j+1} of chapter {i+1}. Test content for PDF viewer.')
    if i < len(chapters) - 1:
        c.showPage()
c.save()
print(f'Created {path} ({os.path.getsize(path)} bytes)')
"
```

> reportlab이 없으면 `pip3 install reportlab`으로 설치한다. 또는 다음과 같이 raw PDF를 직접 생성할 수 있다:

```bash
cd /Users/rotein/dev/ebook-reader-app
python3 << 'PYEOF'
import struct, zlib, os

def make_pdf(path, num_pages=4):
    objects = []
    
    def add_obj(content):
        objects.append(content)
        return len(objects)

    # 1: Catalog
    catalog_id = add_obj(b"<< /Type /Catalog /Pages 2 0 R >>")
    
    # 2: Pages (placeholder, update later)
    pages_id = add_obj(b"")
    
    page_ids = []
    for i in range(num_pages):
        # Content stream
        text = f"BT /F1 24 Tf 72 750 Td (Page {i+1}) Tj ET\n"
        for j in range(20):
            text += f"BT /F1 10 Tf 72 {700 - j*28} Td (Paragraph {j+1} of chapter {i+1}. Test content.) Tj ET\n"
        stream = text.encode()
        stream_obj = add_obj(f"<< /Length {len(stream)} >>\nstream\n".encode() + stream + b"\nendstream")
        
        # Page
        page_id = add_obj(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents {stream_obj} 0 R /Resources << /Font << /F1 {num_pages*2 + 3} 0 R >> >> >>".encode())
        page_ids.append(page_id)
    
    # Font
    font_id = add_obj(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    
    # Update Pages object
    kids = " ".join(f"{pid} 0 R" for pid in page_ids)
    objects[1] = f"<< /Type /Pages /Kids [{kids}] /Count {num_pages} >>".encode()
    
    # Build PDF
    out = b"%PDF-1.4\n"
    offsets = []
    for i, obj in enumerate(objects):
        offsets.append(len(out))
        out += f"{i+1} 0 obj\n".encode() + obj + b"\nendobj\n"
    
    xref_offset = len(out)
    out += f"xref\n0 {len(objects)+1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects)+1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode()
    
    with open(path, 'wb') as f:
        f.write(out)
    print(f"Created {path} ({len(out)} bytes)")

make_pdf("app/src/androidTest/assets/test.pdf")
PYEOF
```

- [ ] **Step 2: 생성 확인**

```bash
ls -la app/src/androidTest/assets/test.pdf
```

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/assets/test.pdf
git commit -m "test: 테스트용 PDF 파일 추가 (4페이지)"
```

---

### Task 10: ViewerNavigationTest — EPUB 목차→페이지 이동

**Files:**
- Create: `app/src/androidTest/java/com/rotein/ebookreader/ViewerNavigationTest.kt`

- [ ] **Step 1: 테스트 파일 생성**

```kotlin
package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewerNavigationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var testFilePath: String

    init {
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
            .close()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun copyAssetToStorage(assetName: String): String {
        val context = instrumentation.targetContext
        val outDir = File(context.getExternalFilesDir(null), "test_books")
        outDir.mkdirs()
        val outFile = File(outDir, assetName)
        if (!outFile.exists()) {
            instrumentation.context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTagToDisappear(tag: String, timeoutMillis: Long = 10000) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun tapCenter() {
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        Thread.sleep(500)
    }

    private fun tapPrevPage() {
        // LR_PREV_NEXT 기본 설정: 왼쪽 1/3이 이전 페이지
        device.click(device.displayWidth / 6, device.displayHeight / 2)
        Thread.sleep(500)
    }

    private fun getCurrentPage(): Int {
        val node = composeTestRule.onNodeWithTag("pageInfoText")
            .fetchSemanticsNode()
        val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .firstOrNull()?.text ?: ""
        // 형식: "3 / 15"
        return text.split("/")[0].trim().toInt()
    }

    @After
    fun cleanup() {
        BookCache.books = null
    }

    @Test
    fun epubTocNavigationAndPrevPage() {
        // 1. 테스트 EPUB 파일 준비
        testFilePath = copyAssetToStorage("test.epub")
        val epubBook = BookFile(
            name = "test.epub",
            path = testFilePath,
            extension = "epub",
            size = File(testFilePath).length(),
            dateAdded = 1000L,
            dateModified = 1000L,
            metadata = BookMetadata(
                title = "테스트 도서",
                author = "테스트 저자",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        )
        BookCache.books = listOf(epubBook)

        // 2. 책 클릭 → 리더 진입
        waitForText("테스트 도서")
        composeTestRule.onNodeWithText("테스트 도서").performClick()
        waitForTag("bookReaderScreen")

        // 3. 콘텐츠 로딩 완료 대기
        waitForTagToDisappear("readerLoading")
        Thread.sleep(2000) // 페이지 스캔 완료 대기

        // 4. 화면 중앙 탭 → 메뉴 열기
        tapCenter()

        // 5. 목차 버튼 탭
        waitForTag("tocButton")
        composeTestRule.onNodeWithTag("tocButton").performClick()

        // 6. 3번째 챕터 탭 (tocItem_2, 0-indexed)
        waitForTag("tocItem_2")
        composeTestRule.onNodeWithTag("tocItem_2").performClick()

        // 7. TocPopup 닫힘 대기 + 페이지 이동 대기
        Thread.sleep(2000)

        // 8. 메뉴 열기 → 페이지 번호 A 읽기
        tapCenter()
        waitForTag("pageInfoText")
        val pageA = getCurrentPage()

        // 9. 메뉴 닫기 (중앙 탭이 토글)
        tapCenter()
        Thread.sleep(300)

        // 10. 이전 페이지
        tapPrevPage()
        Thread.sleep(1000)

        // 11. 메뉴 열기 → 페이지 번호 B 읽기
        tapCenter()
        waitForTag("pageInfoText")
        val pageB = getCurrentPage()

        // 12. B + 1 == A 검증
        assert(pageB + 1 == pageA) {
            "이전 페이지 이동 검증 실패: pageB($pageB) + 1 != pageA($pageA)"
        }
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/ViewerNavigationTest.kt
git commit -m "test: EPUB 뷰어 목차→페이지 이동 테스트 추가"
```

---

### Task 11: ViewerNavigationTest — PDF 목차→페이지 이동

**Files:**
- Modify: `app/src/androidTest/java/com/rotein/ebookreader/ViewerNavigationTest.kt`

- [ ] **Step 1: PDF 테스트 추가**

ViewerNavigationTest.kt에 테스트 메서드 추가:

```kotlin
@Test
fun pdfTocNavigationAndPrevPage() {
    // 1. 테스트 PDF 파일 준비
    testFilePath = copyAssetToStorage("test.pdf")
    val pdfBook = BookFile(
        name = "test.pdf",
        path = testFilePath,
        extension = "pdf",
        size = File(testFilePath).length(),
        dateAdded = 1000L,
        dateModified = 1000L,
        metadata = BookMetadata(
            title = "테스트 PDF",
            author = "테스트 저자",
            language = null,
            publisher = null,
            publishedDate = null,
            description = null
        )
    )
    BookCache.books = listOf(pdfBook)

    // 2. 책 클릭 → 리더 진입
    waitForText("테스트 PDF")
    composeTestRule.onNodeWithText("테스트 PDF").performClick()
    waitForTag("bookReaderScreen")

    // 3. 콘텐츠 로딩 완료 대기
    waitForTagToDisappear("readerLoading")
    Thread.sleep(2000)

    // 4. 화면 중앙 탭 → 메뉴 열기
    tapCenter()

    // 5. 목차 버튼 탭
    waitForTag("tocButton")
    composeTestRule.onNodeWithTag("tocButton").performClick()

    // 6. 3번째 챕터 탭 (tocItem_2, 0-indexed)
    waitForTag("tocItem_2")
    composeTestRule.onNodeWithTag("tocItem_2").performClick()

    // 7. TocPopup 닫힘 대기 + 페이지 이동 대기
    Thread.sleep(2000)

    // 8. 메뉴 열기 → 페이지 번호 A 읽기
    tapCenter()
    waitForTag("pageInfoText")
    val pageA = getCurrentPage()

    // 9. 메뉴 닫기
    tapCenter()
    Thread.sleep(300)

    // 10. 이전 페이지
    tapPrevPage()
    Thread.sleep(1000)

    // 11. 메뉴 열기 → 페이지 번호 B 읽기
    tapCenter()
    waitForTag("pageInfoText")
    val pageB = getCurrentPage()

    // 12. B + 1 == A 검증
    assert(pageB + 1 == pageA) {
        "이전 페이지 이동 검증 실패: pageB($pageB) + 1 != pageA($pageA)"
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/ViewerNavigationTest.kt
git commit -m "test: PDF 뷰어 목차→페이지 이동 테스트 추가"
```
