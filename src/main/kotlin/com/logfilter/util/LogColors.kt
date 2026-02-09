package com.logfilter.util

import com.logfilter.model.LogLevel
import javafx.scene.paint.Color

/**
 * 로그 레벨별 색상 관리
 */
object LogColors {
    private val colorMap = mutableMapOf(
        LogLevel.TRACE to Color.GRAY,
        LogLevel.DEBUG to Color.web("#0000AA"),
        LogLevel.INFO to Color.web("#008800"),
        LogLevel.WARN to Color.web("#FF9A00"),
        LogLevel.ERROR to Color.RED,
        LogLevel.FATAL to Color.web("#CC0000"),
        LogLevel.UNKNOWN to Color.BLACK
    )

    fun getColor(level: LogLevel): Color {
        return colorMap[level] ?: Color.BLACK
    }

    fun getColorHex(level: LogLevel): String {
        return level.color
    }

    fun setColor(level: LogLevel, color: Color) {
        colorMap[level] = color
    }
}
