package com.example.retrocamera.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.retrocamera.data.Film
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 다크 글래스 토큰. 포인트 색(Accent)은 지금 고른 필름 색으로 바뀐다([liveFilm]). */
object Ds {
    val Bg = Color(0xFF161410)
    val Bg2 = Color(0xFF0A0908)
    val Ink = Color(0xFFF5F1E8)
    val Sub = Color(0xFF9A968C)
    val Line = Color(0x29FFF8E6)
    /** 뷰파인더 위에 뜨는 반투명 칩/패널(블러 없이 색만) */
    val Glass = Color(0xC21E1B16)
    /** 포인트 색 위의 글자/아이콘 */
    val OnAccent = Color(0xFF14110C)
    val Neutral = Color(0xFF201D17)
    val Danger = Color(0xFFFF6B5E)
    val Stamp = Color(0xFFFF9A3C)

    /** 지금 보이는 필름. 스트립을 넘기는 중에는 손가락에 가장 가까운 필름이라, 포인트 색이 바로바로 따라 바뀐다 */
    var liveFilm by mutableStateOf<Film?>(null)
    val Accent get() = liveFilm?.glow() ?: Color(0xFFE8B84A)
    val AccentPress get() = lerp(Accent, Color.Black, .15f)
    /** 어두운 바탕 위 글자/링크는 포인트 색 그대로 */
    val AccentInk get() = Accent
    val AccentBorder get() = Accent
}

/** 필름별 포인트 색. Film.accent는 흰 바탕용이라 어둡다 */
fun Film.glow(): Color = Color(when (this) {
    Film.TUNGSTEN -> 0xFF5C9DEB
    Film.GOLD -> 0xFFE8B84A
    Film.FOREST -> 0xFF6CBF67
    Film.EKTAR -> 0xFFE5534B
    Film.KGOLD -> 0xFFF0B83C
    Film.PORTRA -> 0xFFE0906E
    Film.PROVIA -> 0xFF4FA6E0
    Film.AUTUMN -> 0xFFE88A3A
    Film.CHROME -> 0xFF6FB3B8
    Film.LEICA_STD -> 0xFFD9564C
    Film.LEICA_VIV -> 0xFFE86A4A
    Film.FPROVIA -> 0xFF5FA8DE
    Film.KCHROME -> 0xFFE6B422
    Film.CINE -> 0xFF3FB0B8
    Film.FADED -> 0xFFC9B8A0
    Film.NIGHT -> 0xFFE0584A
    else -> custom?.accent ?: 0xFFE8B84A // 사용자가 만든 필름은 고른 포인트 색
})

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
