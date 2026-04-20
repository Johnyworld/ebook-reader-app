# 기기 저장소 폰트 스캔 기능

## 문제

"글꼴 불러오기" 탭이 `SystemFonts.getAvailableFonts()` API만 사용하여 시스템 폰트만 표시한다. Onix Palma2 같은 e-ink 기기에서 `/Fonts` 등 외부 저장소에 넣어둔 사용자 TTF/OTF 파일이 목록에 나타나지 않는다.

## 해결 방안

외부 저장소를 재귀 스캔하여 TTF/OTF 파일을 발견하고, 시스템 폰트와 하나의 목록으로 합쳐서 "글꼴 불러오기" 탭에 표시한다.

## 구현 상세

### 1. FontScanner 객체 추가

- `FileScanner`와 동일한 패턴으로 `FontScanner` 객체를 새로 만든다.
- `Environment.getExternalStorageDirectory()`부터 재귀 스캔한다.
- 숨김 폴더(`.`으로 시작)는 제외한다.
- 확장자 `ttf`, `otf` 파일을 수집한다.
- 파일명에서 기존 `extractFontFamilyName()` 함수를 재사용하여 폰트 패밀리명을 추출한다.
- 결과: `Map<String, String>` (폰트명 -> 파일 절대경로). 같은 패밀리명이 여러 파일에 있으면 첫 번째 파일을 사용한다.

### 2. getSystemFontFileMap() 수정

- 기존 시스템 폰트 맵에 `FontScanner` 결과를 합친다.
- 이름 충돌 시 시스템 폰트를 우선한다 (중복 방지).
- 이미 임포트된 폰트는 `FontLayerPopup`에서 기존 로직으로 필터링되므로 추가 처리 불필요.

### 3. FontLayerPopup 변경 없음

- `getSystemFontFileMap()` 반환값에 기기 폰트가 포함되므로 UI 코드 변경이 필요 없다.

### 4. 권한

- 이미 `MANAGE_EXTERNAL_STORAGE` 권한을 선언하고 있으므로 추가 권한 불필요.
- 권한이 부여되지 않은 상태에서는 기기 폰트를 빈 맵으로 반환한다 (`listFiles()`가 null 반환).

## 영향 범위

- 새 파일: `FontScanner.kt`
- 수정 파일: `SortPreference.kt` (`getSystemFontFileMap()`)
- UI 변경 없음
