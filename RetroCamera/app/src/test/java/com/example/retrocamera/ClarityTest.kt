package com.example.retrocamera

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 명료도 낮춤 = 가우시안 흐림을 섞되, 피사체의 뚜렷한 경계선은 보호한다. 그 정의대로 동작하는지 검증한다. */
class ClarityTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun gray(v: Int) = rgb(v, v, v)
    private fun ch(px: Int, shift: Int) = (px shr shift) and 0xFF

    /** 가운데 세로선만 다른 이미지 */
    private fun lineImage(w: Int, h: Int, base: Int, line: Int): IntArray =
        IntArray(w * h) { if (it % w == w / 2) line else base }

    /** 왼쪽 절반 left, 오른쪽 절반 right 인 계단 경계 이미지 */
    private fun stepImage(w: Int, h: Int, left: Int, right: Int): IntArray =
        IntArray(w * h) { if (it % w < w / 2) left else right }

    private fun blurOf(src: IntArray, w: Int, h: Int, r: Int = 3) = Clarity.blur(src, w, h, r)

    @Test
    fun zeroAmountDoesNothing() {
        val w = 64; val h = 16
        val src = lineImage(w, h, gray(100), gray(112))
        val out = src.copyOf()
        Clarity.applyArgb(out, blurOf(src, w, h), 0, out.size, 0f)
        assertTrue(out.contentEquals(src))
    }

    @Test
    fun flatAreaIsUntouched() {
        val w = 64; val h = 16
        val src = IntArray(w * h) { gray(128) }
        val out = src.copyOf()
        Clarity.applyArgb(out, blurOf(src, w, h), 0, out.size, -0.5f)
        assertTrue("평탄한 영역은 흐림과 같아서 변하지 않아야 한다", out.contentEquals(src))
    }

    /** 약한 질감(밝기 차가 경계 기준보다 작음)은 정의 그대로 '원본 + (흐림 - 원본) * 강도' 가 된다 */
    @Test
    fun subtleDetailIsBlendedWithGaussian() {
        val w = 64; val h = 16
        val src = lineImage(w, h, rgb(100, 90, 80), rgb(108, 98, 88)) // 밝기 차 8 = 0.03 미만 -> 보호 없음
        val blur = blurOf(src, w, h)
        val out = src.copyOf()
        Clarity.applyArgb(out, blur, 0, out.size, -0.4f)
        for (i in src.indices) for (shift in intArrayOf(16, 8, 0)) {
            val c = ch(src[i], shift); val b = ch(blur[i], shift)
            val expected = Math.round(c + (b - c) * 0.4f).coerceIn(0, 255)
            assertEquals("픽셀 $i 채널 $shift", expected, ch(out[i], shift))
        }
    }

    /** 핵심 요구: 피사체의 경계선이 뭉개지지 않는다 */
    @Test
    fun strongEdgesAreProtected() {
        val w = 64; val h = 16
        val src = stepImage(w, h, gray(30), gray(220))              // 뚜렷한 경계(차이 190)
        val blur = blurOf(src, w, h)
        val out = src.copyOf()
        Clarity.applyArgb(out, blur, 0, out.size, -0.4f)
        val e = w / 2
        val row = (h / 2) * w
        val before = ch(src[row + e], 8) - ch(src[row + e - 1], 8)
        val after = ch(out[row + e], 8) - ch(out[row + e - 1], 8)
        assertTrue("경계선의 대비가 거의 그대로여야 한다: $before -> $after", after >= before * 0.93)

        // 보호가 없었다면(순수 가우시안 40% 혼합) 같은 곳이 실제로 크게 흐려졌다는 것도 확인(테스트가 의미를 갖도록)
        val plain = src.copyOf()
        for (i in plain.indices) {
            val c = ch(src[i], 8); val b = ch(blur[i], 8)
            plain[i] = gray(Math.round(c + (b - c) * 0.4f).coerceIn(0, 255))
        }
        val plainAfter = ch(plain[row + e], 8) - ch(plain[row + e - 1], 8)
        assertTrue("보호가 없으면 경계가 눈에 띄게 약해진다: $before -> $plainAfter", plainAfter < before * 0.85)
    }

    @Test
    fun subtleTextureIsSoftened() {
        // 약한 질감(밝기 차가 경계 기준보다 작은 가는 선)은 확실히 부드러워진다
        val w = 96; val h = 16
        val src = lineImage(w, h, gray(100), gray(107))
        val out = src.copyOf()
        Clarity.applyArgb(out, blurOf(src, w, h, Clarity.boxRadius(w)), 0, out.size, -0.4f)
        val before = ch(src[h / 2 * w + w / 2], 8) - ch(src[h / 2 * w + 5], 8)
        val after = ch(out[h / 2 * w + w / 2], 8) - ch(out[h / 2 * w + 5], 8)
        val ratio = after.toDouble() / before
        assertTrue("질감 대비 비율 $ratio", ratio in 0.45..0.80)
    }

    @Test
    fun edgeWeightIsOneForSubtleAndZeroForStrong() {
        assertEquals(1f, Clarity.edgeWeight(0f), 1e-6f)
        assertEquals(1f, Clarity.edgeWeight(Clarity.EDGE_LOW - 0.001f), 1e-6f)
        assertEquals(0f, Clarity.edgeWeight(Clarity.EDGE_HIGH + 0.001f), 1e-6f)
        assertEquals(0f, Clarity.edgeWeight(-0.5f), 1e-6f) // 부호와 무관
        assertTrue(Clarity.edgeWeight((Clarity.EDGE_LOW + Clarity.EDGE_HIGH) / 2) in 0.4f..0.6f)
    }

    @Test
    fun negativeSoftensAndPositiveSharpens() {
        val w = 64; val h = 16
        val src = lineImage(w, h, gray(110), gray(118))             // 약한 대비라 보호 영역 밖
        val blur = blurOf(src, w, h)
        val base = ch(src[h / 2 * w + w / 2], 8) - ch(src[h / 2 * w + 5], 8)
        val soft = src.copyOf(); Clarity.applyArgb(soft, blur, 0, soft.size, -0.4f)
        val sharp = src.copyOf(); Clarity.applyArgb(sharp, blur, 0, sharp.size, +0.4f)
        assertTrue(ch(soft[h / 2 * w + w / 2], 8) - ch(soft[h / 2 * w + 5], 8) < base)
        assertTrue(ch(sharp[h / 2 * w + w / 2], 8) - ch(sharp[h / 2 * w + 5], 8) > base)
    }

    @Test
    fun twoBoxBlursApproximateAGaussian() {
        // 박스 블러 2회가 같은 표준편차의 진짜 가우시안과 가까운지: 임펄스 응답을 비교
        val w = 129; val h = 1
        val src = IntArray(w) { if (it == w / 2) gray(255) else gray(0) }
        val r = 4
        val blurred = Clarity.blur(src, w, h, r)
        val sigma = sqrt(2.0 * r * (r + 1) / 3.0)
        val gauss = DoubleArray(w) { exp(-0.5 * ((it - w / 2) / sigma) * ((it - w / 2) / sigma)) }
        val gsum = gauss.sum()
        var maxErr = 0.0
        for (i in 0 until w) maxErr = maxOf(maxErr, abs(ch(blurred[i], 8) - 255.0 * gauss[i] / gsum))
        assertTrue("가우시안과의 최대 오차 $maxErr (0~255 단위)", maxErr < 4.0)
    }

    @Test
    fun boxRadiusMatchesSigma() {
        for (w in intArrayOf(1280, 4000)) {
            val r = Clarity.boxRadius(w)
            val sigmaFromRadius = sqrt(2.0 * r * (r + 1) / 3.0)
            assertTrue("폭 $w: 목표 ${Clarity.sigma(w)} vs 반경 $r 의 표준편차 $sigmaFromRadius",
                abs(sigmaFromRadius - Clarity.sigma(w)) < 0.6 * Clarity.sigma(w) / 2.8 + 0.5)
        }
    }
}
