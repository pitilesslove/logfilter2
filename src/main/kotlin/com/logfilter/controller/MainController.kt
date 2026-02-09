package com.logfilter.controller

import com.logfilter.model.K8sConfig
import com.logfilter.model.LogEntry
import com.logfilter.model.LogLevel
import com.logfilter.model.PodInfo
import com.logfilter.service.K8sService
import com.logfilter.service.LogParser
import com.logfilter.util.Settings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.*
import java.io.File

/**
 * 메인 컨트롤러 - 비즈니스 로직 담당
 */
class MainController : Controller() {

    private val k8sService = K8sService()
    private val logParser = LogParser()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // K8s 설정
    val k8sConfig = K8sConfig()

    // 로그 데이터
    private val allLogs: ObservableList<LogEntry> = FXCollections.observableArrayList()
    val filteredLogs: FilteredList<LogEntry> = FilteredList(allLogs) { true }

    // 상태 표시
    val statusProperty = SimpleStringProperty("준비됨")
    var status: String by statusProperty

    val isRunningProperty = SimpleBooleanProperty(false)
    var isRunning: Boolean by isRunningProperty

    // 필터 설정
    val wordFindProperty = SimpleStringProperty("")
    val wordRemoveProperty = SimpleStringProperty("")
    val classShowProperty = SimpleStringProperty("")
    val classRemoveProperty = SimpleStringProperty("")
    val highlightProperty = SimpleStringProperty("")

    // 필터 활성화 여부 (전체)
    val filterEnabledProperty = SimpleBooleanProperty(true)
    var filterEnabled: Boolean by filterEnabledProperty

    // 개별 필터 활성화 여부
    val wordFindEnabledProperty = SimpleBooleanProperty(true)
    val wordRemoveEnabledProperty = SimpleBooleanProperty(true)
    val classShowEnabledProperty = SimpleBooleanProperty(true)
    val classRemoveEnabledProperty = SimpleBooleanProperty(true)
    val highlightEnabledProperty = SimpleBooleanProperty(true)

    // 로그 레벨 필터
    val levelFilters = mutableMapOf(
        LogLevel.TRACE to SimpleBooleanProperty(true),
        LogLevel.DEBUG to SimpleBooleanProperty(true),
        LogLevel.INFO to SimpleBooleanProperty(true),
        LogLevel.WARN to SimpleBooleanProperty(true),
        LogLevel.ERROR to SimpleBooleanProperty(true),
        LogLevel.FATAL to SimpleBooleanProperty(true)
    )

    private var logStreamJob: Job? = null
    private var lineCounter = 0

    init {
        // 필터 변경 시 자동 적용
        listOf(wordFindProperty, wordRemoveProperty, classShowProperty, classRemoveProperty).forEach {
            it.onChange { applyFilters() }
        }
        // 개별 필터 활성화 변경 시 자동 적용
        listOf(
            wordFindEnabledProperty, wordRemoveEnabledProperty,
            classShowEnabledProperty, classRemoveEnabledProperty,
            highlightEnabledProperty
        ).forEach { it.onChange { applyFilters() } }
        levelFilters.values.forEach { it.onChange { applyFilters() } }
        filterEnabledProperty.onChange { applyFilters() }

        // 초기 설정 로드
        loadSettings()
    }

    /**
     * 설정 로드
     */
    private fun loadSettings() {
        wordFindProperty.value = Settings.wordFind
        wordRemoveProperty.value = Settings.wordRemove
        classShowProperty.value = Settings.classShow
        classRemoveProperty.value = Settings.classRemove
        highlightProperty.value = Settings.highlight
        k8sConfig.currentContext = Settings.lastContext
        k8sConfig.currentNamespace = Settings.lastNamespace
    }

    /**
     * 설정 저장
     */
    fun saveSettings() {
        Settings.wordFind = wordFindProperty.value ?: ""
        Settings.wordRemove = wordRemoveProperty.value ?: ""
        Settings.classShow = classShowProperty.value ?: ""
        Settings.classRemove = classRemoveProperty.value ?: ""
        Settings.highlight = highlightProperty.value ?: ""
        Settings.lastContext = k8sConfig.currentContext
        Settings.lastNamespace = k8sConfig.currentNamespace
        Settings.lastPod = k8sConfig.selectedPod
        Settings.save()
    }

    /**
     * Context 목록 로드
     */
    fun loadContexts() {
        coroutineScope.launch {
            status = "Context 목록 로딩 중..."
            try {
                val contexts = k8sService.getContexts()
                k8sConfig.contexts.setAll(contexts)

                val current = k8sService.getCurrentContext()
                if (current.isNotEmpty() && k8sConfig.currentContext.isEmpty()) {
                    k8sConfig.currentContext = current
                }

                status = "Context ${contexts.size}개 로드됨"
            } catch (e: Exception) {
                status = "Context 로드 실패: ${e.message}"
            }
        }
    }

    /**
     * Namespace 목록 로드
     */
    fun loadNamespaces() {
        coroutineScope.launch {
            status = "Namespace 목록 로딩 중..."
            try {
                val namespaces = k8sService.getNamespaces(k8sConfig.currentContext)
                k8sConfig.namespaces.setAll(namespaces)
                status = "Namespace ${namespaces.size}개 로드됨"
            } catch (e: Exception) {
                status = "Namespace 로드 실패: ${e.message}"
            }
        }
    }

    /**
     * Pod 목록 로드
     */
    fun loadPods() {
        coroutineScope.launch {
            status = "Pod 목록 로딩 중..."
            try {
                val pods = k8sService.getPods(
                    k8sConfig.currentNamespace,
                    k8sConfig.currentContext
                )
                k8sConfig.pods.setAll(pods)
                k8sConfig.updatePodNames()
                status = "Pod ${pods.size}개 로드됨"
            } catch (e: Exception) {
                status = "Pod 로드 실패: ${e.message}"
            }
        }
    }

    /**
     * Pod 연결 (로그 스트리밍 시작)
     */
    fun connectToPod() {
        startLogStream()
    }

    /**
     * Container 목록 로드
     */
    fun loadContainers() {
        if (k8sConfig.selectedPod.isEmpty()) return

        coroutineScope.launch {
            try {
                val containers = k8sService.getContainers(
                    k8sConfig.selectedPod,
                    k8sConfig.currentNamespace,
                    k8sConfig.currentContext
                )
                k8sConfig.containers.setAll(containers)

                if (containers.size == 1) {
                    k8sConfig.selectedContainer = containers[0]
                }
            } catch (e: Exception) {
                status = "Container 로드 실패: ${e.message}"
            }
        }
    }

    /**
     * 로그 스트리밍 시작
     */
    fun startLogStream() {
        if (k8sConfig.selectedPod.isEmpty()) {
            status = "Pod를 선택하세요"
            return
        }

        stopLogStream()
        clearLogs()
        isRunning = true
        status = "로그 스트리밍 시작..."

        logStreamJob = coroutineScope.launch {
            k8sService.streamLogs(
                pod = k8sConfig.selectedPod,
                namespace = k8sConfig.currentNamespace,
                context = k8sConfig.currentContext,
                container = k8sConfig.selectedContainer.takeIf { it.isNotEmpty() }
            ).catch { e ->
                withContext(Dispatchers.JavaFx) {
                    status = "스트리밍 오류: ${e.message}"
                    isRunning = false
                }
            }.collect { line ->
                withContext(Dispatchers.JavaFx) {
                    addLogLine(line)
                }
            }
        }
    }

    /**
     * 로그 스트리밍 중지
     */
    fun stopLogStream() {
        logStreamJob?.cancel()
        logStreamJob = null
        isRunning = false
        status = "스트리밍 중지됨"
    }

    /**
     * 로그 라인 추가
     */
    private fun addLogLine(line: String) {
        lineCounter++
        val entry = logParser.parse(line, lineCounter)
        allLogs.add(entry)

        // 자동 스크롤 (TODO: 옵션으로)
        status = "로그: ${allLogs.size}줄"
    }

    /**
     * 로그 클리어
     */
    fun clearLogs() {
        allLogs.clear()
        lineCounter = 0
        status = "로그 클리어됨"
    }

    /**
     * 파일에서 로그 로드
     */
    fun loadFromFile(file: File) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val lines = file.readLines()
                val entries = logParser.parseFile(lines)

                withContext(Dispatchers.JavaFx) {
                    allLogs.clear()
                    allLogs.addAll(entries)
                    lineCounter = entries.size
                    status = "${file.name} - ${entries.size}줄 로드됨"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.JavaFx) {
                    status = "파일 로드 실패: ${e.message}"
                }
            }
        }
    }

    /**
     * 필터 적용
     */
    fun applyFilters() {
        filteredLogs.setPredicate { entry ->
            // 필터가 비활성화되면 모두 표시
            if (!filterEnabled) return@setPredicate true

            // 로그 레벨 필터
            val levelFilter = levelFilters[entry.level]?.value ?: true

            // 단어 필터 (Find) - 개별 활성화 체크
            val wordFind = wordFindProperty.value ?: ""
            val wordFindFilter = if (!wordFindEnabledProperty.value || wordFind.isBlank()) true else {
                wordFind.split("|").any { keyword ->
                    entry.message.contains(keyword.trim(), ignoreCase = true) ||
                    entry.className.contains(keyword.trim(), ignoreCase = true)
                }
            }

            // 단어 필터 (Remove) - 개별 활성화 체크
            val wordRemove = wordRemoveProperty.value ?: ""
            val wordRemoveFilter = if (!wordRemoveEnabledProperty.value || wordRemove.isBlank()) true else {
                wordRemove.split("|").none { keyword ->
                    entry.message.contains(keyword.trim(), ignoreCase = true)
                }
            }

            // 클래스 필터 (Show) - 개별 활성화 체크
            val classShow = classShowProperty.value ?: ""
            val classShowFilter = if (!classShowEnabledProperty.value || classShow.isBlank()) true else {
                classShow.split("|").any { clz ->
                    entry.className.contains(clz.trim(), ignoreCase = true)
                }
            }

            // 클래스 필터 (Remove) - 개별 활성화 체크
            val classRemove = classRemoveProperty.value ?: ""
            val classRemoveFilter = if (!classRemoveEnabledProperty.value || classRemove.isBlank()) true else {
                classRemove.split("|").none { clz ->
                    entry.className.contains(clz.trim(), ignoreCase = true)
                }
            }

            levelFilter && wordFindFilter && wordRemoveFilter && classShowFilter && classRemoveFilter
        }

        status = if (filterEnabled) {
            "필터 적용됨: ${filteredLogs.size}/${allLogs.size}줄"
        } else {
            "필터 OFF: ${allLogs.size}줄"
        }
    }

    /**
     * 엔트리가 Word Find 필터와 매칭되는지 확인 (행 색상 표시용)
     */
    fun matchesWordFilter(entry: LogEntry): Boolean {
        val wordFind = wordFindProperty.value ?: ""
        if (wordFind.isBlank()) return false

        return wordFind.split("|").any { keyword ->
            keyword.trim().isNotEmpty() && (
                entry.message.contains(keyword.trim(), ignoreCase = true) ||
                entry.className.contains(keyword.trim(), ignoreCase = true)
            )
        }
    }

    /**
     * 하이라이트 키워드 자동 적용
     */
    fun applyHighlights() {
        // 하이라이트가 비활성화된 경우 모든 하이라이트 제거
        if (!highlightEnabledProperty.value) {
            allLogs.forEachIndexed { index, entry ->
                if (entry.isHighlighted) {
                    allLogs[index] = entry.copy(isHighlighted = false)
                }
            }
            return
        }

        val highlightKeywords = highlightProperty.value?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        allLogs.forEachIndexed { index, entry ->
            val shouldHighlight = highlightKeywords.any { keyword ->
                entry.message.contains(keyword, ignoreCase = true) ||
                entry.className.contains(keyword, ignoreCase = true)
            }
            if (entry.isHighlighted != shouldHighlight) {
                allLogs[index] = entry.copy(isHighlighted = shouldHighlight)
            }
        }
    }

    /**
     * 북마크 토글
     */
    fun toggleBookmark(entry: LogEntry) {
        val index = allLogs.indexOf(entry)
        if (index >= 0) {
            val updated = entry.copy(isBookmarked = !entry.isBookmarked)
            allLogs[index] = updated
        }
    }

    /**
     * 하이라이트 토글
     */
    fun toggleHighlight(entry: LogEntry) {
        val index = allLogs.indexOf(entry)
        if (index >= 0) {
            val updated = entry.copy(isHighlighted = !entry.isHighlighted)
            allLogs[index] = updated
        }
    }

    /**
     * 정리
     */
    fun cleanup() {
        saveSettings()
        stopLogStream()
        coroutineScope.cancel()
    }
}
