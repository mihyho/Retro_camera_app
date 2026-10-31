package com.example.retrocamera

import com.example.retrocamera.data.CustomFilm
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.FilmAdjust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmLookTest {

    private fun run(a: FilmAdjust, r: Float, g: Float, b: Float): FloatArray = floatArrayOf(r, g, b).also { FilmLook.apply(a, it) }

    private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]

    @Test
    fun neutralBakeIsIdentity() {
        val lut = FilmLook.bake(null, FilmAdjust())
        val n = lut.size
        for (i in 0 until n * n * n) {
            val r = i % n; val g = (i / n) % n; val b = i / (n * n)
            assertEquals(r / (n - 1f), lut.data[i * 3], 1e-6f)
            assertEquals(g / (n - 1f), lut.data[i * 3 + 1], 1e-6f)
            assertEquals(b / (n - 1f), lut.data[i * 3 + 2], 1e-6f)
        }
    }

    @Test
    fun exposureBrightensAndDarkens() {
        val mid = run(FilmAdjust(), .4f, .4f, .4f)[0]
        assertTrue(run(FilmAdjust(exposure = 1f), .4f, .4f, .4f)[0] > mid + .1f)
        assertTrue(run(FilmAdjust(exposure = -1f), .4f, .4f, .4f)[0] < mid - .1f)
    }

    @Test
    fun saturationMinusHundredIsGray() {
        val c = run(FilmAdjust(saturation = -100f), .9f, .3f, .2f)
        assertEquals(c[0], c[1], 1e-4f)
        assertEquals(c[1], c[2], 1e-4f)
    }

    @Test
    fun warmTemperatureAddsRedRemovesBlue() {
        val c = run(FilmAdjust(temperature = 60f), .5f, .5f, .5f)
        assertTrue(c[0] > c[2])
        val cold = run(FilmAdjust(temperature = -60f), .5f, .5f, .5f)
        assertTrue(cold[2] > cold[0])
    }

    @Test
    fun tintPlusGoesMagenta() {
        val c = run(FilmAdjust(tint = 60f), .5f, .5f, .5f)
        assertTrue(c[1] < c[0] && c[1] < c[2])
    }

    @Test
    fun highlightsAffectBrightMoreThanDark() {
        val a = FilmAdjust(highlights = -80f)
        val bright = .9f - run(a, .9f, .9f, .9f)[0]
        val dark = .1f - run(a, .1f, .1f, .1f)[0]
        assertTrue(bright > .1f)
        assertTrue(Math.abs(dark) < .02f)
    }

    @Test
    fun shadowsAffectDarkMoreThanBright() {
        val a = FilmAdjust(shadows = 80f)
        val dark = run(a, .15f, .15f, .15f)[0] - .15f
        val bright = run(a, .9f, .9f, .9f)[0] - .9f
        assertTrue(dark > .1f)
        assertTrue(Math.abs(bright) < .02f)
    }

    @Test
    fun contrastSeparatesTones() {
        val a = FilmAdjust(contrast = 80f)
        assertTrue(run(a, .3f, .3f, .3f)[0] < .3f)
        assertTrue(run(a, .7f, .7f, .7f)[0] > .7f)
    }

    @Test
    fun vibranceFavorsMutedColors() {
        val a = FilmAdjust(vibrance = 100f)
        val muted = run(a, .55f, .5f, .45f)
        val vivid = run(a, .9f, .2f, .1f)
        val mutedGain = (muted[0] - muted[2]) / (.55f - .45f)
        val vividGain = (vivid[0] - vivid[2]) / (.9f - .1f)
        assertTrue(mutedGain > vividGain)
    }

    @Test
    fun dehazePlusAddsContrastMinusLiftsBlacks() {
        assertTrue(run(FilmAdjust(dehaze = 80f), .3f, .3f, .3f)[0] < .3f)
        assertTrue(run(FilmAdjust(dehaze = -80f), .05f, .05f, .05f)[0] > .2f)
    }

    @Test
    fun resultsStayInRange() {
        val extreme = FilmAdjust(exposure = 5f, contrast = 100f, highlights = 100f, shadows = 100f, whites = 100f, blacks = 100f,
            temperature = 100f, tint = 100f, vibrance = 100f, saturation = 100f, dehaze = 100f)
        for (v in floatArrayOf(0f, .25f, .5f, .75f, 1f)) {
            val c = run(extreme, v, 1f - v, v / 2f)
            c.forEach { assertTrue(it in 0f..1f) }
        }
    }

    @Test
    fun bakeStacksOnBaseLut() {
        val dim = FilmLook.bake(null, FilmAdjust(exposure = -1f))
        val both = FilmLook.bake(dim, FilmAdjust(exposure = -1f))
        val c = floatArrayOf(.8f, .8f, .8f)
        FilmLook.sample(both, c)
        assertTrue(c[0] < run(FilmAdjust(exposure = -1f), .8f, .8f, .8f)[0] - .05f)
    }

    @Test
    fun customFilmsJoinTheListAndCanBeReplaced() {
        val def = CustomFilm("custom_1", "내 필름", 0xFFE8B84A, null, FilmAdjust(clarity = 50f, texture = 40f))
        Film.setCustom(listOf(def))
        try {
            val f = Film.fromKey("custom_1")
            assertTrue(f in Film.entries)
            assertEquals(0, f.iso)
            assertEquals(0.4f, f.clarity, 1e-4f) // 기본 필름 없이 시작: 명료도 +50 -> +0.4
            assertEquals(0.24f, f.texture, 1e-4f)
            // 목록이 다시 만들어져도 같은 필름으로 본다
            Film.setCustom(listOf(def, def.copy(key = "custom_2")))
            assertEquals(f, Film.fromKey("custom_1"))
            assertNotEquals(Film.fromKey("custom_1"), Film.fromKey("custom_2"))
        } finally {
            Film.setCustom(emptyList())
        }
        assertFalse(Film.entries.any { it.key.startsWith("custom_") })
    }
}
