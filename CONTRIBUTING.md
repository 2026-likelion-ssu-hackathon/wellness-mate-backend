# Contributing Guide

## 기본 원칙

- `main` 브랜치에서 직접 작업하거나 커밋하지 않습니다.
- 모든 변경은 목적에 맞는 작업 브랜치에서 진행합니다.
- 하나의 PR에는 하나의 명확한 작업 목적만 포함합니다.

## 브랜치 규칙

브랜치 이름은 `<type>/<short-description>` 형식을 사용합니다.

| 접두사 | 용도 | 예시 |
| --- | --- | --- |
| `feat/` | 새 기능 추가 | `feat/health-check` |
| `fix/` | 버그 수정 | `fix/cors-origin` |
| `chore/` | 설정·빌드·유지보수 작업 | `chore/ci-cache` |
| `refactor/` | 기능 변경 없는 코드 구조 개선 | `refactor/config-structure` |
| `docs/` | 문서 작성·수정 | `docs/contributing-guide` |

`short-description`은 작업 목적이 드러나도록 작성하고, 영문 소문자와 하이픈(`-`)을 사용합니다.

## 커밋 규칙

Conventional Commit 형식을 사용합니다.

```text
<type>: <한국어 설명>
```

- 커밋 타입은 영어로 작성합니다.
- 콜론 뒤의 커밋 설명은 한국어로 작성합니다.
- 커밋 설명은 변경 내용을 간결하고 명확하게 표현합니다.

주요 커밋 타입:

- `feat`: 새 기능 추가
- `fix`: 버그 수정
- `chore`: 설정·빌드·유지보수 작업
- `refactor`: 기능 변경 없는 코드 구조 개선
- `docs`: 문서 작성·수정
- `test`: 테스트 추가·수정

예시:

```text
feat: 서버 상태 확인 API 추가
fix: CORS 허용 Origin 설정 수정
docs: PR 작성 규칙 추가
```

## PR 및 Squash Merge 절차

1. 최신 `main` 기준으로 목적에 맞는 작업 브랜치를 생성합니다.
2. 작업과 로컬 테스트를 완료한 뒤 `main` 브랜치를 대상으로 PR을 생성합니다.
3. PR 템플릿의 모든 항목을 작성하고 자체 리뷰 체크리스트를 확인합니다.
4. 리뷰 의견을 반영하고 GitHub Actions CI가 통과했는지 확인합니다.
5. 리뷰와 CI가 완료되면 GitHub의 **Squash and merge**로 병합합니다.
6. Squash 커밋 제목은 `<type>: <한국어 설명>` 형식으로 정리합니다.
7. 병합 후 완료된 작업 브랜치를 정리합니다.
