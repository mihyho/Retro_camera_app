package com.example.retrocamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PixelFontTest {
    @Test fun stampCharactersHaveFullGlyphs() {
        ".0123456789".forEach { ch ->
            val g = PixelFont.glyph(ch)
            assertNotNull("$ch", g)
            assertEquals(PixelFont.ROWS, g!!.size)
            g.forEach { assertEquals(PixelFont.COLS, it.length) }
        }
    }

    @Test fun widthAndCellsStayInsideTheTextBox() {
        val text = "26. 10. 31."
        assertEquals(11 * PixelFont.ADVANCE - 1, PixelFont.widthCells(text))
        PixelFont.forEachCell(text) { c, r ->
            assert(c in 0 until PixelFont.widthCells(text)); assert(r in 0 until PixelFont.ROWS)
        }
    }
}
