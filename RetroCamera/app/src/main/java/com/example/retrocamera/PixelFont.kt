package com.example.retrocamera

/**
 * 날짜 각인용 8비트 도트 글꼴(5x7). 프리뷰(Compose)와 저장되는 사진(Canvas)이 같은 글리프를 쓰도록 칸(cell) 좌표만 내보낸다.
 * 날짜 형식(26. 10. 07.)에 쓰는 숫자·마침표·공백만 있다. 그 밖의 글자는 빈칸으로 그린다.
 */
object PixelFont {
    const val ROWS = 7
    const val COLS = 5
    /** 글자 하나가 차지하는 칸 수(글자 5칸 + 사이 1칸) */
    const val ADVANCE = 6
    /** 한 칸 안에서 점이 차지하는 비율. 1보다 작게 하면 획이 얇아진다(점 사이에 틈이 생긴다) */
    const val DOT = 0.62f

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        '.' to listOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    )

    /** 글 전체의 가로 칸 수 */
    fun widthCells(text: String): Int = if (text.isEmpty()) 0 else text.length * ADVANCE - 1

    /** 켜진 칸마다 (가로 칸, 세로 칸)을 알려 준다. 글 왼쪽 위가 (0, 0) */
    inline fun forEachCell(text: String, block: (col: Int, row: Int) -> Unit) {
        text.forEachIndexed { i, ch ->
            val g = glyph(ch) ?: return@forEachIndexed
            for (r in 0 until ROWS) for (c in 0 until COLS) if (g[r][c] == '1') block(i * ADVANCE + c, r)
        }
    }

    fun glyph(ch: Char): List<String>? = GLYPHS[ch]
}
