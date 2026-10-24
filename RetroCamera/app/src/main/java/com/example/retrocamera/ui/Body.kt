package com.example.retrocamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import android.content.res.Configuration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.Mode
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
    onMode: (Mode) -> Unit, onMenu: () -> Unit, onExposure: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onSet: () -> Unit,
    onFilm: () -> Unit, onShoot: () -> Unit, onPlay: () -> Unit, onShutter: () -> Unit,
    /** 촬영 메인 화면: 가운데 버튼이 카메라 전환(아이콘)이고, 그 밖에는 SET */
    flipKey: Boolean = false,
    /** 촬영 메인 화면: ▲▼가 확대(+)/축소(-)로 쓰인다는 걸 보여 주려고 +/- 로 그린다 */
    zoomKeys: Boolean = false,
    lcd: @Composable ColumnScope.() -> Unit,
) {
    // 모드: FILM / SINGLE (라벨을 눌러 바꾼다)
    val modeRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeLabel("FILM", mode == Mode.FILM, Modifier.weight(1f)) { onMode(Mode.FILM) }
            ModeLabel("SINGLE", mode == Mode.SINGLE, Modifier.weight(1f), alignEnd = true) { onMode(Mode.SINGLE) }
        }
    }
    // MENU / 십자키 / EV
    val keysRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(164.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            KeyButton("MENU", 64.dp, 40.dp, onMenu)
            DPad(156.dp, flipKey, zoomKeys, onUp, onDown, onLeft, onRight, onSet)
            KeyButton("EV", 64.dp, 40.dp, onExposure)
        }
    }
    // 필름 / 셔터 / 촬영·재생
    val bottomRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { FilmKey(filmName, onFilm) }
            Shutter(onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Segmented(playActive, onShoot, onPlay) }
        }
    }
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 세로/가로가 바뀌어도 같은 자리에서 같은 자식들을 배치만 다르게 한다(세로 <-> 가로로 Row/Column을 바꾸면 카메라 화면 뷰가
    // 떼어졌다 다시 붙어서 화면이 깜빡이고 카메라가 다시 연결된다)
    Layout(
        content = {
            Text("RETRO_CAMERA", color = LabelGray, style = labelStyle.copy(letterSpacing = .3.em), modifier = Modifier.layoutId("brand"))
            Column(Modifier.layoutId("lcd")) { lcd() }
            Box(Modifier.layoutId("mode")) { modeRow() }
            Box(Modifier.layoutId("keys")) { keysRow() }
            Box(Modifier.layoutId("bottom")) { bottomRow() }
        },
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BodyTop, BodyBottom)))
            .windowInsetsPadding(WindowInsets.systemBars).windowInsetsPadding(WindowInsets.displayCutout),
    ) { measurables, constraints ->
        fun id(name: String) = measurables.first { it.layoutId == name }
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val padX = 16.dp.roundToPx()
        val modeH = 32.dp.roundToPx(); val keysH = 164.dp.roundToPx(); val bottomH = 84.dp.roundToPx()
        val brand = id("brand").measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(w, h) {
            if (land) {
                // 가로: 왼쪽 LCD, 오른쪽 조작부(폭 340dp)
                val padY = 8.dp.roundToPx(); val gap = 16.dp.roundToPx(); val rightW = 340.dp.roundToPx()
                val leftW = w - 2 * padX - gap - rightW
                val brandGap = 4.dp.roundToPx()
                val lcdH = h - 2 * padY - brand.height - brandGap
                val lcd = id("lcd").measure(Constraints.fixed(leftW, lcdH.coerceAtLeast(0)))
                brand.placeRelative(padX, padY)
                lcd.placeRelative(padX, padY + brand.height + brandGap)
                val x = w - padX - rightW
                val free = (h - 2 * padY - modeH - keysH - bottomH).coerceAtLeast(0)
                val sp = free / 4
                var y = padY + sp
                for ((name, ph) in listOf("mode" to modeH, "keys" to keysH, "bottom" to bottomH)) {
                    id(name).measure(Constraints.fixed(rightW, ph)).placeRelative(x, y)
                    y += ph + sp
                }
            } else {
                val topGap = 8.dp.roundToPx(); val brandBottom = 6.dp.roundToPx(); val modeTop = 6.dp.roundToPx()
                val keysUp = 14.dp.roundToPx(); val bottomPad = 28.dp.roundToPx()
                val contentW = w - 2 * padX
                val lcdH = h - topGap - brand.height - brandBottom - modeTop - modeH - (keysH - keysUp) - bottomH - bottomPad
                val lcd = id("lcd").measure(Constraints.fixed(contentW, lcdH.coerceAtLeast(0)))
                var y = topGap
                brand.placeRelative(padX, y); y += brand.height + brandBottom
                lcd.placeRelative(padX, y); y += lcd.height + modeTop
                id("mode").measure(Constraints.fixed(contentW, modeH)).placeRelative(padX, y); y += modeH
                id("keys").measure(Constraints.fixed(contentW, keysH)).placeRelative(padX, y - keysUp); y += keysH - keysUp
                id("bottom").measure(Constraints.fixed(contentW, bottomH)).placeRelative(padX, y)
            }
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
private fun DPad(size: Dp, flipKey: Boolean, zoomKeys: Boolean, onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onSet: () -> Unit) {
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
            .semantics {
                contentDescription = "십자키"
                customActions = listOf(
                    CustomAccessibilityAction("위") { up(); true },
                    CustomAccessibilityAction("아래") { down(); true },
                    CustomAccessibilityAction("왼쪽") { left(); true },
                    CustomAccessibilityAction("오른쪽") { right(); true },
                    CustomAccessibilityAction("SET") { set(); true },
                )
            }
            .pointerInput(Unit) {
                // 누르는 순간 한 번 입력하고, 계속 누르고 있으면 방향키는 연속으로 입력한다(SET은 연속 입력 없음)
                awaitEachGesture {
                    val o = awaitFirstDown().position
                    val dx = o.x - this.size.width / 2f
                    val dy = o.y - this.size.height / 2f
                    if (hypot(dx, dy) < this.size.width * SET_RADIUS) { set(); waitForUpOrCancellation(); return@awaitEachGesture }
                    // 누르는 동안 화면 상태가 바뀌므로(예: 메뉴 줄 이동) 부를 때마다 가장 최근 콜백을 읽는다
                    val fire: () -> Unit = {
                        when {
                            abs(dx) > abs(dy) -> if (dx > 0) right() else left()
                            else -> if (dy > 0) down() else up()
                        }
                    }
                    fire()
                    var wait = 400L // 처음 반복까지 잠깐 기다린 뒤 빠르게
                    while (true) {
                        var released = false
                        withTimeoutOrNull(wait) { waitForUpOrCancellation(); released = true }
                        if (released) break
                        fire()
                        wait = 90L
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val c = Offset(w / 2f, w / 2f)
            for ((ux, uy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
                if (zoomKeys && uy != 0f) {
                    val m = c + Offset(0f, uy) * (w * .34f)
                    val l = w * .06f
                    val sw = 2.5.dp.toPx()
                    drawLine(KeyText, Offset(m.x - l, m.y), Offset(m.x + l, m.y), strokeWidth = sw)
                    if (uy < 0f) drawLine(KeyText, Offset(m.x, m.y - l), Offset(m.x, m.y + l), strokeWidth = sw)
                    continue
                }
                val tip = c + Offset(ux, uy) * (w * .40f)
                val mid = c + Offset(ux, uy) * (w * .30f)
                val p = Offset(-uy, ux) * (w * .045f)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(mid.x + p.x, mid.y + p.y); lineTo(mid.x - p.x, mid.y - p.y); close() }, LabelGray)
            }
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3A40), Color(0xFF1A1A1D))), w * SET_RADIUS, c)
            drawCircle(KeyEdge, w * SET_RADIUS, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        if (flipKey) FlipIcon(Modifier.size(26.dp)) else Text("SET", color = KeyText, style = labelStyle.copy(fontSize = 11.sp))
    }
}

private const val SET_RADIUS = 0.19f

/** 카메라 전환 아이콘: 서로 쫓는 두 개의 원형 화살표 */
@Composable
private fun FlipIcon(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.width * .36f
        val sw = size.width * .09f
        val head = size.width * .17f
        for (start in listOf(200f, 20f)) {
            drawArc(KeyText, start, 130f, false, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            // 화살촉: 호의 끝에서 진행 방향(시계 방향 접선)으로
            val a = Math.toRadians((start + 130f).toDouble())
            val p = c + Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat()) * r
            val n = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            val t = Offset(-n.y, n.x)
            drawPath(Path().apply {
                moveTo((p + t * head * 1.4f).x, (p + t * head * 1.4f).y)
                lineTo((p + n * head).x, (p + n * head).y)
                lineTo((p - n * head).x, (p - n * head).y)
                close()
            }, KeyText)
        }
    }
}

@Composable
private fun FilmKey(name: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Row(
        Modifier.size(132.dp, 42.dp).then(keyModifier(RoundedCornerShape(8.dp), pressed)).clickable(src, null, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(3.dp, 20.dp).background(LcdOrange))
        Spacer(Modifier.width(8.dp))
        Text(name, color = KeyText, style = monoStyle.copy(fontSize = if (name.length > 11) 10.sp else 12.sp), maxLines = 1, softWrap = false)
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
            .semantics { contentDescription = "셔터" }
            .clickable(src, null, role = Role.Button, onClick = onClick).padding(5.dp),
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
    // 필름이 많아 화면을 넘으므로 스크롤하고, 십자키로 고른 줄이 항상 보이게 따라간다
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<Int, IntRange>() }
    var viewport by remember { mutableStateOf(0) }
    LaunchedEffect(selected, viewport, rows.size) {
        val r = tops[selected] ?: return@LaunchedEffect
        if (r.first < scroll.value) scroll.animateScrollTo(r.first)
        else if (r.last > scroll.value + viewport) scroll.animateScrollTo(r.last - viewport)
    }
    LcdScaffold("필터  Filter", "▲▼ 이동 · SET 선택 · $subtitle", onClose) {
      Column(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewport = it.height }.verticalScroll(scroll)) {
        rows.forEachIndexed { i, r ->
            val fg = if (r.locked) Color(0xFF4F5E6B) else PFLight.Text
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { tops[i] = it.positionInParent().y.toInt()..(it.positionInParent().y.toInt() + it.size.height) }
                    .background(if (i == selected) LcdBlue else Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row {
                    Text(r.film.displayName, color = fg, style = monoStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    Text(r.label, color = if (r.current) Color(0xFFFFB066) else PFLight.Neutral700.copy(alpha = if (r.locked) .5f else 1f), style = monoStyle.copy(fontSize = 11.sp))
                }
                Text(r.film.description, color = fg.copy(alpha = .75f), style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 11.sp), modifier = Modifier.padding(top = 2.dp))
            }
        }
      }
    }
}
