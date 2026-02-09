package com.logfilter.view

import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

/**
 * TornadoFX 스타일 정의
 */
class Styles : Stylesheet() {
    companion object {
        val logTable by cssclass()
        val bookmarkedRow by cssclass()
        val errorRow by cssclass()
        val warnRow by cssclass()
        val filterPanel by cssclass()
        val k8sPanel by cssclass()
        val controlPanel by cssclass()
        val statusBar by cssclass()

        // 색상 정의
        val traceColor = c("#808080")
        val debugColor = c("#0000AA")
        val infoColor = c("#008800")
        val warnColor = c("#FF9A00")
        val errorColor = c("#FF0000")
        val fatalColor = c("#CC0000")
    }

    init {
        logTable {
            fontFamily = "Monospaced"
            fontSize = 12.px

            tableRowCell {
                and(bookmarkedRow) {
                    backgroundColor += c("#E3F2FD")
                }
                and(errorRow) {
                    backgroundColor += c("#FFEBEE")
                }
                and(warnRow) {
                    backgroundColor += c("#FFF8E1")
                }
            }
        }

        filterPanel {
            padding = box(5.px)
            spacing = 10.px
            backgroundColor += c("#F5F5F5")
        }

        k8sPanel {
            padding = box(5.px)
            spacing = 10.px
            backgroundColor += c("#E8E8E8")
        }

        controlPanel {
            padding = box(5.px)
            spacing = 10.px
        }

        statusBar {
            padding = box(3.px, 10.px)
            backgroundColor += c("#E0E0E0")
            fontSize = 11.px
        }

        textField {
            minWidth = 150.px
        }

        button {
            minWidth = 60.px
        }

        comboBox {
            minWidth = 120.px
        }

        checkBox {
            padding = box(0.px, 5.px)
        }

        label {
            padding = box(0.px, 5.px)
        }

        // TitledPane을 fieldset 스타일로 표시
        titledPane {
            padding = box(0.px)

            title {
                padding = box(2.px, 5.px)
                fontSize = 11.px
                fontWeight = FontWeight.BOLD
            }

            content {
                padding = box(5.px)
            }
        }
    }
}
