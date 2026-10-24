package com.example.retrocamera

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun stats(px: IntArray, base: Int): Pair<Double, Double> {
        val d = px.map { ((it shr 8) and 0xFF) - base.toDouble() }
        val mean = d.average()
        val std = sqrt(d.sumOf { (it - mean) * (it - mean) } / d.size)
        return mean to std
    }

    @Test
    fun midtoneGrainIsZeroMeanWithVisibleSpread() {
        val w = 400
        val px = IntArray(w * 300) { gray(128) }
        Grain.applyArgb(px, 0, px.size, w, 0)
        val (mean, std) = stats(px, 128)
        assertTrue("평균은 거의 변하지 않아야 함: $mean", abs(mean) < 0.5)
        assertTrue("눈에 보이는 정도의 분산: $std", std in 2.0..8.0)
    }

    @Test
    fun grainIsWeakerInShadowsAndHighlights() {
        val w = 400
        fun stdAt(v: Int): Double {
            val px = IntArray(w * 200) { gray(v) }
            Grain.applyArgb(px, 0, px.size, w, 0)
            return stats(px, v).second
        }
        assertTrue(stdAt(128) > stdAt(250))
        assertTrue(stdAt(128) > stdAt(5))
    }

    @Test
    fun isDeterministicAndChunkOrderIndependent() {
        val w = 300
        val a = IntArray(w * 100) { gray(100 + it % 50) }
        val b = a.clone()
        Grain.applyArgb(a, 0, a.size, w, 0)
        // 여러 조각으로 나눠 (병렬 처리처럼) 적용해도 결과가 같아야 함
        val third = b.size / 3
        Grain.applyArgb(b, 2 * third, b.size, w, 0)
        Grain.applyArgb(b, 0, third, w, 0)
        Grain.applyArgb(b, third, 2 * third, w, 0)
        assertArrayEquals(a, b)
    }

    @Test
    fun stripOffsetMatchesFullImage() {
        // 이미지를 띠로 나눠 처리할 때(startRow) 전체를 한 번에 처리한 것과 같아야 함
        val w = 200
        val h = 120
        val full = IntArray(w * h) { gray(90 + (it / w) % 40) }
        val strip = full.copyOfRange(w * 60, w * h)
        Grain.applyArgb(full, 0, full.size, w, 0)
        Grain.applyArgb(strip, 0, strip.size, w, 60)
        assertArrayEquals(full.copyOfRange(w * 60, w * h), strip)
    }

    @Test
    fun keepsAlphaAndClampsPureBlackWhite() {
        val w = 100
        val px = IntArray(w * 100) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Grain.applyArgb(px, 0, px.size, w, 0)
        for (p in px) {
            assertEquals(0xFF, p ushr 24)
            for (s in intArrayOf(16, 8, 0)) assertTrue(((p shr s) and 0xFF) in 0..255)
        }
    }

    @Test
    fun grainSizeScalesWithWidth() {
        assertEquals(1, Grain.cellSize(1280))
        assertEquals(2, Grain.cellSize(4000))
    }
}
