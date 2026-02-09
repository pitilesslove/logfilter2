package com.logfilter.cli

import com.logfilter.model.LogLevel
import com.logfilter.service.LogParser
import java.io.File

/**
 * CLI 모드 러너 - GUI 없이 로그 파싱 테스트
 */
object CliRunner {

    private val parser = LogParser()

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(60))
        println("K8s Log Viewer CLI Mode v2.0")
        println("=".repeat(60))

        when {
            args.isEmpty() -> {
                println("\n사용법:")
                println("  --file <path>     : 로그 파일 파싱")
                println("  --test            : 내장 테스트 실행")
                println("  --filter <level>  : 특정 레벨만 표시 (예: ERROR)")
                println()
                runBuiltInTest()
            }
            args[0] == "--test" -> runBuiltInTest()
            args[0] == "--file" && args.size > 1 -> parseFile(args[1], args.getOrNull(3))
            else -> println("알 수 없는 옵션: ${args[0]}")
        }
    }

    private fun runBuiltInTest() {
        println("\n[내장 테스트 실행]")
        println("-".repeat(60))

        val sampleLogs = listOf(
            "2024-01-15 10:30:45.123  INFO [main] o.s.b.SpringApplication - Starting application",
            "2024-01-15 10:30:45.456 DEBUG [main] o.s.c.a.Context - Refreshing context",
            "2024-01-15 10:30:46.789  WARN [http-nio-8080-exec-1] c.e.d.MyController - Slow response",
            "2024-01-15 10:30:47.012 ERROR [http-nio-8080-exec-2] c.e.d.MyService - Exception occurred",
            "java.lang.NullPointerException: null",
            "    at com.example.demo.MyService.process(MyService.java:42)",
            "    at com.example.demo.MyController.handle(MyController.java:28)",
            "2024-01-15 10:30:48.345  INFO [main] o.s.b.SpringApplication - Application started",
            "2024-01-15 10:30:49.456 TRACE [async-1] c.e.d.AsyncService - Async task started",
            "2024-01-15 10:30:50.567 FATAL [main] c.e.d.App - Critical error occurred"
        )

        val entries = parser.parseFile(sampleLogs)

        println("\n파싱 결과: ${entries.size}개 로그 엔트리\n")

        // 레벨별 통계
        val levelStats = entries.groupBy { it.level }
            .mapValues { it.value.size }
            .toSortedMap()

        println("레벨별 통계:")
        levelStats.forEach { (level, count) ->
            val bar = "█".repeat(count * 3)
            println("  ${level.displayName.padEnd(6)} : $bar $count")
        }

        println("\n" + "-".repeat(60))
        println("파싱된 로그:")
        println("-".repeat(60))

        entries.forEach { entry ->
            val levelColor = when (entry.level) {
                LogLevel.ERROR, LogLevel.FATAL -> "\u001B[31m"  // Red
                LogLevel.WARN -> "\u001B[33m"   // Yellow
                LogLevel.INFO -> "\u001B[32m"   // Green
                LogLevel.DEBUG -> "\u001B[34m"  // Blue
                else -> "\u001B[0m"
            }
            val reset = "\u001B[0m"

            println(buildString {
                append("[${entry.lineNumber.toString().padStart(3)}] ")
                append("$levelColor${entry.level.displayName.padEnd(5)}$reset ")
                append(entry.dateTime.take(23).padEnd(23))
                append(" [${entry.thread.take(20).padEnd(20)}] ")
                append(entry.className.takeLast(25).padStart(25))
                append(" - ")
                append(entry.message.take(40))
            })

            if (entry.hasStackTrace) {
                println("       └─ [스택트레이스 ${entry.stackTrace?.lines()?.size ?: 0}줄]")
            }
        }

        println("\n" + "=".repeat(60))
        println("테스트 완료!")
    }

    private fun parseFile(filePath: String, levelFilter: String?) {
        val file = File(filePath)
        if (!file.exists()) {
            println("파일을 찾을 수 없습니다: $filePath")
            return
        }

        println("\n[파일 파싱: ${file.name}]")
        println("-".repeat(60))

        val lines = file.readLines()
        val entries = parser.parseFile(lines)

        val filtered = if (levelFilter != null) {
            val targetLevel = LogLevel.fromString(levelFilter)
            entries.filter { it.level == targetLevel }
        } else {
            entries
        }

        println("총 ${entries.size}줄, 표시: ${filtered.size}줄")
        if (levelFilter != null) {
            println("필터: $levelFilter")
        }
        println()

        filtered.take(50).forEach { entry ->
            println("[${entry.lineNumber}] ${entry.level.displayName} ${entry.dateTime} - ${entry.message.take(60)}")
        }

        if (filtered.size > 50) {
            println("\n... 외 ${filtered.size - 50}줄 생략")
        }
    }
}
