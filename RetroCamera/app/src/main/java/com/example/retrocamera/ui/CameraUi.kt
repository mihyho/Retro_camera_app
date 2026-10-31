package com.example.retrocamera.ui

import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.retrocamera.AppController
import com.example.retrocamera.ZoomMath
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.Mode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 카메라 본체: GL 화면 하나가 전체를 채우고, 그 위에 상단 바 · 프리뷰 틀(4:5 고정, 둥근 모서리) · 필름 스트립 · 모드/셔터 줄을 얹는다.
 * 스트립 칩의 그림도 같은 GL 화면이 그린다(실시간 영상 + 그 필름 LUT).
 * 세로는 위에서 아래로. 가로는 프리뷰를 화면 높이만큼 크게(상태바·내비게이션 바 뒤까지) 왼쪽에 두고, 상단 바와 컨트롤은 오른쪽에 둔다.
 */
@Composable
fun CameraBody(
    c: AppController, glView: GLSurfaceView, topBar: @Composable () -> Unit,
    onLibrary: () -> Unit, onShutter: () -> Unit, onGrid: () -> Unit,
    onZoomBy: (Float) -> Unit, onZoomStep: () -> Unit, onLoad: () -> Unit, modifier: Modifier = Modifier,
) {
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val d = LocalDensity.current
    val sys = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insetTop = if (land) sys.getTop(d) else 0
    val insetBottom = if (land) sys.getBottom(d) else 0
    BoxWithConstraints(
        modifier.fillMaxSize()
            // 가로: 부모(시스템 바를 피한 영역)보다 위아래로 넓혀서 프리뷰를 화면 전체 높이로 쓴다
            .then(if (!land) Modifier else Modifier.layout { m, cs ->
                val p = m.measure(Constraints.fixed(cs.maxWidth, cs.maxHeight + insetTop + insetBottom))
                layout(cs.maxWidth, cs.maxHeight) { p.place(0, -insetTop) }
            })
            .background(Ds.Bg).clipToBounds(),
    ) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val margin = with(d) { 8.dp.roundToPx() }
        val vMargin = with(d) { 24.dp.roundToPx() }
        val topH = with(d) { 45.dp.roundToPx() }
        val band = with(d) { FILM_STRIP_HEIGHT.roundToPx() }
        val ctrl = with(d) { 123.dp.roundToPx() }
        val radius = with(d) { 26.dp.roundToPx() }
        // 촬영 비율은 4:5 고정: 세로로 들면 가로:세로 = 4:5, 가로로 들면 같은 비율을 눕힌 5:4
        val pH: Int; val pW: Int; val pX: Int; val pY: Int
        if (!land) {
            pY = topH
            pH = minOf((w - 2 * margin) * 5 / 4, h - topH - band - ctrl).coerceAtLeast(0); pW = pH * 4 / 5; pX = (w - pW) / 2
        } else {
            // 가로로 들면 4:5를 눕힌 5:4. 위아래에 여유를 둔다(가장자리를 쓸어 내리는 시스템 제스처 영역도 피한다)
            pY = vMargin; pH = (h - 2 * vMargin).coerceAtLeast(0); pW = pH * 5 / 4; pX = margin
        }
        val rx = if (land) pX + pW + margin else 0
        val rw = (w - rx).coerceAtLeast(0)
        val barY = if (land) insetTop + vMargin / 2 else 0
        // 가로: 셔터 줄(89dp)은 아래 여유 위에 붙이고, 그 위로 간격을 두고 모드 줄(34dp)과 스트립을 쌓는다
        val rowH = with(d) { 89.dp.roundToPx() }; val modesH = with(d) { 34.dp.roundToPx() }
        val rowY = h - insetBottom - vMargin - rowH
        val modesY = rowY - with(d) { 28.dp.roundToPx() } - modesH // 셔터 줄과의 간격이 스트립과의 간격보다 넓다
        val stripY = if (land) maxOf(barY + topH, modesY - with(d) { 24.dp.roundToPx() } - band) else pY + pH

        AndroidView({ glView }, Modifier.fillMaxSize())
        if (c.perm == AppController.Perm.GRANTED) {
            LaunchedEffect(pX, pY, pW, pH, radius) { c.onPreviewRect?.invoke(intArrayOf(pX, pY, pW, pH, radius)) }
            val capturing = c.maker == AppController.Maker.CAPTURE
            if (!capturing) Box(Modifier.offset { IntOffset(rx, barY) }.width(with(d) { rw.toDp() })) { topBar() }
            Box(Modifier.offset { IntOffset(pX, pY) }.size(with(d) { pW.toDp() }, with(d) { pH.toDp() })
                // 필름 메뉴(그리드)를 연 동안은 모서리를 둥글게 자르지 않는다: 영상 타일은 GL이 네모로 그려서, 위에 얹는 칸만 둥글게 자르면 아래쪽 모서리가 흐리게 잘려 보인다
                .then(if (c.drawerOpen) Modifier else Modifier.clip(RoundedCornerShape(26.dp)))) {
                // 가로에서는 프리뷰가 상태바 뒤까지 올라가므로, 필름 칩이 상태바 글자와 겹치지 않게 내린다
                Viewfinder(c, onZoomBy, onZoomStep, onLoad, with(d) { (insetTop - margin).coerceAtLeast(0).toDp() })
            }
            if (!land) {
                Column(Modifier.offset { IntOffset(rx, stripY) }.width(with(d) { rw.toDp() })) {
                    // 필름 메뉴가 열리면 스트립 자리는 지우기/순서 버튼, 필름용 사진을 찍는 동안은 비운다
                    if (capturing) Spacer(Modifier.height(FILM_STRIP_HEIGHT)) else if (c.drawerOpen) FilmMenuBar(c) else FilmStrip(c, origin = IntOffset(rx, stripY))
                    // 스트립과 FILM/SINGLE 줄 사이를 띄운다. 14dp 띄우면 셔터 줄은 6dp 내려가 버리므로 컨트롤 높이를 12dp 줄여 셔터 줄 위치는 그대로 둔다
                    Spacer(Modifier.height(14.dp))
                    Controls(c, onLibrary, onShutter, onGrid, Modifier.height((with(d) { (h - stripY - band).coerceAtLeast(0).toDp() } - 20.dp).coerceAtLeast(0.dp)))
                }
            } else {
                // 가로: 스트립 → (좁은 간격) → FILM/SINGLE → (조금 더 넓은 간격) → 셔터 줄. 셔터 줄 위치는 그대로
                Box(Modifier.offset { IntOffset(rx, stripY) }.width(with(d) { rw.toDp() })) {
                    if (capturing) Unit else if (c.drawerOpen) FilmMenuBar(c) else FilmStrip(c, origin = IntOffset(rx, stripY))
                }
                if (!capturing) ModesRow(c, Modifier.offset { IntOffset(rx, modesY) }.width(with(d) { rw.toDp() }).height(34.dp))
                ButtonsRow(c, onLibrary, onShutter, onGrid, Modifier.offset { IntOffset(rx, rowY) }.width(with(d) { rw.toDp() }).height(89.dp))
            }
        } else {
            PermissionOverlay(c)
        }
    }
}

// ───────────────────────── 상단 바 ─────────────────────────

@Composable
private fun TopIcon(modifier: Modifier = Modifier, alpha: Float = 1f, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.size(44.dp).graphicsLayer { this.alpha = alpha }.tap(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun TopBar(c: AppController, onSettings: () -> Unit, onLibrary: () -> Unit) {
    val s = c.state
    val roll = s.roll
    Column(Modifier.fillMaxWidth().background(Ds.Bg)) {
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                // 플래시
                TopIcon(alpha = if (c.flashAvailable) 1f else .35f, onClick = c::toggleFlash) {
                    Row(
                        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (c.flashOn) Ds.Accent else Color.Transparent).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 켜진 것은 색으로만 알린다(글자 없음)
                        Icon(Ico.BOLT, 19.dp, if (c.flashOn) Ds.OnAccent else Ds.Ink, fill = c.flashOn)
                    }
                }
                // 노출
                val evActive = c.exposureOpen || c.exposureIndex != 0
                TopIcon(onClick = c::toggleExposure) {
                    Row(
                        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (evActive) Ds.Accent else Color.Transparent).padding(horizontal = if (evActive) 8.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.EXPOSURE, 19.dp, if (evActive) Ds.OnAccent else Ds.Ink)
                        if (c.exposureIndex != 0) Text(evText(c.exposureIndex, c.exposureStep), Ds.OnAccent, t13, Modifier.padding(start = 3.dp), softWrap = false)
                    }
                }
            }
            // 카운터: 탭하면 사진 보관함
            Row(
                Modifier.align(Alignment.Center).height(34.dp).clip(RoundedCornerShape(17.dp)).background(Ds.Glass)
                    .border(1.dp, Ds.Line, RoundedCornerShape(17.dp)).tap(onClick = onLibrary).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(end = 8.dp).size(7.dp).clip(CircleShape).background(Ds.Accent))
                when {
                    s.mode == Mode.SINGLE -> Text("SINGLE", Ds.Ink, t15.copy(fontWeight = FontWeight.Medium))
                    roll == null -> Text("필름 없음", Ds.Ink, t15.copy(fontWeight = FontWeight.Medium))
                    else -> {
                        Text("${roll.remaining}", Ds.Ink, t15.copy(fontWeight = FontWeight.Medium))
                        Text(" / ${roll.total}", Ds.Sub, t13)
                    }
                }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                TopIcon(alpha = if (c.flipAvailable) 1f else .35f, onClick = { if (c.flipAvailable) c.flipCamera() else c.showToast("전환할 카메라가 없어요") }) {
                    Icon(Ico.FLIP, 22.dp, Ds.Ink)
                }
                TopIcon(onClick = onSettings) { Icon(Ico.GEAR, 22.dp, Ds.Ink) }
            }
        }
        Hairline()
    }
}

fun evText(index: Int, step: Float): String = "%+.1f".format(index * step)

// ───────────────────────── 하단 ─────────────────────────

@Composable
private fun ModeLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.tap(onClick = onClick).padding(horizontal = 13.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text, if (active) Ds.Ink else Ds.Sub,
            TextStyle(fontFamily = Sans, fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
            softWrap = false,
        )
        Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (active) Ds.Accent else Color.Transparent))
    }
}

@Composable
private fun Shutter(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier.size(76.dp).graphicsLayer { val k = if (pressed) .96f else 1f; scaleX = k; scaleY = k }
            .clip(CircleShape).border(3.dp, Ds.Ink.copy(alpha = .85f), CircleShape)
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(62.dp).clip(CircleShape).background(if (pressed) Ds.AccentPress else Ds.Accent)) }
}

/** 모드(FILM/SINGLE) 줄 + 셔터 줄. 보관함 버튼과 필름 그리드 버튼은 어느 방향에서나 셔터 좌우에 있다 */
@Composable
fun Controls(c: AppController, onLibrary: () -> Unit, onShutter: () -> Unit, onGrid: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        // 필름을 만들 사진을 찍는 동안은 FILM/SINGLE 줄도 없이(자리만 남기고) 셔터만 둔다
        if (c.maker == AppController.Maker.CAPTURE) Spacer(Modifier.fillMaxWidth().height(34.dp))
        else ModesRow(c, Modifier.fillMaxWidth().height(34.dp))
        ButtonsRow(c, onLibrary, onShutter, onGrid, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
fun ModesRow(c: AppController, modifier: Modifier = Modifier) {
    val s = c.state
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            ModeLabel("FILM", s.mode == Mode.FILM) { c.setMode(Mode.FILM) }
            ModeLabel("SINGLE", s.mode == Mode.SINGLE) { c.setMode(Mode.SINGLE) }
        }
    }
}

@Composable
fun ButtonsRow(c: AppController, onLibrary: () -> Unit, onShutter: () -> Unit, onGrid: () -> Unit, modifier: Modifier = Modifier) {
    val s = c.state
    val roll = s.roll
    val libShape = RoundedCornerShape(12.dp)
    val capturing = c.maker == AppController.Maker.CAPTURE // 필름용 사진을 찍는 중: 셔터만 보인다
    run {
        Row(modifier.padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (!capturing) Box(
                    Modifier.size(50.dp).clip(libShape).background(Ds.Bg2).border(1.5.dp, Ds.Line, libShape).tap(onClick = onLibrary),
                    contentAlignment = Alignment.Center,
                ) {
                    val last = s.singleShots.firstOrNull()
                    when {
                        s.mode == Mode.SINGLE && last != null -> Thumb(last, 120, Modifier.fillMaxSize())
                        s.mode == Mode.FILM && roll != null -> Text("${roll.shots.size}", Ds.Ink, t17m)
                        else -> Icon(Ico.IMAGES, 22.dp, Ds.Ink)
                    }
                }
            }
            Shutter(onShutter)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (!capturing) Box(Modifier.size(50.dp).clip(CircleShape).background(if (c.drawerOpen) Ds.Accent else Color.Transparent).tap(onClick = onGrid), contentAlignment = Alignment.Center) {
                    Icon(Ico.CIRCLES, 26.dp, if (c.drawerOpen) Ds.OnAccent else Ds.Ink)
                }
            }
        }
    }
}

// ───────────────────────── 뷰파인더 ─────────────────────────

@Composable
fun Viewfinder(c: AppController, onZoomBy: (Float) -> Unit, onZoomStep: () -> Unit, onLoad: () -> Unit, chipInset: androidx.compose.ui.unit.Dp = 0.dp) {
    val s = c.state
    val noFilm = s.mode == Mode.FILM && s.roll == null
    val gestures = !c.drawerOpen && !noFilm && c.panel == null && !c.loadOpen
    val capturing = c.maker == AppController.Maker.CAPTURE // 필름용 사진을 찍는 중: 안내와 셔터 말고는 아무것도 얹지 않는다
    // 영상은 GL이 이 뒤에서 그린다. 여기는 프리뷰 위에 얹는 요소들
    Box(Modifier.fillMaxSize()) {
        if (gestures) GestureLayer(onZoomBy)
        if (s.grid && !c.drawerOpen && !noFilm && !capturing) GridLines()
        if (!c.drawerOpen && !noFilm && !capturing) {
            FilmChip(Ds.liveFilm ?: c.currentFilm, Modifier.align(Alignment.TopStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 8.dp + chipInset))
            if (s.stamp) Text(
                c.stampText(), Ds.Stamp,
                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0x99FF781E), blurRadius = 8f)),
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = if (c.exposureOpen) 86.dp else 10.dp), softWrap = false,
            )
            if (c.zoomMax > 1.01f) ZoomHud(c.zoomRatio, onZoomStep, Modifier.align(Alignment.BottomCenter).padding(bottom = if (c.exposureOpen) 92.dp else 12.dp))
        }
        if (c.maker == AppController.Maker.CAPTURE) CaptureBanner(c, Modifier.align(Alignment.TopCenter).padding(top = 52.dp))
        if (c.drawerOpen) FilmGrid(c)
        if (noFilm) NoFilmOverlay(onLoad)
        if (c.exposureOpen) ExposurePanel(c, Modifier.align(Alignment.BottomCenter))
        Shot(c.flashTrigger)
        if (c.screenFlash) Box(Modifier.fillMaxSize().background(Color.White))
    }
}

/** 두 손가락 핀치로 확대/축소(필름은 아래 스트립으로 바꾼다) */
@Composable
private fun GestureLayer(onZoomBy: (Float) -> Unit) {
    val zoom = rememberUpdatedState(onZoomBy)
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var prev = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val down = ev.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            val d = (down[0].position - down[1].position).getDistance()
                            if (prev > 0f && d > 0f) zoom.value(d / prev)
                            prev = d
                        } else prev = 0f
                    } while (ev.changes.any { it.pressed })
                }
            },
    )
}

@Composable
private fun GridLines() {
    Canvas(Modifier.fillMaxSize()) {
        val col = Color.White.copy(alpha = .55f)
        for (i in 1..2) {
            drawLine(col, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
            drawLine(col, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
        }
    }
}

/** 필름 이름이 이미 숫자로 끝나면(Ektar100) ISO를 덧붙이지 않는다 */
fun filmTitle(f: Film) = if (f.iso <= 0 || f.displayName.last().isDigit()) f.displayName else "${f.displayName} ${f.iso}"

@Composable
private fun FilmChip(film: Film, modifier: Modifier) {
    Row(
        modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(Ds.Glass).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(film.accent)))
        Text(filmTitle(film), Ds.Ink, t11, Modifier.padding(start = 5.dp), softWrap = false)
    }
}

@Composable
private fun ZoomHud(zoom: Float, onClick: () -> Unit, modifier: Modifier) {
    Text(
        ZoomMath.format(zoom), if (zoom > 1.02f) Ds.OnAccent else Ds.Ink, t13.copy(fontWeight = FontWeight.Medium),
        modifier.clip(RoundedCornerShape(50)).background(if (zoom > 1.02f) Ds.Accent else Ds.Glass)
            .tap(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 셔터 피드백: 흰 막이 .9에서 0으로 380ms */
@Composable
private fun Shot(trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) { alpha.snapTo(.9f); alpha.animateTo(0f, tween(380)) }
    }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.White))
}

/** 필름 그리드: 같은 실시간 영상이 칸마다 해당 필름 LUT로 보인다(그림은 LutRenderer의 타일, 여기는 이름/선택/잠금) */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun FilmGrid(c: AppController) {
    val s = c.state
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val films = c.visibleFilms // 지운 필름은 그리드에서도 빠진다(GL 쪽도 같은 순서로 그린다)
    val rows = (films.size + 1 + GRID_COLS - 1) / GRID_COLS // 마지막 필름 다음 칸은 "+"(새 필름)
    Column(Modifier.fillMaxSize()) {
        repeat(rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(GRID_COLS) { col ->
                    val idx = r * GRID_COLS + col
                    val f = films.getOrNull(idx)
                    if (idx == films.size && c.gridSelection.isNotEmpty()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Ds.Bg)) // 고르는 중에는 "+" 칸을 비워 둔다
                    } else if (idx == films.size) {
                        val locked = s.mode == Mode.FILM && s.roll != null
                        Box(
                            Modifier.weight(1f).fillMaxHeight().background(Ds.Bg).border(0.5.dp, Ds.Line)
                                .tap { if (locked) c.lockedFilmTapped() else c.openFilmMenu() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Ico.PLUS, 30.dp, Ds.Accent)
                                Text("새 필름", Ds.Sub, t11)
                            }
                        }
                    } else if (f == null) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Ds.Bg))
                    } else {
                        val roll = s.roll
                        val locked = s.mode == Mode.FILM && roll != null && roll.film != f
                        val current = when {
                            s.mode == Mode.SINGLE -> s.singleFilm == f
                            roll != null -> roll.film == f
                            else -> c.pendingFilm == f
                        }
                        val selecting = c.gridSelection.isNotEmpty()
                        val picked = f.key in c.gridSelection
                        Box(
                            Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Ds.Bg)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            selecting -> c.toggleGridSelect(f)                  // 고르는 중에는 탭이 선택을 바꾼다
                                            current -> c.closeDrawer()
                                            locked -> c.lockedFilmTapped()
                                            else -> { c.takeFilm(f); c.showToast(f.displayName) }
                                        }
                                    },
                                    onLongClick = {
                                        // 꾹 누르면 선택 시작. 롤을 장전한 동안은 필름 메뉴를 바꾸지 않는다
                                        if (s.mode == Mode.FILM && s.roll != null) c.lockedFilmTapped()
                                        else { haptic.performHapticFeedback(HapticFeedbackType.LongPress); c.toggleGridSelect(f) }
                                    },
                                ),
                        ) {
                            if (locked) Box(Modifier.fillMaxSize().background(Ds.Bg.copy(alpha = .6f)))
                            if (picked) Box(Modifier.fillMaxSize().background(Ds.Accent.copy(alpha = .30f)))
                            if (selecting) Box(
                                Modifier.align(Alignment.TopStart).padding(7.dp).size(22.dp).clip(CircleShape)
                                    .background(if (picked) Ds.Accent else Color.Black.copy(alpha = .3f))
                                    .border(1.5.dp, if (picked) Ds.Accent else Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (picked) Icon(Ico.CHECK, 14.dp, Ds.OnAccent) }
                            Text(
                                f.displayName, Color.White, t11.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f))))
                                    .padding(top = 12.dp, bottom = 4.dp, start = 2.dp, end = 2.dp),
                                maxLines = 1, softWrap = false,
                            )
                            if (locked) Icon(Ico.LOCK, 16.dp, Ds.Ink, Modifier.align(Alignment.TopEnd).padding(5.dp))
                            if (current) Box(Modifier.fillMaxSize().border(3.dp, Ds.Accent))
                        }
                    }
                }
            }
        }
    }
}

const val GRID_COLS = 3

/** 노출 패널: 뷰파인더 하단. ±2 EV 범위에서 카메라가 지원하는 칸으로 붙는다 */
@Composable
private fun ExposurePanel(c: AppController, modifier: Modifier) {
    val step = c.exposureStep
    val range = c.exposureRange
    val lo = if (step > 0f) maxOf(range.first, ceil(-2f / step).toInt()) else 0
    val hi = if (step > 0f) minOf(range.last, floor(2f / step).toInt()) else 0
    Column(modifier.fillMaxWidth().background(Ds.Glass)) {
        Hairline()
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("노출", Ds.Sub, t13)
                Text("${evText(c.exposureIndex, step)} EV", Ds.Ink, t13.copy(fontWeight = FontWeight.Medium), Modifier.weight(1f).padding(start = 10.dp))
                Text("초기화", Ds.AccentInk, t13, Modifier.tap { c.setExposure(0) })
            }
            StepSlider(c.exposureIndex.coerceIn(lo, hi), lo, hi, onChange = c::setExposure)
        }
    }
}

@Composable
private fun NoFilmOverlay(onLoad: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(
        Modifier.fillMaxSize().background(Ds.Bg.copy(alpha = .9f)).tap { /* 아래 뷰파인더로 탭이 새지 않게 */ }.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("필름이 없어요", Ds.Ink, t17m)
        Text("필름 모드는 롤을 넣고 다 찍을 때까지 필름을 바꿀 수 없어요. 사진은 롤이 끝나면 현상돼요.", Ds.Sub, t13.copy(textAlign = TextAlign.Center, lineHeight = 19.sp), Modifier.widthIn(max = 240.dp))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (pressed) Ds.AccentPress else Ds.Accent)
                .clickable(src, null, onClick = onLoad).padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) { Text("필름 넣기", Ds.OnAccent, sansStyle(15.sp, FontWeight.Medium)) }
    }
}

@Composable
private fun PermissionOverlay(c: AppController) {
    val denied = c.perm == AppController.Perm.DENIED
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(
        Modifier.fillMaxSize().background(Ds.Bg2).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(if (denied) Ico.PROHIBIT else Ico.CAMERA, 40.dp, Ds.Sub)
        Text(if (denied) "카메라 접근이 거부됐어요" else "카메라를 사용하려면", Ds.Ink, t17m)
        Text(
            if (denied) "설정에서 카메라 권한을 허용한 뒤 다시 시도해 주세요." else "실시간 미리보기와 촬영을 위해 카메라 접근 권한이 필요해요. 사진은 이 기기 안에만 저장돼요.",
            Ds.Sub, t13.copy(textAlign = TextAlign.Center, lineHeight = 20.sp), Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (pressed) Ds.AccentPress else Ds.Accent)
                .clickable(src, null) { c.onRequestPermission?.invoke() }.padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (denied) "다시 시도" else "카메라 켜기", Ds.OnAccent, sansStyle(15.sp, FontWeight.Medium)) }
    }
}
