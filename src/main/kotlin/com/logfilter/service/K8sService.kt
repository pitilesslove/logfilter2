package com.logfilter.service

import com.logfilter.model.PodInfo
import com.logfilter.util.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Kubernetes 명령 실행 서비스 (코루틴 기반)
 */
class K8sService {

    private val kubectl: String get() = Settings.kubectlPath

    /**
     * Context 목록 조회
     */
    suspend fun getContexts(): List<String> = withContext(Dispatchers.IO) {
        executeCommand("$kubectl config get-contexts -o name")
            .filter { it.isNotBlank() }
    }

    /**
     * 현재 Context 조회
     */
    suspend fun getCurrentContext(): String = withContext(Dispatchers.IO) {
        executeCommand("$kubectl config current-context")
            .firstOrNull()?.trim() ?: ""
    }

    /**
     * Namespace 목록 조회
     */
    suspend fun getNamespaces(context: String): List<String> = withContext(Dispatchers.IO) {
        val cmd = if (context.isNotEmpty()) {
            "$kubectl get namespaces -o name --context $context"
        } else {
            "$kubectl get namespaces -o name"
        }
        executeCommand(cmd)
            .filter { it.isNotBlank() }
            .map { it.removePrefix("namespace/") }
    }

    /**
     * Pod 목록 조회 (-o wide로 노드 정보 포함)
     */
    suspend fun getPods(namespace: String, context: String): List<PodInfo> = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("$kubectl get pods -n $namespace -o wide")
            if (context.isNotEmpty()) {
                append(" --context $context")
            }
        }

        executeCommand(cmd)
            .drop(1) // 헤더 제거
            .mapNotNull { PodInfo.parse(it) }
    }

    /**
     * Pod의 컨테이너 목록 조회
     */
    suspend fun getContainers(
        pod: String,
        namespace: String,
        context: String
    ): List<String> = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("$kubectl get pod $pod -n $namespace")
            append(" -o jsonpath='{.spec.containers[*].name}'")
            if (context.isNotEmpty()) {
                append(" --context $context")
            }
        }

        executeCommand(cmd)
            .flatMap { it.trim('\'', ' ').split(" ") }
            .filter { it.isNotBlank() }
    }

    /**
     * 로그 스트리밍 (Flow 기반)
     */
    fun streamLogs(
        pod: String,
        namespace: String,
        context: String,
        container: String? = null,
        tailLines: Int = 1000
    ): Flow<String> = flow {
        val cmd = buildList {
            add(kubectl)
            add("logs")
            add("-f")
            add("--tail=$tailLines")
            add(pod)
            add("-n")
            add(namespace)
            if (!container.isNullOrEmpty()) {
                add("-c")
                add(container)
            }
            if (context.isNotEmpty()) {
                add("--context")
                add(context)
            }
        }

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    emit(line!!)
                }
            }
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 명령 실행 및 결과 반환
     */
    private fun executeCommand(command: String): List<String> {
        return try {
            val process = ProcessBuilder(command.split(" "))
                .redirectErrorStream(true)
                .start()

            val result = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()

            process.waitFor()
            result
        } catch (e: Exception) {
            println("명령 실행 실패: $command - ${e.message}")
            emptyList()
        }
    }

    /**
     * kubectl 설치 확인
     */
    suspend fun isKubectlAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(kubectl, "version", "--client", "--short")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
