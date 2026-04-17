# Ebook Reader

E-ink 디바이스에 최적화된 Android 전자책 리더 앱.

## 주요 기능

- **다중 포맷 지원** — EPUB, PDF
- **독서 관리** — 북마크, 하이라이트, 메모, 읽기 진행률 추적
- **목차 네비게이션** — 현재 챕터 표시 및 빠른 이동
- **본문 검색** — 전자책 내 텍스트 전문 검색
- **서재 관리** — 검색, 정렬, 필터, 즐겨찾기, 숨김
- **커스텀 읽기 환경** — 글꼴, 크기, 자간, 여백, 정렬, 페이지 넘김 방식

## 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 데이터베이스 | Room |
| EPUB 렌더링 | WebView |
| PDF 렌더링 | WebView + PDF.js |
| 비동기 처리 | Kotlin Coroutines |
| 빌드 | Gradle (KSP) |

## 요구 사항

- Android 8.0 (API 26) 이상
- Android Studio Ladybug 이상

## 빌드

```bash
git clone https://github.com/Johnyworld/ebook-reader-app.git
cd ebook-reader-app
./gradlew assembleDebug
```

## 프로젝트 구조

```
app/src/main/java/com/rotein/ebookreader/
├── MainActivity.kt              # 앱 진입점
├── HomeScreen.kt                # 서재 화면
├── BookReaderScreen.kt          # 리더 화면
├── BookReaderViewModel.kt       # 리더 상태 관리
├── BookDatabase.kt              # Room DB (북마크, 하이라이트, 메모)
├── FileScanner.kt               # 기기 내 도서 탐색
├── *MetadataParser.kt           # 포맷별 메타데이터 파싱
└── reader/                      # 포맷별 뷰어 및 브릿지
    ├── EpubViewer.kt
    ├── PdfViewer.kt
    └── *Popup.kt                # TOC, 검색 등 팝업 UI
```

## 테스트

```bash
# 단위 테스트
./gradlew test

# 인스트루먼트 테스트 (기기/에뮬레이터 필요)
./gradlew connectedAndroidTest
```

- 메타데이터 파서, 헬퍼 함수, DB 마이그레이션 등 순수 로직은 단위 테스트로 커버
- UI 관련 코드는 Compose UI Test + UIAutomator로 검증
- 테스트 프레임워크: JUnit 4, MockK, Robolectric

## 디자인 원칙

- E-ink 최적화: 애니메이션 없음, 흰색 배경, 최소한의 UI
- 미니멀한 인터페이스로 독서에 집중

## 라이선스

MIT
