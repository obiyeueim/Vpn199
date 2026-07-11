package com.khanhan.pingpilot.network

import org.junit.Assert.assertEquals
import org.junit.Test

class PingMonitorTest {
    @Test
    fun latencyLabelsMatchExpectedRanges() {
        assertEquals("Không có dữ liệu", latencyLabel(null))
        assertEquals("Rất tốt", latencyLabel(30))
        assertEquals("Tốt", latencyLabel(80))
        assertEquals("Trung bình", latencyLabel(150))
        assertEquals("Cao", latencyLabel(250))
    }
}
