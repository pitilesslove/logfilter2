# K8s Log Viewer 스펙 문서

## 1. 개요

### 1.1 프로젝트 목적
Kubernetes 환경의 Pod 로그를 실시간으로 조회하고 필터링하는 웹 기반 로그 뷰어

### 1.2 기술 스택
- **Backend**: Node.js + Express
- **Frontend**: HTML/CSS/JavaScript + Tailwind CSS (CDN)
- **실시간 통신**: Server-Sent Events (SSE)
- **K8s 연동**: kubectl CLI
- **테스트**: Playwright

### 1.3 주요 특징
- 웹 브라우저 기반 UI (별도 설치 불필요)
- 실시간 로그 스트리밍
- 다양한 로그 형식 파싱 지원
- 가상 스크롤로 대용량 로그 처리

---

## 2. 화면 구성 (UI Layout)

### 2.1 전체 레이아웃

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              [메뉴바]                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                        [K8s 선택 패널]                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                        [필터 패널]                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                        [컨트롤 패널]                                         │
├────────┬────────────────────────────────────────────────────────────────────┤
│        │                                                                     │
│  인디  │                                                                     │
│  케이  │                      [로그 테이블]                                  │
│  터    │                                                                     │
│  패널  │                                                                     │
│        │                                                                     │
├────────┴────────────────────────────────────────────────────────────────────┤
│                           [상태바]                                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 K8s 선택 패널

| 컴포넌트 | 타입 | 설명 |
|----------|------|------|
| Context | ComboBox | K8s context 목록 |
| Namespace | ComboBox | 네임스페이스 목록 |
| Pod | ComboBox | 파드 목록 (상태, Ready, 노드 표시) |
| Container | ComboBox | 컨테이너 목록 |
| 새로고침 | Button | 목록 새로고침 |
| 연결 | Button | 선택한 파드에 연결 |

### 2.3 필터 패널

| 컴포넌트 | 타입 | 설명 |
|----------|------|------|
| Presets | ComboBox | 저장된 필터 프리셋 |
| Find | TextField | 포함 키워드 (`\|`로 OR 조건) |
| Remove | TextField | 제외 키워드 (`\|`로 OR 조건) |
| Class Show | TextField | 표시할 클래스 필터 |
| Class Remove | TextField | 제외할 클래스 필터 |
| Highlight | TextField | 하이라이트 키워드 |
| Log Level | Checkbox[] | TRACE, DEBUG, INFO, WARN, ERROR, FATAL |

### 2.4 컨트롤 패널

| 컴포넌트 | 설명 |
|----------|------|
| 로그 개수 | 현재 로그 줄 수 표시 |
| 시작/중지 | 스트리밍 제어 |
| 전체 로그 | 모든 로그 다운로드 |
| 샘플 | 샘플 로그 로드 |
| 다운로드 | 필터링된 로그 저장 |
| 파일 열기 | 로컬 로그 파일 불러오기 |
| 클리어 | 로그 삭제 |

### 2.5 인디케이터 패널

| 요소 | 색상 | 설명 |
|------|------|------|
| 하이라이트 | 노란색 (#FFEE58) | 하이라이트된 로그 위치 |
| 북마크 | 파란색 (#42A5F5) | 북마크된 로그 위치 |
| 에러 | 빨간색 (#FF5252) | ERROR/FATAL 로그 위치 |
| 경고 | 주황색 (#FFB74D) | WARN 로그 위치 |

### 2.6 로그 테이블

| 컬럼 | 설명 |
|------|------|
| # | 라인 번호 |
| ★ | 북마크 표시 |
| DateTime | 타임스탬프 |
| Level | 로그 레벨 (색상별) |
| Thread | 스레드명 |
| Class | 클래스명 |
| Message | 로그 메시지 |

**로그 레벨 색상:**
| 레벨 | 색상 |
|------|------|
| TRACE | #808080 (회색) |
| DEBUG | #0066cc (파란색) |
| INFO | #2e8b57 (녹색) |
| WARN | #ff9a00 (주황색) |
| ERROR | #ff0000 (빨간색) |
| FATAL | #cc0000 (진한 빨간색) |

---

## 3. 기능 요구사항

### 3.1 로그 수집
- `kubectl logs -f <pod> -n <namespace>` 명령어로 실시간 스트리밍
- SSE를 통해 브라우저로 전송
- tail 옵션으로 초기 로그 양 제한 (기본 5000줄)

### 3.2 자동 스크롤링
- 새 로그 추가 시 자동으로 맨 아래로 스크롤
- 스크롤/클릭 시 자동 스크롤 비활성화
- 상태바에서 ON/OFF 토글 가능

### 3.3 필터링 기능
- 레벨 필터: 각 로그 레벨별 체크박스
- 단어 필터: Find (포함) / Remove (제외)
- 클래스 필터: Show / Remove
- 각 필터별 개별 활성화/비활성화 체크박스

### 3.4 키워드 하이라이트
- Highlight 필드에 입력된 키워드 강조
- 행 배경색: 노란색
- F3/Shift+F3으로 네비게이션

### 3.5 북마크 기능
- 클릭으로 북마크 토글
- ★ 표시 및 배경색 변경

### 3.6 필터 프리셋
- 현재 필터 상태를 이름으로 저장
- localStorage에 영구 저장
- 적용/삭제 기능

### 3.7 가상 스크롤
- 1000줄 이상에서 자동 활성화
- DOM 풀 재사용으로 성능 최적화
- rowHeight: 28px, bufferSize: 20

### 3.8 스크롤 위치 보존
- 필터 변경 시 화면 중심 라인 유지
- 자동스크롤 OFF 상태에서만 동작
- 바이너리 서치로 최적 위치 탐색

---

## 4. 단축키 목록

| 단축키 | 기능 |
|--------|------|
| F3 | 다음 하이라이트로 이동 |
| Shift+F3 | 이전 하이라이트로 이동 |
| Ctrl+G | Goto 라인 입력 |
| Escape | 입력창 포커스 해제 |

---

## 5. 로그 파싱

### 5.1 지원 로그 형식

#### Spring Boot Logback 표준 패턴
```
2024-01-15 12:34:56.789  INFO 12345 --- [main] c.e.demo.MyClass : Message
```

#### DX 로그 패턴
```
2024-01-15 10:30:45.123 [INFO ] [main] ClassName - Message
```

#### Logback 커스텀 패턴 (traceId 포함)
```
[2024-01-15 10:30:45,123 22161][INFO ][ClassName][traceId]|Message
```

#### 일반 로그 패턴
```
2024-01-15 10:30:45 INFO ClassName - Message
```

### 5.2 파싱 필드

| 필드 | 설명 |
|------|------|
| line | 라인 번호 |
| dateTime | 날짜 시간 |
| level | 로그 레벨 |
| thread | 스레드명 |
| class | 클래스명 |
| message | 로그 메시지 |
| hasStackTrace | 스택트레이스 여부 |

---

## 6. API 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/` | GET | 웹 UI 페이지 |
| `/api/k8s/contexts` | GET | Context 목록 |
| `/api/k8s/namespaces?context=` | GET | Namespace 목록 |
| `/api/k8s/pods?namespace=&context=` | GET | Pod 목록 |
| `/api/k8s/logs?pod=&namespace=&tail=` | GET | Pod 로그 조회 |
| `/api/k8s/logs/all?pod=&namespace=` | GET | Pod 전체 로그 |
| `/api/k8s/logs/stream?pod=&namespace=` | GET (SSE) | 로그 스트리밍 |
| `/api/k8s/logs/stream/stop?streamId=` | GET | 스트리밍 중지 |

### SSE 이벤트 형식

```javascript
// init 이벤트
{ "streamId": 1, "pod": "my-pod" }

// log 이벤트
{
  "line": 1,
  "dateTime": "2024-01-15 10:30:45.123",
  "level": "INFO",
  "thread": "main",
  "class": "c.e.d.MyService",
  "message": "Application started"
}

// error 이벤트
{ "error": "Connection failed" }

// end 이벤트
{ "streamId": 1, "lines": 1234 }
```

---

## 7. 프로젝트 구조

```
logfilter/
├── server/                    # Express 서버
│   ├── index.js              # 서버 진입점
│   ├── routes/
│   │   └── k8s.js            # K8s API 라우터
│   └── services/
│       ├── k8sClient.js      # kubectl 래퍼
│       ├── logParser.js      # 로그 파서
│       └── logStore.js       # 로그 저장소
├── samples/
│   ├── ui-mockup.html        # 웹 UI 메인 파일
│   ├── ui-mockup-legacy.html # 백업 (이전 버전)
│   ├── spring-boot-sample.log
│   └── ums-log-sample.log
├── package.json              # Node.js 설정
├── README.md                 # 프로젝트 문서
├── SPEC.md                   # 이 문서
└── REPORT.md                 # 개발 보고서
```

---

## 8. 다크/라이트 모드

### CSS 테마 변수

```css
/* 라이트 모드 */
:root {
  --bg-primary: #f5f5f5;
  --bg-secondary: #fafafa;
  --text-primary: #333;
  --border-color: #ddd;
}

/* 다크 모드 */
.dark {
  --bg-primary: #1e1e1e;
  --bg-secondary: #252526;
  --text-primary: #e0e0e0;
  --border-color: #444;
}
```

- Tailwind CSS `darkMode: 'class'` 기반
- 시스템 테마 자동 감지
- localStorage에 설정 저장

---

## 9. kubectl 명령어 참조

```bash
# Context 목록
kubectl config get-contexts -o name

# 현재 Context
kubectl config current-context

# Namespace 목록
kubectl get namespaces -o jsonpath='{.items[*].metadata.name}'

# Pod 목록
kubectl get pods -n <namespace> -o json

# 로그 조회
kubectl logs <pod> -n <namespace> --tail=5000

# 로그 스트리밍
kubectl logs -f <pod> -n <namespace> --tail=5000
```

---

## 10. 버전 정보

- **현재 버전**: 2.0 (Node.js/Express 웹 애플리케이션)
- **이전 버전**: 1.x (Java Swing / Kotlin TornadoFX)
- **애플리케이션 이름**: K8s Log Viewer
