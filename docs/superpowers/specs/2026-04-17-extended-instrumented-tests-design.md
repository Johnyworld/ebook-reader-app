# 확장 Instrumented 테스트 설계

## 목적

AllBooksScreen의 핵심 기능과 EPUB/PDF 뷰어의 목차 → 페이지 이동 플로우를 instrumented 테스트로 검증한다.

## 범위

### Part 1: AllBooksScreen 테스트 (6개)

1. **빈 목록 상태** — BookCache.books = emptyList() → "비어있음" 표시 확인
2. **메타데이터 표시** — 책 제목뿐 아니라 저자명도 화면에 표시되는지 확인
3. **검색 필터** — 검색어 입력 → 매칭되는 책만 표시
4. **정렬** — 정렬 기준 변경 → 목록 순서 변경
5. **즐겨찾기 토글** — 3-dot 메뉴 → 즐겨찾기 → 필터 변경 시 반영
6. **스플래시 → 목록 전환** — 앱 시작 시 스플래시 후 목록 표시

### Part 2: 뷰어 목차 → 페이지 이동 테스트 (2개: EPUB, PDF)

목차에서 임의의 챕터 탭 → 현재 페이지 A 저장 → 이전 페이지 → 현재 페이지 B 저장 → B + 1 == A 검증

## 접근 방식

### Part 1: 기존 더미 데이터 방식 유지

BookCache에 가짜 BookFile 리스트를 세팅하여 AllBooksScreen UI 인터랙션을 검증한다. 기존 HomeToReaderFlowTest와 동일한 패턴.

### Part 2: 테스트 전용 파일 + UIAutomator

- `androidTest/assets/`에 최소한의 EPUB/PDF 파일을 직접 만들어 포함
- 테스트 시작 시 assets에서 에뮬레이터 내부 저장소로 복사
- BookCache에 해당 경로의 BookFile 세팅
- UIAutomator로 좌표 기반 탭 (이전 페이지 = 화면 왼쪽 1/3)
- Compose Test로 목차, 메뉴, 페이지 번호 등 Compose UI 검증

## 필요한 변경

### 의존성 추가

- `androidx.test.uiautomator:uiautomator` (뷰어 테스트용)

### testTag 추가 (프로덕션 코드)

| 컴포저블 | 태그 | 파일 |
|---|---|---|
| 검색 입력 BasicTextField | `"searchInput"` | AllBooksScreen.kt TopBar |
| 목차 버튼 | `"tocButton"` | BookReaderScreen.kt 메뉴바 |
| 페이지 번호 텍스트 | `"pageInfoText"` | BookReaderScreen.kt 메뉴바 |
| TocPopup 아이템 Row | `"tocItem_{index}"` | TocPopup.kt |

### 테스트용 파일 생성

- `app/src/androidTest/assets/test.epub` — 4챕터, 충분한 텍스트 (각 챕터 1000자 이상)
- `app/src/androidTest/assets/test.pdf` — 4페이지

### 테스트 파일

- `AllBooksScreenTest.kt` — Part 1 테스트 6개
- `ViewerNavigationTest.kt` — Part 2 테스트 2개 (EPUB, PDF)

## Part 1 테스트 상세

### 테스트 1: 빈 목록 상태

- BookCache.books = emptyList()
- Activity 실행
- "비어있음" 텍스트 표시 확인

### 테스트 2: 메타데이터 표시

- 기존 더미 BookFile (metadata.author = "테스트 저자")
- Activity 실행
- "테스트 저자" 텍스트 표시 확인

### 테스트 3: 검색 필터

- 더미 BookFile 2개 세팅
- 검색 아이콘 클릭 → 검색 입력 필드 활성화
- "책 1" 입력
- "테스트 책 1" 표시됨, "테스트 책 2" 미표시 확인

### 테스트 4: 정렬

- 더미 BookFile 2개 (저자: "가나다", "라마바")
- 정렬 드롭다운에서 AUTHOR 선택
- 첫 번째 아이템이 "가나다" 저자인지 확인

### 테스트 5: 즐겨찾기 토글

- 더미 BookFile 세팅
- 첫 번째 책의 3-dot 메뉴 클릭
- "즐겨찾기" 텍스트 클릭
- 필터를 FAVORITE로 변경
- 해당 책이 표시되는지 확인

### 테스트 6: 스플래시 → 목록 전환

- BookCache.books 세팅
- Activity 실행 직후 "ebook-reader" 스플래시 텍스트 확인
- 일정 시간 후 스플래시 사라지고 책 목록 표시 확인

## Part 2 테스트 상세

### 공통 플로우 (EPUB, PDF 동일)

1. 테스트 전용 파일을 assets에서 내부 저장소로 복사
2. BookCache.books에 해당 파일의 BookFile 세팅
3. 책 클릭 → 리더 진입
4. 콘텐츠 로딩 완료 대기 (readerLoading testTag 사라질 때까지)
5. UIAutomator로 화면 중앙 탭 → 메뉴 열기
6. 목차 버튼 (tocButton) 탭 → TocPopup 표시
7. 2번째 또는 3번째 챕터 (tocItem_2) 탭 → 페이지 이동, TocPopup 닫힘
8. UIAutomator로 화면 중앙 탭 → 메뉴 열기
9. pageInfoText에서 현재 페이지 번호 A 파싱
10. UIAutomator로 화면 왼쪽 1/3 탭 → 이전 페이지
11. UIAutomator로 화면 중앙 탭 → 메뉴 열기
12. pageInfoText에서 현재 페이지 번호 B 파싱
13. assert(B + 1 == A)

### 페이지 번호 파싱

BookReaderScreen 메뉴바에 표시되는 텍스트 형식: `"{currentPage} / {totalPages}"`
→ "/" 기준 split 후 첫 번째 값을 Int로 파싱

### UIAutomator 좌표 계산

```kotlin
val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
val screenWidth = device.displayWidth
val screenHeight = device.displayHeight

// 메뉴 열기 (화면 중앙)
device.click(screenWidth / 2, screenHeight / 2)

// 이전 페이지 (LR_PREV_NEXT 기본 설정 기준: 왼쪽 1/3)
device.click(screenWidth / 6, screenHeight / 2)
```

## 제약 사항

- EPUB 페이지 수는 화면 크기/글꼴에 따라 달라짐 → 절대값 아닌 상대 비교(B + 1 == A)로 검증
- 콘텐츠 로딩 시간이 필요하므로 waitUntil 타임아웃 10초
- UIAutomator 좌표 기반 탭은 기기 해상도에 따라 다를 수 있으나, displayWidth/displayHeight 기반 비율 계산으로 대응
- 페이지 넘기기 방향 설정(LR_PREV_NEXT)이 기본값이라고 가정
