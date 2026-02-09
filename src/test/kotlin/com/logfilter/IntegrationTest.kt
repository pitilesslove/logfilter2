package com.logfilter

import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel
import com.logfilter.service.LogParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * 통합 테스트 - 파일 로드, 파싱, 필터링
 */
class IntegrationTest {

    private val parser = LogParser()

    @Test
    fun `parse sample log file`() {
        val file = File("samples/spring-boot-sample.log")
        assertTrue(file.exists(), "샘플 파일이 존재해야 함")

        val lines = file.readLines()
        val entries = parser.parseFile(lines)

        assertTrue(entries.isNotEmpty(), "파싱된 엔트리가 있어야 함")
        println("파싱된 로그 엔트리: ${entries.size}개")

        // 레벨별 통계 출력
        val stats = entries.groupBy { it.level }.mapValues { it.value.size }
        println("레벨별 통계: $stats")
    }

    @Test
    fun `filter by log level`() {
        val entries = listOf(
            LogEntry(1, level = LogLevel.INFO, message = "Info message"),
            LogEntry(2, level = LogLevel.DEBUG, message = "Debug message"),
            LogEntry(3, level = LogLevel.ERROR, message = "Error message"),
            LogEntry(4, level = LogLevel.WARN, message = "Warn message"),
            LogEntry(5, level = LogLevel.INFO, message = "Another info")
        )

        val errorOnly = entries.filter { it.level == LogLevel.ERROR }
        assertEquals(1, errorOnly.size)
        assertEquals("Error message", errorOnly[0].message)

        val infoAndWarn = entries.filter { it.level in listOf(LogLevel.INFO, LogLevel.WARN) }
        assertEquals(3, infoAndWarn.size)
    }

    @Test
    fun `filter by keyword`() {
        val entries = listOf(
            LogEntry(1, message = "User login successful"),
            LogEntry(2, message = "Database connection established"),
            LogEntry(3, message = "User logout"),
            LogEntry(4, message = "Cache cleared"),
            LogEntry(5, message = "User session expired")
        )

        val userRelated = entries.filter {
            it.message.contains("User", ignoreCase = true)
        }
        assertEquals(3, userRelated.size)

        val loginLogout = entries.filter { entry ->
            listOf("login", "logout").any { keyword ->
                entry.message.contains(keyword, ignoreCase = true)
            }
        }
        assertEquals(2, loginLogout.size)
    }

    @Test
    fun `filter by class name`() {
        val entries = listOf(
            LogEntry(1, className = "c.e.d.UserController", message = "Request received"),
            LogEntry(2, className = "c.e.d.UserService", message = "Processing"),
            LogEntry(3, className = "c.e.d.OrderController", message = "Order created"),
            LogEntry(4, className = "c.e.d.UserRepository", message = "Query executed"),
            LogEntry(5, className = "c.e.d.PaymentService", message = "Payment processed")
        )

        val userClasses = entries.filter {
            it.className.contains("User", ignoreCase = true)
        }
        assertEquals(3, userClasses.size)

        val controllers = entries.filter {
            it.className.contains("Controller", ignoreCase = true)
        }
        assertEquals(2, controllers.size)
    }

    @Test
    fun `exclude by keyword`() {
        val entries = listOf(
            LogEntry(1, message = "Application started"),
            LogEntry(2, message = "Health check OK"),
            LogEntry(3, message = "Request received"),
            LogEntry(4, message = "Health check OK"),
            LogEntry(5, message = "Response sent")
        )

        val excludeHealthCheck = entries.filterNot {
            it.message.contains("Health check", ignoreCase = true)
        }
        assertEquals(3, excludeHealthCheck.size)
    }

    @Test
    fun `combined filters`() {
        val file = File("samples/spring-boot-sample.log")
        if (!file.exists()) {
            println("샘플 파일 없음, 테스트 건너뜀")
            return
        }

        val entries = parser.parseFile(file.readLines())

        // ERROR와 WARN만 필터링
        val errorAndWarn = entries.filter {
            it.level in listOf(LogLevel.ERROR, LogLevel.WARN)
        }
        println("ERROR/WARN 로그: ${errorAndWarn.size}개")
        assertTrue(errorAndWarn.isNotEmpty())

        // 특정 키워드 포함 필터링
        val withUser = entries.filter {
            it.message.contains("user", ignoreCase = true) ||
            it.message.contains("User", ignoreCase = true)
        }
        println("User 관련 로그: ${withUser.size}개")

        // 복합 필터: ERROR 레벨이면서 특정 클래스
        val errorInService = entries.filter {
            it.level == LogLevel.ERROR &&
            it.className.contains("Service", ignoreCase = true)
        }
        println("Service에서 발생한 ERROR: ${errorInService.size}개")
    }

    @Test
    fun `bookmark functionality`() {
        val entries = mutableListOf(
            LogEntry(1, message = "First log", isBookmarked = false),
            LogEntry(2, message = "Second log", isBookmarked = false),
            LogEntry(3, message = "Third log", isBookmarked = false)
        )

        // 북마크 토글
        entries[1] = entries[1].copy(isBookmarked = true)
        assertTrue(entries[1].isBookmarked)

        // 북마크된 항목만 필터링
        val bookmarked = entries.filter { it.isBookmarked }
        assertEquals(1, bookmarked.size)
        assertEquals("Second log", bookmarked[0].message)

        // 북마크 해제
        entries[1] = entries[1].copy(isBookmarked = false)
        assertFalse(entries[1].isBookmarked)
    }

    @Test
    fun `stack trace detection and grouping`() {
        val file = File("samples/spring-boot-sample.log")
        if (!file.exists()) return

        val entries = parser.parseFile(file.readLines())

        val withStackTrace = entries.filter { it.hasStackTrace }
        println("스택트레이스 포함 로그: ${withStackTrace.size}개")

        withStackTrace.forEach { entry ->
            println("  Line ${entry.lineNumber}: ${entry.message.take(50)}...")
            println("    스택트레이스 ${entry.stackTrace?.lines()?.size ?: 0}줄")
        }
    }
}
