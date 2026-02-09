package com.logfilter

import com.logfilter.model.LogLevel
import com.logfilter.service.LogParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LogParserTest {

    private val parser = LogParser()

    @Test
    fun `parse standard logback format`() {
        val line = "2024-01-15 10:30:45.123  INFO [main] o.s.b.SpringApplication - Starting application"
        val entry = parser.parse(line, 1)

        assertEquals(1, entry.lineNumber)
        assertEquals("2024-01-15 10:30:45.123", entry.dateTime)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("main", entry.thread)
        assertEquals("o.s.b.SpringApplication", entry.className)
        assertEquals("Starting application", entry.message)
    }

    @Test
    fun `parse DEBUG level`() {
        val line = "2024-01-15 10:30:45.456 DEBUG [main] o.s.c.a.Context - Refreshing context"
        val entry = parser.parse(line, 2)

        assertEquals(LogLevel.DEBUG, entry.level)
        assertEquals("Refreshing context", entry.message)
    }

    @Test
    fun `parse WARN level`() {
        val line = "2024-01-15 10:30:46.789  WARN [http-nio-8080-exec-1] c.e.d.MyController - Slow response detected"
        val entry = parser.parse(line, 3)

        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("http-nio-8080-exec-1", entry.thread)
    }

    @Test
    fun `parse ERROR level`() {
        val line = "2024-01-15 10:30:47.012 ERROR [http-nio-8080-exec-2] c.e.d.MyService - Exception occurred"
        val entry = parser.parse(line, 4)

        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("c.e.d.MyService", entry.className)
    }

    @Test
    fun `parse TRACE level`() {
        val line = "2024-01-15 10:30:57.890 TRACE [http-nio-8080-exec-6] c.e.d.f.LoggingFilter - Request headers"
        val entry = parser.parse(line, 5)

        assertEquals(LogLevel.TRACE, entry.level)
    }

    @Test
    fun `parse FATAL level`() {
        val line = "2024-01-15 10:31:05.678 FATAL [main] c.e.d.Application - Application startup failed"
        val entry = parser.parse(line, 6)

        assertEquals(LogLevel.FATAL, entry.level)
    }

    @Test
    fun `parse non-matching line returns message only`() {
        val line = "This is a random log line without format"
        val entry = parser.parse(line, 7)

        assertEquals(7, entry.lineNumber)
        assertEquals("This is a random log line without format", entry.message)
        assertEquals(LogLevel.UNKNOWN, entry.level)
        assertTrue(entry.dateTime.isEmpty())
    }

    @Test
    fun `detect stack trace line starting with at`() {
        val line = "    at com.example.demo.MyService.process(MyService.java:42)"
        assertTrue(parser.isStackTraceLine(line))
    }

    @Test
    fun `detect stack trace line starting with Caused by`() {
        val line = "Caused by: java.lang.IllegalArgumentException: Invalid value"
        assertTrue(parser.isStackTraceLine(line))
    }

    @Test
    fun `detect stack trace line with more`() {
        val line = "    ... 10 more"
        assertTrue(parser.isStackTraceLine(line))
    }

    @Test
    fun `normal log line is not stack trace`() {
        val line = "2024-01-15 10:30:45.123  INFO [main] o.s.b.SpringApplication - Starting"
        assertFalse(parser.isStackTraceLine(line))
    }

    @Test
    fun `parse file with stack trace`() {
        val lines = listOf(
            "2024-01-15 10:30:53.456 ERROR [http-nio-8080-exec-4] c.e.d.s.OrderService - Failed to process order",
            "java.lang.NullPointerException: Order item cannot be null",
            "    at com.example.demo.service.OrderService.validateOrder(OrderService.java:42)",
            "    at com.example.demo.service.OrderService.processOrder(OrderService.java:28)",
            "2024-01-15 10:30:54.567  INFO [http-nio-8080-exec-5] c.e.d.c.ProductController - Product list requested"
        )

        val entries = parser.parseFile(lines)

        assertEquals(2, entries.size)

        // 첫 번째 엔트리 (ERROR with stack trace)
        val errorEntry = entries[0]
        assertEquals(LogLevel.ERROR, errorEntry.level)
        assertEquals("Failed to process order", errorEntry.message)
        assertNotNull(errorEntry.stackTrace)
        assertTrue(errorEntry.stackTrace!!.contains("NullPointerException"))
        assertTrue(errorEntry.stackTrace!!.contains("validateOrder"))

        // 두 번째 엔트리 (INFO)
        val infoEntry = entries[1]
        assertEquals(LogLevel.INFO, infoEntry.level)
        assertEquals("Product list requested", infoEntry.message)
        assertNull(infoEntry.stackTrace)
    }

    @Test
    fun `LogLevel fromString works correctly`() {
        assertEquals(LogLevel.INFO, LogLevel.fromString("INFO"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromString("debug"))
        assertEquals(LogLevel.ERROR, LogLevel.fromString("Error"))
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromString("INVALID"))
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromString(null))
    }

    @Test
    fun `parse empty line`() {
        val entry = parser.parse("", 1)
        assertEquals("", entry.message)
        assertEquals(LogLevel.UNKNOWN, entry.level)
    }

    @Test
    fun `parse UMS custom log format`() {
        val line = "[2026-02-06 08:42:32,921 06699][INFO ][.RefreshTokenService][]|auth. Refresh token saved successfully"
        val entry = parser.parse(line, 1)

        assertEquals(1, entry.lineNumber)
        assertEquals("2026-02-06 08:42:32.921", entry.dateTime)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("RefreshTokenService", entry.className)
        assertTrue(entry.message.contains("Refresh token saved"))
    }

    @Test
    fun `parse UMS ERROR level`() {
        val line = "[2026-02-06 08:42:32,934 06712][ERROR][henticationFilterNew][]|auth. Authentication failed"
        val entry = parser.parse(line, 1)

        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("henticationFilterNew", entry.className)
    }

    @Test
    fun `parse UMS WARN level`() {
        val line = "[2026-02-06 08:42:32,937 06715][WARN ][henticationFilterNew][]|auth. Warning message"
        val entry = parser.parse(line, 1)

        assertEquals(LogLevel.WARN, entry.level)
    }

    @Test
    fun `parse UMS DEBUG level`() {
        val line = "[2026-02-06 08:42:32,938 06716][DEBUG][a.u.a.s.LoginService][]|auth. Debug info"
        val entry = parser.parse(line, 1)

        assertEquals(LogLevel.DEBUG, entry.level)
        assertEquals("a.u.a.s.LoginService", entry.className)
    }
}
