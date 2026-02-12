# 06. 사용자 인터랙션 흐름

## 주요 워크플로우

### 1. K8s Pod 연결 워크플로우

```
앱 시작
  → loadTheme() — localStorage에서 테마 로드
  → loadSettings() — 이전 선택 상태 복원
  → loadContexts() — K8s context 목록 조회
       → API: GET /api/k8s/contexts
       → Context 드롭다운 채우기
       → 이전 선택값 복원 (applySettings)
  → Context 변경 시
       → loadNamespaces() — Namespace 목록 조회
       → API: GET /api/k8s/namespaces?context=
  → Namespace 변경 시
       → loadPods() — Pod 목록 조회
       → API: GET /api/k8s/pods?namespace=&context=
       → Pod 검색 드롭다운 채우기 (podSearch.setItems)
  → Pod 선택 후 "연결" 클릭
       → connectPod()
       → saveSettings() — 선택 상태 localStorage 저장
```

### 2. Pod 로그 조회 + 스트리밍 워크플로우

```
connectPod()
  → 기존 스트림이 있으면 stopStream()
  → clearLogs() — 이전 로그 제거
  │
  ├── 1단계: 초기 로그 로드
  │   → API: GET /api/k8s/logs?pod=&namespace=&context=&tail=5000
  │   → 응답의 logs[] → allLogs에 추가
  │   → applyFilter() → renderLogs()
  │
  └── 2단계: 실시간 스트리밍 시작
      → startStream(pod, namespace, context)
      → EventSource 열기: /api/k8s/logs/stream?pod=&namespace=&context=&tail=5000
      → event: init → streamId 저장
      → event: log → addLogEntry(log) × 반복
          → passesFilter() → filteredLogs에 추가
          → appendFilteredLog() — 증분 DOM 업데이트
          → autoScroll 활성이면 scrollToBottom()
      → event: error → 에러 메시지 표시
      → event: end → 스트리밍 종료 처리
```

### 3. 전체 로그 다운로드 워크플로우

```
"전체 로그" 버튼 클릭
  → fetchAllLogs()
  → 기존 스트림이 있으면 stopStream()
  → setStatus("전체 로그 다운로드 중...")
  → API: GET /api/k8s/logs/all?pod=&namespace=&context=
  → 응답의 logs[] → allLogs 교체
  → applyFilter() → renderLogs()
  → setStatus("완료: N줄 로드")
```

### 4. 로컬 파일 업로드 워크플로우

```
"파일 열기" 버튼 클릭
  → <input type="file" accept=".log,.txt"> 트리거
  → FileReader로 텍스트 읽기
  → parseLogLines(lines) — 클라이언트 사이드 파싱
  → allLogs 교체
  → applyFilter() → renderLogs()
  → setStatus("파일 로드: 파일명 (N줄)")
```

### 5. 샘플 로그 로드 워크플로우

```
"샘플" 버튼 클릭
  → loadSampleLogs()
  → API: GET /api/load
  → refreshLogs() — API: GET /api/logs
  → allLogs 교체
  → applyFilter() → renderLogs()
```

---

## 필터링 흐름

### 필터 입력 변경

```
사용자가 필터 입력 필드 수정 (word-find, word-remove, class-show, class-remove, highlight-keyword)
  → invalidateFilterCache() — 캐시 무효화
  → updateFilterInputStyles() — 비활성 필터 취소선 스타일
  → debounce(500ms)
  → applyFilter()
      → getCachedFilterState() — DOM에서 필터 상태 수집, 캐시
      → getCurrentCenterLogLine() — 현재 뷰포트 중심 라인 기억
      → allLogs.filter(passesFilter) → filteredLogs
      → checkHighlight() — 하이라이트 여부 표시
      → updateHighlightCount() — 카운터 갱신
      → renderLogs(filteredLogs) — 테이블 다시 렌더
      → 중심 라인 위치로 스크롤 복원
      → updateIndicator() — 미니맵 갱신
```

### 로그 레벨 체크박스 변경

```
체크박스 토글
  → invalidateFilterCache()
  → applyFilter() (디바운스 없이 즉시)
```

### 필터 활성화 체크박스 토글

```
체크박스 토글 (예: #word-find-enabled)
  → invalidateFilterCache()
  → updateFilterInputStyles()
     → 비활성: 입력 필드에 .filter-disabled 추가 (회색, 취소선)
     → 활성: .filter-disabled 제거
  → applyFilter()
```

### 필터 프리셋 적용

```
프리셋 드롭다운에서 선택 → "적용" 클릭
  → loadPreset()
  → localStorage에서 프리셋 읽기
  → setFilterState(preset) — 모든 필터 입력값 + 체크박스 상태 복원
  → invalidateFilterCache()
  → applyFilter()
```

---

## 하이라이트 네비게이션 흐름

### F3 키 (다음 하이라이트)

```
F3 키 입력
  → navigateHighlight(1)
  → highlightedIndices 배열에서 다음 인덱스 찾기
  → currentHighlightPos 증가 (순환)
  → scrollToRow(해당 인덱스) — 뷰포트 중앙으로 이동
  → selectRow(해당 인덱스) — 파란 배경 선택
  → updateHighlightCount() — "3/10" 형식 갱신
```

### Shift+F3 키 (이전 하이라이트)

```
Shift+F3 키 입력
  → navigateHighlight(-1)
  → 위와 동일하되 인덱스 감소 (역순환)
```

---

## 선택 흐름

### 단일 클릭

```
행 클릭 (Shift/Ctrl 없이)
  → clearSelection()
  → selectedLogIndices = { index }
  → lastSelectedIndex = index
  → updateSelectionUI() — 파란 배경 적용
```

### Shift+클릭 (범위 선택)

```
Shift + 행 클릭
  → start = lastSelectedIndex, end = 클릭한 인덱스
  → min~max 범위의 모든 인덱스를 selectedLogIndices에 추가
  → updateSelectionUI()
```

### Ctrl+클릭 (토글 선택)

```
Ctrl + 행 클릭
  → selectedLogIndices에 index가 있으면 제거, 없으면 추가
  → lastSelectedIndex = index
  → updateSelectionUI()
```

### Ctrl+드래그 (다중 선택)

```
Ctrl + 마우스다운 (행 위에서)
  → isDragging = true
  → dragStartIndex = 마우스 아래 행 인덱스
  → dragBaseSelection = new Set(selectedLogIndices) — 기존 선택 보존
  │
  → 마우스이동 (5px 이상)
  │   → dragActivated = true
  │   → .log-table-container에 .is-dragging 추가 (텍스트 선택 방지)
  │   → updateDragSelection(currentIndex)
  │       → dragStartIndex~currentIndex 범위를 selectedLogIndices에 추가
  │       → dragBaseSelection도 유지
  │   → 가장자리 자동 스크롤
  │       → 위쪽 30px 이내 → 위로 스크롤
  │       → 아래쪽 30px 이내 → 아래로 스크롤
  │
  → 마우스업
      → isDragging = false
      → .is-dragging 제거
      → 자동 스크롤 타이머 해제
```

### 더블클릭 (북마크 토글)

```
행 더블클릭
  → log.isBookmarked = !log.isBookmarked
  → updateSingleRow(row, log, index) — 전체 재렌더 없이 단일 행 갱신
      → ★ 아이콘 추가/제거
      → .log-row-bookmark 클래스 토글
  → updateIndicator() — 미니맵 갱신
```

### 클립보드 복사 (Ctrl+C)

```
Ctrl+C (선택된 행이 있을 때)
  → copySelectedLogs()
  → selectedLogIndices를 정렬
  → 각 로그를 "dateTime level [thread] class message" 형식으로 포맷
  → navigator.clipboard.writeText()
  → setStatus("N줄 복사됨")
```

---

## 키보드 단축키

| 단축키 | 동작 | 조건 |
|--------|------|------|
| F3 | 다음 하이라이트로 이동 | 하이라이트 키워드 존재 시 |
| Shift+F3 | 이전 하이라이트로 이동 | 하이라이트 키워드 존재 시 |
| Ctrl+G (Cmd+G) | Goto Line 입력 포커스 | 항상 |
| Ctrl+C (Cmd+C) | 선택된 로그 클립보드 복사 | 선택된 행 존재 시 |
| Ctrl+A (Cmd+A) | 전체 로그 선택 | 포커스가 입력 필드에 없을 때 |
| Escape | 선택 해제 / 입력 필드 blur | 항상 |

---

## 자동 스크롤 흐름

```
초기 상태: autoScroll = true

사용자 스크롤 (로그 테이블에서)
  → 스크롤 위치가 하단에서 28px 이상 떨어지면
      → autoScroll = false
      → updateAutoScrollStatus() — "OFF" 표시

새 로그 도착 (SSE) + autoScroll === true
  → scrollToBottom()

스크롤이 하단에 도달하면
  → autoScroll = true (자동 복원)

상태바의 "자동스크롤" 클릭
  → toggleAutoScroll()
  → autoScroll 토글
  → 활성화 시 scrollToBottom()
```

---

## 스트리밍 제어 흐름

### 시작

```
"시작" 버튼 클릭 (또는 connectPod 내부)
  → startStream(pod, namespace, context)
  → EventSource 생성
  → isStreaming = true
  → 버튼 텍스트 → "중지" (빨간 배경)
  → 상태 → "스트리밍 중..."
```

### 중지

```
"중지" 버튼 클릭
  → stopStream()
  → eventSource.close()
  → API: GET /api/k8s/logs/stream/stop?streamId=
  → isStreaming = false
  → 버튼 텍스트 → "시작" (초록 배경)
  → 상태 → "스트리밍 중지"
```

---

## 엣지 케이스

| 상황 | 동작 |
|------|------|
| kubectl이 설치되지 않음 | 서버 에러 응답, 클라이언트에서 에러 메시지 표시 |
| K8s context가 없음 | 빈 드롭다운, 상태 메시지 "context 없음" |
| Pod가 없음 | 빈 드롭다운 |
| Pod 연결 실패 | 에러 메시지 표시 |
| SSE 연결 끊김 | event: error 이벤트 처리 |
| 매우 큰 로그 (수만 건) | 가상 스크롤 자동 활성화 |
| 빈 로그 라인 | 파서가 null 반환 → 무시 |
| 패턴 매칭 실패 라인 | 폴백 처리 (전체를 message로) |
| 모든 로그가 필터로 제거됨 | 빈 테이블, 상태바에 "표시: 0/N" |
| 로컬 파일이 비어 있음 | "로그를 찾을 수 없습니다" 상태 메시지 |
| JSON이 포함된 메시지 | 자동 감지 → 축약 표시 → 클릭 시 펼치기 |
| 하이라이트 없는 상태에서 F3 | 동작 없음 |
| 필터 프리셋 이름 미입력 | prompt() 취소 → 저장 중단 |
| localStorage 접근 불가 | 프리셋/설정 저장 실패 (에러 무시, 기능은 동작) |
