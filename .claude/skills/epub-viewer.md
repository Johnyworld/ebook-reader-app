# EPUB Viewer 개발 가이드

## 1. Description

이 프로젝트의 EPUB 뷰어는 Android Kotlin + Jetpack Compose 앱에서 WebView + epub.js 조합으로 구현되어 있다.
EPUB 관련 기능을 개발하거나 수정할 때 이 스킬이 실행된다.

## 2. Trigger

- EPUB 뷰어 기능을 개발하거나 수정할 때
- `EpubViewer`, `EpubBridge`, `buildEpubJsHtml`, `extractEpub` 등 epub 관련 함수를 다룰 때
- epub.js 스크립트 로직을 수정할 때

## 3. 사전 준비

- `epub.min.js`를 `app/src/main/assets/epub.min.js`에 배치
  - 다운로드: https://github.com/futurepress/epub.js/releases
- 공식 문서: http://epubjs.org/documentation/0.3/
