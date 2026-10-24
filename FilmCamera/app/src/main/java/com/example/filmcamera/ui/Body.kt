package com.example.filmcamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.filmcamera.data.Film
import com.example.filmcamera.data.Mode
import kotlin.math.abs
import kotlin.math.hypot

/** 구형 디카 몸체와 LCD 팔레트 */
val LcdOrange = Color(0xFFFF7A1A)
val LcdBlue = Color(0xFF2E5FA8)
private val BodyTop = Color(0xFF242427)
private val BodyBottom = Color(0xFF0D0D0F)
private val KeyTop = Color(0xFF36363C)
private val KeyBottom = Color(0xFF1B1B1F)
private val KeyEdge = Color(0xFF47474E)
private val LabelGray = Color(0xFF8E8E96)
private val KeyText = Color(0xFFD8D8DC)

private val labelStyle = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .12.em)
private val monoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)

/**
 * 몸체(고정 레이아웃): 위쪽 브랜드, 가운데 LCD, 아래쪽 버튼들. 어떤 버튼을 눌러도 이 몸체는 그대로이고
 * 갤러리/설정/필름 메뉴는 LCD 안에서만 바뀐다.
 */
@Composable
fun CameraBody(
    mode: Mode, playActive: Boolean, filmName: String,
    onMode: (Mode) -> Unit, onMenu: () -> Unit, onDisp: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onSet: () -> Unit,
    onFilm: () -> Unit, onShoot: () -> Unit, onPlay: () -> Unit, onShutter: () -> Unit,
    lcd: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BodyTop, BodyBottom)))
            .windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp),
    ) {
        Text("PHONE_FILM", color = LabelGray, style = labelStyle.copy(letterSpacing = .3.em), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        lcd()

        // 모드: FILM <-(원형 키)-> SINGLE
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeLabel("FILM", mode == Mode.FILM, Modifier.weight(1f)) { onMode(Mode.FILM) }
            RoundKey(34.dp) { onMode(if (mode == Mode.FILM) Mode.SINGLE else Mode.FILM) }
            ModeLabel("SINGLE", mode == Mode.SINGLE, Modifier.weight(1f), alignEnd = true) { onMode(Mode.SINGLE) }
        }
        // MENU / 십자키 / DISP
        Row(Modifier.fillMaxWidth().height(144.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            KeyButton("MENU", 64.dp, 40.dp, onMenu)
            DPad(136.dp, onUp, onDown, onLeft, onRight, onSet)
            KeyButton("DISP", 64.dp, 40.dp, onDisp)
        }
        // 필름 / 촬영·재생 / 셔터
        Row(Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { FilmKey(filmName, onFilm) }
            Shutter(onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Segmented(playActive, onShoot, onPlay) }
        }
    }
}

@Composable
private fun ModeLabel(text: String, active: Boolean, modifier: Modifier, alignEnd: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (alignEnd) Text(text, color = if (active) KeyText else LabelGray, style = labelStyle)
        Box(Modifier.padding(horizontal = 6.dp).size(6.dp).clip(CircleShape).background(if (active) LcdOrange else Color(0xFF3A2A20)))
        if (!alignEnd) Text(text, color = if (active) KeyText else LabelGray, style = labelStyle)
    }
}

@Composable
private fun keyModifier(shape: androidx.compose.ui.graphics.Shape, pressed: Boolean): Modifier =
    Modifier.clip(shape)
        .background(Brush.verticalGradient(if (pressed) listOf(KeyBottom, KeyBottom) else listOf(KeyTop, KeyBottom)))
        .border(1.dp, KeyEdge, shape)

@Composable
private fun RoundKey(size: Dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(Modifier.size(size).then(keyModifier(CircleShape, pressed)).clickable(src, null, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size / 3).clip(CircleShape).background(Color(0xFF0E0E10)))
    }
}

@Composable
private fun KeyButton(text: String, w: Dp, h: Dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.size(w, h).then(keyModifier(RoundedCornerShape(8.dp), pressed)).clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = KeyText, style = labelStyle.copy(fontSize = 11.sp)) }
}

/** 상하좌우 + 가운데 SET. 눌린 위치로 방향을 판정한다 */
@Composable
private fun DPad(size: Dp, onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onSet: () -> Unit) {
    // pointerInput(Unit)의 블록은 처음 한 번만 만들어지므로, 콜백을 그대로 쓰면 처음 화면의 낡은 설정값을 계속 쓴다.
    // 항상 가장 최근 콜백을 부르도록 한다.
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    val set by rememberUpdatedState(onSet)
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2E2E33), Color(0xFF151518))))
            .border(1.dp, KeyEdge, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val dx = o.x - this.size.width / 2f
                    val dy = o.y - this.size.height / 2f
                    when {
                        hypot(dx, dy) < this.size.width * SET_RADIUS -> set()
                        abs(dx) > abs(dy) -> if (dx > 0) right() else left()
                        else -> if (dy > 0) down() else up()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val c = Offset(w / 2f, w / 2f)
            for ((ux, uy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
                val tip = c + Offset(ux, uy) * (w * .40f)
                val mid = c + Offset(ux, uy) * (w * .30f)
                val p = Offset(-uy, ux) * (w * .045f)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(mid.x + p.x, mid.y + p.y); lineTo(mid.x - p.x, mid.y - p.y); close() }, LabelGray)
            }
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3A40), Color(0xFF1A1A1D))), w * SET_RADIUS, c)
            drawCircle(KeyEdge, w * SET_RADIUS, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Text("SET", color = KeyText, style = labelStyle.copy(fontSize = 11.sp))
    }
}

private const val SET_RADIUS = 0.19f

@Composable
private fun FilmKey(name: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Row(
        Modifier.size(116.dp, 42.dp).then(keyModifier(RoundedCornerShape(8.dp), pressed)).clickable(src, null, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(3.dp, 20.dp).background(LcdOrange))
        Spacer(Modifier.width(8.dp))
        Text(name, color = KeyText, style = monoStyle.copy(fontSize = 12.sp), maxLines = 1, softWrap = false)
    }
}

/** 촬영 / 재생 전환 */
@Composable
private fun Segmented(playActive: Boolean, onShoot: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.size(118.dp, 36.dp).clip(shape).background(Color(0xFF101012)).border(1.dp, KeyEdge, shape)) {
        for ((text, active, click) in listOf(Triple("촬영", !playActive, onShoot), Triple("재생", playActive, onPlay))) {
            Box(
                Modifier.weight(1f).fillMaxSize()
                    .background(if (active) Brush.verticalGradient(listOf(KeyTop, KeyBottom)) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = click),
                contentAlignment = Alignment.Center,
            ) { Text(text, color = if (active) KeyText else LabelGray, style = labelStyle.copy(fontSize = 12.sp)) }
        }
    }
}

@Composable
private fun Shutter(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.size(68.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9A9AA0), Color(0xFF3A3A40))))
            .clickable(src, null, onClick = onClick).padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(Brush.verticalGradient(if (pressed) listOf(Color(0xFF0B0B0C), Color(0xFF0B0B0C)) else listOf(Color(0xFF2B2B2F), Color(0xFF111113)))),
        )
    }
}

/** 필름 메뉴가 그리는 한 줄 */
data class FilmRow(val film: Film, val current: Boolean, val locked: Boolean, val label: String)

/** LCD 안의 필름 메뉴(디카 메뉴처럼 목록, 십자키로 고른 줄은 파란 막대). 서랍 대신 쓴다 */
@Composable
fun FilmMenu(subtitle: String, rows: List<FilmRow>, selected: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    LcdScaffold("필름  Film", "▲▼ 이동 · SET 선택 · $subtitle", onClose) {
        rows.forEachIndexed { i, r ->
            val fg = if (r.locked) Color(0xFF4F5E6B) else Color(0xFFE4ECF2)
            Column(
                Modifier.fillMaxWidth().background(if (i == selected) LcdBlue else Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row {
                    Text(r.film.displayName, color = fg, style = monoStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    Text(r.label, color = if (r.current) Color(0xFFFFB066) else Color(0xFF93A5B3).copy(alpha = if (r.locked) .5f else 1f), style = monoStyle.copy(fontSize = 11.sp))
                }
                Text(r.film.description, color = fg.copy(alpha = .75f), style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 11.sp), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
