package dev.nanoIsland

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandTouchListenerTest {

    @Test
    fun testComputeTranslationY() {
        // Elastic resistance factor is 0.4f
        assertEquals(0f, IslandTouchListener.computeTranslationY(0f), 0.001f)
        assertEquals(40f, IslandTouchListener.computeTranslationY(100f), 0.001f)
        assertEquals(-28.8f, IslandTouchListener.computeTranslationY(-72f), 0.001f)
    }

    @Test
    fun testComputeScaleX() {
        val threshold = 100f

        // Negative movement (swiping up) stays at normal 1.0 scale
        assertEquals(1.0f, IslandTouchListener.computeScaleX(-50f, threshold), 0.001f)
        assertEquals(1.0f, IslandTouchListener.computeScaleX(0f, threshold), 0.001f)

        // Halfway drag adds half of max expansion (0.15 / 2 = 0.075)
        assertEquals(1.075f, IslandTouchListener.computeScaleX(50f, threshold), 0.001f)

        // At threshold reaches max expansion (1.15)
        assertEquals(1.15f, IslandTouchListener.computeScaleX(100f, threshold), 0.001f)

        // Beyond threshold clamps to max expansion 1.15
        assertEquals(1.15f, IslandTouchListener.computeScaleX(250f, threshold), 0.001f)
    }

    @Test
    fun testConstants() {
        assertEquals(72f, IslandTouchListener.PULL_THRESHOLD_DP, 0.001f)
        assertEquals(0.4f, IslandTouchListener.RESISTANCE_FACTOR, 0.001f)
        assertEquals(0.15f, IslandTouchListener.MAX_SCALE_EXPANSION, 0.001f)
    }
}
