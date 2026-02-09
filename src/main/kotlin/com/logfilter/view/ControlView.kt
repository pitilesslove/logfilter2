package com.logfilter.view

import com.logfilter.controller.MainController
import tornadofx.*

/**
 * 컨트롤 패널 (시작/중지/클리어 버튼)
 */
class ControlView : View() {

    private val controller: MainController by inject()

    override val root = hbox {
        addClass(Styles.controlPanel)
        spacing = 10.0

        button("Start") {
            enableWhen(controller.isRunningProperty.not())
            action { controller.startLogStream() }
            style {
                textFill = c("#008800")
            }
        }

        button("Stop") {
            enableWhen(controller.isRunningProperty)
            action { controller.stopLogStream() }
            style {
                textFill = c("#FF0000")
            }
        }

        button("Clear") {
            action { controller.clearLogs() }
        }

        separator { }

        label {
            textProperty().bind(
                controller.isRunningProperty.stringBinding { running ->
                    if (running == true) "● 스트리밍 중" else "○ 대기"
                }
            )
            style {
                controller.isRunningProperty.onChange { running ->
                    textFill = if (running) c("#008800") else c("#808080")
                }
            }
        }

    }
}
