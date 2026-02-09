package com.logfilter.service

import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel

/**
 * Spring Boot Logback 형식 로그 파서
 *
 * 지원 형식:
 * 1. 기본 Logback: "2024-01-15 12:34:56.789  INFO [main] c.e.demo.MyClass - message"
 * 2. ISO 8601: "2024-01-15T12:34:56.789+09:00  INFO 12345 --- [main] c.e.demo.MyClass : message"
 * 3. UMS 커스텀: "[2026-02-06 08:42:32,921 06699][INFO ][.RefreshTokenService][]|auth. message"
 */
class LogParser {

    // 기본 Logback 패턴
    private val logbackPattern = Regex(
        """^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\[([^\]]+)]\s+(\S+)\s+-\s+(.*)$"""
    )

    // Spring Boot 기본 패턴 (ISO 8601 타임스탬프)
    private val springBootPattern = Regex(
        """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2})\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\d+\s+---\s+\[([^\]]+)]\s+(\S+)\s+:\s+(.*)$"""
    )

    // 간단한 패턴
    private val simplePattern = Regex(
        """^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[.,]\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+(?:---\s+)?\[([^\]]+)]\s*(\S+)\s*[-:]?\s*(.*)$"""
    )

    // UMS 커스텀 로그 형식
    // [2026-02-06 08:42:32,921 06699][INFO ][.RefreshTokenService][]|auth. message
    private val umsPattern = Regex(
        """^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2},\d{3})\s+\d+]\[(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s*]\[([^\]]*)](?:\[[^\]]*])?\|?(.*)$"""
    )

    // 스택트레이스 라인 감지 패턴
    private val stackTracePatterns = listOf(
        Regex("""^\s+at\s+.*"""),
        Regex("""^Caused by:.*"""),
        Regex("""^\.\.\.\s+\d+\s+more.*"""),
        Regex("""^Suppressed:.*"""),
        Regex("""^java\..*Exception.*"""),
        Regex("""^org\..*Exception.*"""),
        Regex("""^com\..*Exception.*""")
    )

    /**
     * 로그 라인 파싱
     */
    fun parse(line: String, lineNumber: Int): LogEntry {
        if (line.isBlank()) {
            return LogEntry(lineNumber = lineNumber, message = "")
        }

        // 스택트레이스 라인 감지
        if (isStackTraceLine(line)) {
            return LogEntry(
                lineNumber = lineNumber,
                message = line,
                stackTrace = line
            )
        }

        // 기본 Logback 패턴 시도
        logbackPattern.find(line)?.let { match ->
            return parseMatchedLog(match, lineNumber)
        }

        // Spring Boot 패턴 시도
        springBootPattern.find(line)?.let { match ->
            return parseMatchedLog(match, lineNumber)
        }

        // 간단한 패턴 시도
        simplePattern.find(line)?.let { match ->
            return parseMatchedLog(match, lineNumber)
        }

        // UMS 커스텀 패턴 시도
        umsPattern.find(line)?.let { match ->
            return parseUmsLog(match, lineNumber)
        }

        // 매칭되지 않으면 전체를 메시지로
        return LogEntry(lineNumber = lineNumber, message = line)
    }

    private fun parseMatchedLog(match: MatchResult, lineNumber: Int): LogEntry {
        val (dateTime, level, thread, className, message) = match.destructured
        return LogEntry(
            lineNumber = lineNumber,
            dateTime = dateTime,
            level = LogLevel.fromString(level),
            thread = thread.trim(),
            className = className,
            message = message
        )
    }

    private fun parseUmsLog(match: MatchResult, lineNumber: Int): LogEntry {
        val (dateTime, level, className, message) = match.destructured
        // 메시지에서 모듈 이름 추출 (예: "auth. Creating login...")
        val parts = message.split(".", limit = 2)
        val module = if (parts.size > 1) parts[0].trim() else ""
        val actualMessage = if (parts.size > 1) parts[1].trim() else message
        return LogEntry(
            lineNumber = lineNumber,
            dateTime = dateTime.replace(",", "."),  // 콤마를 점으로 변환
            level = LogLevel.fromString(level.trim()),
            thread = module,  // 모듈을 thread 컬럼에 표시
            className = className.trim().removePrefix("."),
            message = actualMessage
        )
    }

    /**
     * 스택트레이스 라인인지 확인
     */
    fun isStackTraceLine(line: String): Boolean {
        val trimmed = line.trim()
        return stackTracePatterns.any { it.matches(trimmed) } ||
            trimmed.startsWith("at ") ||
            trimmed.startsWith("Caused by:") ||
            trimmed.startsWith("... ")
    }

    /**
     * 파일에서 로그 파싱
     */
    fun parseFile(lines: List<String>): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        var currentEntry: LogEntry? = null
        var stackTraceBuilder = StringBuilder()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1

            if (isStackTraceLine(line)) {
                // 스택트레이스 라인은 이전 엔트리에 추가
                stackTraceBuilder.appendLine(line)
            } else {
                // 이전 엔트리가 있으면 스택트레이스 연결 후 저장
                currentEntry?.let { entry ->
                    if (stackTraceBuilder.isNotEmpty()) {
                        entries.add(entry.copy(stackTrace = stackTraceBuilder.toString().trimEnd()))
                        stackTraceBuilder = StringBuilder()
                    } else {
                        entries.add(entry)
                    }
                }

                // 새 엔트리 파싱
                currentEntry = parse(line, lineNumber)
            }
        }

        // 마지막 엔트리 처리
        currentEntry?.let { entry ->
            if (stackTraceBuilder.isNotEmpty()) {
                entries.add(entry.copy(stackTrace = stackTraceBuilder.toString().trimEnd()))
            } else {
                entries.add(entry)
            }
        }

        return entries
    }
}
