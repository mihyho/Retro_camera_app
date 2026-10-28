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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * 카메라 화면 뼈대: 상단 바 · 뷰파인더(3:4) · 하단. 세로/가로 모두 같은 `Layout` 하나로 배치만 바꾼다
 * (GLSurfaceView가 다른 부모로 옮겨지면 GL 컨텍스트가 새로 만들어져 화면이 깜빡이기 때문).
 */
@Composable
fun CameraLayout(
    top: @Composable () -> Unit,
    viewfinder: @Composable () -> Unit,
    bottom: @Composable (landscape: Boolean) -> Unit,
) {
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Layout(
        {
            Box(Modifier.layoutId("top")) { top() }
            Box(Modifier.layoutId("vf")) { viewfinder() }
            Box(Modifier.layoutId("bottom")) { bottom(land) }
        },
        Modifier.fillMaxSize(),
    ) { measurables, cs ->
        val w = cs.maxWidth; val h = cs.maxHeight
        fun find(id: String) = measurables.first { it.layoutId == id }
        val topP = find("top").measure(Constraints.fixedWidth(w))
        val topH = topP.height
        if (!land) {
            // 뷰파인더는 폭을 꽉 채운 3:4. 하단이 최소 123dp는 남게 한다
            val vfH = minOf(w * 4 / 3, h - topH - 123.dp.roundToPx()).coerceAtLeast(0)
            val vfW = vfH * 3 / 4
            val vfP = find("vf").measure(Constraints.fixed(vfW, vfH))
            val bP = find("bottom").measure(Constraints.fixed(w, (h - topH - vfH).coerceAtLeast(0)))
            layout(w, h) {
                topP.place(0, 0)
                vfP.place((w - vfW) / 2, topH)
                bP.place(0, topH + vfH)
            }
        } else {
            val availH = h - topH
            val vfH = minOf(availH, (w - 170.dp.roundToPx()) * 3 / 4).coerceAtLeast(0)
            val vfW = vfH * 4 / 3
            val vfP = find("vf").measure(Constraints.fixed(vfW, vfH))
            val bP = find("bottom").measure(Constraints.fixed((w - vfW).coerceAtLeast(0), availH))
            layout(w, h) {
                topP.place(0, 0)
                vfP.place(0, topH)
                bP.place(vfW, topH)
            }
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
                        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (c.flashOn) Ds.Accent else Color.Transparent).padding(horizontal = if (c.flashOn) 8.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.BOLT, 19.dp, Ds.Ink, fill = c.flashOn)
                        if (c.flashOn) Text("켬", Ds.Ink, t13, Modifier.padding(start = 3.dp))
                    }
                }
                // 노출
                val evActive = c.exposureOpen || c.exposureIndex != 0
                TopIcon(onClick = c::toggleExposure) {
                    Row(
                        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (evActive) Ds.Accent else Color.Transparent).padding(horizontal = if (evActive) 8.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.EXPOSURE, 19.dp, Ds.Ink)
                        if (c.exposureIndex != 0) Text(evText(c.exposureIndex, c.exposureStep), Ds.Ink, t13, Modifier.padding(start = 3.dp), softWrap = false)
                    }
                }
            }
            // 카운터: 탭하면 사진 보관함
            Row(Modifier.align(Alignment.Center).height(44.dp).tap(onClick = onLibrary), verticalAlignment = Alignment.CenterVertically) {
                when {
                    s.mode == Mode.SINGLE -> Text("SINGLE", Ds.Ink, t17m)
                    roll == null -> Text("필름 없음", Ds.Ink, t17m)
                    else -> {
                        Text("${roll.remaining}", Ds.Ink, t17m)
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
        Modifier.size(68.dp).graphicsLayer { val k = if (pressed) .96f else 1f; scaleX = k; scaleY = k }
            .clip(CircleShape).border(3.dp, Ds.Line, CircleShape)
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(54.dp).clip(CircleShape).background(if (pressed) Ds.AccentPress else Ds.Accent)) }
}

@Composable
fun BottomBar(c: AppController, landscape: Boolean, onLibrary: () -> Unit, onShutter: () -> Unit, onGrid: () -> Unit) {
    val s = c.state
    val roll = s.roll
    val modes: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 13.dp)) {
            ModeLabel("FILM", s.mode == Mode.FILM) { c.setMode(Mode.FILM) }
            ModeLabel("SINGLE", s.mode == Mode.SINGLE) { c.setMode(Mode.SINGLE) }
        }
    }
    val library: @Composable () -> Unit = {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(Ds.Bg2).border(0.5.dp, Ds.Line, RoundedCornerShape(4.dp)).tap(onClick = onLibrary),
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
    val grid: @Composable () -> Unit = {
        Box(
            Modifier.size(44.dp).tap(onClick = onGrid), contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (c.drawerOpen) Ds.Accent else Color.Transparent), contentAlignment = Alignment.Center) {
                Icon(Ico.CIRCLES, 22.dp, Ds.Ink)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Ds.Bg)) {
        Hairline()
        if (!landscape) {
            Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) { modes() }
            Row(Modifier.fillMaxWidth().weight(1f).padding(start = 22.dp, end = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { library() }
                Shutter(onShutter)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { grid() }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) { modes() }
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                library(); Shutter(onShutter); grid()
            }
        }
    }
}

// ───────────────────────── 뷰파인더 ─────────────────────────

@Composable
fun Viewfinder(c: AppController, glView: GLSurfaceView, onZoomBy: (Float) -> Unit, onZoomToggle: () -> Unit, onLoad: () -> Unit) {
    val s = c.state
    val noFilm = s.mode == Mode.FILM && s.roll == null
    val gestures = c.perm == AppController.Perm.GRANTED && !c.drawerOpen && !noFilm && c.panel == null && !c.loadOpen
    Box(Modifier.fillMaxSize().background(Ds.Neutral).clipToBounds()) {
        AndroidView({ glView }, Modifier.fillMaxSize())
        if (c.perm == AppController.Perm.GRANTED) {
            if (gestures) GestureLayer(onZoomBy, onZoomToggle, onSwipe = { c.cycleFilm(it) })
            if (s.grid && !c.drawerOpen && !noFilm) GridLines()
            if (!c.drawerOpen && !noFilm) {
                FilmChip(c.currentFilm, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (s.stamp) Text(
                    c.stampText(), Ds.Stamp,
                    TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(Color(0x99FF781E), blurRadius = 8f)),
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = if (c.exposureOpen) 86.dp else 10.dp), softWrap = false,
                )
                ZoomHud(c.zoomRatio, Modifier.align(Alignment.BottomCenter).padding(bottom = if (c.exposureOpen) 92.dp else 12.dp))
            }
            if (c.drawerOpen) FilmGrid(c)
            if (noFilm) NoFilmOverlay(onLoad)
            if (c.exposureOpen) ExposurePanel(c, Modifier.align(Alignment.BottomCenter))
            Shot(c.flashTrigger)
            if (c.screenFlash) Box(Modifier.fillMaxSize().background(Color.White))
        } else {
            PermissionOverlay(c)
        }
    }
}

/** 핀치로 확대/축소, 두 번 탭하면 1배 <-> 2배, 한 손가락으로 60dp 이상 좌우로 밀면 필름 전환 */
@Composable
private fun GestureLayer(onZoomBy: (Float) -> Unit, onZoomToggle: () -> Unit, onSwipe: (Int) -> Unit) {
    val zoom = rememberUpdatedState(onZoomBy)
    val toggle = rememberUpdatedState(onZoomToggle)
    val swipe = rememberUpdatedState(onSwipe)
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggle.value() }) }
            .pointerInput(Unit) {
                val threshold = 60.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f; var dy = 0f; var multi = false; var prev = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val down = ev.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            multi = true
                            val d = (down[0].position - down[1].position).getDistance()
                            if (prev > 0f && d > 0f) zoom.value(d / prev)
                            prev = d
                        } else {
                            prev = 0f
                            if (!multi && down.size == 1) { dx = down[0].position.x - first.position.x; dy = down[0].position.y - first.position.y }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (!multi && abs(dx) > threshold && abs(dx) > abs(dy) * 1.5f) swipe.value(if (dx < 0) 1 else -1)
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
fun filmTitle(f: Film) = if (f.displayName.last().isDigit()) f.displayName else "${f.displayName} ${f.iso}"

@Composable
private fun FilmChip(film: Film, modifier: Modifier) {
    Row(
        modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = .88f)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(film.accent)))
        Text(filmTitle(film), Ds.Ink, t11, Modifier.padding(start = 5.dp), softWrap = false)
    }
}

@Composable
private fun ZoomHud(zoom: Float, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(zoom) {
        visible = zoom > 1.02f
    }
    if (!visible) return
    Text(
        ZoomMath.format(zoom), Ds.Ink, t13.copy(fontWeight = FontWeight.Medium),
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .88f)).padding(horizontal = 10.dp, vertical = 3.dp),
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
private fun FilmGrid(c: AppController) {
    val s = c.state
    val films = Film.entries
    val rows = (films.size + GRID_COLS - 1) / GRID_COLS
    Column(Modifier.fillMaxSize()) {
        repeat(rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(GRID_COLS) { col ->
                    val f = films.getOrNull(r * GRID_COLS + col)
                    if (f == null) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                    } else {
                        val roll = s.roll
                        val locked = s.mode == Mode.FILM && roll != null && roll.film != f
                        val current = when {
                            s.mode == Mode.SINGLE -> s.singleFilm == f
                            roll != null -> roll.film == f
                            else -> c.pendingFilm == f
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.White)
                                .tap { if (current) c.closeDrawer() else if (locked) c.lockedFilmTapped() else { c.takeFilm(f); c.showToast(f.displayName) } },
                        ) {
                            if (locked) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .6f)))
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
    Column(modifier.fillMaxWidth().background(Color.White.copy(alpha = .94f))) {
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
        Modifier.fillMaxSize().background(Color.White.copy(alpha = .9f)).tap { /* 아래 뷰파인더로 탭이 새지 않게 */ }.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("필름이 없어요", Ds.Ink, t17m)
        Text("필름 모드는 롤을 넣고 다 찍을 때까지 필름을 바꿀 수 없어요. 사진은 롤이 끝나면 현상돼요.", Ds.Sub, t13.copy(textAlign = TextAlign.Center, lineHeight = 19.sp), Modifier.widthIn(max = 240.dp))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (pressed) Ds.AccentPress else Ds.Accent)
                .clickable(src, null, onClick = onLoad).padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) { Text("필름 넣기", Ds.Ink, sansStyle(15.sp, FontWeight.Medium)) }
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
        ) { Text(if (denied) "다시 시도" else "카메라 켜기", Ds.Ink, sansStyle(15.sp, FontWeight.Medium)) }
    }
}
