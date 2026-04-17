# Instrumented Test: 홈 → 책 열기 플로우

## 목적

Compose UI Test를 도입하여 "홈 화면에서 책을 선택하면 리더 화면이 표시되는" 핵심 플로우를 자동화된 UI 테스트로 검증한다.

## 범위

- 책 목록 표시 확인
- 책 아이템 클릭 → BookReaderScreen 진입 확인
- 뒤로가기 → 책 목록 복귀 확인
- WebView 내부 콘텐츠는 검증하지 않음

## 접근 방식

### 데이터 주입: BookCache 직접 세팅

`BookCache.books`가 전역 volatile 변수이므로, 테스트 시작 전에 더미 `BookFile` 리스트를 직접 세팅한다. DI 프레임워크를 도입하지 않는다.

FileScanner가 백그라운드에서 실행되어 데이터를 덮어쓸 수 있으므로, AllBooksScreen에서 `BookCache.books`가 이미 비어있지 않으면 스캔을 건너뛰는 조건을 활용하거나, 테스트용 Activity에서 스캔을 차단한다.

### 테스트 프레임워크: Compose UI Test

`androidx.compose.ui.test.junit4`를 사용한다. 에뮬레이터 또는 실기기에서 실행된다.

## 필요한 변경

### 1. 의존성 추가 (build.gradle.kts)

```kotlin
androidTestImplementation(libs.compose.ui.test.junit4)
debugImplementation(libs.compose.ui.test.manifest)
```

libs.versions.toml에 해당 라이브러리 정의도 추가한다.

### 2. testTag 추가 (프로덕션 코드)

최소한의 testTag만 추가한다:

| 컴포저블 | 태그 | 위치 |
|---|---|---|
| BookItem의 Row | `"bookItem_${book.name}"` | AllBooksScreen.kt |
| BookReaderScreen 루트 | `"bookReaderScreen"` | BookReaderScreen.kt |
| 로딩 인디케이터 | `"readerLoading"` | BookReaderScreen.kt |

### 3. androidTest 디렉토리 생성

```
app/src/androidTest/java/com/rotein/ebookreader/
  HomeToReaderFlowTest.kt
```

## 테스트 시나리오

### 테스트 1: 책 목록에 더미 책이 표시됨

1. BookCache.books에 더미 BookFile 2개 세팅
2. HomeScreen 렌더링
3. 책 제목 텍스트가 화면에 존재하는지 확인

### 테스트 2: 책 클릭 → 리더 화면 진입

1. BookCache.books에 더미 BookFile 세팅
2. HomeScreen 렌더링
3. 첫 번째 책 아이템 클릭
4. `bookReaderScreen` testTag가 화면에 존재하는지 확인

### 테스트 3: 뒤로가기 → 책 목록 복귀

1. 테스트 2 수행 후
2. 뒤로가기 수행
3. 책 목록이 다시 표시되는지 확인

## 제약 사항

- BookReaderScreen 내부의 WebView 렌더링은 검증하지 않음
- 파일 시스템 스캔은 우회하므로 실제 EPUB 파일 불필요
- 에뮬레이터/실기기에서만 실행 가능 (JVM 로컬 테스트 불가)
