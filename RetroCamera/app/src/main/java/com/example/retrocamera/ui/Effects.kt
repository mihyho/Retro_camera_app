package com.example.retrocamera.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.util.Random

/**
 * feTurbulence(fractalNoise)를 흉내 낸 그레인 타일. 픽셀마다 독립 노이즈라 타일 이음새가 없다.
 * alpha = maxAlpha * n (n은 평균 .5 부근에 몰린 값, feTurbulence의 알파 채널 분포와 비슷)
 */
fun grainTile(sizePx: Int, rgb: Int, maxAlpha: Float, seed: Long): Bitmap {
    val rnd = Random(seed)
    val px = IntArray(sizePx * sizePx) {
        // 4개 균일분포 평균 -> 종 모양, 평균 .5, 표준편차 ~.14
        val n = ((rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat()) / 4f).coerceIn(0f, 1f)
        val a = (n * maxAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        (a shl 24) or (rgb and 0xFFFFFF)
    }
    return Bitmap.createBitmap(px, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

fun DrawScope.drawTile(bitmap: Bitmap, origin: Offset = Offset.Zero) {
    val m = android.graphics.Matrix().apply { setTranslate(origin.x, origin.y) }
    val p = Paint()
    p.asFrameworkPaint().apply {
        this.shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT).also { it.setLocalMatrix(m) }
        isFilterBitmap = false
    }
    drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, p) }
}
