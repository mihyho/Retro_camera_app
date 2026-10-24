package com.example.retrocamera.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.retrocamera.R

/** 디자인 핸드오프(2a 실버 × 블랙) 토큰. 값은 design_handoff_phone_film/README.md 기준. */
object PF {
    val Black = Color(0xFF141414)
    val Light = Color(0xFFF0F0EE)
    val Accent400 = Color(0xFFB0B0AE)
    val ToastBg = Color(0xFF201E1D)
}

object PFFonts {
    // 가변 폰트 하나 대신 굵기별 정적 폰트를 쓴다: 가변 폰트의 기본 굵기(Light)가 한글 대체 폰트의 굵기 선택을 망쳤다
    val Figtree = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
        Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
    )
    /** 제목용: 디카 LCD 느낌의 고정폭 글꼴(픽셀 폰트는 내려받아야 해서 일단 시스템 고정폭) */
    val Heading = FontFamily.Monospace
}

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
