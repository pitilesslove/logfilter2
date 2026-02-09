# K8s Spring Boot Log Viewer 스펙 문서

## 1. 개요

### 1.1 프로젝트 목적
기존 안드로이드 로그 필터링 도구(LogFilter)를 쿠버네티스 환경의 스프링 부트 파드 로그 뷰어로 전환한다.

### 1.2 기술 스택
- **언어**: Kotlin 1.9+
- **UI 프레임워크**: TornadoFX (JavaFX 기반 Kotlin 프레임워크)
- **빌드 시스템**: Gradle Kotlin DSL
- **비동기 처리**: Kotlin Coroutines + Flow
- **JDK**: 17+

### 1.3 주요 변경 사항
- Java Swing → Kotlin + TornadoFX
- 안드로이드 adb logcat → kubectl logs 명령어 기반 로그 수집
- 안드로이드 로그 형식 → 스프링 부트 Logback 로그 형식 파싱
- 디바이스 선택 → Context/Namespace/Pod 선택

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

### 2.2 메뉴바

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 파일  편집  보기  도움말                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

| 메뉴 | 항목 | 단축키 | 기능 |
|------|------|--------|------|
| 파일 | 열기... | Ctrl+O | 로그 파일 열기 |
| 파일 | 종료 | Alt+F4 | 애플리케이션 종료 |
| 편집 | 로그 클리어 | Ctrl+L | 로그 전체 삭제 |
| 편집 | 필터 적용 | Ctrl+F | 필터 수동 적용 |
| 보기 | 자동 스크롤 | - | 자동 스크롤 ON/OFF 토글 |
| 보기 | 맨 아래로 이동 | End | 자동 스크롤 활성화 & 맨 아래로 |
| 보기 | 글꼴 크기 + | Ctrl++ | 글꼴 크기 증가 |
| 보기 | 글꼴 크기 - | Ctrl+- | 글꼴 크기 감소 |
| 도움말 | 단축키... | - | 단축키 목록 표시 |
| 도움말 | 정보... | - | 버전 정보 표시 |

### 2.3 K8s 선택 패널 (K8sSelectView)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Context: [─────────────▼]  Namespace: [─────────────▼]  [새로고침]          │
│                                                                              │
│ Pod:     [─────────────────────────────────────────────▼]   [연결]          │
│ Container: [─────────▼] (멀티 컨테이너 시에만 표시)                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

| 컴포넌트 | 타입 | 설명 |
|----------|------|------|
| Context | ComboBox | K8s context 목록 (kubectl config get-contexts) |
| Namespace | ComboBox | 네임스페이스 목록 |
| Pod | ComboBox | 파드 목록 (타이핑으로 필터링 가능) |
| Container | ComboBox | 컨테이너 목록 (멀티 컨테이너 파드일 때만 표시) |
| 새로고침 | Button | 목록 새로고침 |
| 연결 | Button | 선택한 파드에 연결 및 로그 스트리밍 시작 |

### 2.4 필터 패널 (FilterView)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ [☑] 필터 │ Find:   [________________] │ Class:  [____________] │           │
│          │ Remove: [________________] │ 제외:   [____________] │           │
│          └────────────────────────────┴────────────────────────┘           │
│                                                                              │
│ │ Highlight: [____________________] │                           [적용]      │
├─────────────────────────────────────────────────────────────────────────────┤
│ Log Level: [☑]TRACE [☑]DEBUG [☑]INFO [☑]WARN [☑]ERROR [☑]FATAL            │
└─────────────────────────────────────────────────────────────────────────────┘
```

| 컴포넌트 | 타입 | 설명 |
|----------|------|------|
| 필터 | Checkbox | 필터 ON/OFF 토글 (체크 해제 시 모든 로그 표시) |
| Find | TextField | 검색어 필터 (`\|` 로 구분하여 OR 조건) |
| Remove | TextField | 제외어 필터 (`\|` 로 구분하여 OR 조건) |
| Class | TextField | 표시할 클래스 필터 |
| 제외 | TextField | 제외할 클래스 필터 |
| Highlight | TextField | 하이라이트 키워드 (`\|` 로 구분) |
| Log Level | Checkbox[] | 각 로그 레벨별 표시 ON/OFF |
| 적용 | Button | 필터 수동 적용 |

### 2.5 컨트롤 패널 (ControlView)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 로그: [          ]줄   [▶ 시작] [⏸ 일시정지] [⏹ 중지] [🗑 클리어]            │
└─────────────────────────────────────────────────────────────────────────────┘
```

| 컴포넌트 | 타입 | 설명 |
|----------|------|------|
| 로그 개수 | Label | 현재 로그 줄 수 표시 |
| 시작 | Button | 로그 스트리밍 시작 |
| 일시정지 | Button | 스트리밍 일시정지 |
| 중지 | Button | 스트리밍 중지 |
| 클리어 | Button | 로그 전체 삭제 |

### 2.6 인디케이터 패널 (IndicatorPanel)

```
┌────────┐
│[☑]★ [☑]E│  ← 북마크/에러 표시 토글
├────────┤
│████████│  ← 하이라이트 (노란색)
│        │
│   ████ │  ← 에러 (빨간색)
│        │
│████    │  ← 북마크 (파란색)
│   ████ │  ← 경고 (주황색)
│        │
│        │
│████████│  ← 하이라이트
│        │
└────────┘
```

| 요소 | 색상 | 설명 |
|------|------|------|
| 배경 | #1E1E1E (어두운 회색) | 다크 테마 배경 |
| 하이라이트 | #FFEE58 (노란색) | 하이라이트된 로그 위치 |
| 북마크 | #42A5F5 (파란색) | 북마크된 로그 위치 |
| 에러 | #FF5252 (빨간색) | ERROR/FATAL 로그 위치 |
| 경고 | #FFB74D (주황색) | WARN 로그 위치 |

**인터랙션:**
- 클릭: 해당 위치로 즉시 이동
- 드래그: 빠른 스크롤 이동

### 2.7 로그 테이블 (LogTableView)

```
┌────┬──────┬─────────────────────┬───────┬─────────────────┬──────────────┬────────────────────────────────┐
│ ★  │ Line │ DateTime            │ Level │ Thread          │ Class        │ Message                        │
├────┼──────┼─────────────────────┼───────┼─────────────────┼──────────────┼────────────────────────────────┤
│    │   1  │ 2024-01-15 10:30:45 │ INFO  │ main            │ Application  │ Starting application...        │
│    │   2  │ 2024-01-15 10:30:46 │ DEBUG │ nio-8080-exec-1 │ MyController │ Request received               │
│ ★  │   3  │ 2024-01-15 10:30:47 │ WARN  │ nio-8080-exec-2 │ MyService    │ Slow response: [keyword] match │
│    │   4  │ 2024-01-15 10:30:48 │ ERROR │ nio-8080-exec-1 │ MyController │ Exception occurred             │
└────┴──────┴─────────────────────┴───────┴─────────────────┴──────────────┴────────────────────────────────┘
```

| 컬럼 | 너비 | 색상 | 설명 |
|------|------|------|------|
| ★ (북마크) | 30px | 금색 (#FFD700) | 북마크 표시 |
| Line | 60px | 기본 | 라인 번호 |
| DateTime | 180px | 기본 | 타임스탬프 |
| Level | 60px | 레벨별 색상 | 로그 레벨 |
| Thread | 150px | 보라색 (#6A5ACD) | 스레드명 |
| Class | 200px | 녹색 (#2E8B57) | 클래스명 |
| Message | 가변 | 기본 (매칭 키워드는 주황색) | 로그 메시지 |

**로그 레벨 색상:**
| 레벨 | 색상 | 배경색 (해당 행) |
|------|------|-----------------|
| TRACE | #808080 (회색) | - |
| DEBUG | #0000AA (파란색) | - |
| INFO | #00AA00 (녹색) | - |
| WARN | #FF9A00 (주황색) | #FFF8E1 (연한 노란색) |
| ERROR | #FF0000 (빨간색) | #FFEBEE (연한 빨간색) |
| FATAL | #FF0000 (빨간색) | #FFEBEE (연한 빨간색) |

**특수 행 배경색:**
| 상태 | 배경색 |
|------|--------|
| 하이라이트 | #FFFF99 (노란색) |
| 북마크 | #E3F2FD (연한 파란색) |

### 2.8 상태바

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 준비됨                                        자동스크롤: ON   표시: 1234줄  │
└─────────────────────────────────────────────────────────────────────────────┘
```

| 요소 | 위치 | 설명 |
|------|------|------|
| 상태 메시지 | 왼쪽 | 현재 작업 상태 표시 |
| 자동스크롤 상태 | 오른쪽 | ON/OFF 표시 |
| 표시 줄 수 | 오른쪽 | 현재 필터링된 로그 줄 수 |

---

## 3. 기능 요구사항

### 3.1 로그 수집

#### 3.1.1 수집 방식
- `kubectl logs -f <pod-name> -n <namespace>` 명령어를 통한 실시간 스트리밍
- Kotlin Coroutines Flow를 통한 비동기 스트리밍 처리

#### 3.1.2 kubectl 설정
- 기본 kubeconfig 경로: `~/.kube/config`
- 여러 context 중 선택 가능
- kubectl 바이너리 경로 자동 감지 (PATH에서 검색)

### 3.2 자동 스크롤링

#### 3.2.1 동작 방식
- 새 로그 추가 시 자동으로 맨 아래로 스크롤
- 자동 스크롤 활성화 시에만 동작

#### 3.2.2 자동 스크롤 비활성화 조건
- 로그 테이블 클릭 시
- 로그 테이블 스크롤 시 (마우스 휠)
- 키보드로 행 선택 시

#### 3.2.3 자동 스크롤 재활성화 조건
- End 키 누를 때
- 테이블에서 포커스가 벗어날 때 (다른 윈도우 클릭 등)
- 메뉴에서 "자동 스크롤" 체크 시

### 3.3 Pod ComboBox 타이핑 필터

#### 3.3.1 동작 방식
- Pod ComboBox에서 키보드로 영문 입력 시
- 입력된 키워드로 시작하는 Pod로 포커스 이동
- 대소문자 구분 없음

### 3.4 필터링 기능

#### 3.4.1 필터 ON/OFF
- 체크박스로 필터 전체 활성화/비활성화
- 비활성화 시 모든 로그 표시

#### 3.4.2 Word Filter (Find)
- 특정 단어가 포함된 로그만 표시
- `|` 구분자로 OR 조건 지원
- 메시지 및 클래스명 대상으로 검색
- 대소문자 구분 없음

#### 3.4.3 Word Filter (Remove)
- 특정 단어가 포함된 로그 제외
- `|` 구분자로 OR 조건 지원

#### 3.4.4 Class Filter (Show)
- 특정 클래스명이 포함된 로그만 표시
- `|` 구분자로 OR 조건 지원

#### 3.4.5 Class Filter (Remove)
- 특정 클래스명이 포함된 로그 제외
- `|` 구분자로 OR 조건 지원

#### 3.4.6 Log Level Filter
- 각 로그 레벨별 체크박스로 표시/숨김 제어
- TRACE, DEBUG, INFO, WARN, ERROR, FATAL

#### 3.4.7 실시간 필터 적용
- TextField 값 변경 시 즉시 필터 적용
- 성능을 위한 디바운싱 고려

### 3.5 키워드 하이라이트

#### 3.5.1 Word Filter 매칭 하이라이트
- Find 필터에 매칭되는 키워드를 Message 컬럼에서 색상 강조
- 색상: 주황색 (#FF6600), 굵은 글꼴
- 행 배경이 아닌 텍스트 자체를 컬러링

#### 3.5.2 Highlight 필터
- Highlight TextField에 입력된 키워드가 포함된 행 강조
- 행 배경색: 노란색 (#FFFF99)
- IndicatorPanel에 노란색으로 위치 표시

### 3.6 북마크 기능

#### 3.6.1 북마크 토글
- 더블클릭으로 하이라이트 토글
- Ctrl+B로 북마크 토글
- 북마크된 행은 ★ 표시 및 배경색 변경

#### 3.6.2 북마크 네비게이션
- F2: 이전 하이라이트로 이동
- F3: 다음 하이라이트로 이동
- Ctrl+F2: 이전 북마크로 이동
- Ctrl+F3: 다음 북마크로 이동

### 3.7 선택 및 복사/저장

#### 3.7.1 다중 선택
- Shift+클릭: 범위 선택
- Ctrl+클릭: 개별 추가 선택

#### 3.7.2 복사
- Ctrl+C: 선택된 로그를 클립보드에 복사
- 원본 로그 형식으로 포맷팅

#### 3.7.3 파일 저장
- 컨텍스트 메뉴 > "선택 항목 저장..."
- .log 또는 .txt 형식으로 저장

### 3.8 글꼴 크기 조정

#### 3.8.1 조정 범위
- 최소: 8px
- 최대: 24px
- 기본: 12px

#### 3.8.2 조정 방법
- Ctrl++: 글꼴 크기 증가 (+1px)
- Ctrl+-: 글꼴 크기 감소 (-1px)
- 메뉴 > 보기에서도 접근 가능

### 3.9 스택트레이스 처리

#### 3.9.1 감지
- `at ` 또는 `Caused by:` 로 시작하는 라인 감지
- 이전 ERROR/FATAL 로그에 연결

#### 3.9.2 표시
- 컨텍스트 메뉴 > "스택트레이스 보기"
- 별도 다이얼로그에 모노스페이스 폰트로 표시

---

## 4. 단축키 목록

### 4.1 로그 탐색
| 단축키 | 기능 |
|--------|------|
| 더블클릭 | 하이라이트 토글 |
| F2 | 이전 하이라이트로 이동 |
| F3 | 다음 하이라이트로 이동 |
| Ctrl+B | 북마크 토글 |
| End | 자동 스크롤 활성화 & 맨 아래로 |

### 4.2 파일
| 단축키 | 기능 |
|--------|------|
| Ctrl+O | 파일 열기 |
| Ctrl+L | 로그 클리어 |

### 4.3 보기
| 단축키 | 기능 |
|--------|------|
| Ctrl++ | 글꼴 크기 증가 |
| Ctrl+- | 글꼴 크기 감소 |

### 4.4 편집
| 단축키 | 기능 |
|--------|------|
| Ctrl+C | 선택 항목 복사 |

---

## 5. 로그 파싱

### 5.1 지원 로그 형식

#### 5.1.1 Spring Boot Logback 표준 패턴
```
2024-01-15 12:34:56.789  INFO [main] c.e.demo.MyClass - This is a log message
```

#### 5.1.2 파싱 정규표현식
```kotlin
val LOGBACK_PATTERN = Regex(
    """^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\[([^\]]+)]\s+(\S+)\s+-\s+(.*)$"""
)
```

#### 5.1.3 파싱 필드
| 필드 | 설명 | 예시 |
|------|------|------|
| dateTime | 날짜 시간 | 2024-01-15 12:34:56.789 |
| level | 로그 레벨 | INFO, DEBUG, WARN, ERROR |
| thread | 스레드명 | main, http-nio-8080-exec-1 |
| className | 클래스명 | c.e.demo.MyClass |
| message | 로그 메시지 | This is a log message |

---

## 6. 데이터 모델

### 6.1 LogEntry (data class)
```kotlin
data class LogEntry(
    val lineNumber: Int,
    val dateTime: String = "",
    val level: LogLevel = LogLevel.INFO,
    val thread: String = "",
    val className: String = "",
    val message: String = "",
    val stackTrace: String? = null,
    val isBookmarked: Boolean = false,
    val isHighlighted: Boolean = false
)
```

### 6.2 LogLevel (enum)
```kotlin
enum class LogLevel(val displayName: String, val color: String) {
    TRACE("TRACE", "#808080"),
    DEBUG("DEBUG", "#0000AA"),
    INFO("INFO", "#00AA00"),
    WARN("WARN", "#FF9A00"),
    ERROR("ERROR", "#FF0000"),
    FATAL("FATAL", "#FF0000"),
    UNKNOWN("", "#000000")
}
```

### 6.3 K8sConfig (data class)
```kotlin
data class K8sConfig(
    var currentContext: String = "",
    var currentNamespace: String = "default",
    var selectedPod: String = "",
    var selectedContainer: String = "",
    val contexts: ObservableList<String>,
    val namespaces: ObservableList<String>,
    val pods: ObservableList<PodInfo>,
    val containers: ObservableList<String>
)
```

### 6.4 PodInfo (data class)
```kotlin
data class PodInfo(
    val name: String,
    val status: String,
    val ready: String,
    val restarts: Int = 0,
    val containers: List<String> = emptyList()
)
```

---

## 7. 프로젝트 구조

```
logfilter/
├── build.gradle.kts              # Gradle Kotlin DSL
├── settings.gradle.kts
├── gradle.properties
├── SPEC.md                       # 이 문서
├── samples/
│   └── spring-boot-sample.log    # 테스트용 샘플 로그
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/logfilter/
│   │   │       ├── K8sLogViewerApp.kt      # TornadoFX App
│   │   │       ├── view/
│   │   │       │   ├── MainView.kt         # 메인 화면
│   │   │       │   ├── K8sSelectView.kt    # K8s 선택 패널
│   │   │       │   ├── FilterView.kt       # 필터 패널
│   │   │       │   ├── ControlView.kt      # 컨트롤 패널
│   │   │       │   ├── LogTableView.kt     # 로그 테이블
│   │   │       │   ├── IndicatorPanel.kt   # 인디케이터 패널
│   │   │       │   └── Styles.kt           # CSS 스타일
│   │   │       ├── model/
│   │   │       │   ├── LogEntry.kt         # 로그 데이터 클래스
│   │   │       │   ├── LogLevel.kt         # 로그 레벨 enum
│   │   │       │   ├── K8sConfig.kt        # K8s 설정
│   │   │       │   └── PodInfo.kt          # Pod 정보
│   │   │       ├── service/
│   │   │       │   ├── K8sService.kt       # kubectl 실행 (코루틴)
│   │   │       │   └── LogParser.kt        # 로그 파서
│   │   │       ├── controller/
│   │   │       │   └── MainController.kt   # 비즈니스 로직
│   │   │       └── util/
│   │   │           └── Settings.kt         # 설정 저장/로드
│   │   └── resources/
│   └── test/
│       ├── kotlin/
│       │   └── com/logfilter/
│       │       ├── LogParserTest.kt
│       │       └── K8sServiceTest.kt
│       └── resources/
│           └── sample-logs/
```

---

## 8. 설정 파일

### 8.1 저장 항목
```properties
# 필터 설정
WORD_FIND=
WORD_REMOVE=
CLZ_SHOW=
CLZ_REMOVE=
HIGHLIGHT=

# K8s 설정
LAST_CONTEXT=docker-desktop
LAST_NAMESPACE=default
LAST_POD=

# 창 크기
INI_WIDTH=1280
INI_HEIGHT=720
```

### 8.2 저장 위치
- 사용자 홈 디렉토리의 .logfilter 폴더

---

## 9. 컨텍스트 메뉴

### 9.1 로그 테이블 컨텍스트 메뉴
| 항목 | 단축키 | 기능 |
|------|--------|------|
| 하이라이트 토글 | 더블클릭 | 하이라이트 ON/OFF |
| 북마크 토글 | Ctrl+B | 북마크 ON/OFF |
| 다음 하이라이트 | F3 | 다음 하이라이트로 이동 |
| 이전 하이라이트 | F2 | 이전 하이라이트로 이동 |
| 스택트레이스 보기 | - | 스택트레이스 다이얼로그 |
| 선택 항목 복사 | Ctrl+C | 클립보드에 복사 |
| 선택 항목 저장... | - | 파일로 저장 |
| 자동 스크롤 | - | 자동 스크롤 토글 |

---

## 10. 명령어 참조

### 10.1 kubectl 명령어
```bash
# Context 목록
kubectl config get-contexts -o name

# 현재 Context
kubectl config current-context

# Namespace 목록
kubectl get namespaces -o name --context <context>

# Pod 목록
kubectl get pods -n <namespace> --context <context>

# Container 목록
kubectl get pod <pod> -n <namespace> --context <context> -o jsonpath='{.spec.containers[*].name}'

# 로그 스트리밍
kubectl logs -f <pod> -n <namespace> --context <context>
kubectl logs -f <pod> -n <namespace> --context <context> -c <container>
```

---

## 11. 웹 UI (Web Interface)

### 11.1 개요

JavaFX 데스크톱 UI 외에 웹 브라우저 기반 UI를 제공합니다.

- **서버**: Kotlin HTTP Server (com.sun.net.httpserver)
- **UI**: 순수 HTML/CSS/JavaScript (프레임워크 없음)
- **실시간 통신**: Server-Sent Events (SSE)
- **파일 위치**: `samples/ui-mockup.html`

### 11.2 실행 방법

```bash
# 웹 서버 실행
./gradlew runWeb

# 브라우저에서 접속
open http://localhost:8080
```

### 11.3 주요 기능

#### 11.3.1 실시간 로그 스트리밍 (SSE)
- Server-Sent Events를 통한 실시간 로그 수신
- 자동 재연결 지원
- 스트림 시작/중지 제어

#### 11.3.2 가상 스크롤 (Virtual Scrolling)
- 1000줄 이상의 대용량 로그 최적화
- DOM 요소 최소화로 성능 향상
- 설정값:
  - `rowHeight`: 28px
  - `bufferSize`: 20 (위/아래 버퍼)
  - `threshold`: 1000줄 (활성화 임계값)

#### 11.3.3 다크/라이트 모드
- CSS 변수 기반 테마 시스템
- `localStorage`에 설정 저장
- 시스템 테마 자동 감지

#### 11.3.4 검색 결과 네비게이션
- 하이라이트된 로그 간 이동
- 단축키:
  - `F3`: 다음 하이라이트
  - `Shift+F3`: 이전 하이라이트
- 현재 위치/전체 개수 표시 (예: "3/10")

#### 11.3.5 로그 다운로드
- 현재 필터링된 로그를 텍스트 파일로 다운로드
- 파일명 형식: `{pod명}-{timestamp}.log`

#### 11.3.6 필터 프리셋
- 자주 사용하는 필터 조합을 프리셋으로 저장
- `localStorage`에 프리셋 저장
- 프리셋 저장/적용/삭제 기능

### 11.4 API 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/` | GET | 웹 UI 페이지 |
| `/api/k8s/contexts` | GET | Context 목록 조회 |
| `/api/k8s/namespaces` | GET | Namespace 목록 조회 |
| `/api/k8s/pods` | GET | Pod 목록 조회 |
| `/api/k8s/logs/stream` | GET (SSE) | 로그 스트리밍 시작 |
| `/api/k8s/logs/stream/stop` | POST | 로그 스트리밍 중지 |
| `/api/logs` | GET | 현재 로그 조회 |
| `/api/clear` | POST | 로그 클리어 |

### 11.5 SSE 이벤트 형식

```javascript
// 이벤트 타입: "log"
{
  "lineNumber": 1,
  "dateTime": "2024-01-15 10:30:45.123",
  "level": "INFO",
  "thread": "main",
  "class": "c.e.d.MyService",
  "message": "Application started"
}

// 이벤트 타입: "connected"
{ "message": "Connected to log stream" }

// 이벤트 타입: "error"
{ "error": "Connection failed" }
```

### 11.6 키보드 단축키 (웹 UI)

| 단축키 | 기능 |
|--------|------|
| F3 | 다음 하이라이트로 이동 |
| Shift+F3 | 이전 하이라이트로 이동 |
| Ctrl+G | Goto 라인 입력창으로 포커스 |
| Escape | 입력창 포커스 해제 |

### 11.7 CSS 테마 변수

```css
:root {
  --bg-primary: #f5f5f5;
  --bg-secondary: #fafafa;
  --bg-panel: #f0f0f0;
  --bg-table: #fff;
  --text-primary: #333;
  --text-secondary: #666;
  --text-muted: #999;
  --border-color: #ddd;
  /* 로그 레벨 색상 */
  --level-trace: #808080;
  --level-debug: #0066cc;
  --level-info: #2e8b57;
  --level-warn: #ff9a00;
  --level-error: #ff0000;
  --level-fatal: #cc0000;
}

[data-theme="dark"] {
  --bg-primary: #1e1e1e;
  --bg-secondary: #252526;
  --bg-panel: #333333;
  --bg-table: #2d2d2d;
  --text-primary: #e0e0e0;
  --text-secondary: #b0b0b0;
  --text-muted: #808080;
  --border-color: #444;
}
```

---

## 12. 버전 정보

- **현재 버전**: 2.0 (K8s Log Viewer - Kotlin + TornadoFX + Web UI)
- **이전 버전**: 1.8 (LogFilter - Java Swing, Android)
- **애플리케이션 이름**: K8s Log Viewer
