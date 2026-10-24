package com.example.retrocamera

import java.util.Random
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LUT를 입힐 때 노이즈가 늘지 않는지(디테일 제한)와 박스 블러의 정확성을 검증한다. */
class NoiseSafeTest {

    /** 중간(0.5)을 기준으로 대비를 gain배 높이는 LUT: out = 0.5 + (in - 0.5) * gain */
    private fun contrastLut(gain: Float, n: Int = 17): CubeLut {
        val data = FloatArray(n * n * n * 3)
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            val i = ((b * n + g) * n + r) * 3
            fun f(v: Int) = (0.5f + (v / (n - 1f) - 0.5f) * gain).coerceIn(0f, 1f)
            data[i] = f(r); data[i + 1] = f(g); data[i + 2] = f(b)
        }
        return CubeLut(n, data)
    }

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    /** 평균 128 근처의 잡음 이미지(균일 분포 ±amp) */
    private fun noisyImage(w: Int, h: Int, amp: Int, seed: Long): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { gray(128 + rnd.nextInt(2 * amp + 1) - amp) }
    }

    /** 초록 채널의 표준편차(가장자리 제외) */
    private fun noiseStd(px: IntArray, w: Int, h: Int, margin: Int): Double {
        val v = ArrayList<Double>()
        for (y in margin until h - margin) for (x in margin until w - margin) v += ((px[y * w + x] shr 8) and 0xFF).toDouble()
        val mean = v.average()
        return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
    }

    @Test
    fun plainLutAmplifiesNoise_soTheTestIsMeaningful() {
        val w = 96; val h = 96
        val src = noisyImage(w, h, 8, 1)
        val lut = contrastLut(3f)
        val out = src.copyOf()
        LutApplier.applyArgb(out, 0, out.size, lut)
        val ratio = noiseStd(out, w, h, 4) / noiseStd(src, w, h, 4)
        assertTrue("대비 3배 LUT를 그냥 입히면 노이즈가 약 3배가 되어야 한다: $ratio", ratio > 2.5)
    }

    /**
     * 세부/노이즈는 원본 크기로 제한되지만, 흐린 이미지(base)에 남은 저주파 잔여 노이즈는 LUT 대비만큼 커진다.
     * 그래서 정확히 1.0배는 아니고: 기울기 상한(tools/extract_lut.py의 MAX_GAIN = 2.0) 이내의 LUT는 약 1.1배 이내,
     * 그보다 극단적인 LUT도 그냥 입힐 때(= 게인 배)의 절반 이하로 줄어든다.
     */
    @Test
    fun detailSafeLimitsNoiseAmplification() {
        val w = 96; val h = 96
        for (gain in floatArrayOf(1.5f, 2f, 3f, 6f)) {
            val src = noisyImage(w, h, 8, 2)
            val lut = contrastLut(gain)
            val base = BoxBlur.blurArgb(src, w, h, 2)
            val out = src.copyOf()
            LutApplier.applyDetailSafe(out, base, 0, out.size, lut)
            val ratio = noiseStd(out, w, h, 4) / noiseStd(src, w, h, 4)
            val limit = if (gain <= 2f) 1.10 else gain * 0.5
            assertTrue("대비 ${gain}배 LUT 노이즈 배율 $ratio (허용 $limit, 그냥 입히면 약 $gain)", ratio <= limit)
        }
    }

    @Test
    fun detailSafeKeepsTheLookOnFlatAreas() {
        // 잡음 없는 평탄한 영역에서는 그냥 입힌 것과 같아야 한다(색감이 손상되지 않음)
        val w = 32; val h = 32
        val lut = contrastLut(2f)
        val src = IntArray(w * h) { gray(140) }
        val plain = src.copyOf(); LutApplier.applyArgb(plain, 0, plain.size, lut)
        val safe = src.copyOf()
        LutApplier.applyDetailSafe(safe, BoxBlur.blurArgb(src, w, h, 2), 0, safe.size, lut)
        for (i in src.indices) {
            val a = (plain[i] shr 8) and 0xFF
            val b = (safe[i] shr 8) and 0xFF
            assertTrue("평탄 영역에서 색감 차이: $a vs $b", kotlin.math.abs(a - b) <= 1)
        }
    }

    @Test
    fun detailSafeStillAppliesTheLowFrequencyGrade() {
        // 평균 색(저주파)은 LUT대로 바뀐다: 평균 128 + 잡음 -> 평균은 LUT(128)=128 이지만, 160 평균이면 대비 2배로 192 근처
        val w = 96; val h = 96
        val rnd = Random(3)
        val src = IntArray(w * h) { gray(160 + rnd.nextInt(9) - 4) }
        val out = src.copyOf()
        LutApplier.applyDetailSafe(out, BoxBlur.blurArgb(src, w, h, 2), 0, out.size, contrastLut(2f))
        var sum = 0.0; var n = 0
        for (y in 4 until h - 4) for (x in 4 until w - 4) { sum += (out[y * w + x] shr 8) and 0xFF; n++ }
        val mean = sum / n
        // 기대: 0.5 + (160/255 - 0.5) * 2 = 0.7549 -> 약 192
        assertTrue("저주파 색감은 그대로 적용되어야 한다: 평균 $mean", kotlin.math.abs(mean - 192.5) < 3)
    }

    @Test
    fun boxBlurOfConstantImageIsConstant() {
        val w = 20; val h = 15
        val out = BoxBlur.blurArgb(IntArray(w * h) { gray(77) }, w, h, 3)
        assertTrue(out.all { it == gray(77) })
    }

    @Test
    fun boxBlurAveragesAHorizontalStep() {
        // 한 줄 0..9 에서 반경 1 블러: 가운데 값은 이웃 3개 평균(가장자리는 가장 가까운 픽셀로 채움)
        val w = 10; val h = 1
        val src = IntArray(w) { gray(it * 10) }
        val out = BoxBlur.blurArgb(src, w, h, 1)
        assertEquals((20 + 30 + 40) / 3, (out[3] shr 8) and 0xFF)
        assertEquals(Math.round((0 + 0 + 10) / 3f), (out[0] shr 8) and 0xFF) // 왼쪽 가장자리
        assertEquals(Math.round((80 + 90 + 90) / 3f), (out[9] shr 8) and 0xFF) // 오른쪽 가장자리(블러는 반올림)
    }
}
