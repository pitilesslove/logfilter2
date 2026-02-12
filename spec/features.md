# 기능 리스트 (Feature List)

> 관리 규칙: [spec/07-feature-list-rules.md](07-feature-list-rules.md)
> 최종 업데이트: 2026-02-12

---

## 요약

| ID | 기능 | 상태 | 의존성 |
|----|------|------|--------|
| F1 | Express 서버 구축 | [done] | — |
| F2 | 로그 파싱 엔진 | [done] | — |
| F3 | 실시간 스트리밍 (SSE) | [done] | ← F1, F2 |
| F4 | 프론트엔드 UI 기본 구조 | [done] | — |
| F5 | 필터 시스템 | [done] | ← F4 |
| F6 | 가상 스크롤 & 렌더링 | [done] | ← F4 |
| F7 | 선택 & 네비게이션 | [done] | ← F4, F6 |
| F8 | 로그 작업 (다운로드/파일/샘플) | [done] | ← F1, F4 |
| F9 | K8s 연결 관리 | [done] | ← F1, F4 |
| F10 | 필터 기능 강화 | [pending] | ← F5 |
| F11 | 안정성 개선 | [pending] | ← F1, F3 |
| F12 | UI/UX 개선 | [pending] | ← F4, F6 |
| F13 | 내보내기 확장 | [pending] | ← F8 |
| F14 | 성능 최적화 | [pending] | ← F1, F6 |
| F15 | 고급 기능 | [pending] | ← F9, F10 |
| F16 | 인프라 & 배포 | [pending] | ← F1 |
| F17 | 개발자 경험 | [pending] | — |

---

## F1. Express 서버 구축 [done]
> 참조: spec/02-architecture.md | 파일: server/index.js, package.json, logfilter.sh
<!-- done: 2026-02-10 -->

#### F1.1 [done] Express 앱 초기화
Node.js + Express 서버 설정 및 미들웨어 구성
<!-- done: 2026-02-10 -->
- F1.1.1 [done] Express 앱 생성 및 포트 설정 (8888)
- F1.1.2 [done] CORS 미들웨어 적용
- F1.1.3 [done] JSON 파서 미들웨어 적용
- F1.1.4 [done] 정적 파일 서빙 (samples/ 디렉토리)

#### F1.2 [done] API 라우팅 구조
REST API 엔드포인트 설계 및 라우터 분리
<!-- done: 2026-02-10 -->
- F1.2.1 [done] K8s 라우터 분리 (server/routes/k8s.js)
- F1.2.2 [done] 기본 API 라우트 (logs, load, clear)
- F1.2.3 [done] 메인 UI 서빙 (GET / → samples/index.html)

#### F1.3 [done] 서비스 레이어
비즈니스 로직을 서비스로 분리
<!-- done: 2026-02-10 -->
- F1.3.1 [done] K8sClient 서비스 (kubectl CLI 래퍼)
- F1.3.2 [done] LogParser 서비스 (로그 파싱)
- F1.3.3 [done] LogStore 서비스 (인메모리 저장소)

#### F1.4 [done] 실행 환경
서버 실행 스크립트 및 npm 스크립트
<!-- done: 2026-02-10 -->
- F1.4.1 [done] npm start / npm run dev (--watch) 스크립트
- F1.4.2 [done] logfilter.sh (tmux 기반 실행 스크립트)

---

## F2. 로그 파싱 엔진 [done]
> 참조: spec/04-core-algorithm.md §1 | 파일: server/services/logParser.js, samples/index.html
<!-- done: 2026-02-10 -->

#### F2.1 [done] 서버 사이드 파서 (logParser.js)
6가지 로그 포맷 정규식 매칭 + 폴백 처리
<!-- done: 2026-02-10 -->
- F2.1.1 [done] Spring Boot 패턴 매칭
- F2.1.2 [done] DX 패턴 매칭
- F2.1.3 [done] Logback (traceId 포함) 패턴 매칭
- F2.1.4 [done] Logback (traceId 없음) 패턴 매칭
- F2.1.5 [done] 레거시 Bracket 패턴 매칭
- F2.1.6 [done] 일반 패턴 매칭
- F2.1.7 [done] 폴백 처리 (전체 라인을 message로)

#### F2.2 [done] 클라이언트 사이드 파서 (parseLogLines)
로컬 파일 업로드용 브라우저 내 파싱
<!-- done: 2026-02-10 -->
- F2.2.1 [done] 서버 파서와 동일한 정규식 패턴 동기화
- F2.2.2 [done] LogEntry 구조 생성 (line, dateTime, level, thread, class, message)

#### F2.3 [done] 레벨 정규화
다양한 로그 레벨 문자열을 표준화
<!-- done: 2026-02-10 -->
- F2.3.1 [done] LEVEL_MAP 기반 정규화 (WARNING→WARN, SEVERE→ERROR)
- F2.3.2 [done] Logback 콤마→마침표 치환 (날짜 포맷)
- F2.3.3 [done] traceId 앞 8자리 추출 (thread 필드)

---

## F3. 실시간 스트리밍 (SSE) [done]
> 참조: spec/02-architecture.md, spec/06-user-interaction.md §2 | 파일: server/routes/k8s.js, server/services/k8sClient.js
<!-- done: 2026-02-10 -->

#### F3.1 [done] SSE 서버 구현
Server-Sent Events 기반 실시간 로그 전송
<!-- done: 2026-02-10 -->
- F3.1.1 [done] /api/k8s/logs/stream SSE 엔드포인트
- F3.1.2 [done] kubectl logs -f 를 child_process.spawn으로 실행
- F3.1.3 [done] 라인 단위 파싱 후 event: log 전송
- F3.1.4 [done] event: init (streamId 발급), event: error, event: end

#### F3.2 [done] 스트림 관리
활성 스트림 추적 및 정리
<!-- done: 2026-02-10 -->
- F3.2.1 [done] activeStreams Map (streamId → {process, res})
- F3.2.2 [done] 클라이언트 연결 종료 시 프로세스 kill
- F3.2.3 [done] /api/k8s/logs/stream/stop 수동 중지 API

#### F3.3 [done] 클라이언트 SSE 수신
브라우저 EventSource를 통한 실시간 로그 수신
<!-- done: 2026-02-10 -->
- F3.3.1 [done] EventSource 생성 및 이벤트 리스너 등록
- F3.3.2 [done] 증분 필터링 (addLogEntry → passesFilter → appendFilteredLog)
- F3.3.3 [done] 자동 스크롤 연동
- F3.3.4 [done] 시작/중지 버튼 상태 전환

---

## F4. 프론트엔드 UI 기본 구조 [done]
> 참조: spec/05-ui-layout.md | 파일: samples/index.html
<!-- done: 2026-02-10 -->

#### F4.1 [done] 레이아웃 구성
수직 분할 flex 레이아웃 (h-screen)
<!-- done: 2026-02-10 -->
- F4.1.1 [done] 메뉴바 (테마 토글)
- F4.1.2 [done] K8s 선택 패널
- F4.1.3 [done] 필터 패널 (6개 fieldset 그룹)
- F4.1.4 [done] 컨트롤 패널 (시작/중지, 전체로그, 샘플, 다운로드, 파일열기, 클리어)
- F4.1.5 [done] 메인 영역 (인디케이터 + 로그 테이블)
- F4.1.6 [done] 상태바

#### F4.2 [done] 테마 시스템
Tailwind darkMode: 'class' 기반 다크/라이트 모드
<!-- done: 2026-02-10 -->
- F4.2.1 [done] HTML class="dark" 토글
- F4.2.2 [done] localStorage에 테마 저장/복원
- F4.2.3 [done] 모든 UI 요소에 dark: 변형 적용

#### F4.3 [done] 로그 테이블 구조
7컬럼 고정 테이블 (모노스페이스 폰트)
<!-- done: 2026-02-10 -->
- F4.3.1 [done] 컬럼: #(60px), ★(30px), DateTime(160px), Level(50px), Thread(100px), Class(150px), Message(나머지)
- F4.3.2 [done] 레벨별 텍스트 색상 (TRACE=회색, DEBUG=파랑, INFO=초록, WARN=주황, ERROR=빨강, FATAL=진빨강)
- F4.3.3 [done] 행 배경색 우선순위 (선택 > 하이라이트 > 북마크 > 에러 > 경고)
- F4.3.4 [done] RTL direction 트릭으로 스크롤바 좌측 배치

#### F4.4 [done] 인디케이터 미니맵
16px 너비 좌측 패널, 로그 위치 시각화
<!-- done: 2026-02-10 -->
- F4.4.1 [done] 좌측: 북마크(파랑), 하이라이트(노랑) 마커
- F4.4.2 [done] 우측: 에러(빨강), 경고(주황) 마커
- F4.4.3 [done] 마커 클릭 → 해당 로그로 스크롤

#### F4.5 [done] 상태바
하단 정보 표시 바
<!-- done: 2026-02-10 -->
- F4.5.1 [done] 상태 메시지 (좌측)
- F4.5.2 [done] 자동 스크롤 ON/OFF (중앙, 클릭 토글)
- F4.5.3 [done] 표시 건수 "표시: 필터된수/전체수" (우측)

---

## F5. 필터 시스템 [done]
> 참조: spec/04-core-algorithm.md §2, spec/06-user-interaction.md §필터링 | 파일: samples/index.html
<!-- done: 2026-02-10 -->

#### F5.1 [done] 필터 체인 (passesFilter)
5가지 필터 조건 순차 검사
<!-- done: 2026-02-10 -->
- F5.1.1 [done] 로그 레벨 필터 (6개 체크박스: TRACE~FATAL)
- F5.1.2 [done] Word Find — 포함 필터 (message + class, `|` 구분 OR)
- F5.1.3 [done] Word Remove — 제외 필터 (message, `|` 구분 OR)
- F5.1.4 [done] Class Show — 클래스 포함 필터
- F5.1.5 [done] Class Remove — 클래스 제외 필터

#### F5.2 [done] 필터 UI
필터 입력 필드 + 활성화 체크박스
<!-- done: 2026-02-10 -->
- F5.2.1 [done] 각 필터 입력 필드 색상 코딩 (Find=초록, Remove=빨강, Show=파랑, Highlight=주황)
- F5.2.2 [done] 활성화/비활성화 체크박스
- F5.2.3 [done] 비활성 필터 취소선 스타일 (.filter-disabled)
- F5.2.4 [done] 글꼴 크기 슬라이더 (#font-size)
- F5.2.5 [done] Goto Line 입력 (#goto-line)

#### F5.3 [done] 하이라이트 시스템
키워드 하이라이트 + F3 네비게이션
<!-- done: 2026-02-10 -->
- F5.3.1 [done] 하이라이트 키워드 입력 (#highlight-keyword)
- F5.3.2 [done] 매칭 행 노랑 배경 (.log-row-highlight)
- F5.3.3 [done] F3 / Shift+F3 — 다음/이전 하이라이트 이동
- F5.3.4 [done] 카운터 표시 "N/M" (#highlight-count)

#### F5.4 [done] 필터 프리셋
필터 상태 저장/로드/삭제
<!-- done: 2026-02-10 -->
- F5.4.1 [done] 프리셋 저장 (prompt()로 이름 입력, localStorage)
- F5.4.2 [done] 프리셋 로드 (드롭다운 선택 → 적용)
- F5.4.3 [done] 프리셋 삭제

#### F5.5 [done] 필터 성능 최적화
대용량 로그 필터링 최적화
<!-- done: 2026-02-10 -->
- F5.5.1 [done] 필터 상태 캐싱 (cachedFilterState, 명시적 무효화)
- F5.5.2 [done] 디바운스 500ms (텍스트 입력 필터)
- F5.5.3 [done] 비동기 청크 처리 (1000건 이상, 500건/청크, requestIdleCallback)
- F5.5.4 [done] 스트리밍 중 증분 필터링 (전체 재필터링 없이 신규 로그만)

---

## F6. 가상 스크롤 & 렌더링 [done]
> 참조: spec/04-core-algorithm.md §3 | 파일: samples/index.html (rowPool, virtualScroll 객체)
<!-- done: 2026-02-10 -->

#### F6.1 [done] DOM Pool 구현
100개 재사용 가능 TR 요소 풀
<!-- done: 2026-02-10 -->
- F6.1.1 [done] rowPool.init() — 100개 TR 생성 (각 7개 TD)
- F6.1.2 [done] topPadding / bottomPadding 스페이서 TR
- F6.1.3 [done] updateRow() — 풀 행에 로그 데이터 매핑

#### F6.2 [done] 가상 스크롤 로직
1000건 이상 자동 활성화
<!-- done: 2026-02-10 -->
- F6.2.1 [done] 활성화 임계값 (threshold: 1000)
- F6.2.2 [done] 가시 범위 계산 (scrollTop / rowHeight ± bufferSize)
- F6.2.3 [done] requestAnimationFrame 기반 렌더링
- F6.2.4 [done] 스크롤 변경 감지 임계값 (50px)

#### F6.3 [done] 스크롤 위치 보존
필터 변경 시 뷰포트 중심 유지
<!-- done: 2026-02-10 -->
- F6.3.1 [done] getCurrentCenterLogLine() — 뷰포트 중앙 로그 라인 번호 기억
- F6.3.2 [done] findNearestLogIndex() — 바이너리 서치로 가장 가까운 행 탐색
- F6.3.3 [done] scrollToCenter() — 복원

#### F6.4 [done] 메시지 포맷팅
메시지 셀 HTML 생성
<!-- done: 2026-02-10 -->
- F6.4.1 [done] escapeHtml() — XSS 방지
- F6.4.2 [done] Word Find 매칭 하이라이트 (초록, .word-filter-match)
- F6.4.3 [done] Highlight 키워드 강조 (주황, .keyword-match)
- F6.4.4 [done] JSON 감지 및 접기/펼치기 (extractJsonObjects, 브래킷 카운팅)

---

## F7. 선택 & 네비게이션 [done]
> 참조: spec/06-user-interaction.md §선택, §키보드 | 파일: samples/index.html
<!-- done: 2026-02-10 -->

#### F7.1 [done] 클릭 선택
단일/범위/토글 선택 지원
<!-- done: 2026-02-10 -->
- F7.1.1 [done] 단일 클릭 — clearSelection() + 단일 선택
- F7.1.2 [done] Shift+클릭 — 범위 선택 (lastSelectedIndex ~ 클릭 인덱스)
- F7.1.3 [done] Ctrl+클릭 — 토글 선택 (추가/제거)

#### F7.2 [done] 드래그 선택
Ctrl+드래그로 연속 다중 선택
<!-- done: 2026-02-10 -->
- F7.2.1 [done] 5px 이동 임계값 후 활성화
- F7.2.2 [done] .is-dragging 클래스 (텍스트 선택 방지)
- F7.2.3 [done] 가장자리 자동 스크롤 (상하 30px 이내)
- F7.2.4 [done] 기존 선택 보존 (dragBaseSelection)

#### F7.3 [done] 북마크
더블클릭 토글 북마크
<!-- done: 2026-02-10 -->
- F7.3.1 [done] 더블클릭 — isBookmarked 토글
- F7.3.2 [done] ★ 아이콘 표시/제거
- F7.3.3 [done] .log-row-bookmark 배경색
- F7.3.4 [done] 인디케이터 미니맵 갱신

#### F7.4 [done] 클립보드 복사
선택된 로그 클립보드 복사
<!-- done: 2026-02-10 -->
- F7.4.1 [done] Ctrl+C — copySelectedLogs()
- F7.4.2 [done] "dateTime level [thread] class message" 형식
- F7.4.3 [done] navigator.clipboard.writeText()

#### F7.5 [done] 키보드 단축키
전역 키보드 단축키
<!-- done: 2026-02-10 -->
- F7.5.1 [done] F3 / Shift+F3 — 하이라이트 네비게이션
- F7.5.2 [done] Ctrl+G (Cmd+G) — Goto Line 포커스
- F7.5.3 [done] Ctrl+A (Cmd+A) — 전체 선택
- F7.5.4 [done] Escape — 선택 해제 / 입력 blur

#### F7.6 [done] 자동 스크롤
새 로그 도착 시 자동 하단 스크롤
<!-- done: 2026-02-10 -->
- F7.6.1 [done] autoScroll 상태 관리 (하단 28px 이내 → ON)
- F7.6.2 [done] 상태바 ON/OFF 표시 + 클릭 토글
- F7.6.3 [done] SSE 새 로그 도착 시 scrollToBottom()

---

## F8. 로그 작업 (다운로드/파일/샘플) [done]
> 참조: spec/06-user-interaction.md §3~5 | 파일: samples/index.html, server/routes/k8s.js
<!-- done: 2026-02-10 -->

#### F8.1 [done] 전체 로그 다운로드
Pod 전체 로그를 한 번에 로드
<!-- done: 2026-02-10 -->
- F8.1.1 [done] fetchAllLogs() — GET /api/k8s/logs/all (tail 없음)
- F8.1.2 [done] 서버: 120초 타임아웃, 100MB maxBuffer
- F8.1.3 [done] 다운로드 중 상태 메시지 표시

#### F8.2 [done] 텍스트 파일 다운로드
필터링된 로그를 텍스트 파일로 저장
<!-- done: 2026-02-10 -->
- F8.2.1 [done] filteredLogs를 텍스트로 변환
- F8.2.2 [done] Blob → URL.createObjectURL → 다운로드 트리거

#### F8.3 [done] 로컬 파일 업로드
.log/.txt 파일을 브라우저에서 직접 파싱
<!-- done: 2026-02-10 -->
- F8.3.1 [done] `<input type="file" accept=".log,.txt">` 트리거
- F8.3.2 [done] FileReader로 텍스트 읽기
- F8.3.3 [done] parseLogLines()로 클라이언트 사이드 파싱
- F8.3.4 [done] allLogs 교체 → applyFilter() → renderLogs()

#### F8.4 [done] 샘플 로그
데모용 샘플 로그 로드
<!-- done: 2026-02-10 -->
- F8.4.1 [done] loadSampleLogs() — GET /api/load
- F8.4.2 [done] refreshLogs() — GET /api/logs

#### F8.5 [done] 로그 클리어
전체 로그 삭제
<!-- done: 2026-02-10 -->
- F8.5.1 [done] clearLogs() — allLogs, filteredLogs 초기화
- F8.5.2 [done] DOM 및 인디케이터 초기화

---

## F9. K8s 연결 관리 [done]
> 참조: spec/06-user-interaction.md §1 | 파일: samples/index.html, server/routes/k8s.js, server/services/k8sClient.js
<!-- done: 2026-02-10 -->

#### F9.1 [done] Context/Namespace/Pod 선택
계단식 드롭다운 (Context → Namespace → Pod)
<!-- done: 2026-02-10 -->
- F9.1.1 [done] loadContexts() — GET /api/k8s/contexts (현재 context 포함)
- F9.1.2 [done] loadNamespaces() — GET /api/k8s/namespaces?context=
- F9.1.3 [done] loadPods() — GET /api/k8s/pods?namespace=&context=

#### F9.2 [done] Pod 검색 드롭다운
커스텀 검색 가능 드롭다운 (podSearch 객체)
<!-- done: 2026-02-10 -->
- F9.2.1 [done] 실시간 타이핑 필터링 + 매칭 하이라이트
- F9.2.2 [done] 방향키(↑↓) + Enter 키보드 네비게이션
- F9.2.3 [done] Backspace 검색어 삭제
- F9.2.4 [done] 검색어 팝업 표시 (가운데 모달)

#### F9.3 [done] Pod 연결
선택한 Pod의 로그 조회 및 스트리밍 시작
<!-- done: 2026-02-10 -->
- F9.3.1 [done] connectPod() — 초기 로그 로드 (tail=5000) + SSE 스트리밍 시작
- F9.3.2 [done] 기존 스트림 정리 후 새 연결
- F9.3.3 [done] saveSettings() — 선택 상태 localStorage 저장

#### F9.4 [done] 설정 영속화
이전 선택 상태 복원
<!-- done: 2026-02-10 -->
- F9.4.1 [done] loadSettings() — localStorage에서 이전 선택값 복원
- F9.4.2 [done] applySettings() — Context/Namespace/Pod 드롭다운에 반영

---

## F10. 필터 기능 강화
> 참조: spec/04-core-algorithm.md §2 | 파일: samples/index.html
> ← F5 완료 후

#### F10.1 [pending] 정규표현식 필터 지원
Word Find/Remove에서 정규식 사용 가능하도록 확장
- F10.1.1 [pending] 정규식 토글 버튼 (각 필터 옆)
- F10.1.2 [pending] 정규식 유효성 검사 + 에러 표시
- F10.1.3 [pending] passesFilter()에 정규식 분기 추가

#### F10.2 [pending] 시간 범위 필터링
시작/종료 시간 지정으로 로그 범위 축소
- F10.2.1 [pending] 시작 시간 입력 (datetime-local 또는 텍스트)
- F10.2.2 [pending] 종료 시간 입력
- F10.2.3 [pending] passesFilter()에 시간 범위 검사 추가

#### F10.3 [pending] 인라인 검색 (Ctrl+F 스타일)
빠른 텍스트 검색 UI
- F10.3.1 [pending] Ctrl+F로 검색 바 열기
- F10.3.2 [pending] 실시간 매칭 + 하이라이트
- F10.3.3 [pending] Enter/Shift+Enter로 다음/이전 이동

---

## F11. 안정성 개선
> 참조: spec/06-user-interaction.md §엣지 케이스 | 파일: server/routes/k8s.js, samples/index.html
> ← F1, F3 완료 후

#### F11.1 [pending] 자동 재연결
SSE 연결 끊김 시 자동 복구
- F11.1.1 [pending] EventSource onerror 시 재연결 로직
- F11.1.2 [pending] 지수 백오프 재시도 (1초, 2초, 4초, ...)
- F11.1.3 [pending] 재연결 상태 표시 (상태바)

#### F11.2 [pending] 에러 핸들링 개선
사용자 친화적 에러 메시지
- F11.2.1 [pending] kubectl 미설치 시 안내 메시지
- F11.2.2 [pending] K8s 인증 만료 시 안내
- F11.2.3 [pending] 네트워크 에러 구분 표시

---

## F12. UI/UX 개선
> 참조: spec/05-ui-layout.md | 파일: samples/index.html
> ← F4, F6 완료 후

#### F12.1 [pending] 컬럼 너비 조절
드래그로 컬럼 너비 리사이즈
- F12.1.1 [pending] 컬럼 헤더 경계에 리사이즈 핸들
- F12.1.2 [pending] 드래그로 너비 조절
- F12.1.3 [pending] 변경된 너비 localStorage 저장

#### F12.2 [pending] 컬럼 표시/숨김
불필요한 컬럼 숨기기
- F12.2.1 [pending] 컬럼 표시/숨김 설정 UI
- F12.2.2 [pending] 숨겨진 컬럼 공간 재배분
- F12.2.3 [pending] 설정 localStorage 저장

#### F12.3 [pending] 로그 상세 보기 모달
긴 메시지, 스택트레이스 전체 보기
- F12.3.1 [pending] 행 클릭 시 모달 또는 하단 패널 열기
- F12.3.2 [pending] 전체 메시지 표시 (줄바꿈, 포맷팅)
- F12.3.3 [pending] JSON 구문 하이라이팅

#### F12.4 [pending] 키보드 단축키 도움말
? 키로 단축키 목록 표시
- F12.4.1 [pending] ? 키 입력 시 오버레이 모달
- F12.4.2 [pending] 모든 단축키 + 설명 목록
- F12.4.3 [pending] ESC로 닫기

#### F12.5 [pending] 북마크 영구 저장
세션 간 북마크 유지
- F12.5.1 [pending] 북마크 상태 localStorage 저장
- F12.5.2 [pending] 동일 Pod 재연결 시 복원

#### F12.6 [pending] 최근 연결 Pod 기록
이전 연결 이력 표시
- F12.6.1 [pending] 최근 연결 Pod 목록 localStorage 저장 (최대 10개)
- F12.6.2 [pending] 빠른 재연결 UI

---

## F13. 내보내기 확장
> 참조: spec/06-user-interaction.md | 파일: samples/index.html
> ← F8 완료 후

#### F13.1 [pending] JSON 형식 내보내기
구조화된 JSON 파일로 내보내기
- F13.1.1 [pending] filteredLogs를 JSON 배열로 변환
- F13.1.2 [pending] 파일 다운로드 트리거

#### F13.2 [pending] CSV 형식 내보내기
스프레드시트 호환 CSV 파일로 내보내기
- F13.2.1 [pending] 헤더 행 + 데이터 행 생성
- F13.2.2 [pending] 쉼표/줄바꿈 이스케이프 처리

#### F13.3 [pending] 선택한 로그만 내보내기
selectedLogIndices 기반 부분 내보내기
- F13.3.1 [pending] 현재 선택된 행만 텍스트/JSON/CSV로 내보내기
- F13.3.2 [pending] 내보내기 포맷 선택 드롭다운

---

## F14. 성능 최적화
> 참조: spec/04-core-algorithm.md §성능 | 파일: server/, samples/index.html
> ← F1, F6 완료 후

#### F14.1 [pending] 서버 사이드 필터링
대용량 로그 서버에서 사전 필터링
- F14.1.1 [pending] API 쿼리 파라미터로 필터 조건 전달
- F14.1.2 [pending] kubectl grep 파이프라인 또는 서버 필터링

#### F14.2 [pending] 로그 압축 다운로드
대용량 로그 gzip 압축 전송
- F14.2.1 [pending] 서버: gzip 인코딩 응답
- F14.2.2 [pending] Content-Encoding: gzip 헤더

---

## F15. 고급 기능
> ← F9, F10 완료 후

#### F15.1 [pending] Container 선택 완성
멀티 컨테이너 Pod 지원
- F15.1.1 [pending] Pod 선택 시 컨테이너 목록 조회
- F15.1.2 [pending] Container 드롭다운 활성화
- F15.1.3 [pending] 선택된 컨테이너의 로그만 조회

#### F15.2 [pending] 멀티 Pod 동시 조회
여러 Pod 로그를 동시에 표시
- F15.2.1 [pending] 탭 또는 분할 화면 UI
- F15.2.2 [pending] 복수 SSE 스트림 관리
- F15.2.3 [pending] Pod별 색상 구분

#### F15.3 [pending] 로그 통계 대시보드
레벨별 카운트, 시간대별 분포
- F15.3.1 [pending] 레벨별 카운트 차트
- F15.3.2 [pending] 시간대별 로그 발생 빈도 그래프

#### F15.4 [pending] 커스텀 로그 패턴 설정
UI에서 정규식 편집
- F15.4.1 [pending] 사용자 정의 패턴 입력 UI
- F15.4.2 [pending] 패턴 테스트 (샘플 로그로 검증)
- F15.4.3 [pending] 패턴 localStorage 저장

#### F15.5 [pending] 알림 기능
특정 키워드 발생 시 알림
- F15.5.1 [pending] 알림 키워드 설정
- F15.5.2 [pending] 브라우저 Notification API 연동
- F15.5.3 [pending] 소리 알림 옵션

---

## F16. 인프라 & 배포
> ← F1 완료 후

#### F16.1 [pending] Docker 이미지
컨테이너 이미지 생성
- F16.1.1 [pending] Dockerfile 작성 (Node.js 베이스)
- F16.1.2 [pending] .dockerignore 설정
- F16.1.3 [pending] 멀티스테이지 빌드 (경량 이미지)

#### F16.2 [pending] Kubernetes 배포
K8s 매니페스트 작성
- F16.2.1 [pending] Deployment + Service YAML
- F16.2.2 [pending] ServiceAccount + RBAC (Pod 로그 조회 권한)
- F16.2.3 [pending] ConfigMap (서버 설정)

#### F16.3 [pending] Helm 차트
패키지 배포용 Helm 차트
- F16.3.1 [pending] Chart.yaml, values.yaml
- F16.3.2 [pending] 템플릿 파일
- F16.3.3 [pending] 커스텀 값 오버라이드 지원

#### F16.4 [pending] 인증/권한 관리
K8s RBAC 연동
- F16.4.1 [pending] 사용자 인증 미들웨어
- F16.4.2 [pending] K8s ServiceAccount 토큰 기반 인증

---

## F17. 개발자 경험
> 독립적 (의존성 없음)

#### F17.1 [pending] TypeScript 마이그레이션
JavaScript → TypeScript 전환
- F17.1.1 [pending] tsconfig.json 설정
- F17.1.2 [pending] 서버 파일 .ts 변환
- F17.1.3 [pending] 타입 정의 (LogEntry, FilterState 등)

#### F17.2 [pending] 단위 테스트
핵심 로직 테스트 커버리지
- F17.2.1 [pending] logParser 테스트 (모든 패턴)
- F17.2.2 [pending] k8sClient 테스트 (모킹)
- F17.2.3 [pending] 필터 로직 테스트

#### F17.3 [pending] E2E 테스트 보강
Playwright 기반 종합 테스트
- F17.3.1 [pending] Pod 연결 플로우 테스트
- F17.3.2 [pending] 필터링 동작 테스트
- F17.3.3 [pending] 가상 스크롤 테스트

#### F17.4 [pending] API 문서화
OpenAPI/Swagger 명세
- F17.4.1 [pending] OpenAPI 3.0 스펙 파일
- F17.4.2 [pending] Swagger UI 제공 (/api-docs)
