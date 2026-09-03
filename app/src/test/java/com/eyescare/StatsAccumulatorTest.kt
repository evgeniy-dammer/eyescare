package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsAccumulatorTest {

    @Test
    fun `monitoring segment flushes seconds and stops when not kept`() {
        val a = StatsAccumulator()
        a.startMonitoring(1_000)
        val f = a.flush(66_000, keepCounting = false)
        assertEquals(65, f.monitoringSeconds) // (66000-1000)/1000
        assertEquals(0, f.tooCloseSeconds)
        // Отрезок закрыт → следующий flush ничего не добавляет.
        assertEquals(0, a.flush(90_000, keepCounting = false).monitoringSeconds)
    }

    @Test
    fun `keepCounting restarts the segment from flush moment`() {
        val a = StatsAccumulator()
        a.startMonitoring(0)
        assertEquals(10, a.flush(10_000, keepCounting = true).monitoringSeconds)
        assertEquals(15, a.flush(25_000, keepCounting = true).monitoringSeconds) // считает с 10000
    }

    @Test
    fun `too close engage and release returns duration once`() {
        val a = StatsAccumulator()
        a.tooCloseEngaged(1_000)
        assertEquals(3, a.tooCloseReleased(4_000))
        assertEquals(0, a.tooCloseReleased(9_000)) // повторный release — уже закрыт
    }

    @Test
    fun `flush closes an open too-close segment alongside monitoring`() {
        val a = StatsAccumulator()
        a.startMonitoring(0)
        a.tooCloseEngaged(2_000)
        val f = a.flush(12_000, keepCounting = false)
        assertEquals(12, f.monitoringSeconds)
        assertEquals(10, f.tooCloseSeconds)
    }

    @Test
    fun `distance samples are flushed as a sum and a count`() {
        val a = StatsAccumulator()
        a.addDistanceSample(30f)
        a.addDistanceSample(50f)
        val f = a.flush(1_000, keepCounting = true)
        assertEquals(80, f.distanceSumCm)
        assertEquals(2, f.distanceSamples)
        // Замеры отданы — при следующем сбросе их уже нет, даже если отрезок продолжается.
        assertEquals(0, a.flush(2_000, keepCounting = true).distanceSamples)
    }

    @Test
    fun `distance samples are rounded, not truncated`() {
        val a = StatsAccumulator()
        a.addDistanceSample(29.6f)
        a.addDistanceSample(29.6f)
        // Усечение дало бы 58 — среднее уехало бы на полсантиметра вниз, в сторону ложной тревоги.
        assertEquals(60, a.flush(1_000, keepCounting = true).distanceSumCm)
    }

    @Test
    fun `broken distance readings are ignored`() {
        val a = StatsAccumulator()
        a.addDistanceSample(0f)
        a.addDistanceSample(-15f)
        a.addDistanceSample(Float.NaN)
        a.addDistanceSample(Float.POSITIVE_INFINITY)
        val f = a.flush(1_000, keepCounting = true)
        assertEquals(0, f.distanceSamples)
        assertEquals(0, f.distanceSumCm)
    }

    @Test
    fun `flush is a no-op when nothing started`() {
        val a = StatsAccumulator()
        val f = a.flush(5_000, keepCounting = false)
        assertEquals(0, f.monitoringSeconds)
        assertEquals(0, f.tooCloseSeconds)
        assertEquals(0, f.distanceSamples)
    }
}
