package com.logfilter.cli

import com.logfilter.controller.MainController
import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel
import com.logfilter.view.MainView
import javafx.application.Application
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.stage.Stage
import tornadofx.*
import java.io.File
import javax.imageio.ImageIO

/**
 * UI 스크린샷 캡처 러너
 * 헤드리스 환경에서 UI를 렌더링하고 스크린샷을 저장
 */
class ScreenshotApp : App(MainView::class) {

    override fun start(stage: Stage) {
        super.start(stage)

        stage.width = 1280.0
        stage.height = 720.0
        stage.title = "K8s Log Viewer v2.0 - Screenshot Mode"

        // 샘플 데이터 로드
        Platform.runLater {
            loadSampleData()

            // 약간의 지연 후 스크린샷 캡처
            Thread {
                Thread.sleep(2000)  // UI 렌더링 대기
                Platform.runLater {
                    captureScreenshot(stage.scene, "ui-screenshot.png")
                    println("스크린샷 저장 완료: ui-screenshot.png")

                    // 추가 스크린샷 (필터 적용 후)
                    applyFilter()
                    Thread {
                        Thread.sleep(500)
                        Platform.runLater {
                            captureScreenshot(stage.scene, "ui-filtered.png")
                            println("필터 적용 스크린샷 저장: ui-filtered.png")
                            Platform.exit()
                        }
                    }.start()
                }
            }.start()
        }
    }

    private fun loadSampleData() {
        val controller = find<MainController>()

        // 샘플 로그 데이터 추가
        val sampleLogs = listOf(
            LogEntry(1, "2024-01-15 10:30:45.123", LogLevel.INFO, "main", "o.s.b.SpringApplication", "Starting K8sLogViewerApplication v2.0.0"),
            LogEntry(2, "2024-01-15 10:30:45.456", LogLevel.DEBUG, "main", "o.s.c.a.AnnotationConfig", "Refreshing ApplicationContext"),
            LogEntry(3, "2024-01-15 10:30:46.789", LogLevel.INFO, "main", "o.s.b.w.e.t.TomcatWebServer", "Tomcat initialized with port(s): 8080"),
            LogEntry(4, "2024-01-15 10:30:47.012", LogLevel.WARN, "http-nio-8080-exec-1", "c.e.d.UserController", "User not found: userId=12345"),
            LogEntry(5, "2024-01-15 10:30:48.345", LogLevel.ERROR, "http-nio-8080-exec-2", "c.e.d.OrderService", "Failed to process order",
                stackTrace = "java.lang.NullPointerException: Order item cannot be null\n    at com.example.OrderService.process(OrderService.java:42)"),
            LogEntry(6, "2024-01-15 10:30:49.456", LogLevel.INFO, "main", "o.s.b.SpringApplication", "Application started in 4.5 seconds"),
            LogEntry(7, "2024-01-15 10:30:50.567", LogLevel.DEBUG, "async-1", "c.e.d.CacheService", "Cache refreshed: 150 entries"),
            LogEntry(8, "2024-01-15 10:30:51.678", LogLevel.TRACE, "scheduler-1", "c.e.d.HealthCheck", "Health check completed"),
            LogEntry(9, "2024-01-15 10:30:52.789", LogLevel.INFO, "http-nio-8080-exec-3", "c.e.d.ProductController", "Product list requested"),
            LogEntry(10, "2024-01-15 10:30:53.890", LogLevel.FATAL, "main", "c.e.d.DatabaseConnection", "Database connection lost!")
        )

        @Suppress("UNCHECKED_CAST")
        val source = controller.filteredLogs.source as javafx.collections.ObservableList<LogEntry>
        source.addAll(sampleLogs)

        controller.status = "샘플 데이터 로드됨: ${sampleLogs.size}줄"
    }

    private fun applyFilter() {
        val controller = find<MainController>()
        controller.levelFilters[LogLevel.DEBUG]?.value = false
        controller.levelFilters[LogLevel.TRACE]?.value = false
        controller.applyFilters()
    }

    private fun captureScreenshot(scene: Scene, filename: String) {
        try {
            val image = scene.snapshot(null)
            val bufferedImage = SwingFXUtils.fromFXImage(image, null)
            val file = File(filename)
            ImageIO.write(bufferedImage, "png", file)
            println("스크린샷 저장: ${file.absolutePath}")
        } catch (e: Exception) {
            println("스크린샷 캡처 실패: ${e.message}")
        }
    }
}

fun main(args: Array<String>) {
    println("UI 스크린샷 캡처 모드 시작...")

    // Headless 모드 설정
    System.setProperty("prism.order", "sw")
    System.setProperty("prism.text", "t2k")
    System.setProperty("java.awt.headless", "false")  // 스크린샷은 headless=false 필요

    Application.launch(ScreenshotApp::class.java, *args)
}
