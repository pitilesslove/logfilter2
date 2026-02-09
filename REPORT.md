# K8s Log Viewer 웹 UI 개발 완료 보고서

## 📋 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | K8s Log Viewer v2.0 |
| **개발 기간** | 2024년 1월 ~ 2월 |
| **기술 스택** | Kotlin, TornadoFX, Tailwind CSS, SSE |
| **목표** | Kubernetes Pod 로그를 실시간으로 조회하고 필터링하는 웹 기반 로그 뷰어 |

---

## 🎯 주요 기능

### 1. K8s 클러스터 연결
- Context/Namespace/Pod 선택 드롭다운
- 실시간 Pod 목록 조회 (상태, Ready 상태, 재시작 횟수 표시)
- Container 선택 지원

### 2. 로그 필터링
- **레벨 필터**: TRACE, DEBUG, INFO, WARN, ERROR, FATAL 개별 토글
- **단어 필터**: Find (포함) / Remove (제외) - `|`로 복수 키워드 지원
- **클래스 필터**: Show / Remove - 특정 클래스만 표시/제외
- **하이라이트**: 키워드 노란색 강조 표시

### 3. 검색 네비게이션
- **F3**: 다음 하이라이트로 이동
- **Shift+F3**: 이전 하이라이트로 이동
- 현재 위치/전체 개수 표시 (예: "3/10")
- 자동 스크롤 및 행 선택

### 4. 필터 프리셋
- 자주 사용하는 필터 조합을 프리셋으로 저장
- localStorage에 프리셋 영구 저장
- 적용/저장/삭제 기능

### 5. 다크/라이트 모드
- Tailwind CSS `darkMode: 'class'` 기반 테마 시스템
- 시스템 테마 자동 감지
- localStorage에 설정 저장
- 원클릭 토글 버튼

### 6. 로그 다운로드
- 현재 필터링된 로그를 텍스트 파일로 다운로드
- 파일명 형식: `{pod명}-{timestamp}.log`
- 전체 로그 또는 필터링된 로그만 다운로드 가능

### 7. 가상 스크롤 (Virtual Scrolling)
- 1000줄 이상의 대용량 로그 최적화
- DOM 요소 최소화로 메모리 효율성 향상
- 부드러운 스크롤 성능

### 8. 실시간 로그 스트리밍 (SSE)
- Server-Sent Events를 통한 실시간 로그 수신
- 자동 재연결 지원
- 스트림 시작/중지 제어

---

## 🛠 기술 구현

### 백엔드 (Kotlin)
```kotlin
// WebServer.kt - 핵심 구조
class WebServer(private val port: Int = 8888) {
    private val parser = LogParser()
    private val k8sService = K8sService()

    // SSE 스트리밍
    private fun startLogStream(exchange: HttpExchange, pod: String, ...)

    // REST API 엔드포인트
    server?.createContext("/api/k8s/contexts") { ... }
    server?.createContext("/api/k8s/namespaces") { ... }
    server?.createContext("/api/k8s/pods") { ... }
    server?.createContext("/api/k8s/logs/stream") { ... }
}
```

### 프론트엔드 (Tailwind CSS)
```javascript
// Tailwind 설정
tailwind.config = {
    darkMode: 'class',
    theme: {
        extend: {
            colors: {
                'level-trace': '#808080',
                'level-debug': '#0066cc',
                'level-info': '#2e8b57',
                'level-warn': '#ff9a00',
                'level-error': '#ff0000',
                'level-fatal': '#cc0000',
            }
        }
    }
}
```

### API 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/` | GET | 웹 UI 페이지 |
| `/api/k8s/contexts` | GET | Context 목록 조회 |
| `/api/k8s/namespaces` | GET | Namespace 목록 조회 |
| `/api/k8s/pods` | GET | Pod 목록 조회 |
| `/api/k8s/logs/stream` | GET (SSE) | 로그 스트리밍 시작 |
| `/api/k8s/logs/stream/stop` | POST | 로그 스트리밍 중지 |
| `/api/logs` | GET | 현재 로그 조회 |
| `/api/load` | GET | 샘플 로그 로드 |
| `/api/clear` | POST | 로그 클리어 |

---

## 📸 스크린샷

### 1. 라이트 모드
![라이트 모드](k8s-logviewer-light-mode.png)

### 2. 다크 모드
![다크 모드](k8s-logviewer-dark-mode.png)

### 3. 하이라이트 기능
![하이라이트](k8s-logviewer-highlight.png)

### 4. 필터링 적용 (WARN/ERROR/FATAL만 표시)
![필터링](k8s-logviewer-filtered.png)

---

## 📁 파일 구조

```
logfilter/
├── build.gradle.kts              # Gradle Kotlin DSL
├── SPEC.md                       # 전체 스펙 문서
├── REPORT.md                     # 이 보고서
├── samples/
│   ├── ui-mockup.html            # 웹 UI 메인 파일 (Tailwind)
│   ├── ui-mockup-legacy.html     # 백업 (이전 CSS 버전)
│   └── spring-boot-sample.log    # 샘플 로그 파일
├── src/main/kotlin/com/logfilter/
│   ├── web/
│   │   └── WebServer.kt          # 웹 서버 + SSE
│   ├── service/
│   │   ├── K8sService.kt         # kubectl 명령 실행
│   │   └── LogParser.kt          # 로그 파싱
│   ├── model/
│   │   ├── LogEntry.kt           # 로그 데이터 클래스
│   │   └── PodInfo.kt            # Pod 정보
│   └── view/
│       └── ...                   # TornadoFX UI (데스크톱)
└── k8s-logviewer-*.png           # 스크린샷 이미지
```

---

## ⌨️ 키보드 단축키

| 단축키 | 기능 |
|--------|------|
| F3 | 다음 하이라이트로 이동 |
| Shift+F3 | 이전 하이라이트로 이동 |
| Ctrl+G | Goto 라인 입력창으로 포커스 |
| Escape | 입력창 포커스 해제 |

---

## ✅ 완료된 작업

- [x] Tailwind CSS 전환 (CDN 기반)
- [x] 다크/라이트 모드 구현
- [x] 검색 결과 네비게이션 (F3/Shift+F3)
- [x] 로그 다운로드 기능
- [x] 필터 프리셋 저장/로드/삭제
- [x] 개별 필터 활성화 체크박스
- [x] 가상 스크롤 구현
- [x] SSE 실시간 스트리밍
- [x] SPEC.md 문서 업데이트 (섹션 11 추가)
- [x] WebServer.kt 컴파일 오류 수정

---

## 🚀 실행 방법

```bash
# 웹 서버 실행
./gradlew runWeb

# 브라우저에서 접속
open http://localhost:8888

# 데스크톱 앱 실행 (TornadoFX)
./gradlew run
```

---

## 📝 향후 개선 사항

1. **인증/권한 관리**: K8s RBAC 연동
2. **멀티 Pod 지원**: 여러 Pod 로그 동시 조회
3. **로그 검색 API**: 서버 사이드 검색
4. **WebSocket 전환**: SSE → WebSocket (양방향 통신)
5. **로그 북마크 영구 저장**: 서버 사이드 저장소

---

*Generated: 2024-02-07*
*Author: Claude Code*
