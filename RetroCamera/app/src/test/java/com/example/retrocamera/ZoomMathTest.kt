package com.example.retrocamera

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {

    @Test
    fun pinchMultipliesAndClamps() {
        assertEquals(2f, ZoomMath.pinch(1f, 2f, 1f, 8f), 1e-6f)
        assertEquals(1.5f, ZoomMath.pinch(3f, 0.5f, 1f, 8f), 1e-6f)
        assertEquals("최대를 넘지 않는다", 8f, ZoomMath.pinch(6f, 3f, 1f, 8f), 1e-6f)
        assertEquals("최소 밑으로 내려가지 않는다", 1f, ZoomMath.pinch(1.2f, 0.1f, 1f, 8f), 1e-6f)
    }

    @Test
    fun smallRepeatedPinchesAccumulate() {
        var r = 1f
        repeat(20) { r = ZoomMath.pinch(r, 1.04f, 1f, 8f) } // 1.04^20 ≈ 2.19
        assertEquals(2.19f, r, 0.02f)
    }

    @Test
    fun doubleTapTogglesBetweenMinAndTwoX() {
        assertEquals(2f, ZoomMath.doubleTapTarget(1f, 1f, 8f), 1e-6f)
        assertEquals(1f, ZoomMath.doubleTapTarget(2f, 1f, 8f), 1e-6f)
        assertEquals("조금만 확대된 상태에서도 원래로 돌아간다", 1f, ZoomMath.doubleTapTarget(1.3f, 1f, 8f), 1e-6f)
        assertEquals("최대 배율이 2배보다 작으면 최대 배율까지", 1.5f, ZoomMath.doubleTapTarget(1f, 1f, 1.5f), 1e-6f)
        assertEquals("확대를 못 하는 카메라는 그대로", 1f, ZoomMath.doubleTapTarget(1f, 1f, 1f), 1e-6f)
    }

    @Test
    fun formatsForTheHud() {
        assertEquals("1.0×", ZoomMath.format(1f))
        assertEquals("2.5×", ZoomMath.format(2.5f))
        assertEquals("10×", ZoomMath.format(10f))
    }
}
