/**
 * K8s API Routes
 * K8s 클러스터 정보 조회 및 로그 스트리밍 API
 */
const express = require('express');
const router = express.Router();
const k8sClient = require('../services/k8sClient');
const logParser = require('../services/logParser');
const logStore = require('../services/logStore');

// 활성 스트림 관리
const activeStreams = new Map();
let streamIdCounter = 0;

/**
 * GET /api/k8s/contexts
 * K8s context 목록 조회
 */
router.get('/contexts', (req, res) => {
    try {
        const contexts = k8sClient.getContexts();
        const current = k8sClient.getCurrentContext();
        res.json({ contexts, current });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/k8s/namespaces
 * Namespace 목록 조회
 */
router.get('/namespaces', (req, res) => {
    try {
        const { context } = req.query;
        const namespaces = k8sClient.getNamespaces(context);
        res.json({ namespaces });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/k8s/pods
 * Pod 목록 조회
 */
router.get('/pods', (req, res) => {
    try {
        const { namespace = 'default', context } = req.query;
        const pods = k8sClient.getPods(namespace, context);
        res.json({ pods });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/k8s/logs
 * Pod 로그 조회 (일회성)
 */
router.get('/logs', (req, res) => {
    try {
        const { pod, namespace = 'default', context, tail = '5000' } = req.query;

        if (!pod) {
            return res.status(400).json({ error: 'Pod name required' });
        }

        const lines = k8sClient.getLogs(pod, namespace, context, parseInt(tail));
        logParser.reset();
        const entries = logParser.parseLines(lines);

        // 로그 저장소에 저장 (refreshLogs에서 /api/logs로 가져감)
        logStore.setLogs(entries);

        res.json({
            status: 'ok',
            pod,
            count: entries.length,
            logs: entries
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/k8s/logs/all
 * Pod 전체 로그 다운로드 (tail 없이 모든 로그)
 */
router.get('/logs/all', (req, res) => {
    try {
        const { pod, namespace = 'default', context } = req.query;

        if (!pod) {
            return res.status(400).json({ error: 'Pod name required' });
        }

        console.log(`[Download] 전체 로그 다운로드 시작: pod=${pod}, namespace=${namespace}`);
        const startTime = Date.now();

        const lines = k8sClient.getAllLogs(pod, namespace, context);
        logParser.reset();
        const entries = logParser.parseLines(lines);

        const elapsed = Date.now() - startTime;
        console.log(`[Download] 완료: ${entries.length}줄, ${elapsed}ms`);

        // 로그 저장소에 저장
        logStore.setLogs(entries);

        res.json({
            status: 'ok',
            pod,
            count: entries.length,
            elapsed,
            logs: entries
        });
    } catch (error) {
        console.error('[Download] 에러:', error.message);
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/k8s/logs/stream
 * Pod 로그 스트리밍 (SSE)
 */
router.get('/logs/stream', (req, res) => {
    const { pod, namespace = 'default', context, tail = '5000' } = req.query;

    if (!pod) {
        return res.status(400).json({ error: 'Pod name required' });
    }

    const streamId = ++streamIdCounter;
    console.log(`[Stream ${streamId}] 시작: pod=${pod}, namespace=${namespace}`);

    // SSE 헤더 설정
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.flushHeaders();

    // kubectl 프로세스 시작
    const process = k8sClient.streamLogs(pod, namespace, context, parseInt(tail));
    logParser.reset();

    activeStreams.set(streamId, { process, res });

    // 초기화 이벤트 전송
    res.write(`event: init\ndata: ${JSON.stringify({ streamId, pod })}\n\n`);

    let lineNumber = 0;

    // stdout 처리
    process.stdout.on('data', (data) => {
        const lines = data.toString().split('\n');
        for (const line of lines) {
            if (!line.trim()) continue;

            lineNumber++;
            const entry = logParser.parse(line, lineNumber);
            if (entry) {
                res.write(`event: log\ndata: ${JSON.stringify(entry)}\n\n`);
            }
        }
    });

    // stderr 처리
    process.stderr.on('data', (data) => {
        console.error(`[Stream ${streamId}] stderr: ${data}`);
        res.write(`event: error\ndata: ${JSON.stringify({ error: data.toString() })}\n\n`);
    });

    // 프로세스 종료 처리
    process.on('close', (code) => {
        console.log(`[Stream ${streamId}] 종료: code=${code}, lines=${lineNumber}`);
        res.write(`event: end\ndata: ${JSON.stringify({ streamId, lines: lineNumber })}\n\n`);
        res.end();
        activeStreams.delete(streamId);
    });

    // 클라이언트 연결 종료 처리
    req.on('close', () => {
        console.log(`[Stream ${streamId}] 클라이언트 연결 종료`);
        process.kill();
        activeStreams.delete(streamId);
    });
});

/**
 * GET /api/k8s/logs/stream/stop
 * 스트림 중지
 */
router.get('/logs/stream/stop', (req, res) => {
    const { streamId } = req.query;

    if (streamId) {
        const id = parseInt(streamId);
        const stream = activeStreams.get(id);
        if (stream) {
            stream.process.kill();
            activeStreams.delete(id);
            console.log(`[Stream ${id}] 수동 중지`);
        }
        res.json({ status: 'ok', streamId: id });
    } else {
        // 모든 스트림 중지
        for (const [id, stream] of activeStreams) {
            stream.process.kill();
        }
        activeStreams.clear();
        console.log('모든 스트림 중지');
        res.json({ status: 'ok', message: 'All streams stopped' });
    }
});

module.exports = router;
