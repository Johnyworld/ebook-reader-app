# Cross-Page Highlight Design

## Overview

현재 페이지의 마지막 텍스트까지 선택한 경우, "이어하기" 버튼을 통해 다음 페이지로 넘어가 선택 범위를 확장하고, 두 페이지에 걸친 하나의 하이라이트를 생성한다.

## Constraints

- 최대 2페이지까지만 (연쇄 이어하기 불가)
- epub.js의 CFI 범위 합성 방식 사용 (DB 스키마 변경 없음)
- 기존 단일 페이지 하이라이트 흐름에 영향 없어야 함

## User Flow

### 1. 선택 완료 -> 팝오버 표시

- 기존처럼 텍스트 선택 후 탭하면 팝오버 표시
- JavaScript에서 선택 범위의 끝이 현재 페이지 visible area의 마지막 텍스트 노드 끝과 일치하는지 판단
- 일치하면 팝오버에 **"이어하기"** 버튼 추가 표시 (기존 하이라이트/메모/공유와 함께)

### 2. "이어하기" 클릭

- 현재 선택의 시작 CFI를 Android 측 상태(`pendingStartCfi`, `pendingStartText`)에 저장
- `rendition.next()`로 다음 페이지 이동
- 이동 완료 후 JavaScript에서 다음 페이지 첫 번째 텍스트 노드의 첫 글자를 프로그래밍 방식으로 선택 (`Range.setStart/setEnd`)
- 선택 핸들이 보이는 상태

### 3. 사용자가 끝 핸들 드래그로 범위 조정

- 사용자가 원하는 위치까지 핸들을 드래그
- 이 상태에서 탭하면 팝오버 표시
- 이어하기 모드이므로 "이어하기" 버튼 없음 (최대 2페이지 제한)

### 4. 하이라이트 생성

- 팝오버에서 "하이라이트" 클릭
- JavaScript에서 현재 선택의 끝 CFI 추출
- `pendingStartCfi`의 시작점 + 현재 선택의 끝점을 합쳐 하나의 범위 CFI 생성
- 이 합성 CFI로 `rendition.annotations.add()` 호출
- DB에는 기존과 동일하게 `cfi` 컬럼 하나에 저장
- `text`는 두 페이지 텍스트 합산

## Technical Changes

### JavaScript (HTML template in BookReaderScreen.kt)

#### `_isSelectionAtPageEnd()`
- iframe의 contentDocument에서 현재 selection의 Range를 가져옴
- 현재 페이지의 visible area 내 마지막 텍스트 노드를 찾음
- selection의 endContainer/endOffset이 해당 노드의 끝과 일치하는지 반환

#### `_selectFirstCharOfPage()`
- 현재 페이지의 visible area 내 첫 번째 텍스트 노드를 찾음
- `Range.setStart/setEnd`로 첫 글자를 선택
- `window.getSelection().addRange()`로 선택 적용

#### `_mergeCfi(startCfi, endCfi)`
- 두 CFI에서 각각 범위의 시작점과 끝점을 추출
- startCfi의 시작점 + endCfi의 끝점으로 새로운 범위 CFI 문자열 합성
- 같은 spine item 내에서만 동작 (크로스 spine은 지원하지 않음)

#### `onSelectionTapped` 콜백 변경
- 기존 파라미터에 `isAtPageEnd: Boolean` 추가
- `_isSelectionAtPageEnd()` 결과를 함께 전달

### Android (Kotlin - BookReaderScreen.kt)

#### 새로운 상태
- `pendingStartCfi: String?` - 이어하기 시작 CFI
- `pendingStartText: String?` - 이어하기 시작 텍스트
- `isContinuationMode: Boolean` - 이어하기 모드 여부

#### SelectionState 변경
- `isAtPageEnd: Boolean` 필드 추가

#### EpubBridge 변경
- `onSelectionTapped`에 `isAtPageEnd` 파라미터 추가

#### SelectionPopup 변경
- `isAtPageEnd == true`일 때 "이어하기" 버튼 표시
- `isContinuationMode == true`일 때 "이어하기" 버튼 숨김

#### "이어하기" 핸들러
1. `pendingStartCfi` = 현재 selection의 시작 CFI 저장
2. `pendingStartText` = 현재 selection의 텍스트 저장
3. `selectionState` 초기화
4. `isContinuationMode = true`
5. `webView.evaluateJavascript("window._next()")` 호출
6. 페이지 이동 완료 후 `webView.evaluateJavascript("window._selectFirstCharOfPage()")` 호출

#### 하이라이트 생성 변경 (onHighlight)
- `isContinuationMode`일 때:
  - `text` = `pendingStartText + text` (합산)
  - CFI = `window._mergeCfi(pendingStartCfi, currentCfi)` 결과 사용
  - 저장 후 `pendingStartCfi`, `pendingStartText` 초기화, `isContinuationMode = false`

### Database

변경 없음. 합성된 CFI가 기존 `cfi` 컬럼에 그대로 저장됨.
