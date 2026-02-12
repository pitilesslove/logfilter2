# 05. UI 레이아웃 및 컴포넌트 구조

## 실제 UI 파일

UI 구현은 단일 파일 `samples/index.html`에 HTML + CSS + JavaScript가 모두 포함되어 있다.

> **참조**: 실제 동작 UI를 확인하려면 `npm start` 후 `http://localhost:8888`에 접속한다.

---

## 전체 레이아웃

```
┌─────────────────────────────────────────────────────────────────────────┐
│  메뉴바                                                    [🌙 다크]   │
├─────────────────────────────────────────────────────────────────────────┤
│  K8s 선택 패널                                                          │
│  Context: [▼]   Namespace: [▼]   [새로고침]                             │
│  Pod: [검색 가능 드롭다운]   Container: [▼]   [연결]                      │
├─────────────────────────────────────────────────────────────────────────┤
│  필터 패널                                                               │
│  [Presets] [Word filter] [Class filter] [Highlight] [Log filter] [Font] │
├─────────────────────────────────────────────────────────────────────────┤
│  컨트롤 패널                                                             │
│  999줄 [시작] [전체로그] [샘플] [다운로드] [파일열기] [클리어]              │
├────┬────────────────────────────────────────────────────────────────────┤
│인디│  # │ ★ │ DateTime            │ Level │ Thread │ Class   │ Message  │
│케이├────┼───┼─────────────────────┼───────┼────────┼─────────┼──────────┤
│터  │  1 │   │ 2024-01-15 10:30:45 │ INFO  │ main   │ App     │ Started  │
│패널│  2 │ ★ │ 2024-01-15 10:30:46 │ ERROR │ http-1 │ Service │ Failed   │
│    │  3 │   │ 2024-01-15 10:30:47 │ WARN  │ main   │ Config  │ Missing  │
│(16 │ .. │   │ ...                 │ ...   │ ...    │ ...     │ ...      │
│px) │    │   │                     │       │        │         │          │
├────┴────────────────────────────────────────────────────────────────────┤
│  상태바: 준비  │  자동스크롤: ON  │  표시: 999/1234                       │
└─────────────────────────────────────────────────────────────────────────┘
```

## 레이아웃 구조

- **수직 분할 (위→아래)**:
  - 메뉴바 (고정)
  - K8s 선택 패널 (고정)
  - 필터 패널 (고정)
  - 컨트롤 패널 (고정)
  - 메인 영역 (flex: 1, 나머지 전체)
  - 상태바 (고정)

- **메인 영역 수평 분할**:
  - 인디케이터 패널 (16px 고정 너비)
  - 로그 테이블 컨테이너 (나머지 영역, overflow-y: auto)

- **전체**: `h-screen flex flex-col` (화면 전체 높이, 수직 플렉스)

---

## 패널별 상세

### 메뉴바

```html
<div class="bg-gray-200 dark:bg-gray-800 border-b px-2 py-1 flex justify-between">
```

| 요소 | 설명 |
|------|------|
| 파일 / 편집 / 보기 / 도움말 | 메뉴 항목 (현재 스텁, 미구현) |
| 테마 토글 버튼 | 🌙 다크 ↔ ☀️ 라이트 전환, localStorage 저장 |

### K8s 선택 패널

```html
<div class="bg-gray-50 dark:bg-gray-800 border-b p-2">
```

**1행: Context + Namespace**

| 요소 | ID | 타입 | 설명 |
|------|------|------|------|
| Context | `#context` | `<select>` | K8s context 드롭다운 |
| Namespace | `#namespace` | `<select>` | Namespace 드롭다운 (context 선택 후 활성) |
| 새로고침 | - | `<button>` | loadContexts() 호출 |

**2행: Pod + Container + 연결**

| 요소 | ID | 타입 | 설명 |
|------|------|------|------|
| Pod | `#pod-dropdown` | 커스텀 검색 드롭다운 | 키보드 네비게이션, 실시간 검색 필터 |
| Container | `#container` | `<select>` | 컨테이너 드롭다운 |
| 연결 | - | `<button>` | connectPod() 호출, 초록 배경 |

**Pod 검색 드롭다운 (`podSearch` 객체)**:
- 일반 `<select>` 대신 커스텀 구현
- 트리거 버튼 클릭 → 드롭다운 열림
- 타이핑 시 실시간 필터링 + 매칭 하이라이트
- 방향키(↑↓) + Enter 키보드 네비게이션
- Backspace로 검색어 삭제
- 검색어 팝업 표시 (가운데 모달)

### 필터 패널

```html
<div class="bg-gray-50 dark:bg-gray-800 border-b p-2">
```

6개의 `<fieldset>` 그룹으로 구성:

#### Presets

| 요소 | ID | 설명 |
|------|------|------|
| 프리셋 선택 | `#preset-select` | `<select>` 드롭다운 |
| 적용 | - | loadPreset() |
| 저장 | - | savePreset() — prompt()로 이름 입력 |
| 삭제 | - | deletePreset() — 빨간 텍스트 |

#### Word Filter

| 요소 | ID | 색상 | 설명 |
|------|------|------|------|
| Find 입력 | `#word-find` | 초록 (#16a34a) | 포함 키워드, `\|` 구분 |
| Find 활성화 | `#word-find-enabled` | - | 체크박스 |
| Remove 입력 | `#word-remove` | 빨강 (#dc2626) | 제외 키워드, `\|` 구분 |
| Remove 활성화 | `#word-remove-enabled` | - | 체크박스 |

#### Class Filter

| 요소 | ID | 색상 | 설명 |
|------|------|------|------|
| Show 입력 | `#class-show` | 파랑 (#2563eb) | 표시할 클래스 |
| Show 활성화 | `#class-show-enabled` | - | 체크박스 |
| Remove 입력 | `#class-remove` | 빨강 (#dc2626) | 제외할 클래스 |
| Remove 활성화 | `#class-remove-enabled` | - | 체크박스 |

#### Highlight

| 요소 | ID | 설명 |
|------|------|------|
| Keyword 입력 | `#highlight-keyword` | 주황 배경, 하이라이트 키워드 |
| 활성화 | `#highlight-enabled` | 체크박스 |
| 카운터 | `#highlight-count` | "N/M" 형식 |
| ▲ | `#highlight-prev` | 이전 하이라이트 (Shift+F3) |
| ▼ | `#highlight-next` | 다음 하이라이트 (F3) |

#### Log Filter (로그 레벨)

| 요소 | ID | 색상 |
|------|------|------|
| Verbose (TRACE) | `#level-verbose` | 회색 (#808080) |
| Debug | `#level-debug` | 파랑 (#0066cc) |
| Info | `#level-info` | 초록 (#2e8b57) |
| Warn | `#level-warn` | 주황 (#ff9a00) |
| Error | `#level-error` | 빨강 (#ff0000) |
| Fatal | `#level-fatal` | 진한빨강 (#cc0000) |

#### Font (글꼴 설정)

| 요소 | ID | 설명 |
|------|------|------|
| Size 슬라이더 | `#font-size` | 글꼴 크기 조절 |
| Goto 입력 | `#goto-line` | 라인 번호 이동 |

### 필터 입력 스타일 규칙

입력값이 있을 때(`not(:placeholder-shown)`) 색상으로 시각 피드백:
- Find: 초록색 텍스트, 굵은 글씨
- Remove: 빨간색 텍스트, 굵은 글씨
- Class Show: 파란색 텍스트, 굵은 글씨
- Highlight: 주황색 텍스트, 노란 배경

비활성화된 필터(`.filter-disabled`):
- 회색 텍스트, 일반 두께, 취소선

### 컨트롤 패널

```html
<div class="bg-white dark:bg-gray-800 border-b px-3 py-1 flex items-center gap-2">
```

| 요소 | 설명 |
|------|------|
| 로그 개수 | `#log-count` — "999줄" 형식 |
| 시작/중지 | `#start-stop-btn` — 스트리밍 제어, 상태에 따라 텍스트 변경 |
| 전체 로그 | 모든 로그 다운로드 (tail 없음) |
| 샘플 | 샘플 로그 로드 |
| 다운로드 | 필터링된 로그 텍스트 파일 저장 |
| 파일 열기 | `<input type="file">` 트리거 |
| 클리어 | 로그 전체 삭제 |

### 로그 테이블

```html
<table class="w-full text-xs font-mono">
```

**컬럼 구조:**

| 컬럼 | 너비 | 내용 | 정렬 |
|------|------|------|------|
| # | 60px | 라인 번호 | 우측 |
| ★ | 30px | 북마크 아이콘 | 중앙 |
| DateTime | 160px | 타임스탬프 | 좌측 |
| Level | 50px | 로그 레벨 (색상) | 중앙 |
| Thread | 100px | 스레드명 | 좌측 |
| Class | 150px | 클래스명 | 좌측 |
| Message | 나머지 | 로그 메시지 | 좌측 |

**행 배경색 우선순위 (높은→낮은):**

| 클래스 | 색상 (라이트) | 색상 (다크) | 조건 |
|--------|-------------|------------|------|
| `.log-row-selected` | #3B82F6 (파랑) | #1D4ED8 | 선택된 행 |
| `.log-row-highlight` | #FFFF99 (노랑) | #4a4a00 | 하이라이트 키워드 매칭 |
| `.log-row-bookmark` | #E3F2FD (연파랑) | #0d3a58 | 북마크된 행 |
| `.log-row-error` | #FFEBEE (연빨강) | #3a1515 | ERROR/FATAL 레벨 |
| `.log-row-warn` | #FFF8E1 (연노랑) | #3a3515 | WARN 레벨 |

**로그 테이블 컨테이너 특수 CSS:**

```css
.log-table-container {
    overflow-anchor: none;   /* 브라우저 자동 스크롤 조정 비활성화 */
    scroll-behavior: auto;   /* smooth 스크롤 비활성화 */
    direction: rtl;          /* 스크롤바를 왼쪽으로 이동 */
}
.log-table-container > * {
    direction: ltr;          /* 내부 콘텐츠는 정상 방향 */
}
```

### 인디케이터 패널 (미니맵)

```html
<div id="indicator" class="w-4 border-r relative flex-shrink-0">
```

- 너비: 16px, 전체 높이
- 좌측 절반: 북마크(파랑), 하이라이트(노랑) 마커
- 우측 절반: 에러(빨강), 경고(주황) 마커
- 각 마커: 절대 위치, `top: (index/total × 100)%`
- 마커 클릭 → 해당 로그로 스크롤

| 마커 | 색상 | 위치 |
|------|------|------|
| 하이라이트 | #FFEE58 (노랑) | 좌측 |
| 북마크 | #42A5F5 (파랑) | 좌측 |
| ERROR/FATAL | #FF5252 (빨강) | 우측 |
| WARN | #FFB74D (주황) | 우측 |

### 상태바

```html
<div class="bg-gray-200 dark:bg-gray-800 border-t px-3 py-0.5 flex justify-between text-xs">
```

| 요소 | 위치 | 설명 |
|------|------|------|
| 상태 메시지 | 좌측 | `#status` — "준비", "연결 중...", "스트리밍 중" 등 |
| 자동스크롤 | 중앙 | `#auto-scroll-status` — 클릭 토글 ("ON" / "OFF") |
| 표시 건수 | 우측 | `#display-count` — "표시: 필터된수/전체수" |

---

## 테마 및 색상

### Tailwind 다크모드

- `darkMode: 'class'` 전략 사용
- `<html>` 태그에 `class="dark"` 토글
- 모든 UI 요소에 `dark:` 변형 적용 필수

### 로그 레벨 색상

| 레벨 | 텍스트 색상 | CSS 클래스 |
|------|-----------|-----------|
| TRACE | #808080 (회색) | `.level-TRACE` |
| DEBUG | #0066cc (파랑) | `.level-DEBUG` |
| INFO | #2e8b57 (초록) | `.level-INFO` |
| WARN | #ff9a00 (주황) | `.level-WARN` |
| ERROR | #ff0000 (빨강) | `.level-ERROR` |
| FATAL | #cc0000 (진한빨강) | `.level-FATAL` |

### 키워드 매칭 색상

| 유형 | 클래스 | 색상 |
|------|--------|------|
| Highlight 키워드 | `.keyword-match` | #FF6600 (주황), bold |
| Word Find 매칭 | `.word-filter-match` | #16a34a (초록), bold, 연초록 배경 |

### 배경색 팔레트

| 용도 | 라이트 | 다크 |
|------|--------|------|
| 페이지 배경 | `bg-gray-100` | `bg-gray-900` |
| 패널 배경 | `bg-gray-50` / `bg-white` | `bg-gray-800` |
| 입력 배경 | `bg-white` | `bg-gray-600` / `bg-gray-700` |
| 테두리 | `border-gray-300` | `border-gray-600` / `border-gray-700` |
| 텍스트 | `text-gray-800` | `text-gray-200` |

### 외부 의존성

| 라이브러리 | 용도 | 로드 방식 |
|-----------|------|----------|
| Tailwind CSS v3 | 유틸리티 CSS | `<script src="https://cdn.tailwindcss.com">` |

빌드 타임 의존성 없음. 위 CDN만 필요.
