package com.logfilter

import com.logfilter.controller.MainController
import com.logfilter.util.Settings
import com.logfilter.view.MainView
import com.logfilter.view.Styles
import javafx.stage.Stage
import tornadofx.*

/**
 * K8s Log Viewer 메인 애플리케이션
 */
class K8sLogViewerApp : App(MainView::class, Styles::class) {

    private val controller: MainController by inject()

    override fun start(stage: Stage) {
        stage.width = Settings.windowWidth.toDouble()
        stage.height = Settings.windowHeight.toDouble()

        stage.setOnCloseRequest {
            Settings.windowWidth = stage.width.toInt()
            Settings.windowHeight = stage.height.toInt()
            controller.cleanup()
        }

        super.start(stage)
    }
}

fun main(args: Array<String>) {
    launch<K8sLogViewerApp>(args)
}
