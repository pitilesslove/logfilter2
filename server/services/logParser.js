/**
 * Log Parser Service
 * 다양한 로그 형식을 파싱하여 구조화된 LogEntry로 변환
 */

// 로그 레벨 정규화
const LEVEL_MAP = {
    'TRACE': 'TRACE',
    'DEBUG': 'DEBUG',
    'INFO': 'INFO',
    'WARN': 'WARN',
    'WARNING': 'WARN',
    'ERROR': 'ERROR',
    'FATAL': 'FATAL',
    'SEVERE': 'ERROR'
};

// Spring Boot 로그 패턴
// 2024-01-15 10:30:45.123  INFO 12345 --- [main] c.e.demo.Application : Starting...
const SPRING_PATTERN = /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w+)\s+\d+\s+---\s+\[([^\]]+)\]\s+(\S+)\s+:\s+(.*)$/;

// DX 로그 패턴
// 2024-01-15 10:30:45.123 [INFO ] [main] ClassName - Message
const DX_PATTERN = /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\[(\w+)\s*\]\s+\[([^\]]+)\]\s+(\S+)\s+-\s+(.*)$/;

// Logback 패턴: [%d %-5.5r][%-5p][%-1.20class{1}][%X{traceId:-}]|%msg%n
// [2026-02-10 01:16:23,125 22161][INFO ][LoggingAspect][traceId]|message
// traceId가 있는 경우
const LOGBACK_PATTERN_WITH_TRACE = /^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[,\.]\d{3})(?:\s+\d+)?\]\[(\w+)\s*\]\[([^\]]+)\]\[([^\]]*)\]\|?(.*)$/;

// traceId가 없거나 빈 경우: [datetime][LEVEL][class]|message 또는 [datetime][LEVEL][class][]|message
const LOGBACK_PATTERN_NO_TRACE = /^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[,\.]\d{3})(?:\s+\d+)?\]\[(\w+)\s*\]\[([^\]]+)\](?:\[\])?\|?(.*)$/;

// 레거시 Bracket 로그 패턴 (pipe 없는 구형식)
// [2026-02-10 01:16:23,125 22161][INFO ][ClassName][RequestId]Message
const BRACKET_PATTERN_LEGACY = /^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[,\.]\d{3})(?:\s+\d+)?\]\[(\w+)\s*\]\[([^\]]+)\]\[([^\]]+)\]([^|].*)$/;

// 일반 로그 패턴
// 2024-01-15 10:30:45 INFO ClassName - Message
const GENERAL_PATTERN = /^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{3})?)\s+(\w+)\s+(\S+)\s+-?\s*(.*)$/;

class LogParser {
    constructor() {
        this.lineNumber = 0;
    }

    /**
     * 단일 라인 파싱
     */
    parse(line, lineNum = null) {
        if (lineNum !== null) {
            this.lineNumber = lineNum;
        } else {
            this.lineNumber++;
        }

        const trimmed = line.trim();
        if (!trimmed) {
            return null;
        }

        // Spring Boot 패턴 시도
        let match = trimmed.match(SPRING_PATTERN);
        if (match) {
            return {
                line: this.lineNumber,
                dateTime: match[1],
                level: this.normalizeLevel(match[2]),
                thread: match[3].trim(),
                class: match[4],
                message: match[5],
                hasStackTrace: false
            };
        }

        // DX 패턴 시도
        match = trimmed.match(DX_PATTERN);
        if (match) {
            return {
                line: this.lineNumber,
                dateTime: match[1],
                level: this.normalizeLevel(match[2]),
                thread: match[3].trim(),
                class: match[4],
                message: match[5],
                hasStackTrace: false
            };
        }

        // Logback 패턴 (traceId 포함): [datetime][LEVEL][class][traceId]|message
        match = trimmed.match(LOGBACK_PATTERN_WITH_TRACE);
        if (match) {
            const traceId = match[4].trim();
            return {
                line: this.lineNumber,
                dateTime: match[1].replace(',', '.'),
                level: this.normalizeLevel(match[2]),
                thread: traceId ? traceId.substring(0, 8) : 'main',  // traceId 앞 8자리
                class: match[3].trim(),
                message: match[5].trim(),
                hasStackTrace: false
            };
        }

        // Logback 패턴 (traceId 없음): [datetime][LEVEL][class]|message
        match = trimmed.match(LOGBACK_PATTERN_NO_TRACE);
        if (match) {
            return {
                line: this.lineNumber,
                dateTime: match[1].replace(',', '.'),
                level: this.normalizeLevel(match[2]),
                thread: 'main',
                class: match[3].trim(),
                message: match[4].trim(),
                hasStackTrace: false
            };
        }

        // 레거시 Bracket 패턴 (pipe 없음): [datetime][LEVEL][class][id]message
        match = trimmed.match(BRACKET_PATTERN_LEGACY);
        if (match) {
            return {
                line: this.lineNumber,
                dateTime: match[1].replace(',', '.'),
                level: this.normalizeLevel(match[2]),
                thread: match[4].trim().substring(0, 8),
                class: match[3].trim(),
                message: match[5].trim(),
                hasStackTrace: false
            };
        }

        // 일반 패턴 시도
        match = trimmed.match(GENERAL_PATTERN);
        if (match) {
            return {
                line: this.lineNumber,
                dateTime: match[1],
                level: this.normalizeLevel(match[2]),
                thread: 'main',
                class: match[3],
                message: match[4],
                hasStackTrace: false
            };
        }

        // 매칭 실패 시 기본 엔트리 반환
        return {
            line: this.lineNumber,
            dateTime: new Date().toISOString().replace('T', ' ').substring(0, 23),
            level: 'INFO',
            thread: 'unknown',
            class: 'unknown',
            message: trimmed,
            hasStackTrace: trimmed.startsWith('at ') || trimmed.includes('Exception')
        };
    }

    /**
     * 여러 라인 파싱
     */
    parseLines(lines) {
        this.lineNumber = 0;
        return lines
            .map(line => this.parse(line))
            .filter(entry => entry !== null);
    }

    /**
     * 로그 레벨 정규화
     */
    normalizeLevel(level) {
        const upper = level.toUpperCase().trim();
        return LEVEL_MAP[upper] || 'INFO';
    }

    /**
     * 라인 카운터 리셋
     */
    reset() {
        this.lineNumber = 0;
    }
}

module.exports = new LogParser();
