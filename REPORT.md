# K8s Log Viewer 웹 UI 개발 완료 보고서

## 📋 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | K8s Log Viewer v2.0 |
| **개발 기간** | 2024년 1월 ~ 2월 |
| **기술 스택** | Node.js, Express, Tailwind CSS, SSE |
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
- **개별 필터 ON/OFF**: 각 필터별 활성화 체크박스

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

### 6. 로그 다운로드 & 파일 열기
- 현재 필터링된 로그를 텍스트 파일로 다운로드
- 파일명 형식: `{pod명}-{timestamp}.log`
- 전체 로그 다운로드 기능
- 로컬 로그 파일 불러오기 지원

### 7. 가상 스크롤 (Virtual Scrolling)
- 1000줄 이상의 대용량 로그 최적화
- DOM 풀 재사용 패턴으로 메모리 효율성 향상
- 부드러운 스크롤 성능

### 8. 실시간 로그 스트리밍 (SSE)
- Server-Sent Events를 통한 실시간 로그 수신
- 자동 재연결 지원
- 스트림 시작/중지 제어

### 9. 스크롤 위치 보존
- 필터 변경 시 현재 화면 중심 라인 유지
- 자동스크롤 OFF 상태에서만 동작
- 바이너리 서치로 최적의 위치 탐색

---

## 🛠 기술 구현

### 백엔드 (Node.js + Express)
```javascript
// server/index.js - 핵심 구조
const express = require('express');
const app = express();

// 라우터
app.use('/api/k8s', require('./routes/k8s'));

// 정적 파일 서빙
app.get('/', (req, res) => {
    res.sendFile('samples/ui-mockup.html');
});
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
| `/api/k8s/logs` | GET | Pod 로그 조회 (tail) |
| `/api/k8s/logs/all` | GET | Pod 전체 로그 조회 |
| `/api/k8s/logs/stream` | GET (SSE) | 로그 스트리밍 시작 |
| `/api/k8s/logs/stream/stop` | GET | 로그 스트리밍 중지 |

---

## 📁 파일 구조

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
├── SPEC.md                   # 상세 스펙 문서
└── REPORT.md                 # 이 보고서
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

- [x] Node.js/Express 서버로 전환 (Kotlin → JavaScript)
- [x] Tailwind CSS 전환 (CDN 기반)
- [x] 다크/라이트 모드 구현
- [x] 검색 결과 네비게이션 (F3/Shift+F3)
- [x] 로그 다운로드 기능
- [x] 전체 로그 다운로드 기능
- [x] 로컬 파일 열기 기능
- [x] 필터 프리셋 저장/로드/삭제
- [x] 개별 필터 활성화 체크박스
- [x] 가상 스크롤 구현 (DOM 풀 재사용)
- [x] SSE 실시간 스트리밍
- [x] 필터 변경 시 스크롤 위치 보존
- [x] 자동스크롤 상태 관리 개선

---

## 🚀 실행 방법

```bash
# 의존성 설치
npm install

# 서버 실행
npm start

# 개발 모드 (파일 변경 시 자동 재시작)
npm run dev

# 브라우저에서 접속
open http://localhost:8888
```

---

## 📝 향후 개선 사항

1. **인증/권한 관리**: K8s RBAC 연동
2. **멀티 Pod 지원**: 여러 Pod 로그 동시 조회
3. **로그 검색 API**: 서버 사이드 검색
4. **WebSocket 전환**: SSE → WebSocket (양방향 통신)
5. **로그 북마크 영구 저장**: 서버 사이드 저장소

---

*Generated: 2024-02-10*
*Author: Claude Code*
