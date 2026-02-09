package com.logfilter.ui

import com.logfilter.controller.MainController
import com.logfilter.model.K8sConfig
import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel
import com.logfilter.service.LogParser
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * UI 컴포넌트 로직 테스트 (JavaFX Thread 없이)
 */
class SimpleUITest {

    @Test
    fun `K8sConfig should manage pod selection`() {
        val config = K8sConfig()

        // Context 설정
        config.currentContext = "docker-desktop"
        assertEquals("docker-desktop", config.currentContext)

        // Namespace 설정
        config.currentNamespace = "my-namespace"
        assertEquals("my-namespace", config.currentNamespace)

        // Pod 목록 설정
        config.contexts.addAll(listOf("docker-desktop", "minikube", "production"))
        assertEquals(3, config.contexts.size)

        // 선택된 Pod 정보 확인
        config.selectedPod = ""
        assertNull(config.getSelectedPodInfo())
    }

    @Test
    fun `filter logic should work correctly`() {
        val allLogs = FXCollections.observableArrayList(
            LogEntry(1, level = LogLevel.INFO, message = "Info message", className = "c.e.Service"),
            LogEntry(2, level = LogLevel.DEBUG, message = "Debug message", className = "c.e.Controller"),
            LogEntry(3, level = LogLevel.ERROR, message = "Error occurred", className = "c.e.Service"),
            LogEntry(4, level = LogLevel.WARN, message = "Warning message", className = "c.e.Repository"),
            LogEntry(5, level = LogLevel.INFO, message = "Another info", className = "c.e.Controller")
        )

        val filtered = FilteredList(allLogs) { true }

        // 기본 - 모든 로그 표시
        assertEquals(5, filtered.size)

        // ERROR만 필터링
        filtered.setPredicate { it.level == LogLevel.ERROR }
        assertEquals(1, filtered.size)
        assertEquals("Error occurred", filtered[0].message)

        // INFO와 WARN만 필터링
        filtered.setPredicate { it.level in listOf(LogLevel.INFO, LogLevel.WARN) }
        assertEquals(3, filtered.size)

        // 키워드 필터링
        filtered.setPredicate {
            it.message.contains("message", ignoreCase = true)
        }
        assertEquals(3, filtered.size)

        // 클래스 필터링
        filtered.setPredicate {
            it.className.contains("Service", ignoreCase = true)
        }
        assertEquals(2, filtered.size)

        // 복합 필터: ERROR 레벨 + Service 클래스
        filtered.setPredicate {
            it.level == LogLevel.ERROR && it.className.contains("Service")
        }
        assertEquals(1, filtered.size)
    }

    @Test
    fun `word filter with OR condition`() {
        val logs = listOf(
            LogEntry(1, message = "User login successful"),
            LogEntry(2, message = "Order created"),
            LogEntry(3, message = "User logout"),
            LogEntry(4, message = "Payment processed"),
            LogEntry(5, message = "Order shipped")
        )

        val wordFilter = "User|Order"
        val keywords = wordFilter.split("|").map { it.trim() }

        val filtered = logs.filter { entry ->
            keywords.any { keyword ->
                entry.message.contains(keyword, ignoreCase = true)
            }
        }

        assertEquals(4, filtered.size)  // User login, Order created, User logout, Order shipped
    }

    @Test
    fun `exclude filter with OR condition`() {
        val logs = listOf(
            LogEntry(1, message = "Application started"),
            LogEntry(2, message = "Health check OK"),
            LogEntry(3, message = "Request received"),
            LogEntry(4, message = "Health check OK"),
            LogEntry(5, message = "Heartbeat sent")
        )

        val excludeFilter = "Health|Heartbeat"
        val excludeKeywords = excludeFilter.split("|").map { it.trim() }

        val filtered = logs.filterNot { entry ->
            excludeKeywords.any { keyword ->
                entry.message.contains(keyword, ignoreCase = true)
            }
        }

        assertEquals(2, filtered.size)  // Application started, Request received
    }

    @Test
    fun `bookmark toggle should work`() {
        val logs = mutableListOf(
            LogEntry(1, message = "First", isBookmarked = false),
            LogEntry(2, message = "Second", isBookmarked = false),
            LogEntry(3, message = "Third", isBookmarked = false)
        )

        // 북마크 토글
        logs[1] = logs[1].copy(isBookmarked = true)
        assertTrue(logs[1].isBookmarked)

        // 북마크된 항목 필터링
        val bookmarked = logs.filter { it.isBookmarked }
        assertEquals(1, bookmarked.size)

        // 북마크 해제
        logs[1] = logs[1].copy(isBookmarked = false)
        assertFalse(logs[1].isBookmarked)
    }

    @Test
    fun `log level filter state management`() {
        // 레벨 필터 상태 관리
        val levelFilters = mutableMapOf(
            LogLevel.TRACE to true,
            LogLevel.DEBUG to true,
            LogLevel.INFO to true,
            LogLevel.WARN to true,
            LogLevel.ERROR to true,
            LogLevel.FATAL to true
        )

        val logs = listOf(
            LogEntry(1, level = LogLevel.DEBUG),
            LogEntry(2, level = LogLevel.INFO),
            LogEntry(3, level = LogLevel.ERROR)
        )

        // 기본: 모든 레벨 표시
        var filtered = logs.filter { levelFilters[it.level] == true }
        assertEquals(3, filtered.size)

        // DEBUG 해제
        levelFilters[LogLevel.DEBUG] = false
        filtered = logs.filter { levelFilters[it.level] == true }
        assertEquals(2, filtered.size)

        // ERROR 해제
        levelFilters[LogLevel.ERROR] = false
        filtered = logs.filter { levelFilters[it.level] == true }
        assertEquals(1, filtered.size)
        assertEquals(LogLevel.INFO, filtered[0].level)
    }

    @Test
    fun `combined filters should work together`() {
        val logs = listOf(
            LogEntry(1, level = LogLevel.INFO, className = "UserService", message = "User created"),
            LogEntry(2, level = LogLevel.DEBUG, className = "UserService", message = "Debug info"),
            LogEntry(3, level = LogLevel.ERROR, className = "OrderService", message = "Order failed"),
            LogEntry(4, level = LogLevel.INFO, className = "OrderService", message = "Order created"),
            LogEntry(5, level = LogLevel.WARN, className = "UserService", message = "User warning")
        )

        // 복합 필터: INFO/WARN + UserService + "User" 키워드
        val filtered = logs.filter { entry ->
            // 레벨 필터
            entry.level in listOf(LogLevel.INFO, LogLevel.WARN) &&
            // 클래스 필터
            entry.className.contains("UserService") &&
            // 키워드 필터
            entry.message.contains("User")
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.className == "UserService" })
    }

    @Test
    fun `log parser integration with filter`() {
        val parser = LogParser()

        val logLines = listOf(
            "2024-01-15 10:30:45.123  INFO [main] c.e.d.UserService - User login successful",
            "2024-01-15 10:30:46.456 DEBUG [main] c.e.d.UserService - Processing request",
            "2024-01-15 10:30:47.789 ERROR [http-nio-8080-exec-1] c.e.d.OrderService - Order failed",
            "java.lang.NullPointerException: null",
            "    at com.example.OrderService.process(OrderService.java:42)",
            "2024-01-15 10:30:48.012  INFO [main] c.e.d.UserService - User logout"
        )

        val entries = parser.parseFile(logLines)

        // 스택트레이스가 연결되어야 함
        assertEquals(4, entries.size)

        // ERROR 로그에 스택트레이스가 있어야 함
        val errorEntry = entries.find { it.level == LogLevel.ERROR }
        assertNotNull(errorEntry)
        assertTrue(errorEntry!!.hasStackTrace)

        // UserService 로그만 필터링
        val userServiceLogs = entries.filter {
            it.className.contains("UserService")
        }
        assertEquals(3, userServiceLogs.size)
    }

    @Test
    fun `navigation between bookmarks`() {
        val logs = listOf(
            LogEntry(1, message = "First", isBookmarked = false),
            LogEntry(2, message = "Second", isBookmarked = true),  // 북마크
            LogEntry(3, message = "Third", isBookmarked = false),
            LogEntry(4, message = "Fourth", isBookmarked = true),  // 북마크
            LogEntry(5, message = "Fifth", isBookmarked = false),
            LogEntry(6, message = "Sixth", isBookmarked = true)    // 북마크
        )

        val bookmarkIndices = logs.mapIndexedNotNull { index, entry ->
            if (entry.isBookmarked) index else null
        }

        assertEquals(listOf(1, 3, 5), bookmarkIndices)

        // 현재 인덱스 0에서 다음 북마크 찾기
        var currentIndex = 0
        var nextBookmark = bookmarkIndices.firstOrNull { it > currentIndex } ?: bookmarkIndices.first()
        assertEquals(1, nextBookmark)

        // 현재 인덱스 2에서 다음 북마크 찾기
        currentIndex = 2
        nextBookmark = bookmarkIndices.firstOrNull { it > currentIndex } ?: bookmarkIndices.first()
        assertEquals(3, nextBookmark)

        // 현재 인덱스 5에서 다음 북마크 찾기 (래핑)
        currentIndex = 5
        nextBookmark = bookmarkIndices.firstOrNull { it > currentIndex } ?: bookmarkIndices.first()
        assertEquals(1, nextBookmark)  // 래핑되어 첫 번째 북마크로

        // 현재 인덱스 3에서 이전 북마크 찾기
        currentIndex = 3
        val prevBookmark = bookmarkIndices.lastOrNull { it < currentIndex } ?: bookmarkIndices.last()
        assertEquals(1, prevBookmark)
    }
}
