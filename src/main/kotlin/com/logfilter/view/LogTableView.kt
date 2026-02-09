package com.logfilter.view

import com.logfilter.controller.MainController
import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.control.TableCell
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import tornadofx.*

/**
 * 로그 테이블 뷰 - 자동 스크롤링 및 하이라이트 기능 포함
 */
class LogTableView : View() {

    private val controller: MainController by inject()

    private var tableView: TableView<LogEntry> by singleAssign()

    // 자동 스크롤 상태
    val autoScrollProperty = SimpleBooleanProperty(true)
    var autoScroll: Boolean by autoScrollProperty

    // 폰트 크기
    val fontSizeProperty = SimpleDoubleProperty(12.0)
    var fontSize: Double by fontSizeProperty

    // 사용자가 직접 상호작용 중인지 여부
    private var userInteracting = false

    override val root = tableview(controller.filteredLogs) {
        tableView = this
        addClass(Styles.logTable)

        // 선택 모드 - 다중 선택 활성화
        selectionModel.isCellSelectionEnabled = false
        selectionModel.selectionMode = javafx.scene.control.SelectionMode.MULTIPLE

        // 컬럼 정의
        column<LogEntry, Boolean>("★", "isBookmarked").apply {
            prefWidth = 30.0
            setCellFactory {
                object : TableCell<LogEntry, Boolean>() {
                    override fun updateItem(bookmarked: Boolean?, empty: Boolean) {
                        super.updateItem(bookmarked, empty)
                        text = if (empty || bookmarked != true) "" else "★"
                        alignment = Pos.CENTER
                        style = "-fx-text-fill: #FFD700;"
                    }
                }
            }
        }

        column<LogEntry, Int>("Line", "lineNumber").apply {
            prefWidth = 60.0
            setCellFactory {
                object : TableCell<LogEntry, Int>() {
                    override fun updateItem(line: Int?, empty: Boolean) {
                        super.updateItem(line, empty)
                        text = if (empty || line == null) "" else line.toString()
                        alignment = Pos.CENTER_RIGHT
                    }
                }
            }
        }

        column<LogEntry, String>("DateTime", "dateTime").apply {
            prefWidth = 180.0
        }

        column<LogEntry, LogLevel>("Level", "level").apply {
            prefWidth = 60.0
            setCellFactory {
                object : TableCell<LogEntry, LogLevel>() {
                    override fun updateItem(level: LogLevel?, empty: Boolean) {
                        super.updateItem(level, empty)
                        if (empty || level == null) {
                            text = ""
                            style = ""
                        } else {
                            text = level.displayName
                            alignment = Pos.CENTER
                            style = "-fx-text-fill: ${level.color}; -fx-font-weight: bold;"
                        }
                    }
                }
            }
        }

        column<LogEntry, String>("Thread", "thread").apply {
            prefWidth = 150.0
            setCellFactory {
                object : TableCell<LogEntry, String>() {
                    override fun updateItem(thread: String?, empty: Boolean) {
                        super.updateItem(thread, empty)
                        if (empty || thread == null) {
                            text = ""
                            graphic = null
                        } else {
                            text = thread
                            style = "-fx-text-fill: #6A5ACD; -fx-font-size: ${fontSize}px;"
                        }
                    }
                }
            }
        }

        column<LogEntry, String>("Class", "className").apply {
            prefWidth = 200.0
            setCellFactory {
                object : TableCell<LogEntry, String>() {
                    override fun updateItem(className: String?, empty: Boolean) {
                        super.updateItem(className, empty)
                        if (empty || className == null) {
                            text = ""
                            graphic = null
                        } else {
                            text = className
                            style = "-fx-text-fill: #2E8B57; -fx-font-size: ${fontSize}px;"
                        }
                    }
                }
            }
        }

        column<LogEntry, String>("Message", "message").apply {
            prefWidth = 600.0
            tableView.widthProperty().onChange {
                val usedWidth = tableView.columns.dropLast(1).sumOf { it.width }
                prefWidth = maxOf(600.0, tableView.width - usedWidth - 20)
            }
            setCellFactory {
                object : TableCell<LogEntry, String>() {
                    override fun updateItem(message: String?, empty: Boolean) {
                        super.updateItem(message, empty)
                        if (empty || message == null) {
                            text = null
                            graphic = null
                        } else {
                            text = null
                            graphic = createHighlightedText(message)
                        }
                    }
                }
            }
        }

        // 행 스타일링 (선택 시 가독성 개선)
        setRowFactory {
            object : TableRow<LogEntry>() {
                override fun updateItem(item: LogEntry?, empty: Boolean) {
                    super.updateItem(item, empty)

                    if (item == null || empty) {
                        style = ""
                    } else {
                        // 배경색 설정 (선택 시에도 텍스트가 보이도록)
                        val bgColor = when {
                            item.isHighlighted -> "#FFFF99"
                            item.isBookmarked -> "#E3F2FD"
                            item.level == LogLevel.ERROR || item.level == LogLevel.FATAL -> "#FFEBEE"
                            item.level == LogLevel.WARN -> "#FFF8E1"
                            else -> null
                        }
                        style = if (bgColor != null) {
                            "-fx-background-color: $bgColor; " +
                            "-fx-selection-bar: derive($bgColor, -20%); " +
                            "-fx-selection-bar-non-focused: derive($bgColor, -10%);"
                        } else {
                            ""
                        }
                    }
                }
            }
        }

        // 더블클릭으로 하이라이트 토글
        setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                selectedItem?.let { controller.toggleHighlight(it) }
            }
        }

        // 클릭 시 자동 스크롤 비활성화
        setOnMousePressed {
            userInteracting = true
            autoScroll = false
        }

        // 스크롤 시 자동 스크롤 비활성화
        setOnScroll {
            userInteracting = true
            autoScroll = false
        }

        // 키보드 단축키
        setOnKeyPressed { event ->
            when {
                // Ctrl+C: 선택 항목 복사
                event.code == KeyCode.C && event.isControlDown -> {
                    copySelectedToClipboard()
                    event.consume()
                }
                // Ctrl+B: 북마크 토글
                event.code == KeyCode.B && event.isControlDown -> {
                    selectedItem?.let { controller.toggleBookmark(it) }
                }
                // F2: 이전 하이라이트로 이동
                event.code == KeyCode.F2 -> {
                    navigateToHighlight(forward = false)
                }
                // F3: 다음 하이라이트로 이동
                event.code == KeyCode.F3 -> {
                    navigateToHighlight(forward = true)
                }
                // Ctrl+F2: 이전 북마크로 이동
                event.code == KeyCode.F2 && event.isControlDown -> {
                    navigateToBookmark(forward = false)
                }
                // Ctrl+F3: 다음 북마크로 이동
                event.code == KeyCode.F3 && event.isControlDown -> {
                    navigateToBookmark(forward = true)
                }
                // End: 자동 스크롤 다시 활성화
                event.code == KeyCode.END -> {
                    autoScroll = true
                    scrollToBottom()
                }
            }
        }

        // 컨텍스트 메뉴
        contextmenu {
            item("하이라이트 토글 (더블클릭)") {
                action { selectedItem?.let { controller.toggleHighlight(it) } }
            }
            item("북마크 토글 (Ctrl+B)") {
                action { selectedItem?.let { controller.toggleBookmark(it) } }
            }
            separator()
            item("다음 하이라이트 (F3)") {
                action { navigateToHighlight(forward = true) }
            }
            item("이전 하이라이트 (F2)") {
                action { navigateToHighlight(forward = false) }
            }
            separator()
            item("스택트레이스 보기") {
                action { showStackTrace() }
            }
            separator()
            item("선택 항목 복사 (Ctrl+C)") {
                action { copySelectedToClipboard() }
            }
            item("선택 항목 저장...") {
                action { saveSelectedToFile() }
            }
            separator()
            checkmenuitem("자동 스크롤") {
                selectedProperty().bindBidirectional(autoScrollProperty)
            }
        }
    }

    init {
        // 로그 추가 시 자동 스크롤
        controller.filteredLogs.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.wasAdded() && autoScroll && !userInteracting) {
                    runLater { scrollToBottom() }
                }
            }
            userInteracting = false
        })

        // 포커스 이탈 시 자동 스크롤 재활성화
        tableView.focusedProperty().onChange { focused ->
            if (!focused) {
                // 포커스가 빠지면 자동 스크롤 재활성화
                autoScroll = true
            }
        }

        // 워드 필터 변경 시 테이블 갱신 (행 색상 업데이트)
        controller.wordFindProperty.onChange {
            tableView.refresh()
        }

        // 폰트 크기 변경 시 테이블 갱신
        fontSizeProperty.onChange {
            tableView.refresh()
        }
    }

    /**
     * 폰트 크기 증가
     */
    fun increaseFontSize() {
        fontSize = (fontSize + 1.0).coerceAtMost(24.0)
    }

    /**
     * 폰트 크기 감소
     */
    fun decreaseFontSize() {
        fontSize = (fontSize - 1.0).coerceAtLeast(8.0)
    }

    /**
     * 특정 인덱스로 스크롤 (IndicatorPanel에서 호출)
     */
    fun scrollToIndex(index: Int) {
        autoScroll = false
        tableView.scrollTo(index)
        tableView.selectionModel.select(index)
    }

    /**
     * 맨 아래로 스크롤
     */
    fun scrollToBottom() {
        if (tableView.items.isNotEmpty()) {
            tableView.scrollTo(tableView.items.size - 1)
        }
    }

    /**
     * 자동 스크롤 재활성화
     */
    fun enableAutoScroll() {
        autoScroll = true
        scrollToBottom()
    }

    /**
     * 하이라이트로 이동
     */
    private fun navigateToHighlight(forward: Boolean) {
        val items = tableView.items
        val currentIndex = tableView.selectionModel.selectedIndex

        val highlights = items.mapIndexedNotNull { index, entry ->
            if (entry.isHighlighted) index else null
        }

        if (highlights.isEmpty()) return

        val targetIndex = if (forward) {
            highlights.firstOrNull { it > currentIndex } ?: highlights.first()
        } else {
            highlights.lastOrNull { it < currentIndex } ?: highlights.last()
        }

        autoScroll = false
        tableView.selectionModel.select(targetIndex)
        tableView.scrollTo(targetIndex)
    }

    /**
     * 북마크로 이동
     */
    private fun navigateToBookmark(forward: Boolean) {
        val items = tableView.items
        val currentIndex = tableView.selectionModel.selectedIndex

        val bookmarks = items.mapIndexedNotNull { index, entry ->
            if (entry.isBookmarked) index else null
        }

        if (bookmarks.isEmpty()) return

        val targetIndex = if (forward) {
            bookmarks.firstOrNull { it > currentIndex } ?: bookmarks.first()
        } else {
            bookmarks.lastOrNull { it < currentIndex } ?: bookmarks.last()
        }

        autoScroll = false
        tableView.selectionModel.select(targetIndex)
        tableView.scrollTo(targetIndex)
    }

    /**
     * 스택트레이스 표시
     */
    private fun showStackTrace() {
        tableView.selectedItem?.stackTrace?.let { stackTrace ->
            dialog("스택트레이스") {
                prefWidth = 800.0
                prefHeight = 400.0
                textarea(stackTrace) {
                    isEditable = false
                    style = "-fx-font-family: Monospaced;"
                }
            }
        }
    }

    /**
     * 선택 항목 클립보드에 복사 (다중 선택 지원)
     */
    private fun copySelectedToClipboard() {
        val selectedItems = tableView.selectionModel.selectedItems
        if (selectedItems.isEmpty()) return

        val text = formatLogsForExport(selectedItems)
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(
            javafx.scene.input.ClipboardContent().apply { putString(text) }
        )
        controller.status = "${selectedItems.size}개 항목이 클립보드에 복사됨"
    }

    /**
     * 선택 항목 파일로 저장
     */
    private fun saveSelectedToFile() {
        val selectedItems = tableView.selectionModel.selectedItems
        if (selectedItems.isEmpty()) {
            controller.status = "저장할 항목이 선택되지 않음"
            return
        }

        val filters = arrayOf(
            javafx.stage.FileChooser.ExtensionFilter("로그 파일", "*.log", "*.txt"),
            javafx.stage.FileChooser.ExtensionFilter("모든 파일", "*.*")
        )

        chooseFile(
            "선택 로그 저장",
            filters,
            mode = FileChooserMode.Save
        ).firstOrNull()?.let { file ->
            try {
                val text = formatLogsForExport(selectedItems)
                file.writeText(text)
                controller.status = "${selectedItems.size}개 항목이 ${file.name}에 저장됨"
            } catch (e: Exception) {
                controller.status = "저장 실패: ${e.message}"
            }
        }
    }

    /**
     * 로그 항목들을 텍스트로 포맷
     */
    private fun formatLogsForExport(items: List<LogEntry>): String {
        return buildString {
            items.forEach { entry ->
                // 원본 로그 형식으로 출력
                append("${entry.dateTime} ${entry.level.displayName.padEnd(5)} ")
                append("[${entry.thread}] ${entry.className} - ${entry.message}")
                appendLine()
                entry.stackTrace?.let {
                    appendLine(it)
                }
            }
        }
    }

    /**
     * 매칭 키워드를 하이라이트한 TextFlow 생성
     */
    private fun createHighlightedText(message: String): TextFlow {
        val textFlow = TextFlow()
        val wordFind = controller.wordFindProperty.value ?: ""

        if (wordFind.isBlank()) {
            val text = Text(message)
            text.style = "-fx-font-size: ${fontSize}px;"
            textFlow.children.add(text)
            return textFlow
        }

        val keywords = wordFind.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) {
            val text = Text(message)
            text.style = "-fx-font-size: ${fontSize}px;"
            textFlow.children.add(text)
            return textFlow
        }

        // 키워드 매칭 위치 찾기
        val pattern = keywords.joinToString("|") { Regex.escape(it) }
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        var lastEnd = 0

        regex.findAll(message).forEach { match ->
            // 매칭 전 텍스트
            if (match.range.first > lastEnd) {
                val beforeText = Text(message.substring(lastEnd, match.range.first))
                beforeText.style = "-fx-font-size: ${fontSize}px;"
                textFlow.children.add(beforeText)
            }
            // 매칭된 텍스트 (하이라이트)
            val matchedText = Text(match.value)
            matchedText.style = "-fx-font-size: ${fontSize}px; -fx-fill: #FF6600; -fx-font-weight: bold;"
            textFlow.children.add(matchedText)
            lastEnd = match.range.last + 1
        }

        // 마지막 남은 텍스트
        if (lastEnd < message.length) {
            val afterText = Text(message.substring(lastEnd))
            afterText.style = "-fx-font-size: ${fontSize}px;"
            textFlow.children.add(afterText)
        }

        return textFlow
    }
}
