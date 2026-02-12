# 03. 데이터 모델 및 상태 관리

## 핵심 데이터 구조

### LogEntry — 파싱된 로그 엔트리

서버 파서(`logParser.js`)와 클라이언트 파서(`parseLogLines()`) 모두 동일한 구조를 생성한다.

```javascript
{
    line: Number,            // 원본 라인 번호 (1부터 시작)
    dateTime: String,        // "2024-01-15 10:30:45.123" (밀리초 포함)
    level: String,           // "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL"
    thread: String,          // 스레드명 또는 traceId 앞 8자리
    class: String,           // 클래스명 (축약형 포함, 예: "c.e.d.MyService")
    message: String,         // 로그 메시지 본문
    hasStackTrace: Boolean   // 스택트레이스 포함 여부
}
```

#### 필드 상세

| 필드 | 소스 | 설명 |
|------|------|------|
| `line` | 파서가 자동 부여 | 순차 증가, 원본 로그의 라인 번호에 해당 |
| `dateTime` | 정규식 캡처 그룹 1 | Logback 콤마(`,`)는 마침표(`.`)로 치환 |
| `level` | 정규식 캡처 그룹 2 → `normalizeLevel()` | WARNING→WARN, SEVERE→ERROR 등 정규화 |
| `thread` | 정규식 캡처 그룹 3 | Logback traceId의 경우 앞 8자리만 사용 |
| `class` | 정규식 캡처 그룹 4 | 패턴에 따라 전체명 또는 축약명 |
| `message` | 정규식 캡처 그룹 5 | 파이프(`\|`) 구분자 이후 텍스트 (Logback) |
| `hasStackTrace` | 휴리스틱 | `at `으로 시작하거나 `Exception` 포함 시 true |

#### 런타임 확장 필드 (클라이언트에서 추가)

```javascript
{
    ...LogEntry,
    isBookmarked: Boolean,   // 북마크 여부 (더블클릭 토글)
    isHighlighted: Boolean   // 하이라이트 매칭 여부 (필터 적용 시 계산)
}
```

### 로그 레벨 정규화 맵

```javascript
const LEVEL_MAP = {
    'TRACE': 'TRACE',
    'DEBUG': 'DEBUG',
    'INFO': 'INFO',
    'WARN': 'WARN',
    'WARNING': 'WARN',      // Java logging
    'ERROR': 'ERROR',
    'FATAL': 'FATAL',
    'SEVERE': 'ERROR'        // java.util.logging
};
```

---

## 서버 사이드 상태

### LogStore (server/services/logStore.js)

```javascript
class LogStore {
    logs = [];          // LogEntry[] — 인메모리 로그 저장소

    setLogs(logs)       // 전체 교체
    addLog(log)         // 단건 추가
    addLogs(logs)       // 다건 추가
    getLogs()           // 전체 조회
    clear()             // 초기화
    count()             // 건수 조회
}
```

- 싱글턴 인스턴스 (`module.exports = new LogStore()`)
- SSE 스트리밍 시에는 사용하지 않고, 일회성 조회(`/api/k8s/logs`)의 결과를 캐싱
- `/api/logs` 엔드포인트에서 클라이언트가 가져감

### 활성 스트림 관리 (server/routes/k8s.js)

```javascript
const activeStreams = new Map();   // streamId → { process, res }
let streamIdCounter = 0;           // 자동 증가 ID
```

- SSE 스트리밍 시 kubectl spawn 프로세스와 응답 객체를 함께 관리
- 클라이언트 연결 종료 시 (`req.on('close')`) 프로세스 kill
- `/api/k8s/logs/stream/stop` 으로 수동 중지 가능

---

## 클라이언트 사이드 상태

### 핵심 상태 변수

```javascript
// 로그 데이터
let allLogs = [];              // LogEntry[] — 전체 로그 (필터 전)
let filteredLogs = [];         // LogEntry[] — 필터링된 로그 (표시 대상)

// K8s 연결
let podList = [];              // Pod 목록 캐시

// 스트리밍
let eventSource = null;        // EventSource 인스턴스
let streamId = null;           // 현재 스트림 ID
let isStreaming = false;       // 스트리밍 중 여부

// 스크롤
let autoScroll = true;         // 자동 스크롤 활성 여부
```

### 필터 상태 (캐싱)

```javascript
let cachedFilterState = null;  // 파싱된 필터 조건 캐시
```

`getCachedFilterState()` 호출 시 아래 구조를 반환:

```javascript
{
    wordFind: String[],         // Find 필드의 파이프 분리된 키워드 배열
    wordRemove: String[],       // Remove 필드의 파이프 분리된 키워드 배열
    classShow: String,          // Class Show 필드 값
    classRemove: String,        // Class Remove 필드 값
    highlightKeyword: String,   // Highlight 키워드
    levels: {                   // 각 레벨의 활성 여부
        TRACE: Boolean,
        DEBUG: Boolean,
        INFO: Boolean,
        WARN: Boolean,
        ERROR: Boolean,
        FATAL: Boolean
    },
    // 각 필터의 활성화 체크박스 상태
    wordFindEnabled: Boolean,
    wordRemoveEnabled: Boolean,
    classShowEnabled: Boolean,
    classRemoveEnabled: Boolean,
    highlightEnabled: Boolean
}
```

### 하이라이트 네비게이션 상태

```javascript
let highlightedIndices = [];    // filteredLogs 내 하이라이트된 항목의 인덱스 배열
let currentHighlightPos = -1;   // 현재 탐색 위치 (-1: 미탐색)
```

### 선택 상태

```javascript
let selectedLogIndices = new Set();  // 선택된 filteredLogs 인덱스 (다중 선택)
let lastSelectedIndex = -1;          // 마지막 클릭 인덱스 (Shift 범위 선택용)
let selectedLogIndex = -1;           // 단일 선택 인덱스 (하이라이트 네비게이션용)
```

### 드래그 선택 상태

```javascript
let isDragging = false;              // 드래그 중 여부
let dragActivated = false;           // 드래그 활성화 여부 (5px 이동 임계값)
let dragStartIndex = -1;             // 드래그 시작 행 인덱스
let dragStartY = 0;                  // 드래그 시작 Y 좌표
let dragLastIndex = -1;              // 마지막 드래그 위치 인덱스
let dragAutoScrollTimer = null;      // 가장자리 자동 스크롤 타이머
let dragBaseSelection = new Set();   // 드래그 시작 시점의 기존 선택 상태
```

### 가상 스크롤 설정

```javascript
const virtualScroll = {
    rowHeight: 28,           // 행 높이 (px)
    bufferSize: 50,          // 뷰포트 외 추가 렌더링 행 수
    visibleStart: 0,         // 현재 보이는 시작 인덱스
    visibleEnd: 0,           // 현재 보이는 끝 인덱스
    enabled: true,           // 가상 스크롤 활성 여부
    threshold: 1000,         // 활성화 임계값 (이 이상이면 활성)
    lastScrollTop: 0,        // 마지막 스크롤 위치 (중복 렌더링 방지)
    scrollThreshold: 50      // 스크롤 변경 감지 임계값 (px)
};
```

### DOM Pool 설정

```javascript
const rowPool = {
    rows: [],                // HTMLTableRowElement[] — 재사용 TR 풀
    poolSize: 100,           // 풀 크기
    topPadding: null,        // 상단 스페이서 TR
    bottomPadding: null,     // 하단 스페이서 TR
    activeCount: 0           // 현재 활성(visible) 행 수
};
```

---

## 브라우저 영속 저장소 (localStorage)

| 키 | 값 타입 | 설명 |
|----|---------|------|
| `theme` | `"light"` \| `"dark"` | 테마 설정 |
| `k8s-log-viewer-settings` | JSON 문자열 | Context/Namespace/Pod 선택 상태 |
| `k8s-log-viewer-presets` | JSON 문자열 | 필터 프리셋 목록 |

### Settings 구조

```javascript
{
    context: String,       // 선택된 K8s context
    namespace: String,     // 선택된 namespace
    pod: String            // 선택된 pod 이름
}
```

### Preset 구조

```javascript
{
    "프리셋명": {
        wordFind: String,
        wordRemove: String,
        classShow: String,
        classRemove: String,
        highlightKeyword: String,
        levels: { TRACE, DEBUG, INFO, WARN, ERROR, FATAL },
        wordFindEnabled: Boolean,
        wordRemoveEnabled: Boolean,
        classShowEnabled: Boolean,
        classRemoveEnabled: Boolean,
        highlightEnabled: Boolean
    }
}
```

---

## API 응답 형식

### K8s Contexts

```javascript
// GET /api/k8s/contexts
{
    contexts: String[],       // ["context1", "context2", ...]
    current: String           // 현재 활성 context
}
```

### K8s Namespaces

```javascript
// GET /api/k8s/namespaces?context=
{
    namespaces: String[]      // ["default", "kube-system", ...]
}
```

### K8s Pods

```javascript
// GET /api/k8s/pods?namespace=&context=
{
    pods: [{
        name: String,         // Pod 이름
        status: String,       // "Running" | "Pending" | "Failed" 등
        ready: String,        // "1/1" | "0/1"
        restarts: Number,     // 재시작 횟수
        node: String          // 노드 이름
    }]
}
```

### K8s Logs

```javascript
// GET /api/k8s/logs?pod=&namespace=&context=&tail=
{
    status: "ok",
    pod: String,
    count: Number,
    logs: LogEntry[]
}
```

### SSE 이벤트

```javascript
// event: init
{ streamId: Number, pod: String }

// event: log
LogEntry   // 단일 로그 엔트리

// event: error
{ error: String }

// event: end
{ streamId: Number, lines: Number }
```
