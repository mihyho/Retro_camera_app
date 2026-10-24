package com.example.retrocamera.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.retrocamera.ThumbLoader
import com.example.retrocamera.data.DevelopedRoll
import com.example.retrocamera.data.Roll
import com.example.retrocamera.data.Shot
import com.example.retrocamera.formatStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 디자인 프로토타입의 보조 화면 팔레트 (루트에서 덮어쓴 값) */
object PFLight {
    // LCD 팔레트(어두운 화면 + 주황 포인트). 이름은 예전 밝은 테마에서 이어졌다.
    val Bg = Color(0xFF0B1118)
    val Surface = Color(0xFF16212C)
    val Text = Color(0xFFE4ECF2)
    val Neutral300 = Color(0xFF5F6F7C)
    val Neutral400 = Color(0xFF4A5864)
    val Neutral700 = Color(0xFF93A5B3)
    val Accent = Color(0xFFFF7A1A)
    val Sage = Color(0xFFFF7A1A)
    val SageBg = Color(0xFF1B2A38)
    val SageInk = Color(0xFFD5E2EC)
    val Inverse = Color(0xFF000000)
}

private val panelGrain by lazy { grainTile(180, 0x000000, .20f, 33L) }

private val h1 = TextStyle(fontFamily = PFFonts.Heading, fontSize = 26.sp, letterSpacing = (-.015).em)
private val h2 = TextStyle(fontFamily = PFFonts.Heading, fontSize = 20.sp)
private val body13 = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 13.sp)

// ───────────────────────── 사진(갤러리) ─────────────────────────

/** 현상 대기 중 카드: 사진은 보여 주지 않고 찍은 칸만 채운다 */
@Composable
internal fun PendingCard(roll: Roll) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PFLight.Surface).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(roll.film.accent)))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${roll.film.displayName} 현상 대기 중", color = PFLight.Text, style = h2)
                Text("${roll.shots.size} / ${roll.total}장 촬영 · 다 찍으면 현상돼요", color = PFLight.Neutral700, style = body13.copy(fontSize = 12.sp))
            }
        }
        // 12열 슬롯
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until roll.total).chunked(12).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { i ->
                        val shot = i < roll.shots.size
                        Box(
                            Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(3.dp))
                                .background(if (shot) PFLight.Text else Color.Transparent)
                                .border(1.5.dp, if (shot) PFLight.Text else PFLight.Neutral400, RoundedCornerShape(3.dp)),
                        )
                    }
                    repeat(12 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 썸네일(백그라운드 로딩) */
@Composable
fun Thumb(shot: Shot, px: Int, modifier: Modifier) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, shot.uri, shot.file) {
        value = withContext(Dispatchers.IO) { ThumbLoader.load(context, shot, px) }
    }
    Box(modifier.background(Color(0xFF0E0E0E))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// ───────────────────────── 뷰어 ─────────────────────────

@Composable
fun ViewerPanel(shot: Shot, title: String, meta: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, shot.uri, shot.file) {
        value = withContext(Dispatchers.IO) { ThumbLoader.load(context, shot, 1280) }
    }
    Column(Modifier.fillMaxSize().background(PFLight.Inverse).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = PFLight.Text, style = h2)
                Text(meta, color = PFLight.Neutral300, style = body13.copy(fontSize = 12.sp))
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0x1AF5EAD8))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val k = size.width / 24f
                    drawLine(PFLight.Text, Offset(18f * k, 6f * k), Offset(6f * k, 18f * k), 2.75f * k, StrokeCap.Round)
                    drawLine(PFLight.Text, Offset(6f * k, 6f * k), Offset(18f * k, 18f * k), 2.75f * k, StrokeCap.Round)
                }
            }
        }
        // 남은 공간 안에서 3:4를 유지한다(헤더를 덮지 않게)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight * 3f / 4f)
            Box(Modifier.size(w, w * 4f / 3f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0E0E0E))) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
    }
}
