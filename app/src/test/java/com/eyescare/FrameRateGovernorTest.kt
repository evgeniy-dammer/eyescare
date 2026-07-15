package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRateGovernorTest {

    private val threshold = 30

    @Test
    fun `no face returns idle interval`() {
        assertEquals(FrameRateGovernor.IDLE_INTERVAL_MS, FrameRateGovernor.intervalMsFor(null, threshold))
    }

    @Test
    fun `below threshold is active`() {
        assertEquals(FrameRateGovernor.ACTIVE_INTERVAL_MS, FrameRateGovernor.intervalMsFor(20f, threshold))
    }

    @Test
    fun `within the near margin above threshold stays active`() {
        // threshold + margin - 1 → всё ещё «зона внимания»
        val d = (threshold + FrameRateGovernor.NEAR_MARGIN_CM - 1).toFloat()
        assertEquals(FrameRateGovernor.ACTIVE_INTERVAL_MS, FrameRateGovernor.intervalMsFor(d, threshold))
    }

    @Test
    fun `at the near margin boundary switches to relaxed`() {
        val d = (threshold + FrameRateGovernor.NEAR_MARGIN_CM).toFloat()
        assertEquals(FrameRateGovernor.RELAXED_INTERVAL_MS, FrameRateGovernor.intervalMsFor(d, threshold))
    }

    @Test
    fun `comfortably far is relaxed`() {
        assertEquals(FrameRateGovernor.RELAXED_INTERVAL_MS, FrameRateGovernor.intervalMsFor(80f, threshold))
    }
}
