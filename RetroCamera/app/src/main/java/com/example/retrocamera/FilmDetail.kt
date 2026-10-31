package com.example.retrocamera

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 텍스처와 명료도(라이트룸 방식). 서로 다른 크기의 디테일을 다루는 독립된 두 보정이다.
 * 둘 다 원본 픽셀에서 밝기(휘도) 차이만 구해 세 채널에 같은 양을 더하므로 색이 틀어지지 않고, 두 변화량을 각각 원본에서 계산해
 * 더하므로 같이 써도 서로 영향을 주지 않는다. 값이 0이면 아무것도 바꾸지 않는다(원본과 같다).
 *
 * 텍스처(미세 질감): 원본 - 아주 작게 흐린 것(base).
 *   (+) 센서 노이즈 수준의 아주 작은 차이와 뚜렷한 윤곽(아주 큰 차이)은 덜 키우고, 그 사이의 질감(피부결·직물·표면)을 키운다.
 *   (-) 윤곽은 지키고 질감과 노이즈를 지운다.
 * 명료도(중간 크기의 국부 대비): 원본 - 넓게 흐린 것(large).
 *   중간 밝기에서 가장 크게 작용해 어둡거나 밝은 곳이 뭉개지지 않고, 한 번에 더하는 양을 부드럽게 제한해 후광을 줄인다.
 *   (+) 윤곽과 입체감이 살고, (-) 국부 대비가 줄어 부드러워진다(-100이면 넓게 흐린 것과 같아질 때까지).
 */
object FilmDetail {

    /** 명료도용 넓은 흐림의 박스 반경(2회 겹쳐 가우시안 근사): 이미지 폭의 1.2%. 해상도가 달라도 같은 모양이 된다 */
    fun largeRadius(width: Int): Int = maxOf(4, (width * 0.012f).roundToInt())

    /** 박스 블러 2회(가우시안 근사). 가장자리가 올바르려면 위아래로 2*r 행의 여백을 함께 읽어야 한다 */
    fun blurLarge(src: IntArray, w: Int, h: Int, r: Int): IntArray = BoxBlur.blurArgb(BoxBlur.blurArgb(src, w, h, r), w, h, r)

    /**
     * px의 [from, to) 구간을 제자리에서 보정한다.
     * @param base px를 아주 작게 흐린 것(텍스처용), large는 [blurLarge] 결과(명료도용, clarity가 0이면 null). 둘 다 변환 전 원본에서 만든다.
     * @param texture -1..+1, clarity -1..+1
     */
    fun applyArgb(px: IntArray, base: IntArray, large: IntArray?, from: Int, to: Int, texture: Float, clarity: Float) {
        if (texture == 0f && (clarity == 0f || large == null)) return
        for (i in from until to) {
            val c = px[i]
            val l = lumaOf(c) / 255f
            var delta = 0f
            if (texture != 0f) {
                val d = l - lumaOf(base[i]) / 255f
                val ad = abs(d)
                // 키울 때는 노이즈 수준과 뚜렷한 윤곽을 피해 가운데 구간만, 줄일 때는 윤곽만 피한다
                val gain = if (texture > 0f) texture * 1.6f * rise(ad) else texture * keep(ad)
                delta += d * gain
            }
            if (clarity != 0f && large != null) {
                val d = l - lumaOf(large[i]) / 255f
                val mid = 4f * l * (1f - l)                                  // 0.5에서 1, 양끝에서 0
                val edge = 1f - 0.85f * smooth(0.08f, 0.30f, abs(d))         // 윤곽처럼 큰 차이(후광이 생기기 쉬운 곳)는 크게 줄인다
                val gain = if (clarity > 0f) clarity * 1.2f else clarity
                delta += softLimit(d * gain * mid * edge, 0.20f)
            }
            if (delta == 0f) continue
            val add = delta * 255f
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            px[i] = (c and 0xFF000000.toInt()) or (clamp(r + add) shl 16) or (clamp(g + add) shl 8) or clamp(b + add)
        }
    }

    private fun lumaOf(c: Int): Float = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 텍스처를 키울 때 디테일 크기(밝기 차 0~1)별 비율: 노이즈 수준(~1%)은 거의 0, 질감(3~12%)은 1, 윤곽(30%~)은 다시 0 */
    private fun rise(ad: Float): Float = smooth(0.008f, 0.035f, ad) * (1f - smooth(0.12f, 0.35f, ad))

    /** 텍스처를 줄일 때 비율: 질감과 노이즈는 1, 뚜렷한 윤곽은 0(윤곽이 번지지 않게) */
    private fun keep(ad: Float): Float = 1f - smooth(0.08f, 0.30f, ad)

    /** 부드럽게 한도(m)에 가까워지는 제한: 작은 값은 거의 그대로, 큰 값은 m을 넘지 않는다 */
    private fun softLimit(x: Float, m: Float): Float = x / (1f + abs(x) / m)

    private fun clamp(v: Float): Int {
        val i = (v + 0.5f).toInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }
}
