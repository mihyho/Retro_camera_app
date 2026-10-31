package com.example.retrocamera

import com.example.retrocamera.data.AppState
import com.example.retrocamera.data.RollEngine
import com.example.retrocamera.data.StateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainLookTest {
    private fun noisy(size: Float, strength: Float = 0.05f): IntArray {
        val w = 64; val h = 64
        val px = IntArray(w * h) { 0xFF808080.toInt() }
        Grain.applyArgb(px, 0, px.size, w, 0, strength = strength, size = size)
        return px
    }

    private fun spread(px: IntArray): Double {
        val v = px.map { (it shr 8 and 0xFF).toDouble() }
        val m = v.average()
        return Math.sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    @Test fun cellSizeScalesWithSize() {
        assertEquals(1, Grain.cellSize(1600))
        assertEquals(2, Grain.cellSize(1600, 2f))
        assertEquals(1, Grain.cellSize(800, 1f))
    }

    @Test fun amountScalesSpread() {
        val low = spread(noisy(1f, 0.02f)); val mid = spread(noisy(1f, 0.05f)); val high = spread(noisy(1f, 0.1f))
        assertTrue("low < mid < high: $low $mid $high", low < mid && mid < high)
    }

    @Test fun grainLookIsClampedAndPersisted() {
        val s = RollEngine.setGrainLook(AppState(), amount = 9f, size = 0f)
        assertEquals(2f, s.grainAmount, 0f); assertEquals(0.5f, s.grainSize, 0f)
        assertEquals(0.5f, StateStore.fromJson(org.json.JSONObject(StateStore.toJson(s).toString())).grainSize, 0f)
        assertEquals(1f, StateStore.fromJson(org.json.JSONObject("""{"version":1,"mode":"FILM","roll":null,"singleFilm":"gold","singleShots":[],"devRolls":[],"grid":false,"stamp":true}""")).grainAmount, 0f)
    }
}
