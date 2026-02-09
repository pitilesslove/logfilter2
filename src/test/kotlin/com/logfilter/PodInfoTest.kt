package com.logfilter

import com.logfilter.model.PodInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PodInfoTest {

    @Test
    fun `parse valid kubectl get pods output`() {
        val line = "my-app-7d9f8c6b5d-abc12   1/1     Running   0          2d"
        val pod = PodInfo.parse(line)

        assertNotNull(pod)
        assertEquals("my-app-7d9f8c6b5d-abc12", pod!!.name)
        assertEquals("1/1", pod.ready)
        assertEquals("Running", pod.status)
        assertEquals(0, pod.restarts)
        assertEquals("2d", pod.age)
    }

    @Test
    fun `parse pod with restarts`() {
        val line = "backend-deploy-5f4d8c9b7-xyz99   2/2     Running   5          10h"
        val pod = PodInfo.parse(line)

        assertNotNull(pod)
        assertEquals("backend-deploy-5f4d8c9b7-xyz99", pod!!.name)
        assertEquals("2/2", pod.ready)
        assertEquals("Running", pod.status)
        assertEquals(5, pod.restarts)
        assertEquals("10h", pod.age)
    }

    @Test
    fun `parse pod with CrashLoopBackOff status`() {
        val line = "failing-pod-abc123   0/1     CrashLoopBackOff   10          1h"
        val pod = PodInfo.parse(line)

        assertNotNull(pod)
        assertEquals("failing-pod-abc123", pod!!.name)
        assertEquals("0/1", pod.ready)
        assertEquals("CrashLoopBackOff", pod.status)
        assertEquals(10, pod.restarts)
    }

    @Test
    fun `parse returns null for empty line`() {
        assertNull(PodInfo.parse(""))
        assertNull(PodInfo.parse(null))
        assertNull(PodInfo.parse("   "))
    }

    @Test
    fun `parse returns null for invalid format`() {
        val line = "invalid pod line"
        assertNull(PodInfo.parse(line))
    }

    @Test
    fun `isMultiContainer returns true for multiple containers`() {
        val pod = PodInfo(
            name = "multi-container-pod",
            status = "Running",
            ready = "2/2",
            restarts = 0,
            containers = listOf("app", "sidecar")
        )
        assertTrue(pod.isMultiContainer)
    }

    @Test
    fun `isMultiContainer returns false for single container`() {
        val pod = PodInfo(
            name = "single-container-pod",
            status = "Running",
            ready = "1/1",
            restarts = 0,
            containers = listOf("app")
        )
        assertFalse(pod.isMultiContainer)
    }

    @Test
    fun `toString formats correctly`() {
        val pod = PodInfo(
            name = "test-pod",
            status = "Running",
            ready = "1/1"
        )
        val str = pod.toString()
        assertTrue(str.contains("test-pod"))
        assertTrue(str.contains("Running"))
        assertTrue(str.contains("1/1"))
    }
}
