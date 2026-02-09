package com.logfilter.model

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.*

/**
 * 로그 레벨 enum
 */
enum class LogLevel(val displayName: String, val color: String) {
    TRACE("TRACE", "#808080"),
    DEBUG("DEBUG", "#0000AA"),
    INFO("INFO", "#008800"),
    WARN("WARN", "#FF9A00"),
    ERROR("ERROR", "#FF0000"),
    FATAL("FATAL", "#CC0000"),
    UNKNOWN("", "#000000");

    companion object {
        fun fromString(level: String?): LogLevel {
            if (level == null) return UNKNOWN
            return entries.find { it.displayName.equals(level.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * 로그 엔트리 데이터 클래스
 */
data class LogEntry(
    val lineNumber: Int,
    val dateTime: String = "",
    val level: LogLevel = LogLevel.UNKNOWN,
    val thread: String = "",
    val className: String = "",
    val message: String = "",
    val stackTrace: String? = null,
    var isBookmarked: Boolean = false,
    var isHighlighted: Boolean = false,
    var bookmarkLabel: String = ""
) {
    val hasStackTrace: Boolean get() = !stackTrace.isNullOrEmpty()
}

/**
 * TornadoFX용 LogEntry 모델 래퍼
 */
class LogEntryModel : ItemViewModel<LogEntry>() {
    val lineNumber = bind(LogEntry::lineNumber)
    val dateTime = bind(LogEntry::dateTime)
    val level = bind(LogEntry::level)
    val thread = bind(LogEntry::thread)
    val className = bind(LogEntry::className)
    val message = bind(LogEntry::message)
    val stackTrace = bind(LogEntry::stackTrace)
    val isBookmarked = bind(LogEntry::isBookmarked)
}
