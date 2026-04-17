# 홈 → 책 열기 Instrumented Test 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose UI Test를 도입하여 "홈 → 책 열기 → 뒤로가기" 플로우를 instrumented 테스트로 검증한다.

**Architecture:** BookCache에 더미 데이터를 주입하고, Compose UI Test로 화면 전환을 검증한다. 프로덕션 코드에는 최소한의 testTag만 추가한다.

**Tech Stack:** Compose UI Test (junit4), AndroidX Test Runner

---

## 파일 구조

| 파일 | 역할 |
|---|---|
| `gradle/libs.versions.toml` | 테스트 라이브러리 버전 추가 |
| `app/build.gradle.kts` | androidTestImplementation 의존성 추가 |
| `app/src/main/java/.../AllBooksScreen.kt` | BookItem에 testTag 추가 |
| `app/src/main/java/.../BookReaderScreen.kt` | 루트와 로딩에 testTag 추가 |
| `app/src/androidTest/java/.../HomeToReaderFlowTest.kt` | 테스트 파일 |

---

### Task 1: Compose UI Test 의존성 추가

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: libs.versions.toml에 테스트 라이브러리 추가**

`[libraries]` 섹션 끝에 추가:

```toml
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-test-runner = { group = "androidx.test", name = "runner", version = "1.6.2" }
```

> 버전은 Compose BOM이 관리하므로 compose-ui-test 계열은 version을 명시하지 않는다.

- [ ] **Step 2: build.gradle.kts에 의존성 추가**

`dependencies` 블록에 추가:

```kotlin
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.compose.ui.test.junit4)
androidTestImplementation(libs.androidx.test.runner)
debugImplementation(libs.compose.ui.test.manifest)
```

- [ ] **Step 3: Gradle Sync 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew app:dependencies --configuration androidTestRuntimeClasspath | grep ui-test`
Expected: `androidx.compose.ui:ui-test-junit4` 가 출력에 포함됨

- [ ] **Step 4: 커밋**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: Compose UI Test 의존성 추가"
```

---

### Task 2: 프로덕션 코드에 testTag 추가

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt:462-468`
- Modify: `app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt:75-78, 294-301`

- [ ] **Step 1: AllBooksScreen의 BookItem Row에 testTag 추가**

`AllBooksScreen.kt` 462행 부근, BookItem의 Row modifier에 testTag를 추가한다:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .testTag("bookItem_${book.name}")
        .padding(start = EreaderSpacing.L, top = 10.dp, bottom = 10.dp, end = EreaderSpacing.XS),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(EreaderSpacing.M)
)
```

import 추가:
```kotlin
import androidx.compose.ui.platform.testTag
```

- [ ] **Step 2: BookReaderScreen 루트에 testTag 추가**

`BookReaderScreen.kt` 75행 부근, 함수 본문의 최상위 컴포저블에 testTag를 추가한다. BookReaderScreen의 루트 레이아웃을 찾아 modifier에 `.testTag("bookReaderScreen")`을 추가한다.

- [ ] **Step 3: BookReaderScreen 로딩 인디케이터에 testTag 추가**

`BookReaderScreen.kt` 294-301행, CircularProgressIndicator를 감싸는 Box에 testTag 추가:

```kotlin
if (contentState.isLoading) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EreaderColors.White)
            .clickable(enabled = false) {}
            .testTag("readerLoading"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = EreaderColors.Black)
    }
}
```

import 추가:
```kotlin
import androidx.compose.ui.platform.testTag
```

- [ ] **Step 4: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/AllBooksScreen.kt app/src/main/java/com/rotein/ebookreader/BookReaderScreen.kt
git commit -m "test: BookItem, BookReaderScreen에 testTag 추가"
```

---

### Task 3: 테스트 파일 작성 — 책 목록 표시 확인

**Files:**
- Create: `app/src/androidTest/java/com/rotein/ebookreader/HomeToReaderFlowTest.kt`

- [ ] **Step 1: androidTest 디렉토리 생성**

```bash
mkdir -p app/src/androidTest/java/com/rotein/ebookreader
```

- [ ] **Step 2: 테스트 파일 작성 — 첫 번째 테스트**

`HomeToReaderFlowTest.kt` 작성:

```kotlin
package com.rotein.ebookreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeToReaderFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
                author = "테스트 저자",
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
                author = "테스트 저자 2",
                language = null,
                publisher = null,
                publishedDate = null,
                description = null
            )
        )
    )

    @Before
    fun setup() {
        BookCache.books = dummyBooks
    }

    @Test
    fun booksAreDisplayedOnHomeScreen() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("테스트 책 1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("테스트 책 2").assertIsDisplayed()
    }
}
```

> `createAndroidComposeRule<MainActivity>`를 사용하면 실제 Activity가 실행된다. `@Before`에서 BookCache.books를 세팅하면 AllBooksScreen의 LaunchedEffect에서 `BookCache.books != null` 조건에 걸려 FileScanner를 호출하지 않는다.

- [ ] **Step 3: 빌드 확인 (테스트 컴파일만)**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/HomeToReaderFlowTest.kt
git commit -m "test: 책 목록 표시 instrumented 테스트 추가"
```

---

### Task 4: 책 클릭 → 리더 화면 진입 테스트

**Files:**
- Modify: `app/src/androidTest/java/com/rotein/ebookreader/HomeToReaderFlowTest.kt`

- [ ] **Step 1: 리더 진입 테스트 추가**

`HomeToReaderFlowTest.kt`에 테스트 메서드 추가:

```kotlin
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

@Test
fun clickingBookOpensReaderScreen() {
    composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule
            .onAllNodesWithText("테스트 책 1")
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    composeTestRule.onNodeWithText("테스트 책 1").performClick()
    composeTestRule.onNodeWithTag("bookReaderScreen").assertIsDisplayed()
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/HomeToReaderFlowTest.kt
git commit -m "test: 책 클릭 시 리더 화면 진입 테스트 추가"
```

---

### Task 5: 뒤로가기 → 책 목록 복귀 테스트

**Files:**
- Modify: `app/src/androidTest/java/com/rotein/ebookreader/HomeToReaderFlowTest.kt`

- [ ] **Step 1: 뒤로가기 테스트 추가**

`HomeToReaderFlowTest.kt`에 테스트 메서드 추가:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist

@Test
fun pressingBackFromReaderReturnsToBookList() {
    composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule
            .onAllNodesWithText("테스트 책 1")
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    composeTestRule.onNodeWithText("테스트 책 1").performClick()
    composeTestRule.onNodeWithTag("bookReaderScreen").assertIsDisplayed()

    composeTestRule.activityRule.scenario.onActivity { activity ->
        activity.onBackPressedDispatcher.onBackPressed()
    }

    composeTestRule.onNodeWithTag("bookReaderScreen").assertDoesNotExist()
    composeTestRule.onNodeWithText("테스트 책 1").assertIsDisplayed()
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/rotein/dev/ebook-reader-app && ./gradlew assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add app/src/androidTest/java/com/rotein/ebookreader/HomeToReaderFlowTest.kt
git commit -m "test: 뒤로가기 시 책 목록 복귀 테스트 추가"
```
