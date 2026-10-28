package com.example.retrocamera.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 디자인 핸드오프(iPhone 6 / iOS 8 톤, 흰 바탕 + 필름 옐로우) 토큰. 값은 design_handoff_film_camera/README.md 기준. */
object Ds {
    val Bg = Color(0xFFFFFFFF)
    val Bg2 = Color(0xFFF7F7F7)
    val Ink = Color(0xFF1C1C1E)
    val Sub = Color(0xFF8E8E93)
    val Line = Color(0xFFD9D9DE)
    /** 노란색은 채움 + 검은 글자로만 쓴다. 흰 바탕 위 글자는 AccentInk */
    val Accent = Color(0xFFE8B84A)
    val AccentPress = Color(0xFFD4A43A)
    val AccentInk = Color(0xFF8A6400)
    val AccentBorder = Color(0xFFC99A2E)
    val Neutral = Color(0xFFE8E2D7)
    val Danger = Color(0xFFD70015)
    val Stamp = Color(0xFFFF9A3C)
}

/** "Helvetica Neue / Apple SD Gothic Neo / Noto Sans KR" 대신 안드로이드 기본 산세리프(Roboto + Noto Sans CJK) */
val Sans = FontFamily.SansSerif

fun sansStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Sans, fontSize = size, fontWeight = weight)

/** material3 없이 쓰는 최소 Text. 색은 style이 아니라 color 인자로 넘긴다. */
@Composable
fun Text(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) = BasicText(
    text, modifier, style.copy(color = color), overflow = TextOverflow.Clip, softWrap = softWrap, maxLines = maxLines,
)

/** 물결/하이라이트 없는 탭 (iOS 7 톤) */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)
}

val t11 = sansStyle(11.sp)
val t12 = sansStyle(12.sp)
val t13 = sansStyle(13.sp)
val t15 = sansStyle(15.sp)
val t17 = sansStyle(17.sp)
val t17m = sansStyle(17.sp, FontWeight.Medium)
