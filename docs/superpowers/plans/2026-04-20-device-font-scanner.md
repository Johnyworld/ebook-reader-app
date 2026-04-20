# 기기 저장소 폰트 스캔 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기기 외부 저장소의 TTF/OTF 폰트 파일을 스캔하여 "글꼴 불러오기" 탭에 시스템 폰트와 통합 표시한다.

**Architecture:** `FontScanner` 객체가 외부 저장소를 재귀 스캔하여 폰트 파일 맵을 반환한다. 기존 `getSystemFontFileMap()`이 `FontScanner` 결과를 시스템 폰트와 합쳐서 반환하도록 수정한다. `extractFontFamilyName()`을 `FontScanner`에서도 재사용하기 위해 `internal` 가시성으로 변경한다.

**Tech Stack:** Kotlin, Android SDK (`Environment.getExternalStorageDirectory()`, `SystemFonts`)

---

## 파일 구조

| 파일 | 변경 | 역할 |
|------|------|------|
| `app/src/main/java/com/rotein/ebookreader/FontScanner.kt` | 생성 | 외부 저장소 TTF/OTF 파일 재귀 스캔 |
| `app/src/main/java/com/rotein/ebookreader/SortPreference.kt` | 수정 | `extractFontFamilyName()` 가시성 변경, `getSystemFontFileMap()`에 기기 폰트 병합 |
| `app/src/test/java/com/rotein/ebookreader/FontScannerTest.kt` | 생성 | `FontScanner.scanFontFiles()` 단위 테스트 |

---

### Task 1: extractFontFamilyName 가시성 변경

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/SortPreference.kt:168`

- [ ] **Step 1: `extractFontFamilyName`을 `private`에서 `internal`로 변경**

`SortPreference.kt:168` 에서:

```kotlin
private fun extractFontFamilyName(fileName: String): String {
```

→

```kotlin
internal fun extractFontFamilyName(fileName: String): String {
```

- [ ] **Step 2: 기존 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.SortPreferenceTest" --console=plain`
Expected: PASS (기존 테스트에 영향 없음)

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/SortPreference.kt
git commit -m "refactor: extractFontFamilyName 가시성을 internal로 변경"
```

---

### Task 2: FontScanner 구현 (TDD)

**Files:**
- Create: `app/src/test/java/com/rotein/ebookreader/FontScannerTest.kt`
- Create: `app/src/main/java/com/rotein/ebookreader/FontScanner.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/rotein/ebookreader/FontScannerTest.kt`:

```kotlin
package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FontScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setup() {
        root = tempFolder.root
    }

    @Test
    fun `ttf 파일을 발견하면 맵에 포함한다`() {
        File(root, "MyFont-Regular.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("My Font"))
    }

    @Test
    fun `otf 파일을 발견하면 맵에 포함한다`() {
        File(root, "NotoSans-Bold.otf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("Noto Sans"))
    }

    @Test
    fun `같은 패밀리의 여러 weight는 하나만 포함한다`() {
        File(root, "MyFont-Regular.ttf").createNewFile()
        File(root, "MyFont-Bold.ttf").createNewFile()
        File(root, "MyFont-Italic.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("My Font"))
    }

    @Test
    fun `하위 디렉토리도 재귀 스캔한다`() {
        val subDir = File(root, "Fonts").also { it.mkdirs() }
        File(subDir, "DeepFont-Regular.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("Deep Font"))
    }

    @Test
    fun `숨김 폴더는 제외한다`() {
        val hidden = File(root, ".hidden").also { it.mkdirs() }
        File(hidden, "SecretFont-Regular.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ttf otf 외 확장자는 무시한다`() {
        File(root, "NotAFont.txt").createNewFile()
        File(root, "Image.png").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `빈 디렉토리는 빈 맵을 반환한다`() {
        val result = FontScanner.scanFontFiles(root)
        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FontScannerTest" --console=plain`
Expected: FAIL — `FontScanner` 클래스가 존재하지 않음

- [ ] **Step 3: FontScanner 구현**

`app/src/main/java/com/rotein/ebookreader/FontScanner.kt`:

```kotlin
package com.rotein.ebookreader

import android.os.Environment
import java.io.File

object FontScanner {

    private val FONT_EXTENSIONS = setOf("ttf", "otf")

    fun scanDeviceFonts(): Map<String, String> {
        val root = Environment.getExternalStorageDirectory()
        return scanFontFiles(root)
    }

    internal fun scanFontFiles(root: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        scanDirectory(root, result)
        return result
    }

    private fun scanDirectory(dir: File, result: MutableMap<String, String>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.startsWith(".")) continue
                scanDirectory(file, result)
            } else {
                val ext = file.extension.lowercase()
                if (ext in FONT_EXTENSIONS) {
                    val name = extractFontFamilyName(file.nameWithoutExtension)
                    if (name.isNotBlank() && !result.containsKey(name)) {
                        result[name] = file.absolutePath
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rotein.ebookreader.FontScannerTest" --console=plain`
Expected: 7 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/FontScanner.kt \
       app/src/test/java/com/rotein/ebookreader/FontScannerTest.kt
git commit -m "feat: FontScanner - 기기 저장소 TTF/OTF 파일 재귀 스캔"
```

---

### Task 3: getSystemFontFileMap()에 기기 폰트 병합

**Files:**
- Modify: `app/src/main/java/com/rotein/ebookreader/SortPreference.kt:155-166`

- [ ] **Step 1: `getSystemFontFileMap()`에 기기 폰트 병합 로직 추가**

`SortPreference.kt`의 `getSystemFontFileMap()` 함수를 다음으로 교체:

```kotlin
fun getSystemFontFileMap(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    // 시스템 폰트 (Android 10+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        SystemFonts.getAvailableFonts().forEach { font ->
            val file = font.file ?: return@forEach
            val name = extractFontFamilyName(file.nameWithoutExtension)
            if (name.isNotBlank() && !result.containsKey(name)) {
                result[name] = file.absolutePath
            }
        }
    }
    // 기기 저장소 폰트 (시스템 폰트와 이름 충돌 시 시스템 폰트 우선)
    FontScanner.scanDeviceFonts().forEach { (name, path) ->
        if (!result.containsKey(name)) {
            result[name] = path
        }
    }
    return result
}
```

- [ ] **Step 2: 기존 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: 전체 PASS

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/rotein/ebookreader/SortPreference.kt
git commit -m "feat: getSystemFontFileMap()에 기기 저장소 폰트 병합"
```
