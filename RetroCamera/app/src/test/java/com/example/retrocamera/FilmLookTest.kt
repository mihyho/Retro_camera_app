package com.example.retrocamera

import com.example.retrocamera.data.ColorGrade
import com.example.retrocamera.data.ColorMix
import com.example.retrocamera.data.CustomFilm
import com.example.retrocamera.data.GradeZone
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

    private fun mix(ch: Int, hue: Float = 0f, sat: Float = 0f, lum: Float = 0f) = ColorMix(
        List(8) { if (it == ch) hue else 0f }, List(8) { if (it == ch) sat else 0f }, List(8) { if (it == ch) lum else 0f },
    )

    private fun hueOf(c: FloatArray): Float {
        val mx = maxOf(c[0], c[1], c[2]); val mn = minOf(c[0], c[1], c[2]); val d = mx - mn
        if (d < 1e-6f) return 0f
        val h = when (mx) { c[0] -> ((c[1] - c[2]) / d) % 6f; c[1] -> (c[2] - c[0]) / d + 2f; else -> (c[0] - c[1]) / d + 4f } * 60f
        return if (h < 0) h + 360f else h
    }

    @Test
    fun colorMixSaturationMinusMakesThatColorGrayButNotOthers() {
        val green = run(FilmAdjust(mix = mix(3, sat = -100f)), .1f, .8f, .2f)
        assertTrue(green.max() - green.min() < .08f)
        val red = run(FilmAdjust(mix = mix(3, sat = -100f)), .9f, .2f, .1f)
        assertEquals(.9f, red[0], .02f) // 초록만 건드리고 빨강은 그대로
    }

    @Test
    fun colorMixHueShiftsOnlyThatColor() {
        val before = hueOf(floatArrayOf(.1f, .8f, .2f))
        val after = hueOf(run(FilmAdjust(mix = mix(3, hue = 100f)), .1f, .8f, .2f))
        assertTrue(after > before + 15f)
        assertEquals(hueOf(floatArrayOf(.9f, .2f, .1f)), hueOf(run(FilmAdjust(mix = mix(3, hue = 100f)), .9f, .2f, .1f)), 1.5f)
    }

    @Test
    fun colorMixLuminanceBrightensThatColorAndLeavesGrayAlone() {
        val a = FilmAdjust(mix = mix(5, lum = 100f)) // 파랑
        assertTrue(luma(run(a, .15f, .25f, .8f)) > luma(floatArrayOf(.15f, .25f, .8f)) + .03f)
        val gray = run(a, .5f, .5f, .5f)
        assertEquals(.5f, gray[0], .005f); assertEquals(.5f, gray[2], .005f)
    }

    @Test
    fun colorMixBlendsSmoothlyBetweenNeighborColors() {
        // 노랑(60도)과 초록(120도) 사이를 지나며 결과가 튀지 않는다
        val a = FilmAdjust(mix = mix(2, hue = 100f))
        var prev = Float.NaN
        for (h in 55..125 step 2) {
            val rad = Math.toRadians(h.toDouble())
            val c = floatArrayOf(.5f + .4f * Math.cos(rad).toFloat(), .5f + .4f * Math.cos(rad - 2.0944).toFloat(), .5f + .4f * Math.cos(rad + 2.0944).toFloat())
            val out = hueOf(run(a, c[0], c[1], c[2])) - hueOf(c)
            if (!prev.isNaN()) assertTrue("h=$h", Math.abs(out - prev) < 8f)
            prev = out
        }
    }

    @Test
    fun gradingTintsEachToneRangeWithItsOwnColor() {
        val a = FilmAdjust(grade = ColorGrade(
            shadows = GradeZone(hue = 0f, sat = 100f),      // 어두운 곳: 빨강
            highlights = GradeZone(hue = 240f, sat = 100f), // 밝은 곳: 파랑
        ))
        val dark = run(a, .08f, .08f, .08f)
        val bright = run(a, .92f, .92f, .92f)
        val mid = run(a, .5f, .5f, .5f)
        assertTrue(dark[0] > dark[2] + .03f)
        assertTrue(bright[2] > bright[0] + .03f)
        assertEquals(mid[0], mid[2], .005f) // 중간 영역은 색을 안 골랐으니 그대로
    }

    @Test
    fun gradingMidtonesAffectMidNotExtremes() {
        val a = FilmAdjust(grade = ColorGrade(mids = GradeZone(hue = 120f, sat = 100f)))
        val mid = run(a, .5f, .5f, .5f)
        assertTrue(mid[1] > mid[0] + .03f)
        assertEquals(0f, run(a, 0f, 0f, 0f)[1], .01f)
        assertEquals(1f, run(a, 1f, 1f, 1f)[1], .01f)
    }

    @Test
    fun gradingLuminanceAndBalance() {
        val lift = FilmAdjust(grade = ColorGrade(shadows = GradeZone(lum = 100f)))
        assertTrue(run(lift, .1f, .1f, .1f)[0] > .2f)
        assertEquals(.9f, run(lift, .9f, .9f, .9f)[0], .02f)
        // 균형 +: 경계가 어두운 쪽으로 내려가 같은 회색이 밝은 영역으로 더 많이 세어진다
        val tintHi = GradeZone(hue = 240f, sat = 100f)
        val neutral = run(FilmAdjust(grade = ColorGrade(highlights = tintHi)), .45f, .45f, .45f)
        val shifted = run(FilmAdjust(grade = ColorGrade(highlights = tintHi, balance = 100f)), .45f, .45f, .45f)
        assertTrue(shifted[2] - shifted[0] > neutral[2] - neutral[0])
    }

    @Test
    fun gradingResultsStayInRange() {
        val extreme = FilmAdjust(
            mix = ColorMix(List(8) { 100f }, List(8) { 100f }, List(8) { 100f }),
            grade = ColorGrade(GradeZone(10f, 100f, 100f), GradeZone(120f, 100f, -100f), GradeZone(300f, 100f, 100f), 100f, 100f),
        )
        for (v in floatArrayOf(0f, .2f, .5f, .8f, 1f)) run(extreme, v, 1f - v, v / 3f).forEach { assertTrue(it in 0f..1f) }
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
            assertEquals(0.4f, f.texture, 1e-4f)       // 텍스처 +40 -> +0.4
            assertEquals(0.5f, f.localContrast, 1e-4f) // 명료도 +50 -> +0.5
            // 목록이 다시 만들어져도 같은 필름으로 본다
            Film.setCustom(listOf(def, def.copy(key = "custom_2")))
            assertEquals(f, Film.fromKey("custom_1"))
            assertNotEquals(Film.fromKey("custom_1"), Film.fromKey("custom_2"))
        } finally {
            Film.setCustom(emptyList())
        }
        assertFalse(Film.entries.any { it.key.startsWith("custom_") })
    }

    @Test
    fun builtInMonochromeIsGrayAndDreamingKeepsColor() {
        val mono = FilmLook.bake(null, Film.MONOCHROME.custom!!.adjust)
        val c = floatArrayOf(.8f, .3f, .2f)
        FilmLook.sample(mono, c)
        assertTrue(c.max() - c.min() < .03f) // 채도 -100, 생동감 -100: 색이 거의 없다
        val dream = FilmLook.bake(null, Film.DREAMING.custom!!.adjust)
        val d = floatArrayOf(.8f, .3f, .2f)
        FilmLook.sample(dream, d)
        assertTrue(d.max() - d.min() > .1f)
        // 필름 결과는 모두 0~1 안
        for (v in dream.data) assertTrue(v in 0f..1f)
    }
}
