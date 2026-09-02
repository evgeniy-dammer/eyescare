package com.eyescare

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakExerciseTest {

    @Test
    fun `starts on the look-far step`() {
        val p = BreakExercise.progressAt(0)
        assertEquals(BreakStep.LOOK_FAR, p.step)
        assertEquals(BreakExercise.LOOK_FAR_MS, p.remainingInStepMs)
        assertEquals(0f, p.stepFraction, 0.001f)
    }

    @Test
    fun `negative elapsed is treated as the very beginning`() {
        // Часы вызывающего могут дать отрицательную разницу на первом кадре — не должно ломать шкалу.
        val p = BreakExercise.progressAt(-500)
        assertEquals(BreakStep.LOOK_FAR, p.step)
        assertEquals(BreakExercise.LOOK_FAR_MS, p.remainingInStepMs)
    }

    @Test
    fun `look-far step runs to its last millisecond`() {
        val p = BreakExercise.progressAt(BreakExercise.LOOK_FAR_MS - 1)
        assertEquals(BreakStep.LOOK_FAR, p.step)
        assertEquals(1L, p.remainingInStepMs)
        assertEquals(1f, p.stepFraction, 0.001f)
    }

    @Test
    fun `blink step begins exactly when look-far ends`() {
        val p = BreakExercise.progressAt(BreakExercise.LOOK_FAR_MS)
        assertEquals(BreakStep.BLINK, p.step)
        assertEquals(BreakExercise.BLINK_MS, p.remainingInStepMs)
        assertEquals(0f, p.stepFraction, 0.001f)
    }

    @Test
    fun `blink step counts down`() {
        val p = BreakExercise.progressAt(BreakExercise.LOOK_FAR_MS + 2000)
        assertEquals(BreakStep.BLINK, p.step)
        assertEquals(BreakExercise.BLINK_MS - 2000, p.remainingInStepMs)
        assertEquals(0.4f, p.stepFraction, 0.001f)
    }

    @Test
    fun `break is done exactly at the total duration`() {
        val p = BreakExercise.progressAt(BreakExercise.TOTAL_MS)
        assertEquals(BreakStep.DONE, p.step)
        assertEquals(0L, p.remainingInStepMs)
    }

    @Test
    fun `stays done afterwards`() {
        val p = BreakExercise.progressAt(BreakExercise.TOTAL_MS + 60_000)
        assertEquals(BreakStep.DONE, p.step)
        assertEquals(0L, p.remainingInStepMs)
    }

    @Test
    fun `display seconds round up so zero shows only at the end`() {
        assertEquals(20, BreakExercise.displaySeconds(20_000))
        assertEquals(20, BreakExercise.displaySeconds(19_001))
        assertEquals(19, BreakExercise.displaySeconds(19_000))
        assertEquals(1, BreakExercise.displaySeconds(1))
        assertEquals(0, BreakExercise.displaySeconds(0))
        assertEquals(0, BreakExercise.displaySeconds(-5))
    }

    @Test
    fun `countdown never shows the same second twice in a row at step start`() {
        // На старте шага «20», через секунду «19» — без залипания на 20 две секунды подряд.
        assertEquals(20, BreakExercise.displaySeconds(BreakExercise.progressAt(0).remainingInStepMs))
        assertEquals(19, BreakExercise.displaySeconds(BreakExercise.progressAt(1000).remainingInStepMs))
        assertEquals(18, BreakExercise.displaySeconds(BreakExercise.progressAt(2000).remainingInStepMs))
    }
}
