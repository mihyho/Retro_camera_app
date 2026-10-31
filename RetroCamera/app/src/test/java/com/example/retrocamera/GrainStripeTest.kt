package com.example.retrocamera

import org.junit.Assert.assertTrue
import org.junit.Test

class GrainStripeTest {
    /** 평평한 회색에 그레인을 넣었을 때 열(세로줄)·행 평균이 무작위 예상치를 크게 넘지 않아야 한다(세로줄 무늬 방지) */
    @Test fun noColumnOrRowStripes() {
        val w = 512; val h = 512
        val px = IntArray(w * h) { 0xFF808080.toInt() }
        Grain.applyArgb(px, 0, px.size, w, 0, strength = 0.05f)
        val v = DoubleArray(px.size) { (px[it] shr 8 and 0xFF).toDouble() }
        val mean = v.average()
        val sd = Math.sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
        val col = DoubleArray(w) { x -> (0 until h).sumOf { y -> v[y * w + x] } / h }
        val row = DoubleArray(h) { y -> (0 until w).sumOf { x -> v[y * w + x] } / w }
        fun sdOf(a: DoubleArray): Double { val m = a.average(); return Math.sqrt(a.sumOf { (it - m) * (it - m) } / a.size) }
        val expected = sd / Math.sqrt(h.toDouble())
        assertTrue("열 평균 편차 ${sdOf(col)} (예상 $expected)", sdOf(col) < expected * 2)
        assertTrue("행 평균 편차 ${sdOf(row)} (예상 $expected)", sdOf(row) < expected * 2)
    }
}
