package com.example.retrocamera

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 명료도 낮춤 = 가우시안 흐림 효과를 원본에 섞은 것. 단, **피사체의 경계선은 흐려지지 않게 보호**한다.
 *   결과 = 원본 + (흐림 - 원본) * (-clarity) * w
 *   w = 1 - smoothstep(EDGE_LOW, EDGE_HIGH, |원본 밝기 - 흐림 밝기|)
 * 원본과 흐림의 차이가 큰 곳은 뚜렷한 경계(사물과 배경을 가르는 선)이므로 w가 0에 가까워 그대로 남고,
 * 차이가 작은 곳(질감, 면의 거친 느낌, 약한 대비)은 w가 1이라 흐림이 섞인다.
 * (실사진에서 단순 가우시안 25%는 가장 강한 경계선의 기울기를 약 21% 깎았고, 이 방식은 6% 이내로 지킨다.)
 * clarity < 0 이면 흐려지고(예: -0.40 = 흐림 40% 혼합), > 0 이면 원본과 흐림의 차이를 더해 선명해진다.
 * 프리뷰 셰이더(LutRenderer)와 같은 수식이다. 흐림 세기는 이미지 폭에 비례해서 프리뷰와 촬영본이 같은 느낌이 된다.
 */
object Clarity {

    /** 경계로 보지 않는 최대 밝기 차(0~1). 이보다 작으면 흐림이 온전히 섞인다 */
    const val EDGE_LOW = 0.03f

    /** 뚜렷한 경계로 보는 밝기 차(0~1). 이보다 크면 흐림이 섞이지 않는다 */
    const val EDGE_HIGH = 0.14f

    /** 가우시안 표준편차(px): 이미지 폭의 0.22% (폭 1280에서 약 2.8px) */
    fun sigma(width: Int): Float = maxOf(2f, width * 0.0022f)

    /**
     * 가우시안 근사용 박스 블러 반경. 같은 반경의 박스 블러를 2번 하면 분산이 2*r(r+1)/3 이므로
     * sigma^2 = 2*r(r+1)/3 을 풀어 r을 구한다.
     */
    fun boxRadius(width: Int): Int {
        val s = sigma(width)
        return maxOf(1, Math.round((-1f + sqrt(1f + 6f * s * s)) / 2f))
    }

    /** 박스 블러 2회(가우시안 근사). 가장자리가 올바르려면 호출하는 쪽에서 위아래로 2*boxRadius 행의 여백을 읽어야 한다. */
    fun blur(src: IntArray, w: Int, h: Int, r: Int): IntArray = BoxBlur.blurArgb(BoxBlur.blurArgb(src, w, h, r), w, h, r)

    /** 경계 보호 가중치: 밝기 차(0~1)가 작으면 1, 크면 0 */
    fun edgeWeight(lumaDiff: Float): Float {
        val t = ((abs(lumaDiff) - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW)).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    /** px의 [from, to) 구간을 제자리에서 변환. blurred는 px와 같은 인덱스를 쓰는 [blur] 결과 */
    fun applyArgb(px: IntArray, blurred: IntArray, from: Int, to: Int, clarity: Float) {
        if (clarity == 0f) return
        val k = -clarity
        for (i in from until to) {
            val c = px[i]
            val b = blurred[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val bl = c and 0xFF
            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF
            val dy = ((0.299f * r + 0.587f * g + 0.114f * bl) - (0.299f * br + 0.587f * bg + 0.114f * bb)) / 255f
            val kk = k * edgeWeight(dy)
            px[i] = (c and 0xFF000000.toInt()) or
                (clamp(r + (br - r) * kk) shl 16) or
                (clamp(g + (bg - g) * kk) shl 8) or
                clamp(bl + (bb - bl) * kk)
        }
    }

    private fun clamp(v: Float): Int {
        val i = (v + 0.5f).toInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }
}
