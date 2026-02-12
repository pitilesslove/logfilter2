# 02. 아키텍처 및 기술 결정

## 소프트웨어 아키텍처: 서버-클라이언트 단일 파일 구조

Express 서버가 kubectl CLI를 프록시하고, 단일 HTML 파일이 전체 프론트엔드를 담당하는 경량 구조.

### 선정 이유

이 프로젝트는 세 가지 특성이 아키텍처 선택을 결정한다:

1. **운영 도구 특성**
   - 설치와 설정이 간단해야 한다 (`npm install && npm start`)
   - 빌드 단계가 없어야 한다 (HTML 수정 → 새로고침으로 즉시 반영)
   - → 프론트엔드 프레임워크 없이 단일 HTML 파일로 구현

2. **서버는 프록시 역할**
   - 핵심 로직(필터링, 렌더링, 가상스크롤)은 모두 클라이언트에서 실행
   - 서버는 kubectl CLI 래핑 + SSE 스트리밍 + 정적 파일 서빙만 수행
   - → Express 최소 구성으로 충분

3. **적절한 규모**
   - 서버: 5개 파일 (진입점 + 라우트 + 서비스 3개)
   - 클라이언트: 1개 파일 (HTML + CSS + JavaScript)
   - → 복잡한 모듈 분리 불필요

### 다른 아키텍처를 선택하지 않은 이유

| 아키텍처 | 탈락 사유 |
|----------|----------|
| React/Vue SPA | 빌드 단계 추가, 단일 도구 앱에 과도한 복잡성 |
| Next.js/Nuxt SSR | 서버 사이드 렌더링 불필요, kubectl 프록시에 과도 |
| Electron | 브라우저 접근성 포기, 설치 필요 |
| 순수 CLI 도구 | 필터링/하이라이트 등 리치 UI 구현 불가 |

### 레이어 구조

```
┌─────────────────────────────────────────────┐
│  Browser (samples/index.html)               │
│  ┌──────────────────────────────────────┐   │
│  │  UI Layer (HTML + Tailwind CSS)      │   │
│  ├──────────────────────────────────────┤   │
│  │  Logic Layer (JavaScript)            │   │
│  │  - 필터링 엔진                        │   │
│  │  - 가상 스크롤                        │   │
│  │  - 로그 파서 (클라이언트)             │   │
│  │  - 상태 관리 (전역 변수)             │   │
│  │  - DOM Pool 렌더링                   │   │
│  └──────────────────────────────────────┘   │
│              │ HTTP / SSE                    │
├─────────────────────────────────────────────┤
│  Express Server (server/)                   │
│  ┌──────────────────────────────────────┐   │
│  │  Routes (REST API + SSE)             │   │
│  ├──────────────────────────────────────┤   │
│  │  Services                            │   │
│  │  - K8sClient (kubectl CLI 래퍼)       │   │
│  │  - LogParser (다중 포맷 파싱)          │   │
│  │  - LogStore (인메모리 저장)            │   │
│  └──────────────────────────────────────┘   │
│              │ child_process                 │
├─────────────────────────────────────────────┤
│  kubectl CLI → Kubernetes API Server        │
└─────────────────────────────────────────────┘
```

### 데이터 흐름

```
[K8s Cluster]
     │
     │ kubectl get/logs
     ▼
[K8sClient]  ─── execSync (동기) ──→  [Express Route]  ──→  JSON Response
     │                                      │
     │ spawn (비동기, -f)                    │ SSE
     ▼                                      ▼
[LogParser]  ──→  구조화된 LogEntry  ──→  [Browser EventSource]
                                            │
                                            ▼
                                    [allLogs 배열에 저장]
                                            │
                                     applyFilter()
                                            │
                                            ▼
                                    [filteredLogs 배열]
                                            │
                                     renderLogs()
                                            │
                                ┌───────────┴───────────┐
                                │                       │
                         < 1000건                 ≥ 1000건
                                │                       │
                     renderAllRowsWithPool    renderVisibleRowsWithPool
                                │                       │
                                └───────────┬───────────┘
                                            │
                                       [DOM Pool]
                                    (100개 재사용 TR)
```

---

## 기술 스택

### 백엔드

| 구분 | 선택 | 근거 |
|------|------|------|
| 런타임 | Node.js | 비동기 I/O, SSE 스트리밍에 적합, npm 생태계 |
| 프레임워크 | Express ^4.21 | 최소한의 HTTP 서버, 미들웨어 생태계 |
| CORS | cors ^2.8 | 개발 시 크로스 오리진 요청 허용 |
| K8s 통신 | child_process (내장) | kubectl CLI를 execSync/spawn으로 실행 |

### 프론트엔드

| 구분 | 선택 | 근거 |
|------|------|------|
| 언어 | JavaScript (ES6+) | 빌드 없이 브라우저에서 직접 실행 |
| CSS | Tailwind CSS v3 (CDN) | 유틸리티 클래스 기반, CDN으로 빌드 불필요 |
| 실시간 통신 | EventSource (SSE) | 서버→클라이언트 단방향 스트리밍, 자동 재연결 |

### 개발/테스트

| 구분 | 선택 | 근거 |
|------|------|------|
| E2E 테스트 | Playwright ^1.58 | 크로스 브라우저 E2E 테스트 |
| 개발 서버 | `node --watch` | Node.js 내장 파일 감시, 별도 도구 불필요 |

---

## 기술 결정 사항

### Express 채택 이유

- kubectl CLI를 child_process로 실행하기 위해 서버가 필요
- SSE 스트리밍을 위한 HTTP 서버 필요
- 정적 파일 서빙 + REST API + SSE를 하나의 프로세스로 처리
- 최소한의 의존성 (express, cors 2개)

### 단일 HTML 파일 채택 이유

- 빌드 단계 제거 → HTML 수정 후 브라우저 새로고침으로 즉시 확인
- 모든 코드가 한 파일 → 검색, 디버깅이 용이
- CDN Tailwind CSS → CSS 파일 분리 불필요
- 파일 크기가 커지는 단점이 있으나, 도구 앱 특성상 허용 가능

### SSE(Server-Sent Events) 채택 이유 (WebSocket 대신)

- 로그 스트리밍은 서버→클라이언트 단방향 — SSE가 최적
- HTTP 기반이므로 프록시/방화벽 호환성 우수
- EventSource API로 자동 재연결 내장
- WebSocket 대비 구현 단순 (핸드셰이크, 프레이밍 없음)

### kubectl CLI 채택 이유 (K8s Client SDK 대신)

- Go/Python K8s SDK 없이 Node.js에서 직접 사용 가능
- kubeconfig 설정을 그대로 활용 (인증, context 전환 등)
- 디버깅 시 동일 명령을 터미널에서 재현 가능
- 단점: kubectl 설치 필요, execSync 블로킹

### Tailwind CSS CDN 채택 이유

- 빌드 없이 CDN 스크립트 하나로 즉시 사용
- 다크모드 지원 (`darkMode: 'class'`)
- 커스텀 색상 확장 (로그 레벨별 색상)
- 단점: 프로덕션 번들 최적화 불가 (purge 없음), 초기 로드 시 CDN 의존

### 클라이언트 사이드 로그 파서 유지 이유

- 서버(`logParser.js`)와 클라이언트(`index.html` 내 `parseLogLines()`) 양쪽에 파서 존재
- 로컬 파일 업로드 시 서버를 거치지 않고 클라이언트에서 직접 파싱 필요
- SSE 스트리밍 시에는 서버에서 파싱하여 구조화된 JSON으로 전송
- **두 파서의 정규식 패턴은 동일하게 유지해야 한다**

### 인메모리 LogStore 채택 이유

- 로그는 일시적 데이터 — 영구 저장 불필요
- 단일 사용자 도구 — 동시성 이슈 없음
- 배열에 저장하여 빠른 조회
- 서버 재시작 시 초기화되는 것이 오히려 바람직

---

## 프로젝트 디렉토리 구조

```
logfilter/
├── server/                         # Express 서버
│   ├── index.js                    # 서버 진입점 (Express 앱, 미들웨어, 정적 파일 서빙)
│   ├── routes/
│   │   └── k8s.js                  # K8s API 라우트 (contexts, namespaces, pods, logs, SSE)
│   └── services/
│       ├── k8sClient.js            # kubectl CLI 래퍼 (execSync + spawn)
│       ├── logParser.js            # 서버 사이드 로그 파서 (6가지 포맷)
│       └── logStore.js             # 인메모리 로그 저장소
├── samples/
│   ├── index.html                  # 프론트엔드 UI (HTML + CSS + JavaScript 단일 파일)
│   ├── spring-boot-sample.log      # 샘플 로그 파일 (Spring Boot)
│   └── ums-log-sample.log          # 샘플 로그 파일 (UMS)
├── spec/                           # 프로젝트 스펙 문서
│   ├── 01-overview.md
│   ├── 02-architecture.md          # 본 문서
│   ├── 03-data-model.md
│   ├── 04-core-algorithm.md
│   ├── 05-ui-layout.md
│   ├── 06-user-interaction.md
│   ├── 07-feature-list-rules.md
│   ├── features.md                 # 기능 리스트 (마스터)
│   └── features/                   # 이관된 기능 상세 파일
├── package.json
├── logfilter.sh                    # tmux 기반 서버 실행 스크립트
├── SPEC.md                         # 레거시 스펙 문서
├── REPORT.md                       # 개발 완료 보고서
└── TODO.md                         # 향후 개선 사항
```

---

## 스펙 문서 참조 가이드

작업 상황에 따라 참조해야 할 스펙 문서가 다르다:

| 작업 상황 | 참조할 문서 | 이유 |
|-----------|------------|------|
| 프로젝트 전체 맥락 파악 | `01-overview.md` | 목표, 대상 사용자, 핵심 요구사항 확인 |
| 라이브러리 추가/아키텍처 변경 | `02-architecture.md` (본 문서) | 기술 결정 사항, 디렉토리 구조 확인 |
| 로그 엔트리 구조 수정 | `03-data-model.md` | 타입 정의, 상태 변수, API 응답 형식 |
| 파싱/필터링/렌더링 로직 수정 | `04-core-algorithm.md` | 파싱 우선순위, 필터 체인, 가상스크롤 로직 |
| UI 컴포넌트 추가/수정 | `05-ui-layout.md` | 패널 구조, 컬럼 정의, 테마 색상 |
| 사용자 흐름 변경/기능 추가 | `06-user-interaction.md` | 워크플로우, 키보드 단축키, 엣지 케이스 |
| 파싱 포맷 + 필터 로직 동시 수정 | `03-data-model.md` + `04-core-algorithm.md` | 모델과 알고리즘은 항상 함께 확인 |
| 새 기능 기획 | `01-overview.md` + `06-user-interaction.md` | 요구사항 범위와 기존 흐름 확인 후 설계 |
| 기능 추적/작업 계획 | `07-feature-list-rules.md` + `features.md` | 관리 규칙과 현재 상태 확인 |

### 파일 수정 시 체크리스트

코드 수정 시 아래 규칙을 반드시 확인한다:

- [ ] 서버 로그 파서(`logParser.js`)와 클라이언트 파서(`index.html` 내 `parseLogLines()`)의 정규식 패턴이 동기화되어 있는가?
- [ ] API 응답 형식 변경 시 클라이언트 코드도 함께 수정했는가?
- [ ] 새 필터 추가 시 `getFilterState()`, `passesFilter()`, `getCachedFilterState()` 모두 반영했는가?
- [ ] 가상 스크롤 관련 수정 시 `rowPool`, `virtualScroll` 객체 모두 확인했는가?
- [ ] 다크모드 스타일 추가 시 `dark:` 변형도 함께 추가했는가?
