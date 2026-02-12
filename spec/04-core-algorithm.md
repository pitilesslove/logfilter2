# 04. 핵심 알고리즘

## 개요

이 프로젝트의 핵심 알고리즘은 세 가지 영역으로 나뉜다:

1. **로그 파싱** — 다양한 로그 포맷을 구조화된 LogEntry로 변환
2. **필터링 엔진** — 5가지 필터 조건으로 로그를 걸러내기
3. **가상 스크롤 렌더링** — 대용량 로그를 DOM Pool로 효율적 표시

---

## 1. 로그 파싱

### 핵심 파일

- `server/services/logParser.js` — 서버 사이드 파서 (SSE 스트리밍, API 응답)
- `samples/index.html` 내 `parseLogLines()` — 클라이언트 사이드 파서 (로컬 파일 업로드)

> **중요**: 두 파서의 정규식 패턴과 매칭 우선순위는 반드시 동기화해야 한다.

### 패턴 매칭 우선순위

파서는 아래 순서대로 정규식을 시도하며, 첫 번째 매칭에서 중단한다:

| 순서 | 패턴 이름 | 정규식 | 예시 |
|------|----------|--------|------|
| 1 | Spring Boot | `^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w+)\s+\d+\s+---\s+\[([^\]]+)\]\s+(\S+)\s+:\s+(.*)$` | `2024-01-15 10:30:45.123  INFO 12345 --- [main] c.e.demo.App : Message` |
| 2 | DX | `^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\[(\w+)\s*\]\s+\[([^\]]+)\]\s+(\S+)\s+-\s+(.*)$` | `2024-01-15 10:30:45.123 [INFO ] [main] ClassName - Message` |
| 3 | Logback (traceId) | `^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[,\.]\d{3})(?:\s+\d+)?\]\[(\w+)\s*\]\[([^\]]+)\]\[([^\]]*)\]\|?(.*)$` | `[2026-02-10 01:16:23,125 22161][INFO ][Class][traceId]\|msg` |
| 4 | Logback (traceId 없음) | `^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[,\.]\d{3})(?:\s+\d+)?\]\[(\w+)\s*\]\[([^\]]+)\](?:\[\])?\|?(.*)$` | `[2026-02-10 01:16:23,125][INFO ][Class]\|msg` |
| 5 | 레거시 Bracket | `^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[,\.]\d{3})(?:\s+\d+)?\]\[(\w+)\s*\]\[([^\]]+)\]\[([^\]]+)\]([^\|].*)$` | `[2026-02-10 01:16:23,125][INFO ][Class][Id]message` |
| 6 | 일반 | `^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{3})?)\s+(\w+)\s+(\S+)\s+-?\s*(.*)$` | `2024-01-15 10:30:45 INFO ClassName - Message` |
| 7 | 폴백 | (매칭 실패) | 전체 라인을 message로, 나머지 필드는 기본값 |

### 폴백 처리

모든 패턴 매칭 실패 시:

```javascript
{
    line: lineNumber,
    dateTime: new Date().toISOString().replace('T', ' ').substring(0, 23),
    level: 'INFO',
    thread: 'unknown',
    class: 'unknown',
    message: trimmedLine,
    hasStackTrace: line.startsWith('at ') || line.includes('Exception')
}
```

### Logback 특수 처리

- **콤마 → 마침표 치환**: `2026-02-10 01:16:23,125` → `2026-02-10 01:16:23.125`
- **traceId 앞 8자리**: thread 필드에 traceId의 앞 8자리만 표시
- **파이프 구분자**: `|` 이후가 메시지, 없으면 `]` 이후 전체

### 파싱 프로세스

```
원본 로그 라인
  │
  ├── 빈 줄 → null 반환 (무시)
  │
  ├── trim()
  │
  ├── Spring Boot 패턴 시도 → 매칭 시 LogEntry 반환
  ├── DX 패턴 시도 → 매칭 시 LogEntry 반환
  ├── Logback(traceId) 패턴 시도 → 매칭 시 LogEntry 반환
  ├── Logback(noTrace) 패턴 시도 → 매칭 시 LogEntry 반환
  ├── 레거시 Bracket 패턴 시도 → 매칭 시 LogEntry 반환
  ├── 일반 패턴 시도 → 매칭 시 LogEntry 반환
  │
  └── 모두 실패 → 폴백 LogEntry 반환
```

---

## 2. 필터링 엔진

### 핵심 함수

- `getFilterState()` — DOM에서 현재 필터 상태를 수집하여 객체로 반환
- `getCachedFilterState()` — 캐싱된 필터 상태 반환 (없으면 생성)
- `invalidateFilterCache()` — 캐시 무효화 (필터 입력 변경 시)
- `passesFilter(log, state)` — 단일 로그가 필터를 통과하는지 판단
- `checkHighlight(log, state)` — 로그가 하이라이트 키워드를 포함하는지 판단
- `applyFilter()` — 전체 필터링 오케스트레이터

### 필터 체인 (passesFilter)

아래 순서로 검사하며, 하나라도 실패하면 해당 로그는 제외된다:

```
1. 로그 레벨 필터
   └─ state.levels[log.level] === true 인지 확인

2. Word Find (포함 필터) — 활성화 시
   └─ state.wordFind 키워드 중 하나라도
      log.message 또는 log.class에 포함되는지 (대소문자 무시)

3. Word Remove (제외 필터) — 활성화 시
   └─ state.wordRemove 키워드 중 하나라도
      log.message에 포함되면 제외 (대소문자 무시)

4. Class Show (클래스 포함 필터) — 활성화 시
   └─ log.class가 state.classShow를 포함하는지 (대소문자 무시)

5. Class Remove (클래스 제외 필터) — 활성화 시
   └─ log.class가 state.classRemove를 포함하면 제외 (대소문자 무시)
```

### 필터링 성능 최적화

#### 필터 캐싱

```
필터 입력 변경
  → invalidateFilterCache()  (cachedFilterState = null)
  → debounce(500ms)
  → applyFilter()
     → getCachedFilterState()  (DOM에서 읽어 캐시 생성)
     → passesFilter() × N    (캐시된 상태 재사용)
```

#### 동기 vs 비동기 필터링

```
applyFilter()
  │
  ├── allLogs.length < 1000
  │   └── 동기 처리: filteredLogs = allLogs.filter(passesFilter)
  │       → renderLogs() 즉시 호출
  │
  └── allLogs.length ≥ 1000
      └── 비동기 처리: applyFilterAsync()
          → 뷰포트 중심 라인 기억 (getCurrentCenterLogLine)
          → 500건 청크 단위 처리
          → requestIdleCallback() 사이 쉬기
          → 각 청크 처리 후 렌더링 갱신
          → 완료 후 중심 라인으로 스크롤 복원
```

#### 스트리밍 중 증분 필터링

SSE로 새 로그가 도착하면 전체 재필터링 대신 증분 처리:

```
addLogEntry(log)
  → allLogs.push(log)
  → passesFilter(log, getCachedFilterState())
  → 통과 시 filteredLogs.push(log)
  → appendFilteredLog(log, index)  (DOM에 행 추가)
  → autoScroll 활성이면 scrollToBottom()
```

---

## 3. 가상 스크롤 렌더링

### 활성화 조건

```
filteredLogs.length ≥ virtualScroll.threshold (1000)
  → virtualScroll.enabled = true
  → renderVisibleRowsWithPool() 사용

filteredLogs.length < virtualScroll.threshold
  → virtualScroll.enabled = false
  → renderAllRowsWithPool() 사용
```

### DOM Pool 전략

100개의 재사용 가능한 TR 요소를 미리 생성하여 풀로 관리:

```
rowPool.init(tbody)
  → 100개 TR 생성 (각 7개 TD: line, bookmark, dateTime, level, thread, class, message)
  → topPadding TR 생성 (상단 여백)
  → bottomPadding TR 생성 (하단 여백)
```

### 가상 스크롤 렌더링 프로세스

```
스크롤 이벤트 발생
  │
  ├── scrollTop 변화량 < scrollThreshold (50px) → 무시
  │
  └── requestAnimationFrame()
      │
      ├── visibleStart = Math.floor(scrollTop / rowHeight) - bufferSize
      ├── visibleEnd = visibleStart + visibleCount + (bufferSize × 2)
      │
      ├── 범위 클리핑 (0 ~ filteredLogs.length)
      │
      └── renderVisibleRowsWithPool(filteredLogs, visibleStart, visibleEnd)
          │
          ├── topPadding.height = visibleStart × rowHeight
          ├── bottomPadding.height = (totalCount - visibleEnd) × rowHeight
          │
          └── for i in [visibleStart, visibleEnd):
              └── rowPool.updateRow(rows[i % poolSize], filteredLogs[i], i)
                  → line 셀 업데이트
                  → bookmark 아이콘 업데이트
                  → dateTime, level, thread, class 셀 업데이트
                  → message 셀 업데이트 (하이라이트 + JSON 포맷팅)
                  → 행 배경색 (선택, 북마크, 에러, 경고)
```

### 스크롤 위치 보존

필터 변경 시 사용자가 보고 있던 위치를 유지:

```
applyFilter()
  → centerLine = getCurrentCenterLogLine()
     (뷰포트 중앙에 표시된 로그의 원본 라인 번호)
  → 필터링 수행
  → nearestIndex = findNearestLogIndex(filteredLogs, centerLine)
     (바이너리 서치로 가장 가까운 라인 탐색)
  → scrollToCenter(nearestIndex)
```

### 행 이동 (scrollToRow)

```
scrollToRow(index)
  │
  ├── virtualScroll.enabled === true
  │   └── scrollTop = index × rowHeight - (containerHeight / 2)
  │       → 스크롤 후 renderVisibleRowsWithPool()
  │
  └── virtualScroll.enabled === false
      └── 해당 TR 요소의 scrollIntoView({ block: 'center' })
```

---

## 4. 메시지 포맷팅

### formatMessage(rawMessage, highlightKeyword, jsonExpanded)

메시지 셀에 표시할 HTML을 생성:

```
원본 메시지
  │
  ├── escapeHtml() — XSS 방지 (&, <, >, ", ' 이스케이프)
  │
  ├── Word Find 매칭 하이라이트
  │   └── <span class="word-filter-match">키워드</span> (초록색)
  │
  ├── Highlight 키워드 하이라이트
  │   └── <span class="keyword-match">키워드</span> (주황색)
  │
  └── JSON 감지 및 포맷팅
      └── extractJsonObjects(str) — 브래킷 카운팅 JSON 추출
          ├── JSON.parse() 성공 시 → 접기/펼치기 UI
          └── 실패 시 → 원본 그대로 표시
```

### JSON 확장/축소 (extractJsonObjects)

정규식 대신 브래킷 카운팅으로 JSON 객체를 추출:

```
문자열 순회
  ├── '{' 또는 '[' 발견 → depth++, 시작 위치 기록
  ├── '}' 또는 ']' 발견 → depth--
  │   └── depth === 0 → JSON 후보 추출
  │       └── JSON.parse() 시도
  │           ├── 성공 → JSON 프리뷰 (축약) + 클릭 시 펼치기
  │           └── 실패 → 원본 텍스트 유지
  └── 문자열 내부 (따옴표) → 브래킷 무시
```

---

## 5. 인디케이터 미니맵

### updateIndicator(logs)

전체 필터링된 로그를 스캔하여 미니맵 마커를 생성:

```
filteredLogs 순회
  │
  ├── log.isBookmarked === true
  │   └── 좌측에 파란 마커 (3px × 3px)
  │       position: (index / totalCount × 100)%
  │
  ├── log.isHighlighted === true
  │   └── 좌측에 노란 마커
  │
  ├── log.level === 'ERROR' || log.level === 'FATAL'
  │   └── 우측에 빨간 마커
  │
  └── log.level === 'WARN'
      └── 우측에 주황 마커
```

마커 클릭 시 해당 로그 인덱스로 `scrollToRow()` 호출.

---

## 성능 고려사항

| 항목 | 전략 | 임계값 |
|------|------|--------|
| 필터 입력 | 디바운스 | 500ms |
| 가상 스크롤 | DOM Pool 재사용 | ≥1000건 |
| DOM Pool 크기 | 고정 풀 | 100개 TR |
| 스크롤 렌더링 | requestAnimationFrame | 매 프레임 |
| 비동기 필터링 | 청크 분할 + requestIdleCallback | 500건/청크 |
| 스크롤 변경 감지 | 최소 변경량 | 50px |
| 필터 상태 | 캐싱 + 명시적 무효화 | - |
| kubectl 타임아웃 | 일반 조회 / 전체 다운로드 | 30초 / 120초 |
| kubectl 버퍼 | 전체 로그 다운로드 maxBuffer | 100MB |
