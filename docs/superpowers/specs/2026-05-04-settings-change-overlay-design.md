# 설정 변경 시 비트맵 오버레이로 깜빡임 방지

## 문제

폰트 스타일 관련 설정(여백, 폰트 사이즈, 줄간격 등)을 빠르게 변경하면:
1. 스타일이 즉시 적용되어 레이아웃이 흐트러진 중간 상태가 보임
2. CFI 복원으로 페이지가 점프하는 모습이 보임

사용자 경험이 산만하다.

## 목표

설정을 빠르게 변경해도 뷰어 콘텐츠는 변하지 않고, 모든 처리가 끝난 후 완성된 페이지가 한 번에 나타난다. 바텀시트는 계속 조작 가능해야 한다.

## 설계

### 흐름

```
Stepper 클릭
  → Kotlin: settingsOverlay가 없으면 WebView 비트맵 캡쳐
  → Kotlin: JS _applyReaderSettings() 호출
  → JS: resize + CSS 적용 (오버레이에 가려져 안 보임)
  → JS: 500ms 디바운스 후 display(savedCfi)
  → JS: 완료 시 Android.onSettingsApplyComplete() 호출
  → Kotlin: settingsOverlay 제거 → 완성된 페이지 노출
```

### 변경 파일

#### 1. BookReaderScreen.kt

- `settingsOverlay` 상태 추가 (`Bitmap?`, 기존 `pageJumpOverlay`와 동일 패턴)
- `LaunchedEffect(readerSettings)` 안에서 JS 호출 전에 WebView 비트맵 캡쳐
  - 이미 `settingsOverlay`가 있으면 재캡쳐하지 않음 (연속 클릭 시 최초 화면 유지)
- WebView 위, 바텀시트 아래에 `Image`로 렌더링
- `onSettingsApplyComplete` 브릿지 콜백에서 `settingsOverlay` 제거
- 안전장치: 3초 타임아웃으로 오버레이 자동 제거

#### 2. EpubBridge.kt

- `onSettingsApplyComplete()` 브릿지 메서드 추가
- Kotlin 콜백을 통해 `settingsOverlay = null` 처리

#### 3. settings.js

- `display(cfi)` Promise 완료(then/catch) 시 `Android.onSettingsApplyComplete()` 호출
- CFI가 없는 경우(초기 로드 등)에도 rescan 예약 후 호출

### 핵심 포인트

- 오버레이 캡쳐는 첫 번째 설정 변경 시에만 (연속 클릭 시 최초 화면 유지)
- 바텀시트는 오버레이보다 위 레이어이므로 터치 가능
- e-ink 최적화: 애니메이션 없이 즉시 교체
- 기존 `pageJumpOverlay` 패턴을 그대로 따름
