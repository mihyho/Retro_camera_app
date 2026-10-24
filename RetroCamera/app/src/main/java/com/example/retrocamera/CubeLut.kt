package com.example.retrocamera

import java.io.InputStream

/**
 * .cube 3D LUT. data는 R이 가장 빨리 변하는 순서의 RGB float (size^3 * 3개).
 * 이 순서가 GL 3D 텍스처의 (width=R, height=G, depth=B)와 그대로 일치한다.
 */
class CubeLut(val size: Int, val data: FloatArray) {
    companion object {
        private val WS = Regex("\\s+")

        fun parse(input: InputStream): CubeLut {
            var size = 0
            var values = FloatArray(0)
            var n = 0
            input.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val parts = line.split(WS)
                    if (line[0].isLetter()) {
                        when (parts[0]) {
                            "LUT_3D_SIZE" -> {
                                size = parts[1].toInt()
                                values = FloatArray(size * size * size * 3)
                            }
                            "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUT는 지원하지 않습니다")
                            "DOMAIN_MIN" -> require(parts.drop(1).all { it.toFloat() == 0f }) { "DOMAIN_MIN은 0만 지원합니다" }
                            "DOMAIN_MAX" -> require(parts.drop(1).all { it.toFloat() == 1f }) { "DOMAIN_MAX는 1만 지원합니다" }
                        }
                        continue
                    }
                    require(size > 0) { "LUT_3D_SIZE가 데이터보다 먼저 와야 합니다" }
                    for (i in 0 until 3) values[n++] = parts[i].toFloat()
                }
            }
            require(size > 0 && n == values.size) { "LUT 데이터 개수가 맞지 않습니다 ($n / ${values.size})" }
            return CubeLut(size, values)
        }
    }
}
