package com.example.retrocamera

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class StripSeamTest {
    private fun image(w: Int, h: Int): IntArray {
        val rnd = Random(7)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val v = (128 + 90 * Math.sin(x / 37.0) * Math.cos(y / 53.0) + rnd.nextGaussian() * 6).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    /** 띠 단위로 처리한 결과가 통째로 처리한 결과와 같아야 한다(띠 경계 줄무늬 방지) */
    @Test fun stripsMatchWholeImage() {
        val w = 400; val h = 1000; val strip = 256
        val r = PhotoPipeline.detailRadius(w); val rLC = FilmDetail.largeRadius(w)
        val src = image(w, h)

        val whole = src.copyOf()
        FilmDetail.applyArgb(whole, BoxBlur.blurArgb(src, w, h, r), FilmDetail.blurLarge(src, w, h, rLC), 0, w * h, 0.23f, 0.28f)

        val out = src.copyOf()
        val margin = maxOf(r, 2 * rLC)
        var y = 0
        while (y < h) {
            val rows = minOf(strip, h - y)
            val top = maxOf(0, y - margin); val bottom = minOf(h, y + rows + margin)
            val block = src.copyOfRange(top * w, bottom * w)
            val base = BoxBlur.blurArgb(block, w, bottom - top, r)
            val wide = FilmDetail.blurLarge(block, w, bottom - top, rLC)
            val from = (y - top) * w; val to = from + rows * w
            FilmDetail.applyArgb(block, base, wide, from, to, 0.23f, 0.28f)
            System.arraycopy(block, from, out, y * w, rows * w)
            y += rows
        }
        assertSame(w, h, src, out, whole)
    }

    /** 모노크롬 필름의 전체 처리(디테일 + LUT + 그레인)도 띠 처리와 통째 처리가 같아야 한다 */
    @Test fun monochromeFullPipelineStripsMatchWhole() {
        val film = com.example.retrocamera.data.Film.MONOCHROME
        val lut = FilmLook.bake(null, film.custom!!.adjust)
        val w = 400; val h = 1000; val strip = 256
        val r = PhotoPipeline.detailRadius(w); val rLC = FilmDetail.largeRadius(w)
        val src = image(w, h)
        fun run(block: IntArray, rows: Int, from: Int, to: Int, top: Int) {
            val base = BoxBlur.blurArgb(block, w, rows, r)
            val wide = FilmDetail.blurLarge(block, w, rows, rLC)
            FilmDetail.applyArgb(block, base, wide, from, to, film.texture, film.localContrast)
            LutApplier.applyDetailSafe(block, base, from, to, lut)
            Grain.applyArgb(block, from, to, w, top, strength = film.grain)
        }
        val whole = src.copyOf(); run(whole, h, 0, w * h, 0)
        val out = src.copyOf()
        val margin = maxOf(r, 2 * rLC)
        var y = 0
        while (y < h) {
            val rows = minOf(strip, h - y)
            val top = maxOf(0, y - margin); val bottom = minOf(h, y + rows + margin)
            val block = src.copyOfRange(top * w, bottom * w)
            val from = (y - top) * w
            run(block, bottom - top, from, from + rows * w, top)
            System.arraycopy(block, from, out, y * w, rows * w)
            y += rows
        }
        assertSame(w, h, src, out, whole)
    }

    private fun assertSame(w: Int, h: Int, src: IntArray, out: IntArray, whole: IntArray) {
        var worst = 0; var at = -1
        for (i in out.indices) { val d = Math.abs((out[i] and 0xFF) - (whole[i] and 0xFF)); if (d > worst) { worst = d; at = i / w } }
        assertEquals("가장 크게 다른 줄 $at", 0, worst)
    }
}
