package com.logfilter.model

/**
 * Kubernetes Pod 정보
 */
data class PodInfo(
    val name: String,
    val status: String,
    val ready: String,
    val restarts: Int = 0,
    val age: String = "",
    val node: String = "",
    val containers: List<String> = emptyList()
) {
    val isMultiContainer: Boolean get() = containers.size > 1

    override fun toString(): String {
        return String.format("%-40s %-12s %s", name, status, ready)
    }

    companion object {
        /**
         * kubectl get pods -o wide 출력 파싱
         * 예: "my-app-7d9f8c6b5d-abc12   1/1     Running   0   2d   10.0.0.1   node-1   <none>   <none>"
         */
        fun parse(line: String?): PodInfo? {
            if (line.isNullOrBlank()) return null

            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 5) return null

            return try {
                PodInfo(
                    name = parts[0],
                    ready = parts[1],
                    status = parts[2],
                    restarts = parts[3].toIntOrNull() ?: 0,
                    age = parts[4],
                    // -o wide 출력에서 노드는 7번째 컬럼 (0-indexed: 6)
                    node = if (parts.size > 6) parts[6] else ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
