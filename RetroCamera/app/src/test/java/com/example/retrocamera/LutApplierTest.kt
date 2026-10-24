package com.example.retrocamera

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LutApplierTest {

    private val lut = File("src/test/resources/seoul_test.cube").inputStream().use { CubeLut.parse(it) }

    @Test
    fun parsesSizeAndCount() {
        assertEquals(33, lut.size)
        assertEquals(33 * 33 * 33 * 3, lut.data.size)
    }

    /** 기대값은 extract_lut.apply_lut(Python)으로 계산한 값 (±1 허용: 반올림/float 차이) */
    @Test
    fun matchesPythonReference() {
        val cases = listOf(
            Triple(intArrayOf(0, 0, 0), intArrayOf(20, 28, 4), 0),
            Triple(intArrayOf(255, 255, 255), intArrayOf(249, 234, 222), 0),
            Triple(intArrayOf(128, 128, 128), intArrayOf(158, 134, 99), 0),
            Triple(intArrayOf(200, 40, 90), intArrayOf(207, 80, 40), 0),
            Triple(intArrayOf(17, 230, 64), intArrayOf(79, 169, 50), 0),
            Triple(intArrayOf(250, 180, 10), intArrayOf(255, 125, 0), 0),
            Triple(intArrayOf(60, 90, 200), intArrayOf(81, 134, 188), 0),
            Triple(intArrayOf(3, 251, 128), intArrayOf(61, 200, 117), 0),
        )
        val px = IntArray(cases.size) { i ->
            val (r, g, b) = cases[i].first.let { Triple(it[0], it[1], it[2]) }
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        LutApplier.applyArgb(px, 0, px.size, lut)
        for ((i, case) in cases.withIndex()) {
            val got = intArrayOf((px[i] shr 16) and 0xFF, (px[i] shr 8) and 0xFF, px[i] and 0xFF)
            for (c in 0..2) {
                assertTrue(
                    "입력 ${case.first.toList()} 채널$c: 기대 ${case.second[c]} 실제 ${got[c]}",
                    kotlin.math.abs(got[c] - case.second[c]) <= 1,
                )
            }
            assertEquals("알파 유지", 0xFF, (px[i] ushr 24))
        }
    }
}
