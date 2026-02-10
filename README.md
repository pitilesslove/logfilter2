# K8s Log Viewer

Kubernetes Pod 로그를 실시간으로 조회하고 필터링하는 웹 기반 로그 뷰어

## 기술 스택

- **Backend**: Node.js + Express
- **Frontend**: HTML/CSS/JavaScript + Tailwind CSS (CDN)
- **실시간 통신**: Server-Sent Events (SSE)
- **K8s 연동**: kubectl CLI

## 실행 방법

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

## 주요 기능

- **K8s 클러스터 연결**: Context/Namespace/Pod 선택
- **실시간 로그 스트리밍**: SSE를 통한 실시간 로그 수신
- **로그 필터링**: 레벨별, 키워드, 클래스명 필터
- **하이라이트**: 키워드 강조 및 네비게이션 (F3/Shift+F3)
- **필터 프리셋**: 자주 사용하는 필터 조합 저장
- **다크/라이트 모드**: 테마 전환 지원
- **로그 다운로드**: 필터링된 로그 파일 저장
- **파일 열기**: 로컬 로그 파일 불러오기
- **가상 스크롤**: 대용량 로그 최적화 (1000줄+)

## 프로젝트 구조

```
logfilter/
├── server/                 # Express 서버
│   ├── index.js           # 서버 진입점
│   ├── routes/
│   │   └── k8s.js         # K8s API 라우터
│   └── services/
│       ├── k8sClient.js   # kubectl 래퍼
│       ├── logParser.js   # 로그 파서
│       └── logStore.js    # 로그 저장소
├── samples/
│   ├── ui-mockup.html     # 웹 UI (메인)
│   ├── spring-boot-sample.log
│   └── ums-log-sample.log
├── package.json
└── README.md
```

## API 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/` | GET | 웹 UI 페이지 |
| `/api/k8s/contexts` | GET | Context 목록 |
| `/api/k8s/namespaces` | GET | Namespace 목록 |
| `/api/k8s/pods` | GET | Pod 목록 |
| `/api/k8s/logs` | GET | Pod 로그 조회 (tail) |
| `/api/k8s/logs/all` | GET | Pod 전체 로그 조회 |
| `/api/k8s/logs/stream` | GET (SSE) | 실시간 로그 스트리밍 |
| `/api/k8s/logs/stream/stop` | GET | 스트리밍 중지 |

## 키보드 단축키

| 단축키 | 기능 |
|--------|------|
| F3 | 다음 하이라이트로 이동 |
| Shift+F3 | 이전 하이라이트로 이동 |
| Ctrl+G | Goto 라인 입력 |
| Escape | 입력창 포커스 해제 |

## 지원 로그 형식

- Spring Boot Logback 표준 패턴
- DX 로그 패턴
- Logback 커스텀 패턴 (traceId 포함)
- 일반 로그 패턴

---

*Version 2.0 - Node.js/Express 기반 웹 애플리케이션*
