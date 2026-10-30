---
description: "배포 준비: 버전 업데이트, 태그 생성, 릴리즈 노트 작성, AAB 빌드"
allowed-tools: Bash(git *), Bash(./gradlew *), Read, Edit, AskUserQuestion
---

## 배포 절차

아래 단계를 순서대로 실행한다.

### 1단계: 커밋 목록 확인

- `git tag --list --sort=-v:refname | head -1` 로 가장 최근 태그를 찾는다.
- 태그가 있으면: 해당 태그부터 HEAD까지의 커밋 목록을 `git log {tag}..HEAD --oneline` 으로 출력한다.
- 태그가 없으면 (최초 배포): `app/build.gradle.kts`에서 현재 `versionName`을 읽고, `git log --oneline -- app/build.gradle.kts`에서 해당 버전으로 업데이트한 커밋을 찾는다. 해당 커밋 이후의 변경사항을 `git log {commit}..HEAD --oneline`으로 출력한다. 버전 커밋을 찾을 수 없으면 사용자에게 범위를 확인한다.
- 커밋 목록을 사용자에게 보여준다.

### 2단계: bump 타입 선택

AskUserQuestion으로 bump 타입을 질문한다:

- **patch**: x.y.z → x.y.(z+1) (버그 수정, 소소한 개선)
- **minor**: x.y.z → x.(y+1).0 (새 기능 추가)
- **major**: x.y.z → (x+1).0.0 (큰 변경)

### 3단계: 버전 업데이트

`app/build.gradle.kts` 파일에서:
- `versionCode` 를 현재 값 + 1 로 업데이트
- `versionName` 을 bump 타입에 맞게 업데이트

Edit 도구로 수정한다.

### 4단계: 변경사항 작성

커밋 목록을 기반으로 아래 두 가지를 작성하여 출력한다. 각각 코드 블록으로 감싸서 사용자가 쉽게 복사할 수 있게 한다.

#### (A) 릴리즈 노트 (Google Play Console용)

- 사용자 대상, 한국어
- 기술적 용어 없이 기능/개선/수정 사항을 간결하게 나열
- 글머리 기호(-)로 항목 나열

#### (B) 개발자 changelog

- 커밋 메시지 기반
- `feat:`, `fix:`, `chore:` 등 카테고리별로 그룹핑

### 5단계: 버전 커밋 및 태그

- `git add app/build.gradle.kts`
- 커밋 메시지: `chore: 버전 {versionName} (versionCode {versionCode}) 으로 업데이트`
  - Co-Authored-By를 넣지 않는다.
- 태그 생성: `git tag v{versionName}`

### 6단계: AAB 빌드

- `./gradlew bundleRelease` 를 실행한다.
- 빌드 성공 시 AAB 파일 경로를 안내한다: `app/build/outputs/bundle/release/app-release.aab`
- 빌드 실패 시 에러 내용을 보여주고 대응한다.

### 7단계: 태그 푸시 및 GitHub Release 등록

- 버전 커밋을 main에 푸시하고 태그를 푸시한다: `git push origin HEAD:main`, `git push origin v{versionName}` (푸시 전에 사용자에게 확인한다)
- `gh release create v{versionName} --verify-tag --title "v{versionName}" --notes "<4단계 (B) 개발자 changelog>"` 로 GitHub Release를 등록한다.
- AAB 파일은 첨부하지 않는다.
- 생성된 Release URL을 안내한다.
