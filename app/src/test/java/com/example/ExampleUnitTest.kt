package com.example

import com.example.network.TailscaleNetworkHelper
import com.example.server.BridgeStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun tailscaleCgnatDetection_validatesRangeCorrectly() {
        assertTrue(TailscaleNetworkHelper.isTailscaleCgnatIp("100.64.0.1"))
        assertTrue(TailscaleNetworkHelper.isTailscaleCgnatIp("100.101.42.19"))
        assertTrue(TailscaleNetworkHelper.isTailscaleCgnatIp("100.127.255.254"))

        assertFalse(TailscaleNetworkHelper.isTailscaleCgnatIp("100.63.255.255"))
        assertFalse(TailscaleNetworkHelper.isTailscaleCgnatIp("100.128.0.1"))
        assertFalse(TailscaleNetworkHelper.isTailscaleCgnatIp("192.168.1.50"))
        assertFalse(TailscaleNetworkHelper.isTailscaleCgnatIp("127.0.0.1"))
    }

    @Test
    fun pollQueue_enqueuesAndDrainsInOrder() {
        BridgeStateHolder.clearPollQueue()
        BridgeStateHolder.enqueuePollCommand("PING")
        BridgeStateHolder.enqueuePollCommand("print('hello')")

        assertEquals(2, BridgeStateHolder.pendingPollCount.value)
        val drained = BridgeStateHolder.drainPollCommands()
        assertEquals(listOf("PING", "print('hello')"), drained)
        assertEquals(0, BridgeStateHolder.pendingPollCount.value)
    }
}
