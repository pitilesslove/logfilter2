package com.logfilter.view

import com.logfilter.controller.MainController
import com.logfilter.model.LogLevel
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.TitledPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import tornadofx.*

/**
 * 필터 패널 - 필드셋 스타일 그룹화
 */
class FilterView : View() {

    private val controller: MainController by inject()
    private val logTableView: LogTableView by inject()

    override val root = hbox {
        addClass(Styles.filterPanel)
        spacing = 10.0
        padding = Insets(5.0, 10.0, 5.0, 10.0)

        // 필터 ON/OFF 체크박스
        checkbox("필터") {
            selectedProperty().bindBidirectional(controller.filterEnabledProperty)
            style = "-fx-font-weight: bold;"
        }

        // Word Filter 그룹
        titledpane("Word filter") {
            isCollapsible = false
            content = vbox {
                spacing = 3.0
                padding = Insets(3.0)

                hbox {
                    spacing = 5.0
                    alignment = Pos.CENTER_LEFT
                    label("Find:") { prefWidth = 55.0 }
                    textfield(controller.wordFindProperty) {
                        prefWidth = 150.0
                        promptText = "검색어 (| 로 구분)"
                    }
                    checkbox {
                        selectedProperty().bindBidirectional(controller.wordFindEnabledProperty)
                    }
                }
                hbox {
                    spacing = 5.0
                    alignment = Pos.CENTER_LEFT
                    label("Remove:") { prefWidth = 55.0 }
                    textfield(controller.wordRemoveProperty) {
                        prefWidth = 150.0
                        promptText = "제외어 (| 로 구분)"
                    }
                    checkbox {
                        selectedProperty().bindBidirectional(controller.wordRemoveEnabledProperty)
                    }
                }
            }
        }

        // Class Filter 그룹
        titledpane("Class filter") {
            isCollapsible = false
            content = vbox {
                spacing = 3.0
                padding = Insets(3.0)

                hbox {
                    spacing = 5.0
                    alignment = Pos.CENTER_LEFT
                    label("Show:") { prefWidth = 55.0 }
                    textfield(controller.classShowProperty) {
                        prefWidth = 120.0
                        promptText = "표시할 클래스"
                    }
                    checkbox {
                        selectedProperty().bindBidirectional(controller.classShowEnabledProperty)
                    }
                }
                hbox {
                    spacing = 5.0
                    alignment = Pos.CENTER_LEFT
                    label("Remove:") { prefWidth = 55.0 }
                    textfield(controller.classRemoveProperty) {
                        prefWidth = 120.0
                        promptText = "제외할 클래스"
                    }
                    checkbox {
                        selectedProperty().bindBidirectional(controller.classRemoveEnabledProperty)
                    }
                }
            }
        }

        // Highlight 그룹
        titledpane("Highlight") {
            isCollapsible = false
            content = hbox {
                spacing = 5.0
                padding = Insets(3.0)
                alignment = Pos.CENTER_LEFT

                label("Keyword:")
                textfield(controller.highlightProperty) {
                    prefWidth = 120.0
                    promptText = "하이라이트 키워드"
                    textProperty().onChange { controller.applyHighlights() }
                }
                checkbox {
                    selectedProperty().bindBidirectional(controller.highlightEnabledProperty)
                    selectedProperty().onChange { controller.applyHighlights() }
                }
            }
        }

        // Log Level Filter 그룹
        titledpane("Log filter") {
            isCollapsible = false
            content = hbox {
                spacing = 8.0
                padding = Insets(3.0)
                alignment = Pos.CENTER_LEFT

                LogLevel.entries.filter { it != LogLevel.UNKNOWN }.forEach { level ->
                    checkbox(level.displayName) {
                        selectedProperty().bindBidirectional(
                            controller.levelFilters[level]!!
                        )
                        style {
                            textFill = c(level.color)
                        }
                    }
                }
            }
        }

        // Font 설정 그룹
        titledpane("Font") {
            isCollapsible = false
            content = hbox {
                spacing = 8.0
                padding = Insets(3.0)
                alignment = Pos.CENTER_LEFT

                label("Size:")
                spinner(8, 24, logTableView.fontSize.toInt()) {
                    prefWidth = 60.0
                    isEditable = true
                    valueProperty().onChange { value ->
                        if (value != null) {
                            logTableView.fontSize = value.toDouble()
                        }
                    }
                }

                separator { }

                label("Goto:")
                textfield {
                    prefWidth = 60.0
                    promptText = "라인"
                    action {
                        text.toIntOrNull()?.let { lineNumber ->
                            val index = controller.filteredLogs.indexOfFirst { it.lineNumber == lineNumber }
                            if (index >= 0) {
                                logTableView.scrollToIndex(index)
                                controller.status = "라인 $lineNumber 로 이동"
                            } else {
                                controller.status = "라인 $lineNumber 을 찾을 수 없음"
                            }
                            text = ""
                        }
                    }
                }
            }
        }

        spacer()
    }
}
