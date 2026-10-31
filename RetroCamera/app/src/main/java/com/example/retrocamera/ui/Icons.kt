package com.example.retrocamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/** Phosphor Light 느낌의 선 아이콘(24 격자). 외부 아이콘 파일 없이 Canvas로 그린다. */
enum class Ico { BOLT, EXPOSURE, FLIP, GEAR, IMAGES, CIRCLES, LOCK, CHECK, EXPORT, TRASH, CHEVRON_LEFT, CAMERA, PROHIBIT, PLUS, UNDO, REDO, COMPARE, CHEVRON_DOWN }

@Composable
fun Icon(kind: Ico, size: Dp, color: Color, modifier: Modifier = Modifier, fill: Boolean = false) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        val sw = 1.6f * k
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
        fun poly(vararg pts: Float, close: Boolean = false, filled: Boolean = false) {
            val path = Path().apply {
                moveTo(pts[0] * k, pts[1] * k)
                for (i in 2 until pts.size step 2) lineTo(pts[i] * k, pts[i + 1] * k)
                if (close) close()
            }
            drawPath(path, color, style = if (filled) Fill else st)
        }
        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(color, p(x, y), Size(w * k, h * k), CornerRadius(r * k), style = st)
        fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) =
            drawCircle(color, r * k, p(cx, cy), style = if (filled) Fill else st)

        when (kind) {
            Ico.BOLT -> poly(13f, 3f, 5.5f, 13.5f, 11f, 13.5f, 10f, 21f, 18.5f, 10f, 13f, 10f, close = true, filled = fill)
            Ico.EXPOSURE -> {
                line(7f, 3.5f, 7f, 9.5f); line(4f, 6.5f, 10f, 6.5f); line(14f, 17.5f, 20f, 17.5f); line(19f, 4.5f, 5f, 19.5f)
            }
            Ico.FLIP -> {
                rrect(2.5f, 6f, 19f, 14f, 3f)
                arrowArc(color, k, 12f, 13f, 3.6f, 200f)
                arrowArc(color, k, 12f, 13f, 3.6f, 20f)
            }
            Ico.GEAR -> {
                // 이 6개짜리 윤곽 톱니(이 사이가 파인 모양). 햇살로 보이지 않게 이 폭을 넓게 둔다
                val gear = Path()
                for (i in 0 until 6) {
                    for ((da, r) in listOf(-16.0 to 6.6f, -10.0 to 8.8f, 10.0 to 8.8f, 16.0 to 6.6f)) {
                        val a = Math.toRadians(i * 60.0 + da)
                        val x = (12f + r * sin(a).toFloat()) * k; val y = (12f - r * cos(a).toFloat()) * k
                        if (i == 0 && da == -16.0) gear.moveTo(x, y) else gear.lineTo(x, y)
                    }
                }
                gear.close()
                drawPath(gear, color, style = st)
                circle(12f, 12f, 2.8f)
            }
            Ico.IMAGES -> {
                rrect(3f, 5f, 18f, 14f, 2f); circle(8.5f, 10f, 1.5f)
                poly(3.5f, 17f, 9f, 12f, 13f, 16f, 16f, 13.5f, 20.5f, 17.5f)
            }
            Ico.CIRCLES -> { circle(12f, 7f, 4f); circle(7f, 15.5f, 4f); circle(17f, 15.5f, 4f) }
            Ico.LOCK -> {
                rrect(5.5f, 11f, 13f, 9.5f, 2f)
                val sh = Path().apply { moveTo(8.5f * k, 11f * k); lineTo(8.5f * k, 8f * k); arcTo(androidx.compose.ui.geometry.Rect(p(8.5f, 4f), Size(7f * k, 7f * k)), 180f, 180f, false); lineTo(15.5f * k, 11f * k) }
                drawPath(sh, color, style = st)
            }
            Ico.CHECK -> poly(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f)
            Ico.EXPORT -> {
                line(12f, 14.5f, 12f, 3f); poly(8f, 7f, 12f, 3f, 16f, 7f)
                poly(7.5f, 10f, 5f, 10f, 5f, 21f, 19f, 21f, 19f, 10f, 16.5f, 10f)
            }
            Ico.TRASH -> {
                line(4f, 7f, 20f, 7f); poly(6f, 7f, 6f, 20f, 18f, 20f, 18f, 7f); poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                line(10f, 11f, 10f, 16f); line(14f, 11f, 14f, 16f)
            }
            Ico.CHEVRON_LEFT -> poly(15f, 4.5f, 8f, 12f, 15f, 19.5f)
            Ico.CAMERA -> {
                rrect(2.5f, 6.5f, 19f, 13.5f, 3f); circle(12f, 13.2f, 3.8f)
                poly(8f, 6.5f, 9.5f, 4f, 14.5f, 4f, 16f, 6.5f)
            }
            Ico.PROHIBIT -> { circle(12f, 12f, 9f); line(5.7f, 5.7f, 18.3f, 18.3f) }
            Ico.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            // 되돌리기/다시: 반원 화살표
            Ico.UNDO -> { poly(9f, 5f, 4.5f, 9.5f, 9f, 14f); val p = Path().apply { moveTo(4.5f * k, 9.5f * k); lineTo(14f * k, 9.5f * k); cubicTo(18.5f * k, 9.5f * k, 19.5f * k, 18f * k, 14f * k, 18f * k); lineTo(9f * k, 18f * k) }; drawPath(p, color, style = st) }
            Ico.REDO -> { poly(15f, 5f, 19.5f, 9.5f, 15f, 14f); val p = Path().apply { moveTo(19.5f * k, 9.5f * k); lineTo(10f * k, 9.5f * k); cubicTo(5.5f * k, 9.5f * k, 4.5f * k, 18f * k, 10f * k, 18f * k); lineTo(15f * k, 18f * k) }; drawPath(p, color, style = st) }
            // 원본과 비교: 둘로 나뉜 사각형
            Ico.COMPARE -> { rrect(3.5f, 5f, 17f, 14f, 2f); line(12f, 5f, 12f, 19f); poly(12f, 5f, 20.5f, 5f, 20.5f, 19f, 12f, 19f, close = true, filled = true) }
            Ico.CHEVRON_DOWN -> poly(5f, 9f, 12f, 16f, 19f, 9f)
        }
    }
}

/** 원호 + 화살촉(시계 방향). 카메라 전환 아이콘용 */
private fun DrawScope.arrowArc(color: Color, k: Float, cx: Float, cy: Float, r: Float, start: Float) {
    val sw = 1.5f * k
    drawArc(color, start, 130f, false, Offset((cx - r) * k, (cy - r) * k), Size(2 * r * k, 2 * r * k), style = Stroke(sw, cap = StrokeCap.Round))
    val a = Math.toRadians((start + 130f).toDouble())
    val n = Offset(cos(a).toFloat(), sin(a).toFloat())
    val t = Offset(-n.y, n.x)
    val c = Offset(cx * k, cy * k) + n * (r * k)
    val head = 1.9f * k
    drawPath(Path().apply {
        moveTo((c + t * head * 1.3f).x, (c + t * head * 1.3f).y)
        lineTo((c + n * head).x, (c + n * head).y)
        lineTo((c - n * head).x, (c - n * head).y)
        close()
    }, color)
}
