package com.logfilter.util

import java.io.File
import java.util.Properties

/**
 * 애플리케이션 설정 관리
 */
object Settings {
    private val properties = Properties()
    private val configFile = File(System.getProperty("user.home"), ".k8slogviewer/config.properties")

    // 기본값
    var fontFamily: String = "Monospaced"
    var fontSize: Int = 12
    var windowWidth: Int = 1280
    var windowHeight: Int = 720

    var lastContext: String = ""
    var lastNamespace: String = "default"
    var lastPod: String = ""

    var wordFind: String = ""
    var wordRemove: String = ""
    var classShow: String = ""
    var classRemove: String = ""
    var highlight: String = ""

    var kubectlPath: String = "kubectl"

    init {
        load()
    }

    fun load() {
        try {
            if (configFile.exists()) {
                configFile.inputStream().use { properties.load(it) }

                fontFamily = properties.getProperty("FONT_TYPE", fontFamily)
                fontSize = properties.getProperty("FONT_SIZE", fontSize.toString()).toIntOrNull() ?: fontSize
                windowWidth = properties.getProperty("WINDOW_WIDTH", windowWidth.toString()).toIntOrNull() ?: windowWidth
                windowHeight = properties.getProperty("WINDOW_HEIGHT", windowHeight.toString()).toIntOrNull() ?: windowHeight

                lastContext = properties.getProperty("LAST_CONTEXT", lastContext)
                lastNamespace = properties.getProperty("LAST_NAMESPACE", lastNamespace)
                lastPod = properties.getProperty("LAST_POD", lastPod)

                wordFind = properties.getProperty("WORD_FIND", wordFind)
                wordRemove = properties.getProperty("WORD_REMOVE", wordRemove)
                classShow = properties.getProperty("CLZ_SHOW", classShow)
                classRemove = properties.getProperty("CLZ_REMOVE", classRemove)
                highlight = properties.getProperty("HIGHLIGHT", highlight)

                kubectlPath = properties.getProperty("KUBECTL_PATH", kubectlPath)
            }
        } catch (e: Exception) {
            println("설정 로드 실패: ${e.message}")
        }
    }

    fun save() {
        try {
            configFile.parentFile?.mkdirs()

            properties.setProperty("FONT_TYPE", fontFamily)
            properties.setProperty("FONT_SIZE", fontSize.toString())
            properties.setProperty("WINDOW_WIDTH", windowWidth.toString())
            properties.setProperty("WINDOW_HEIGHT", windowHeight.toString())

            properties.setProperty("LAST_CONTEXT", lastContext)
            properties.setProperty("LAST_NAMESPACE", lastNamespace)
            properties.setProperty("LAST_POD", lastPod)

            properties.setProperty("WORD_FIND", wordFind)
            properties.setProperty("WORD_REMOVE", wordRemove)
            properties.setProperty("CLZ_SHOW", classShow)
            properties.setProperty("CLZ_REMOVE", classRemove)
            properties.setProperty("HIGHLIGHT", highlight)

            properties.setProperty("KUBECTL_PATH", kubectlPath)

            configFile.outputStream().use {
                properties.store(it, "K8s Log Viewer Settings")
            }
        } catch (e: Exception) {
            println("설정 저장 실패: ${e.message}")
        }
    }
}
