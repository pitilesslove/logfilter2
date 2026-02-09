package com.logfilter.web

import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel
import com.logfilter.model.PodInfo
import com.logfilter.service.K8sService
import com.logfilter.service.LogParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 웹 서버 - SSE 실시간 로그 스트리밍 지원
 */
class WebServer(private val port: Int = 8888) {

    private val parser = LogParser()
    private val k8sService = K8sService()
    private var logs = mutableListOf<LogEntry>()
    private var server: HttpServer? = null
    private var logProcess: Process? = null

    // SSE 스트리밍 관련
    private val streamExecutor = Executors.newCachedThreadPool()
    private val activeStreams = ConcurrentHashMap<Int, StreamContext>()
    private val streamIdCounter = AtomicInteger(0)

    data class StreamContext(
        val id: Int,
        val process: Process,
        val exchange: HttpExchange,
        var isActive: Boolean = true
    )

    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0)

        // HTML 파일 서빙
        server?.createContext("/") { exchange -> sendHtml(exchange, loadHtmlFile()) }
        server?.createContext("/api/logs") { exchange -> sendJson(exchange, getLogsJson()) }
        server?.createContext("/api/load") { exchange ->
            loadSampleLogs()
            sendJson(exchange, """{"status": "ok", "count": ${logs.size}}""")
        }
        server?.createContext("/api/clear") { exchange ->
            logs.clear()
            sendJson(exchange, """{"status": "ok"}""")
        }

        // K8s API
        server?.createContext("/api/k8s/contexts") { exchange ->
            val contexts = runBlocking { k8sService.getContexts() }
            val current = runBlocking { k8sService.getCurrentContext() }
            sendJson(exchange, """{"contexts": ${contexts.toJsonArray()}, "current": "$current"}""")
        }

        server?.createContext("/api/k8s/namespaces") { exchange ->
            val context = parseQuery(exchange.requestURI.query ?: "")["context"] ?: ""
            val namespaces = runBlocking { k8sService.getNamespaces(context) }
            sendJson(exchange, """{"namespaces": ${namespaces.toJsonArray()}}""")
        }

        server?.createContext("/api/k8s/pods") { exchange ->
            val params = parseQuery(exchange.requestURI.query ?: "")
            val namespace = params["namespace"] ?: "default"
            val context = params["context"] ?: ""
            val pods = runBlocking { k8sService.getPods(namespace, context) }
            sendJson(exchange, """{"pods": ${pods.toPodsJsonArray()}}""")
        }

        server?.createContext("/api/k8s/logs") { exchange ->
            val params = parseQuery(exchange.requestURI.query ?: "")
            val pod = params["pod"] ?: ""
            val namespace = params["namespace"] ?: "default"
            val context = params["context"] ?: ""
            val tail = params["tail"]?.toIntOrNull() ?: 100

            if (pod.isEmpty()) {
                sendJson(exchange, """{"error": "Pod name required"}""")
                return@createContext
            }

            // kubectl logs 실행
            val logLines = fetchPodLogs(pod, namespace, context, tail)
            logs.clear()
            val entries = parser.parseFile(logLines)
            logs.addAll(entries)
            sendJson(exchange, """{"status": "ok", "count": ${logs.size}, "pod": "$pod"}""")
        }

        // SSE 실시간 로그 스트리밍
        server?.createContext("/api/k8s/logs/stream") { exchange ->
            val params = parseQuery(exchange.requestURI.query ?: "")
            val pod = params["pod"] ?: ""
            val namespace = params["namespace"] ?: "default"
            val context = params["context"] ?: ""
            val tail = params["tail"]?.toIntOrNull() ?: 100

            if (pod.isEmpty()) {
                sendJson(exchange, """{"error": "Pod name required"}""")
                return@createContext
            }

            startLogStream(exchange, pod, namespace, context, tail)
        }

        // 스트림 중지 엔드포인트
        server?.createContext("/api/k8s/logs/stream/stop") { exchange ->
            val params = parseQuery(exchange.requestURI.query ?: "")
            val streamId = params["streamId"]?.toIntOrNull()

            if (streamId != null) {
                stopStream(streamId)
                sendJson(exchange, """{"status": "ok", "streamId": $streamId}""")
            } else {
                // 모든 스트림 중지
                stopAllStreams()
                sendJson(exchange, """{"status": "ok", "message": "All streams stopped"}""")
            }
        }

        server?.executor = Executors.newFixedThreadPool(10)
        server?.start()

        println("웹 서버 시작: http://localhost:$port")
        println("K8s API 엔드포인트:")
        println("  - /api/k8s/contexts")
        println("  - /api/k8s/namespaces?context=xxx")
        println("  - /api/k8s/pods?namespace=xxx&context=xxx")
        println("  - /api/k8s/logs?pod=xxx&namespace=xxx&context=xxx&tail=100")
    }

    fun stop() {
        stopAllStreams()
        logProcess?.destroy()
        server?.stop(0)
        streamExecutor.shutdown()
    }

    /**
     * SSE 로그 스트리밍 시작
     */
    private fun startLogStream(
        exchange: HttpExchange,
        pod: String,
        namespace: String,
        context: String,
        tail: Int
    ) {
        val streamId = streamIdCounter.incrementAndGet()

        // SSE 헤더 설정
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.responseHeaders.add("Connection", "keep-alive")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, 0)

        streamExecutor.submit {
            try {
                val cmd = mutableListOf("kubectl", "logs", "-f", "--tail=$tail", pod, "-n", namespace)
                if (context.isNotEmpty()) {
                    cmd.addAll(listOf("--context", context))
                }

                println("[Stream $streamId] 시작: ${cmd.joinToString(" ")}")

                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()

                val streamContext = StreamContext(streamId, process, exchange)
                activeStreams[streamId] = streamContext

                val output = exchange.responseBody
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                // 스트림 ID 전송
                val initEvent = "event: init\ndata: {\"streamId\": $streamId, \"pod\": \"$pod\"}\n\n"
                output.write(initEvent.toByteArray())
                output.flush()

                var lineNumber = 0
                reader.useLines { lines ->
                    for (line in lines) {
                        if (!streamContext.isActive) break

                        lineNumber++
                        val entry = parser.parse(line, lineNumber)
                        val jsonData = entryToJson(entry)
                        val sseEvent = "event: log\ndata: $jsonData\n\n"

                        try {
                            output.write(sseEvent.toByteArray())
                            output.flush()
                        } catch (e: Exception) {
                            println("[Stream $streamId] 전송 실패: ${e.message}")
                            break
                        }
                    }
                }

                // 스트림 종료 이벤트
                try {
                    val endEvent = "event: end\ndata: {\"streamId\": $streamId, \"lines\": $lineNumber}\n\n"
                    output.write(endEvent.toByteArray())
                    output.flush()
                } catch (e: Exception) {
                    // 클라이언트가 이미 연결을 끊음
                }

                println("[Stream $streamId] 종료: ${lineNumber}줄 전송됨")

            } catch (e: Exception) {
                println("[Stream $streamId] 에러: ${e.message}")
                try {
                    val errorEvent = "event: error\ndata: {\"error\": \"${e.message}\"}\n\n"
                    exchange.responseBody.write(errorEvent.toByteArray())
                    exchange.responseBody.flush()
                } catch (ex: Exception) {
                    // 무시
                }
            } finally {
                activeStreams.remove(streamId)
                try {
                    exchange.responseBody.close()
                } catch (e: Exception) {
                    // 무시
                }
            }
        }
    }

    /**
     * 특정 스트림 중지
     */
    private fun stopStream(streamId: Int) {
        activeStreams[streamId]?.let { ctx ->
            ctx.isActive = false
            ctx.process.destroy()
            println("[Stream $streamId] 중지됨")
        }
    }

    /**
     * 모든 스트림 중지
     */
    private fun stopAllStreams() {
        activeStreams.values.forEach { ctx ->
            ctx.isActive = false
            ctx.process.destroy()
        }
        activeStreams.clear()
        println("모든 스트림 중지됨")
    }

    /**
     * LogEntry를 JSON으로 변환
     */
    private fun entryToJson(entry: LogEntry): String {
        return """{
            "line": ${entry.lineNumber},
            "dateTime": "${entry.dateTime}",
            "level": "${entry.level.name}",
            "thread": "${escapeJson(entry.thread)}",
            "class": "${escapeJson(entry.className)}",
            "message": "${escapeJson(entry.message)}",
            "hasStackTrace": ${entry.hasStackTrace}
        }""".replace("\n", "").replace("  ", "")
    }

    /**
     * JSON 문자열 이스케이프
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")  // 백슬래시 먼저
            .replace("\"", "\\\"")  // 따옴표
            .replace("\n", "\\n")   // 줄바꿈
            .replace("\r", "\\r")   // 캐리지 리턴
            .replace("\t", "\\t")   // 탭
    }

    private fun fetchPodLogs(pod: String, namespace: String, context: String, tail: Int): List<String> {
        return try {
            val cmd = mutableListOf("kubectl", "logs", "--tail=$tail", pod, "-n", namespace)
            if (context.isNotEmpty()) {
                cmd.addAll(listOf("--context", context))
            }

            println("실행: ${cmd.joinToString(" ")}")

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val lines = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            process.waitFor()
            lines
        } catch (e: Exception) {
            println("로그 가져오기 실패: ${e.message}")
            listOf("Error: ${e.message}")
        }
    }

    /**
     * HTML 파일 로드 (samples/ui-mockup.html)
     */
    private fun loadHtmlFile(): String {
        val htmlFile = File("samples/ui-mockup.html")
        return if (htmlFile.exists()) {
            htmlFile.readText()
        } else {
            getMainPage() // fallback to embedded HTML
        }
    }

    private fun loadSampleLogs() {
        logs.clear()
        val sampleFile = File("samples/spring-boot-sample.log")
        if (sampleFile.exists()) {
            val entries = parser.parseFile(sampleFile.readLines())
            logs.addAll(entries)
        } else {
            logs.addAll(listOf(
                LogEntry(1, "2024-01-15 10:30:45.123", LogLevel.INFO, "main", "o.s.b.SpringApplication", "Starting application"),
                LogEntry(2, "2024-01-15 10:30:46.456", LogLevel.DEBUG, "main", "o.s.c.a.Context", "Refreshing context"),
                LogEntry(3, "2024-01-15 10:30:47.789", LogLevel.WARN, "http-nio-8080-exec-1", "c.e.d.UserController", "User not found"),
                LogEntry(4, "2024-01-15 10:30:48.012", LogLevel.ERROR, "http-nio-8080-exec-2", "c.e.d.OrderService", "Order failed",
                    stackTrace = "java.lang.NullPointerException\n    at OrderService.java:42"),
                LogEntry(5, "2024-01-15 10:30:49.345", LogLevel.INFO, "main", "o.s.b.SpringApplication", "Application started"),
                LogEntry(6, "2024-01-15 10:30:50.456", LogLevel.TRACE, "async-1", "c.e.d.CacheService", "Cache refreshed"),
                LogEntry(7, "2024-01-15 10:30:51.567", LogLevel.FATAL, "main", "c.e.d.Database", "Connection lost!")
            ))
        }
    }

    private fun List<String>.toJsonArray(): String = "[${joinToString(",") { "\"$it\"" }}]"

    private fun List<PodInfo>.toPodsJsonArray(): String {
        return "[${joinToString(",") { pod ->
            """{"name":"${pod.name}","status":"${pod.status}","ready":"${pod.ready}","restarts":${pod.restarts},"node":"${pod.node}"}"""
        }}]"
    }

    private fun getLogsJson(): String = logsToJson(logs)

    private fun logsToJson(entries: List<LogEntry>): String {
        val items = entries.joinToString(",\n") { entry ->
            """  {
    "line": ${entry.lineNumber},
    "dateTime": "${entry.dateTime}",
    "level": "${entry.level.name}",
    "thread": "${escapeJson(entry.thread)}",
    "class": "${escapeJson(entry.className)}",
    "message": "${escapeJson(entry.message)}",
    "hasStackTrace": ${entry.hasStackTrace}
  }"""
        }
        return "[\n$items\n]"
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to java.net.URLDecoder.decode(parts.getOrNull(1) ?: "", "UTF-8")
        }
    }

    private fun sendHtml(exchange: HttpExchange, html: String) {
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        exchange.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
        exchange.responseHeaders.add("Pragma", "no-cache")
        exchange.sendResponseHeaders(200, html.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(html.toByteArray()) }
    }

    private fun sendJson(exchange: HttpExchange, json: String) {
        exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(json.toByteArray()) }
    }

    private fun getMainPage() = """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>K8s Log Viewer - Web UI</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Consolas', 'Monaco', monospace; background: #1e1e1e; color: #d4d4d4; }

        .header { background: #2d2d2d; padding: 15px; border-bottom: 1px solid #404040; }
        .header h1 { color: #569cd6; font-size: 20px; }

        .k8s-panel { background: #1a1a2e; padding: 10px 15px; border-bottom: 1px solid #404040; display: flex; gap: 15px; align-items: center; flex-wrap: wrap; }
        .k8s-panel label { color: #9cdcfe; font-size: 13px; }
        .k8s-panel select { background: #3c3c3c; color: #d4d4d4; border: 1px solid #555; padding: 5px 10px; border-radius: 3px; min-width: 150px; }
        .k8s-panel button { background: #16a085; color: white; border: none; padding: 8px 16px; cursor: pointer; border-radius: 3px; font-size: 13px; }
        .k8s-panel button:hover { background: #1abc9c; }
        .k8s-panel button:disabled { background: #555; cursor: not-allowed; }

        .controls { background: #252526; padding: 10px 15px; display: flex; gap: 15px; align-items: center; flex-wrap: wrap; }
        .controls button { background: #0e639c; color: white; border: none; padding: 8px 16px; cursor: pointer; border-radius: 3px; font-size: 13px; }
        .controls button:hover { background: #1177bb; }
        .controls button.danger { background: #c42b1c; }
        .controls button.danger:hover { background: #e63939; }

        .filters { display: flex; gap: 10px; align-items: center; }
        .filters label { color: #9cdcfe; font-size: 13px; }
        .filters select, .filters input { background: #3c3c3c; color: #d4d4d4; border: 1px solid #555; padding: 5px 10px; border-radius: 3px; }

        .level-filters { display: flex; gap: 8px; }
        .level-filters label { display: flex; align-items: center; gap: 4px; cursor: pointer; }

        .log-table { width: 100%; overflow-x: auto; }
        table { width: 100%; border-collapse: collapse; font-size: 12px; }
        th { background: #2d2d2d; color: #9cdcfe; text-align: left; padding: 8px 10px; position: sticky; top: 0; }
        td { padding: 6px 10px; border-bottom: 1px solid #333; vertical-align: top; }
        tr:hover { background: #2a2d2e; }

        .line { color: #6a9955; text-align: right; width: 50px; }
        .datetime { color: #dcdcaa; width: 180px; }
        .level { width: 60px; font-weight: bold; text-align: center; }
        .thread { color: #ce9178; width: 150px; }
        .class { color: #4ec9b0; width: 200px; }
        .message { color: #d4d4d4; }

        .level-TRACE { color: #808080; }
        .level-DEBUG { color: #569cd6; }
        .level-INFO { color: #4ec9b0; }
        .level-WARN { color: #dcdcaa; background: rgba(220,220,170,0.1); }
        .level-ERROR { color: #f14c4c; background: rgba(241,76,76,0.1); }
        .level-FATAL { color: #ff0000; background: rgba(255,0,0,0.15); }
        .level-UNKNOWN { color: #808080; }

        .status { background: #007acc; color: white; padding: 5px 15px; font-size: 12px; }
        .stack-trace { color: #ce9178; font-size: 11px; margin-top: 5px; white-space: pre; }

        #log-container { height: calc(100vh - 220px); overflow-y: auto; }

        .pod-status-Running { color: #4ec9b0; }
        .pod-status-Pending { color: #dcdcaa; }
        .pod-status-Error, .pod-status-CrashLoopBackOff { color: #f14c4c; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🚀 K8s Log Viewer - Web UI (Playwright Test)</h1>
    </div>

    <div class="k8s-panel">
        <label>Context:</label>
        <select id="contextSelect" onchange="loadNamespaces()"></select>

        <label>Namespace:</label>
        <select id="namespaceSelect" onchange="loadPods()"></select>

        <label>Pod:</label>
        <select id="podSelect"></select>

        <label>Tail:</label>
        <select id="tailSelect">
            <option value="50">50</option>
            <option value="100" selected>100</option>
            <option value="500">500</option>
            <option value="1000">1000</option>
        </select>

        <button id="connectBtn" onclick="connectPod()">🔗 Connect Pod</button>
        <button onclick="loadContexts()">🔄 Refresh K8s</button>
    </div>

    <div class="controls">
        <button id="loadBtn" onclick="loadLogs()">📥 Load Sample</button>
        <button id="clearBtn" class="danger" onclick="clearLogs()">🗑️ Clear</button>

        <div class="filters">
            <label>Keyword:</label>
            <input type="text" id="keywordFilter" placeholder="Search..." oninput="applyFilter()">
        </div>

        <div class="level-filters">
            <label><input type="checkbox" id="chk-TRACE" checked onchange="applyFilter()"> TRACE</label>
            <label><input type="checkbox" id="chk-DEBUG" checked onchange="applyFilter()"> DEBUG</label>
            <label><input type="checkbox" id="chk-INFO" checked onchange="applyFilter()"> INFO</label>
            <label><input type="checkbox" id="chk-WARN" checked onchange="applyFilter()"> WARN</label>
            <label><input type="checkbox" id="chk-ERROR" checked onchange="applyFilter()"> ERROR</label>
            <label><input type="checkbox" id="chk-FATAL" checked onchange="applyFilter()"> FATAL</label>
        </div>
    </div>

    <div id="log-container" class="log-table">
        <table>
            <thead>
                <tr>
                    <th class="line">Line</th>
                    <th class="datetime">DateTime</th>
                    <th class="level">Level</th>
                    <th class="thread">Thread</th>
                    <th class="class">Class</th>
                    <th class="message">Message</th>
                </tr>
            </thead>
            <tbody id="logBody"></tbody>
        </table>
    </div>

    <div class="status" id="status">준비됨</div>

    <script>
        let allLogs = [];

        // K8s 함수들
        async function loadContexts() {
            setStatus('Context 로딩 중...');
            try {
                const res = await fetch('/api/k8s/contexts');
                const data = await res.json();
                const select = document.getElementById('contextSelect');
                select.innerHTML = data.contexts.map(c =>
                    '<option value="' + c + '"' + (c === data.current ? ' selected' : '') + '>' + c + '</option>'
                ).join('');
                setStatus('Context ' + data.contexts.length + '개 로드됨');
                if (data.contexts.length > 0) loadNamespaces();
            } catch (e) {
                setStatus('Context 로드 실패: ' + e.message);
            }
        }

        async function loadNamespaces() {
            const context = document.getElementById('contextSelect').value;
            setStatus('Namespace 로딩 중...');
            try {
                const res = await fetch('/api/k8s/namespaces?context=' + encodeURIComponent(context));
                const data = await res.json();
                const select = document.getElementById('namespaceSelect');
                select.innerHTML = data.namespaces.map(n =>
                    '<option value="' + n + '"' + (n === 'default' ? ' selected' : '') + '>' + n + '</option>'
                ).join('');
                setStatus('Namespace ' + data.namespaces.length + '개 로드됨');
                if (data.namespaces.length > 0) loadPods();
            } catch (e) {
                setStatus('Namespace 로드 실패: ' + e.message);
            }
        }

        async function loadPods() {
            const context = document.getElementById('contextSelect').value;
            const namespace = document.getElementById('namespaceSelect').value;
            setStatus('Pod 로딩 중...');
            try {
                const res = await fetch('/api/k8s/pods?context=' + encodeURIComponent(context) + '&namespace=' + encodeURIComponent(namespace));
                const data = await res.json();
                const select = document.getElementById('podSelect');
                select.innerHTML = data.pods.map(p =>
                    '<option value="' + p.name + '" class="pod-status-' + p.status + '">' +
                    p.name + ' (' + p.status + ', ' + p.ready + ')' + '</option>'
                ).join('');
                setStatus('Pod ' + data.pods.length + '개 로드됨');
            } catch (e) {
                setStatus('Pod 로드 실패: ' + e.message);
            }
        }

        async function connectPod() {
            const context = document.getElementById('contextSelect').value;
            const namespace = document.getElementById('namespaceSelect').value;
            const pod = document.getElementById('podSelect').value;
            const tail = document.getElementById('tailSelect').value;

            if (!pod) {
                setStatus('Pod를 선택하세요');
                return;
            }

            setStatus('Pod 로그 가져오는 중: ' + pod);
            try {
                const url = '/api/k8s/logs?pod=' + encodeURIComponent(pod) +
                    '&namespace=' + encodeURIComponent(namespace) +
                    '&context=' + encodeURIComponent(context) +
                    '&tail=' + tail;
                const res = await fetch(url);
                const data = await res.json();
                if (data.error) {
                    setStatus('오류: ' + data.error);
                } else {
                    setStatus('Pod 로그 로드됨: ' + data.pod + ' (' + data.count + '줄)');
                    refreshLogs();
                }
            } catch (e) {
                setStatus('로그 가져오기 실패: ' + e.message);
            }
        }

        // 기존 함수들
        async function loadLogs() {
            setStatus('샘플 로딩 중...');
            const res = await fetch('/api/load');
            const data = await res.json();
            setStatus('샘플 로그 로드됨: ' + data.count + '줄');
            refreshLogs();
        }

        async function clearLogs() {
            await fetch('/api/clear');
            allLogs = [];
            renderLogs([]);
            setStatus('로그 클리어됨');
        }

        async function refreshLogs() {
            const res = await fetch('/api/logs');
            allLogs = await res.json();
            applyFilter();
        }

        function applyFilter() {
            const keyword = document.getElementById('keywordFilter').value.toLowerCase();
            const enabledLevels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL', 'UNKNOWN']
                .filter(l => {
                    const chk = document.getElementById('chk-' + l);
                    return chk ? chk.checked : true;
                });

            const filtered = allLogs.filter(log => {
                if (!enabledLevels.includes(log.level)) return false;
                if (keyword && !log.message.toLowerCase().includes(keyword)) return false;
                return true;
            });

            renderLogs(filtered);
            setStatus('표시: ' + filtered.length + '/' + allLogs.length + '줄');
        }

        function renderLogs(logs) {
            const tbody = document.getElementById('logBody');
            tbody.innerHTML = logs.map(log =>
                '<tr class="level-' + log.level + '">' +
                '<td class="line">' + log.line + '</td>' +
                '<td class="datetime">' + log.dateTime + '</td>' +
                '<td class="level level-' + log.level + '">' + log.level + '</td>' +
                '<td class="thread">' + log.thread + '</td>' +
                '<td class="class">' + log.class + '</td>' +
                '<td class="message">' + escapeHtml(log.message) +
                    (log.hasStackTrace ? '<div class="stack-trace">[스택트레이스]</div>' : '') +
                '</td></tr>'
            ).join('');
        }

        function escapeHtml(str) {
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        function setStatus(msg) {
            document.getElementById('status').textContent = msg;
        }

        // 페이지 로드 시 K8s 초기화
        window.onload = loadContexts;
    </script>
</body>
</html>
"""
}

fun main() {
    val server = WebServer(8888)
    server.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        println("서버 종료 중...")
        server.stop()
    })

    println("서버 실행 중... (Ctrl+C로 종료)")

    while (true) {
        Thread.sleep(1000)
    }
}
