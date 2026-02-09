package com.logfilter.view

import com.logfilter.controller.MainController
import javafx.scene.control.ComboBox
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import tornadofx.*

/**
 * K8s 선택 패널 (Context, Namespace, Pod, Container) - 한 줄 레이아웃
 */
class K8sSelectView : View() {

    private val controller: MainController by inject()

    // Pod 검색용 버퍼
    private var searchBuffer = StringBuilder()
    private var lastKeyTime = 0L

    override val root = hbox {
        addClass(Styles.k8sPanel)
        spacing = 10.0

        label("Context:")
        combobox(controller.k8sConfig.currentContextProperty, controller.k8sConfig.contexts) {
            prefWidth = 180.0
            setOnAction {
                controller.loadNamespaces()
                controller.k8sConfig.pods.clear()
                controller.k8sConfig.podNames.clear()
            }
        }

        label("Namespace:")
        combobox(controller.k8sConfig.currentNamespaceProperty, controller.k8sConfig.namespaces) {
            prefWidth = 150.0
            setOnAction {
                controller.loadPods()
            }
        }

        label("Pod:")
        combobox<String> {
            prefWidth = 350.0
            items = controller.k8sConfig.podNames

            // 타이핑으로 Pod 검색/순회
            addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                handlePodSearch(this, event)
            }

            valueProperty().addListener { _, _, newValue ->
                if (newValue != null) {
                    val podName = controller.k8sConfig.getPodNameByDisplayName(newValue)
                    controller.k8sConfig.selectedPod = podName
                    controller.loadContainers()
                }
            }
        }

        // Container (여러 컨테이너가 있을 때만 표시)
        label("Container:") {
            visibleWhen(controller.k8sConfig.containersProperty.sizeProperty.greaterThan(1))
            managedWhen(visibleProperty())
        }
        combobox(controller.k8sConfig.selectedContainerProperty, controller.k8sConfig.containers) {
            prefWidth = 120.0
            visibleWhen(controller.k8sConfig.containersProperty.sizeProperty.greaterThan(1))
            managedWhen(visibleProperty())
        }

        spacer()

        button("🔄 Refresh") {
            action {
                controller.loadContexts()
                controller.loadNamespaces()
                controller.loadPods()
            }
        }

        button("🔗 Connect") {
            action {
                controller.connectToPod()
            }
            enableWhen(controller.k8sConfig.selectedPodProperty.isNotEmpty)
        }
    }

    /**
     * Pod ComboBox에서 키보드 입력으로 검색/순회
     */
    private fun handlePodSearch(comboBox: ComboBox<String>, event: KeyEvent) {
        val currentTime = System.currentTimeMillis()

        // 500ms 이상 지나면 검색 버퍼 초기화
        if (currentTime - lastKeyTime > 500) {
            searchBuffer.clear()
        }
        lastKeyTime = currentTime

        when {
            // 영문자/숫자/하이픈 입력 시 검색
            event.code.isLetterKey || event.code.isDigitKey || event.code == KeyCode.MINUS -> {
                searchBuffer.append(event.text.lowercase())
                findAndSelectPod(comboBox, searchBuffer.toString())
                event.consume()
            }
            // Backspace: 검색 버퍼에서 문자 삭제
            event.code == KeyCode.BACK_SPACE -> {
                if (searchBuffer.isNotEmpty()) {
                    searchBuffer.deleteCharAt(searchBuffer.length - 1)
                    if (searchBuffer.isNotEmpty()) {
                        findAndSelectPod(comboBox, searchBuffer.toString())
                    }
                }
                event.consume()
            }
            // Escape: 검색 버퍼 초기화
            event.code == KeyCode.ESCAPE -> {
                searchBuffer.clear()
            }
        }
    }

    /**
     * 검색어와 매칭되는 Pod 찾아서 선택
     */
    private fun findAndSelectPod(comboBox: ComboBox<String>, searchText: String) {
        val items = comboBox.items ?: return
        val currentIndex = comboBox.selectionModel.selectedIndex

        // 현재 선택 이후부터 검색
        val afterCurrent = items.drop(currentIndex + 1)
            .firstOrNull { it.lowercase().contains(searchText) }

        // 없으면 처음부터 검색
        val match = afterCurrent ?: items.firstOrNull { it.lowercase().contains(searchText) }

        match?.let {
            comboBox.selectionModel.select(it)

            // 드롭다운이 열려있으면 스크롤
            if (comboBox.isShowing) {
                val index = items.indexOf(it)
                // ListView 스크롤 (내부 API 사용)
                runLater {
                    comboBox.show()
                }
            }
        }
    }
}
