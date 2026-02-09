package com.logfilter.view

import com.logfilter.controller.MainController
import com.logfilter.model.LogLevel
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.canvas.Canvas
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import tornadofx.*

/**
 * 로그 인디케이터 패널 - 전체 로그의 축소판으로 빠른 이동 지원
 * 좌측: 하이라이트/북마크, 우측: 에러/경고
 */
class IndicatorPanel : View() {

    private val controller: MainController by inject()

    private val canvas = Canvas(60.0, 400.0)
    private val showBookmarks = SimpleBooleanProperty(true)
    private val showErrors = SimpleBooleanProperty(true)

    var onNavigate: ((Int) -> Unit)? = null

    private val topOffset = 35.0
    private val bottomOffset = 10.0

    override val root = vbox {
        prefWidth = 70.0
        padding = Insets(5.0)
        style = "-fx-background-color: #2D2D2D; -fx-border-color: #444444; -fx-border-width: 0 1 0 0;"

        hbox {
            spacing = 5.0
            checkbox("★") {
                isSelected = true
                selectedProperty().bindBidirectional(showBookmarks)
                style = "-fx-font-size: 11px; -fx-text-fill: #FFD700;"
                action { redraw() }
            }

            checkbox("E") {
                isSelected = true
                selectedProperty().bindBidirectional(showErrors)
                style = "-fx-font-size: 11px; -fx-text-fill: #FF5252;"
                action { redraw() }
            }
        }

        add(canvas)

        // 캔버스 크기를 부모에 맞춤
        heightProperty().onChange { newHeight ->
            canvas.height = maxOf(100.0, newHeight - topOffset - bottomOffset)
            canvas.width = 60.0
            redraw()
        }
    }

    init {
        // 로그 변경 시 다시 그리기
        controller.filteredLogs.addListener(ListChangeListener { redraw() })

        // 마우스 이벤트
        canvas.setOnMousePressed { handleMouseEvent(it) }
        canvas.setOnMouseDragged { handleMouseEvent(it) }

        // 초기 그리기
        redraw()
    }

    private fun handleMouseEvent(event: MouseEvent) {
        val logs = controller.filteredLogs
        if (logs.isEmpty()) return

        val ratio = event.y / canvas.height
        val index = (logs.size * ratio).toInt().coerceIn(0, logs.size - 1)
        onNavigate?.invoke(index)
    }

    fun redraw() {
        val gc = canvas.graphicsContext2D
        val logs = controller.filteredLogs
        val width = canvas.width
        val height = canvas.height
        val halfWidth = width / 2 - 1  // 중앙 분리선을 위한 여백

        // 배경 (어두운 테마)
        gc.fill = Color.web("#1E1E1E")
        gc.fillRect(0.0, 0.0, width, height)

        if (logs.isEmpty()) {
            // 빈 상태 표시
            gc.fill = Color.web("#555555")
            gc.fillText("No", 5.0, height / 2 - 8)
            gc.fillText("Logs", 5.0, height / 2 + 8)
            return
        }

        val logCount = logs.size.toDouble()
        val markerHeight = maxOf(2.0, height / logCount)

        // ===== 좌측 영역: 하이라이트 & 북마크 =====
        if (showBookmarks.value) {
            logs.forEachIndexed { index, entry ->
                val y = (index / logCount) * height

                // 하이라이트 (노란색) - 우선 표시
                if (entry.isHighlighted) {
                    gc.fill = Color.web("#FFEE58", 0.95)
                    gc.fillRect(1.0, y, halfWidth - 1, maxOf(3.0, markerHeight))
                }
                // 북마크 (파란색)
                else if (entry.isBookmarked) {
                    gc.fill = Color.web("#42A5F5", 0.9)
                    gc.fillRect(1.0, y, halfWidth - 1, maxOf(3.0, markerHeight))
                }
            }
        }

        // ===== 우측 영역: 에러/경고 =====
        if (showErrors.value) {
            logs.forEachIndexed { index, entry ->
                val color = when (entry.level) {
                    LogLevel.ERROR, LogLevel.FATAL -> Color.web("#FF5252", 0.95)
                    LogLevel.WARN -> Color.web("#FFB74D", 0.85)
                    else -> null
                }
                color?.let {
                    gc.fill = it
                    val y = (index / logCount) * height
                    gc.fillRect(halfWidth + 2, y, halfWidth - 1, maxOf(3.0, markerHeight))
                }
            }
        }

        // 중앙 분리선
        gc.stroke = Color.web("#444444")
        gc.lineWidth = 1.0
        gc.strokeLine(width / 2, 0.0, width / 2, height)

        // 외곽 테두리
        gc.stroke = Color.web("#555555")
        gc.lineWidth = 1.0
        gc.strokeRect(0.0, 0.0, width, height)
    }
}
