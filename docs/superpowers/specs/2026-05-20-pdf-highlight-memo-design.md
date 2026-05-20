# PDF 뷰어 하이라이트/메모 기능 설계

## 개요

PDF 뷰어에서 텍스트 선택 기반 하이라이트 및 메모 기능을 추가한다. PDF.js 텍스트 레이어 + Selection API를 사용하며, EPUB 뷰어와 동일한 UX를 제공한다.

## 접근법

PDF.js가 렌더링하는 텍스트 레이어에서 브라우저 네이티브 Selection API로 텍스트를 선택하고, 선택된 범위를 페이지번호 + 텍스트 아이템 인덱스 + 문자 오프셋으로 저장한다.

### 선택한 이유

- 검색 기능(`search.js`)이 이미 텍스트 레이어를 활용 중이라 자연스러운 확장
- 텍스트 단위로 정확한 위치 지정 가능
- 줌/리사이즈 시 텍스트 레이어 리렌더링으로 자동 대응
- EPUB 뷰어와 동일한 UX(롱프레스 → 텍스트 선택 → 하이라이트/메모)

### 트레이드오프

- 텍스트 레이어가 없는 스캔 PDF에서는 동작하지 않음

## 위치 저장 포맷

기존 `cfi` 필드에 커스텀 포맷으로 저장한다.

```
pdf:<pageNum>:<startItemIdx>:<startCharIdx>:<endItemIdx>:<endCharIdx>
```

- `pageNum`: PDF 페이지 번호 (1-based)
- `startItemIdx` / `endItemIdx`: `page.getTextContent().items` 배열의 인덱스
- `startCharIdx` / `endCharIdx`: 해당 텍스트 아이템 내 문자 오프셋

PDF.js의 `textContent.items`는 같은 PDF 파일에 대해 항상 동일한 순서를 반환하므로, 텍스트 아이템 인덱스 + 문자 오프셋이면 위치를 정확히 복원할 수 있다.

DB 마이그레이션 불필요.

## JavaScript 레이어

### `assets/pdf/js/selection.js` (신규)

- PDF.js 텍스트 레이어 활성화: `page.render()` 후 `pdfjsLib.renderTextLayer()` 호출
- 텍스트 레이어를 canvas 위에 투명하게 배치 (기존 `pdf-wrapper` 안)
- `selectionchange` 이벤트 → `Android.onTextSelected(text)` 호출
- `_getLocationFromSelection()`: 현재 선택 범위를 위치 포맷 문자열로 변환
- `_clearSelection()`: 선택 해제

### `assets/pdf/js/annotation.js` (신규)

- `_addHighlight(locationStr, id)`: 위치 문자열 파싱 → 텍스트 아이템의 `transform` + `viewport`로 좌표 계산 → `div` 오버레이 생성 (배경색 `#baffc6`, EPUB과 동일)
- `_removeHighlight(id)`: 해당 오버레이 제거
- `_applyHighlights(json)`: 저장된 하이라이트 목록 일괄 적용
- `_addMemo(locationStr, id)` / `_removeMemo(id)` / `_applyMemos(json)`: 밑줄 스타일(`#000000`)로 동일 패턴
- `_getAnnotationAtPoint(x, y)`: 좌표로 하이라이트/메모 탐색 (롱프레스 시 사용)
- 페이지 전환 시 `_renderPage()` 끝에서 현재 페이지의 하이라이트/메모를 재적용

### 좌표 계산

`search.js`의 `_applySearchHighlights()`와 동일한 방식:
- `textContent.items[i].transform`을 `viewport.convertToViewportPoint()`로 변환
- 문자 단위 오프셋은 `item.str.length` 대비 비율로 x 좌표를 보간

### 줌 대응

- 줌 시 `_renderPage()`가 다시 호출되면 하이라이트도 새 viewport 기준으로 재생성
- 줌만 변경되고 페이지가 안 바뀌는 경우에는 `_restoreZoom()` 시점에 하이라이트 오버레이도 CSS transform으로 함께 스케일

## Kotlin 레이어

### `PdfBridge.kt` — 콜백 추가

- `onTextSelected(text: String)` — 텍스트 선택 시
- `onAnnotationLongPress(json: String)` — 하이라이트/메모 롱프레스 시 (type, id, 좌표 포함)

### `PdfViewer.kt` — 파라미터 + 터치 처리 변경

새 콜백 파라미터 추가 (EPUB 뷰어와 동일 패턴):
- `onTextSelected: (text: String) -> Unit`
- `onHighlight: (text: String, location: String) -> Unit`
- `onMemo: (text: String, location: String) -> Unit`
- `onHighlightLongPress: (id: Long, x: Float, y: Float, bottom: Float) -> Unit`
- `onMemoLongPress: (id: Long, x: Float, y: Float, bottom: Float) -> Unit`

터치 이벤트 변경:
- 현재 overlay가 모든 터치를 가로채서 WebView에 전달 안 됨 → 텍스트 선택 불가능
- 롱프레스 감지 시 overlay를 일시적으로 `GONE`으로 전환 → WebView가 네이티브 텍스트 선택 처리
- 선택 완료(텍스트가 비어지거나 하이라이트/메모 버튼 클릭) 시 overlay 복원
- 줌 상태(scale > 1)에서는 롱프레스를 팬으로 간주하여 선택 모드 진입 안 함

### `PdfHtmlTemplate.kt` — JS/CSS 추가

- `<script>` 목록에 `annotation.js`, `selection.js` 추가
- CSS에 하이라이트/메모 오버레이 스타일 추가

## 데이터 흐름

### 하이라이트 생성

```
1. 사용자 롱프레스 → overlay GONE → WebView 텍스트 선택
2. selectionchange → Android.onTextSelected(text)
3. Kotlin: 액션 팝업 표시 (하이라이트/메모 버튼)
4. 사용자 "하이라이트" 탭
5. Kotlin → JS: _getLocationFromSelection() 호출
6. JS → Kotlin: "pdf:3:5:12:7:4" 반환
7. Kotlin: Highlight(bookPath, cfi="pdf:3:5:12:7:4", text=선택텍스트, page=3) DB 저장
8. Kotlin → JS: _addHighlight("pdf:3:5:12:7:4", id) 호출
9. overlay 복원
```

### 페이지 진입 시 복원

```
1. _renderPage() 완료
2. Android.onPageChanged(pageNum, total)
3. Kotlin: DB에서 해당 bookPath + 현재 pageNum의 하이라이트/메모 조회
4. Kotlin → JS: _applyHighlights(json), _applyMemos(json)
```

### 롱프레스로 삭제/편집

```
1. 롱프레스 → JS: _getAnnotationAtPoint(x, y)
2. JS → Android.onAnnotationLongPress(json) — {type, id, cx, y, bottom}
3. Kotlin: 팝업 표시 (삭제 / 메모 편집)
```

## 스코프 밖

- 스캔 PDF (텍스트 레이어 없는 이미지 PDF) 지원
- 하이라이트 색상 커스텀
- PDF 내보내기/공유
