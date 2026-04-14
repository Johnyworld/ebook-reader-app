# PDF 뷰어 설계

## 개요

EPUB 뷰어와 동일한 WebView + JS Bridge 아키텍처로 PDF 뷰어를 구현한다.
PDF.js(Mozilla)를 렌더링 엔진으로 사용하며, 기존 UI 컴포넌트를 최대한 재사용한다.

## 범위

### 포함

- 페이지 렌더링 (fit-to-page, 단일 페이지 모드)
- 페이지 넘기기 (좌우/상하 스와이프, 방향 설정 가능)
- 북마크
- 목차(TOC) 네비게이션
- 본문 검색
- 하단 좌/우 정보 영역 (설정에서 표시 항목 변경 가능)
- 페이지 넘김 방향 설정
- 읽기 위치 저장/복원

### 제외

- 텍스트 선택 → 하이라이트/메모
- 듀얼 페이지 모드
- 폰트/줄간격/여백 등 텍스트 관련 리더 설정 (PDF는 고정 레이아웃)
- 핀치 줌

## 아키텍처

```
PdfViewer.kt (Composable)
  └─ WebView
       └─ PDF HTML Template
            ├─ pdf.js (렌더링 엔진)
            └─ JS 모듈들
                 ├─ init.js (PDF 로드, 페이지 렌더링)
                 ├─ navigation.js (페이지 넘기기)
                 ├─ search.js (본문 검색)
                 └─ location.js (현재 위치 추적)
  └─ PdfBridge.kt (JS → Kotlin 콜백)
```

### 파일 구조

| 파일 | 역할 |
|------|------|
| `PdfViewer.kt` | WebView 생성, 제스처(탭/스와이프) 처리, 메뉴 토글 |
| `PdfBridge.kt` | JS → Kotlin 콜백 인터페이스 (`onPageChanged`, `onTocLoaded`, `onSearchResult` 등) |
| `PdfHtmlTemplate.kt` | PDF.js를 포함한 HTML 템플릿 생성 |
| `assets/pdf.js` | PDF.js 라이브러리 번들 |
| `assets/pdf/js/*.js` | PDF 전용 JS 모듈 (init, navigation, search, location) |

### 기존 코드 재사용

- `BookReaderViewModel` — popupState, 북마크 상태, 검색 상태 관리
- `BookReaderScreen` — 메뉴바, 헤더바, 팝업 오버레이 구조
- `TocPopup` — 목차 UI
- `SearchPopup` — 검색 UI
- `BookmarkPopup` — 북마크 목록 UI
- `ReaderSettingsSheet` — 페이지 넘김 방향, 하단 정보 설정 항목만 표시

## 상세 설계

### 1. 페이지 렌더링

- PDF.js로 단일 페이지를 `<canvas>`에 렌더링
- fit-to-page: `scale = Math.min(viewWidth / pageWidth, viewHeight / pageHeight)`
- 페이지 전환 시 이전 canvas를 지우고 새 페이지 렌더링
- e-ink 최적화: 배경 흰색, 애니메이션 없음

### 2. 페이지 네비게이션

- EPUB과 동일한 제스처 로직 재사용
  - 좌우 또는 상하 스와이프로 페이지 넘김
  - 넘김 방향은 설정에서 변경 가능
  - 중앙 탭으로 메뉴/헤더 토글
- 페이지 번호는 PDF 고유 페이지 번호 사용 (1-indexed)

### 3. 북마크

- EPUB의 CFI 대신 페이지 번호를 키로 사용
- 기존 `bookmarks` Room 테이블 공유
- `cfi` 필드에 `"pdf-page:{pageNumber}"` 포맷 저장
- 북마크 목록에서 탭하면 해당 페이지로 이동

### 4. 목차 (TOC)

- PDF.js `getOutline()` API로 문서 outline 추출
- outline은 PDF 작성 시 저자가 명시적으로 넣은 목차 트리 구조
- 기존 `TocPopup` UI 재사용
- outline이 없는 PDF는 TOC 버튼 비활성화

### 5. 본문 검색

- PDF.js 내장 텍스트 레이어 + 검색 기능 활용
- 검색 결과를 JS Bridge로 Kotlin에 전달 (매치 텍스트, 페이지 번호)
- 기존 `SearchPopup` UI 재사용
- 결과 선택 시 해당 페이지로 이동 + 텍스트 하이라이트 표시

### 6. 읽기 위치 저장/복원

- `book_read_records` 테이블의 `lastCfi` 필드에 `"pdf-page:{pageNumber}"` 포맷으로 저장
- 앱 재시작 시 마지막 페이지에서 이어서 읽기

### 7. 하단 정보 영역

- 좌/우 각각 표시 항목을 설정에서 선택 가능 (EPUB 뷰어와 동일)
- 선택 가능 항목: 현재 페이지/전체 페이지, 진행률(%), 시간, 배터리 등

### 8. 설정

PDF 뷰어에서 노출하는 설정 항목:
- 페이지 넘김 방향 (좌→우, 우→좌, 위→아래, 아래→위)
- 하단 좌/우 정보 표시 항목

EPUB 전용 설정(폰트, 줄간격, 여백, 텍스트 정렬 등)은 PDF에서 숨김.

## 데이터 저장 포맷

PDF에서는 EPUB의 CFI 대신 `"pdf-page:{pageNumber}"` 문자열을 사용한다.
이 포맷은 다음 테이블에서 공유:

| 테이블 | 필드 | 용도 |
|--------|------|------|
| `book_read_records` | `lastCfi` | 마지막 읽은 페이지 |
| `bookmarks` | `cfi` | 북마크된 페이지 |
