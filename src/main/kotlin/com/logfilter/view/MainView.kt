package com.logfilter.view

import com.logfilter.controller.MainController
import javafx.stage.FileChooser
import tornadofx.*

/**
 * 메인 화면
 */
class MainView : View("K8s Log Viewer v2.0") {

    private val controller: MainController by inject()
    private val logTableView = find<LogTableView>()
    private val indicatorPanel = find<IndicatorPanel>()

    override val root = borderpane {
        prefWidth = 1280.0
        prefHeight = 720.0

        top = vbox {
            // 메뉴바
            menubar {
                menu("파일") {
                    item("열기...", "Ctrl+O") {
                        action { openFile() }
                    }
                    separator()
                    item("종료", "Alt+F4") {
                        action { controller.cleanup(); primaryStage.close() }
                    }
                }
                menu("편집") {
                    item("로그 클리어", "Ctrl+L") {
                        action { controller.clearLogs() }
                    }
                    separator()
                    item("필터 적용", "Ctrl+F") {
                        action { controller.applyFilters() }
                    }
                }
                menu("보기") {
                    checkmenuitem("자동 스크롤") {
                        selectedProperty().bindBidirectional(logTableView.autoScrollProperty)
                    }
                    item("맨 아래로 이동 (End)") {
                        action { logTableView.enableAutoScroll() }
                    }
                    separator()
                    item("글꼴 크기 +", "Ctrl+Plus") {
                        action { logTableView.increaseFontSize() }
                    }
                    item("글꼴 크기 -", "Ctrl+Minus") {
                        action { logTableView.decreaseFontSize() }
                    }
                }
                menu("도움말") {
                    item("단축키...") {
                        action { showShortcuts() }
                    }
                    separator()
                    item("정보...") {
                        action { showAbout() }
                    }
                }
            }

            // K8s 선택, 필터, 컨트롤 패널
            this += find<K8sSelectView>()
            this += find<FilterView>()
            this += find<ControlView>()
        }

        // 왼쪽: 인디케이터 패널 (로그 축소판)
        left = indicatorPanel.root

        // 중앙: 로그 테이블
        center = logTableView.root

        bottom = hbox {
            addClass(Styles.statusBar)
            label(controller.statusProperty)
            spacer()
            label {
                textProperty().bind(logTableView.autoScrollProperty.stringBinding { auto ->
                    if (auto == true) "자동스크롤: ON" else "자동스크롤: OFF"
                })
                style = "-fx-padding: 0 10 0 0;"
            }
            label {
                textProperty().bind(
                    controller.filteredLogs.sizeProperty.stringBinding { size ->
                        "표시: ${size ?: 0}줄"
                    }
                )
            }
        }
    }

    init {
        // 초기 데이터 로드
        controller.loadContexts()

        // IndicatorPanel 클릭 시 LogTable 스크롤
        indicatorPanel.onNavigate = { index ->
            logTableView.scrollToIndex(index)
        }
    }

    private fun showShortcuts() {
        information(
            "단축키",
            """
            |[로그 탐색]
            |• 더블클릭: 하이라이트 토글
            |• F2: 이전 하이라이트로 이동
            |• F3: 다음 하이라이트로 이동
            |• Ctrl+B: 북마크 토글
            |• End: 자동 스크롤 활성화 & 맨 아래로
            |
            |[파일]
            |• Ctrl+O: 파일 열기
            |• Ctrl+L: 로그 클리어
            |
            |[보기]
            |• Ctrl++: 글꼴 크기 증가
            |• Ctrl+-: 글꼴 크기 감소
            |
            |[인디케이터 패널]
            |• 좌측 패널 클릭/드래그로 빠른 이동
            |• ★: 북마크 표시 토글
            |• E: 에러/경고 표시 토글
            """.trimMargin()
        )
    }

    private fun openFile() {
        val filters = arrayOf(
            FileChooser.ExtensionFilter("로그 파일", "*.log", "*.txt"),
            FileChooser.ExtensionFilter("모든 파일", "*.*")
        )
        chooseFile("로그 파일 열기", filters, mode = FileChooserMode.Single).firstOrNull()?.let {
            controller.loadFromFile(it)
        }
    }

    private fun showAbout() {
        information(
            "K8s Log Viewer",
            "K8s Log Viewer v2.0\n\n" +
            "Kubernetes Pod 로그 뷰어\n" +
            "Spring Boot Logback 형식 지원\n\n" +
            "Kotlin + TornadoFX"
        )
    }
}
