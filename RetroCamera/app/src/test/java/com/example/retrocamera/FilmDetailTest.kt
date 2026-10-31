package com.example.retrocamera

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 텍스처와 명료도가 실제 픽셀에 적용되고, 서로 독립이며, 0이면 원본 그대로인지 확인한다 */
class FilmDetailTest {
    private val w = 800
    private val h = 600

    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    private fun lum(c: Int) = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

    /** 중간 밝기 바탕 + 완만한 밝기 변화 + 48px 주기의 중간 크기 무늬(국부 대비) + 3px 주기의 미세한 질감. 색은 약간 따뜻하게 */
    private fun scene(): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val l = 120f + 30f * x / w + 15f * sin(x * 2 * Math.PI / 48).toFloat() * sin(y * 2 * Math.PI / 48).toFloat() +
            14f * sin(x * 2.1f).toFloat() * sin(y * 1.9f).toFloat()
        px((l + 12).toInt(), l.toInt(), (l - 12).toInt())
    }

    private fun run(src: IntArray, texture: Float, clarity: Float): IntArray {
        val out = src.copyOf()
        val base = BoxBlur.blurArgb(src, w, h, PhotoPipeline.detailRadius(w))
        val wide = if (clarity != 0f) FilmDetail.blurLarge(src, w, h, FilmDetail.largeRadius(w)) else null
        FilmDetail.applyArgb(out, base, wide, 0, out.size, texture, clarity)
        return out
    }

    private fun mean(a: FloatArray) = a.average().toFloat()

    /** 미세 질감 에너지: 원본과 작게 흐린 것의 밝기 차이의 평균 */
    private fun fine(a: IntArray): Float {
        val b = BoxBlur.blurArgb(a, w, h, 1)
        return mean(FloatArray(a.size) { abs(lum(a[it]) - lum(b[it])) })
    }

    /** 국부 대비 에너지: 작게 흐린 것과 넓게 흐린 것의 밝기 차이의 평균(중간 크기 무늬) */
    private fun local(a: IntArray): Float {
        val s = BoxBlur.blurArgb(a, w, h, 3)
        val l = FilmDetail.blurLarge(a, w, h, FilmDetail.largeRadius(w))
        return mean(FloatArray(a.size) { abs(lum(s[it]) - lum(l[it])) })
    }

    private fun meanLum(a: IntArray) = mean(FloatArray(a.size) { lum(a[it]) })

    @Test
    fun bothZeroLeavesTheImageUntouched() {
        val src = scene()
        assertArrayEquals(src, run(src, 0f, 0f))
    }

    @Test
    fun texturePlusBoostsFineDetailNotMidScaleContrast() {
        val src = scene(); val out = run(src, 1f, 0f)
        assertTrue("fine ${fine(src)} -> ${fine(out)}", fine(out) > fine(src) * 1.4f)
        assertTrue("local ${local(src)} -> ${local(out)}", local(out) in local(src) * 0.9f..local(src) * 1.2f)
        assertTrue("mean ${meanLum(src)} -> ${meanLum(out)}", abs(meanLum(out) - meanLum(src)) < 1f) // 전체 밝기는 그대로
    }

    @Test
    fun clarityPlusBoostsLocalContrastWithoutShiftingTones() {
        val src = scene(); val out = run(src, 0f, 1f)
        assertTrue("local ${local(src)} -> ${local(out)}", local(out) > local(src) * 1.25f)
        assertTrue(abs(meanLum(out) - meanLum(src)) < 1.5f)
        assertTrue(out.count { (it and 0xFF) == 0 || (it and 0xFF) == 255 } < out.size / 100) // 뭉개지거나 날아간 곳이 거의 없다
    }

    @Test
    fun textureMinusSoftensFineDetail() {
        val src = scene(); val out = run(src, -1f, 0f)
        assertTrue("fine ${fine(src)} -> ${fine(out)}", fine(out) < fine(src) * 0.6f)
    }

    @Test
    fun clarityMinusLowersLocalContrast() {
        val src = scene(); val out = run(src, 0f, -1f)
        assertTrue("local ${local(src)} -> ${local(out)}", local(out) < local(src) * 0.6f)
    }

    @Test
    fun bothTogetherAreIndependentAndAdditive() {
        val src = scene()
        val t = run(src, 1f, 0f); val c = run(src, 0f, 1f); val both = run(src, 1f, 1f)
        assertTrue(fine(both) >= fine(t) * 0.95f)
        assertTrue(local(both) >= local(c) * 0.95f)
        // 각 변화량을 원본에서 따로 계산해 더하므로, 같이 쓴 결과는 따로 쓴 두 변화량의 합과 같다(반올림 오차 2단계 이내)
        for (i in src.indices) for (shift in intArrayOf(16, 8, 0)) {
            val s = (src[i] shr shift) and 0xFF; val a = (t[i] shr shift) and 0xFF; val b = (c[i] shr shift) and 0xFF; val o = (both[i] shr shift) and 0xFF
            if (s in 3..252 && a in 3..252 && b in 3..252) assertTrue("i=$i", abs(o - (a + b - s)) <= 2)
        }
    }

    @Test
    fun resettingToZeroRestoresTheOriginalFromTheSource() {
        // 비파괴: 값을 여러 번 바꿔도 항상 원본에서 계산하므로, 0으로 돌아오면 원본과 같다
        val src = scene()
        run(src, 1f, 1f); run(src, -1f, -1f)
        assertArrayEquals(src, run(src, 0f, 0f))
    }

    @Test
    fun colorIsPreservedBecauseOnlyLuminanceIsChanged() {
        val src = scene(); val out = run(src, 1f, 1f)
        for (i in src.indices) {
            val rg0 = ((src[i] shr 16) and 0xFF) - ((src[i] shr 8) and 0xFF)
            val rg1 = ((out[i] shr 16) and 0xFF) - ((out[i] shr 8) and 0xFF)
            val bg0 = (src[i] and 0xFF) - ((src[i] shr 8) and 0xFF)
            val bg1 = (out[i] and 0xFF) - ((out[i] shr 8) and 0xFF)
            assertTrue(abs(rg1 - rg0) <= 1 && abs(bg1 - bg0) <= 1)
        }
    }

    @Test
    fun texturePlusDoesNotAmplifyPlainNoiseMuch() {
        var seed = 12345
        fun noise(): Int { seed = seed * 1103515245 + 12345; return ((seed shr 16) and 0x7FFF) % 7 - 3 } // -3..+3
        val src = IntArray(w * h) { px(128 + noise(), 128 + noise(), 128 + noise()) }
        val out = run(src, 1f, 0f)
        fun std(a: IntArray): Float { val m = meanLum(a); return sqrt(mean(FloatArray(a.size) { (lum(a[it]) - m) * (lum(a[it]) - m) })) }
        assertTrue("noise ${std(src)} -> ${std(out)}", std(out) < std(src) * 1.35f)
    }

    @Test
    fun clarityKeepsHaloSmallAtAHardEdge() {
        val step = IntArray(w * h) { i -> if (i % w < w / 2) px(80, 80, 80) else px(180, 180, 180) }
        val out = run(step, 0f, 1f)
        val maxOvershoot = out.maxOf { (it and 0xFF) - 180 }
        val maxUndershoot = out.maxOf { 80 - (it and 0xFF) }
        assertTrue("over $maxOvershoot under $maxUndershoot", maxOvershoot <= 25 && maxUndershoot <= 25) // 윤곽 높이(100)의 25% 이하
        assertTrue(out.count { (it and 0xFF) in 100..160 && (it and 0xFF) != 80 && (it and 0xFF) != 180 } < w * 4) // 윤곽 가운데는 그대로 가파르다
    }
}
