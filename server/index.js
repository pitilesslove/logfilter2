/**
 * K8s Log Viewer - Express Server
 *
 * 기능:
 * - 정적 파일 서빙 (UI)
 * - K8s API 프록시
 * - SSE 실시간 로그 스트리밍
 */
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const k8sRoutes = require('./routes/k8s');
const logParser = require('./services/logParser');
const logStore = require('./services/logStore');

const app = express();
const PORT = process.env.PORT || 8888;

// 미들웨어
app.use(cors());
app.use(express.json());

// 요청 로깅
app.use((req, res, next) => {
    if (!req.path.includes('/logs/stream')) {
        console.log(`${new Date().toISOString()} ${req.method} ${req.path}`);
    }
    next();
});

// K8s API 라우트
app.use('/api/k8s', k8sRoutes);

/**
 * GET /api/logs
 * 로드된 로그 조회
 */
app.get('/api/logs', (req, res) => {
    res.json(logStore.getLogs());
});

/**
 * GET /api/load
 * 샘플 로그 파일 로드
 */
app.get('/api/load', (req, res) => {
    try {
        const sampleFile = path.join(__dirname, '../samples/spring-boot-sample.log');
        if (fs.existsSync(sampleFile)) {
            const content = fs.readFileSync(sampleFile, 'utf-8');
            const lines = content.split('\n');
            logParser.reset();
            logStore.setLogs(logParser.parseLines(lines));
        } else {
            // 샘플 데이터
            logStore.setLogs([
                { line: 1, dateTime: '2024-01-15 10:30:45.123', level: 'INFO', thread: 'main', class: 'o.s.b.SpringApplication', message: 'Starting application', hasStackTrace: false },
                { line: 2, dateTime: '2024-01-15 10:30:46.456', level: 'DEBUG', thread: 'main', class: 'o.s.c.a.Context', message: 'Refreshing context', hasStackTrace: false },
                { line: 3, dateTime: '2024-01-15 10:30:47.789', level: 'WARN', thread: 'http-nio-8080-exec-1', class: 'c.e.d.UserController', message: 'User not found', hasStackTrace: false },
                { line: 4, dateTime: '2024-01-15 10:30:48.012', level: 'ERROR', thread: 'http-nio-8080-exec-2', class: 'c.e.d.OrderService', message: 'Order failed', hasStackTrace: true },
                { line: 5, dateTime: '2024-01-15 10:30:49.345', level: 'INFO', thread: 'main', class: 'o.s.b.SpringApplication', message: 'Application started', hasStackTrace: false }
            ]);
        }
        res.json({ status: 'ok', count: logStore.count() });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/clear
 * 로그 클리어
 */
app.get('/api/clear', (req, res) => {
    logStore.clear();
    res.json({ status: 'ok' });
});

// 정적 파일 서빙 (samples 디렉토리)
app.use('/samples', express.static(path.join(__dirname, '../samples')));

/**
 * GET /
 * 메인 UI 페이지
 */
app.get('/', (req, res) => {
    const htmlFile = path.join(__dirname, '../samples/ui-mockup.html');
    if (fs.existsSync(htmlFile)) {
        res.sendFile(htmlFile);
    } else {
        res.status(404).send('UI file not found');
    }
});

// 404 핸들러
app.use((req, res) => {
    res.status(404).json({ error: 'Not found' });
});

// 에러 핸들러
app.use((err, req, res, next) => {
    console.error('Server error:', err);
    res.status(500).json({ error: err.message });
});

// 서버 시작
app.listen(PORT, () => {
    console.log('');
    console.log('╔═══════════════════════════════════════════════════════════╗');
    console.log('║         K8s Log Viewer - Express Server                   ║');
    console.log('╠═══════════════════════════════════════════════════════════╣');
    console.log(`║  Server running at: http://localhost:${PORT}                 ║`);
    console.log('╠═══════════════════════════════════════════════════════════╣');
    console.log('║  Endpoints:                                               ║');
    console.log('║    GET  /                     - Web UI                    ║');
    console.log('║    GET  /api/k8s/contexts     - K8s contexts              ║');
    console.log('║    GET  /api/k8s/namespaces   - Namespaces                ║');
    console.log('║    GET  /api/k8s/pods         - Pods                      ║');
    console.log('║    GET  /api/k8s/logs         - Pod logs                  ║');
    console.log('║    GET  /api/k8s/logs/stream  - SSE log streaming         ║');
    console.log('╚═══════════════════════════════════════════════════════════╝');
    console.log('');
});

// 종료 시 정리
process.on('SIGINT', () => {
    console.log('\n서버 종료 중...');
    process.exit(0);
});

process.on('SIGTERM', () => {
    console.log('\n서버 종료 중...');
    process.exit(0);
});
