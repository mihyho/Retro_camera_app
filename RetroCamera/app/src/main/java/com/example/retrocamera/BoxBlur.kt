package com.example.retrocamera

import java.util.stream.IntStream

/**
 * ARGB 이미지의 박스 블러(가로 -> 세로 분리형, 가장자리는 가장 가까운 픽셀로 채움).
 * LUT를 입힐 때 세부/노이즈와 색감을 분리하는 "흐린 버전"을 만든다. 알파는 255로 둔다.
 * 줄(가로 패스)과 열(세로 패스)은 서로 독립이라 CPU 코어 수만큼 나눠서 계산한다.
 */
object BoxBlur {

    /** 한 변 2r+1 크기의 박스 블러. 결과 크기는 w*h. */
    fun blurArgb(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src.copyOf(w * h)
        val tmp = IntArray(w * h)      // 가로 블러 결과를 채널당 8비트가 아닌 3채널 합으로 보관하지 않고 다시 ARGB로
        val out = IntArray(w * h)
        val win = 2 * r + 1
        // 가로: 줄마다 독립
        IntStream.range(0, h).parallel().forEach { y ->
            val row = y * w
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[row + clamp(i, w)]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = pack(sr, sg, sb, win)
                val add = src[row + clamp(x + r + 1, w)]
                val sub = src[row + clamp(x - r, w)]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        // 세로: 열 묶음(캐시에 맞게 16열씩)마다 독립
        val band = 16
        IntStream.range(0, (w + band - 1) / band).parallel().forEach { bi ->
            val x0 = bi * band
            val x1 = minOf(w, x0 + band)
            for (x in x0 until x1) {
                var sr = 0; var sg = 0; var sb = 0
                for (i in -r..r) {
                    val c = tmp[clamp(i, h) * w + x]
                    sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
                }
                for (y in 0 until h) {
                    out[y * w + x] = pack(sr, sg, sb, win)
                    val add = tmp[clamp(y + r + 1, h) * w + x]
                    val sub = tmp[clamp(y - r, h) * w + x]
                    sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                    sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                    sb += (add and 0xFF) - (sub and 0xFF)
                }
            }
        }
        return out
    }

    private fun clamp(i: Int, n: Int) = if (i < 0) 0 else if (i >= n) n - 1 else i

    private fun pack(sr: Int, sg: Int, sb: Int, win: Int): Int =
        (0xFF shl 24) or (((sr + win / 2) / win) shl 16) or (((sg + win / 2) / win) shl 8) or ((sb + win / 2) / win)
}
