package com.logfilter.model

import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import tornadofx.*

/**
 * Kubernetes 연결 설정
 */
class K8sConfig {
    val currentContextProperty = SimpleStringProperty("")
    var currentContext: String by currentContextProperty

    val currentNamespaceProperty = SimpleStringProperty("default")
    var currentNamespace: String by currentNamespaceProperty

    val selectedPodProperty = SimpleStringProperty("")
    var selectedPod: String by selectedPodProperty

    val selectedContainerProperty = SimpleStringProperty("")
    var selectedContainer: String by selectedContainerProperty

    val contextsProperty = SimpleListProperty(FXCollections.observableArrayList<String>())
    val contexts get() = contextsProperty.get()

    val namespacesProperty = SimpleListProperty(FXCollections.observableArrayList<String>())
    val namespaces get() = namespacesProperty.get()

    val podsProperty = SimpleListProperty(FXCollections.observableArrayList<PodInfo>())
    val pods get() = podsProperty.get()

    // Pod 이름 목록 (ComboBox용)
    val podNamesProperty = SimpleListProperty(FXCollections.observableArrayList<String>())
    val podNames get() = podNamesProperty.get()

    val containersProperty = SimpleListProperty(FXCollections.observableArrayList<String>())
    val containers get() = containersProperty.get()

    fun clear() {
        contexts.clear()
        namespaces.clear()
        pods.clear()
        podNames.clear()
        containers.clear()
        selectedPod = ""
        selectedContainer = ""
    }

    fun updatePodNames() {
        podNames.clear()
        podNames.addAll(pods.map { "${it.name} (${it.status}, ${it.ready})" })
    }

    fun getPodNameByDisplayName(displayName: String): String {
        return displayName.substringBefore(" (")
    }

    fun getSelectedPodInfo(): PodInfo? {
        if (selectedPod.isEmpty()) return null
        return pods.find { it.name == selectedPod }
    }
}
