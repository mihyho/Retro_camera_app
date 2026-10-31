package com.example.retrocamera.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retrocamera.AppController
import com.example.retrocamera.data.Film
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val STEP = 74.dp
private val CHIP = 62.dp
private val TOP_PAD = 6.dp
val FILM_STRIP_HEIGHT = 96.dp

/** 스트립 양끝 14%에서 서서히 사라진다(목업의 마스크) */
private fun edgeFade(center: Float, width: Int): Float =
    if (width <= 0) 1f else (min(center, width - center) / (.14f * width)).coerceIn(0f, 1f)

/**
 * 필름 스트립. 칩 안의 그림은 GL 렌더러가 같은 실시간 영상에 그 필름 LUT를 입혀 그린다(여기서는 위치만 알려 준다).
 * 손가락을 1:1로 따라가고, 놓으면 속도로 도착 지점을 예측해 가까운 필름에 스프링으로 붙는다.
 * 끝의 "+" 칸을 누르면 새 필름 메뉴가 열리고, 필름 칩을 꾹 누르면 그 필름을 보관할 수 있다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilmStrip(c: AppController, origin: IntOffset, modifier: Modifier = Modifier) {
    val s = c.state
    val shown = remember(s.hiddenFilms, s.customFilms, s.filmOrder) { c.visibleFilms } // 지우지 않은 필름(바꾼 순서대로)
    val slots = remember(s.customFilms) { Film.entries }                   // GL 타일 번호(지운 필름 자리도 포함)
    val plus = shown.size                                                   // 맨 끝 "+" 칸

    val cur = shown.indexOf(c.currentFilm).coerceAtLeast(0)
    val density = LocalDensity.current
    val step = with(density) { STEP.toPx() }
    val chip = with(density) { CHIP.toPx() }
    val topPad = with(density) { TOP_PAD.toPx() }
    val band = with(density) { FILM_STRIP_HEIGHT.toPx() }
    val ringPx = with(density) { 2.5.dp.toPx() }
    val pos = remember { Animatable(-cur * step) }
    var raw by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val spec = spring<Float>(dampingRatio = .8f, stiffness = 280f) // 목업의 스프링(감쇠 .8, 응답 .38s)과 같다
    // 손가락 위치에 가장 가까운 칸(끝의 "+" 칸 포함): 필름이 실제로 바뀌기 전에도 링이 먼저 따라온다
    val near by remember { derivedStateOf { (-pos.value / step).roundToInt().coerceIn(0, plus) } }

    // 넘기는 동안 가장 가까운 필름의 색(포인트 색·프리뷰 색감)이 바로바로 따라온다
    val nearFilm = shown.getOrNull(near)
    LaunchedEffect(nearFilm) {
        if (nearFilm != null) { Ds.liveFilm = nearFilm; c.onLiveFilm?.invoke(nearFilm) }
    }

    // 바깥(그리드·필름 보관 등)에서 필름이 바뀌어도 따라간다. 드래그 직후에는 같은 목표라 속도가 이어진다
    LaunchedEffect(cur, step) { pos.animateTo(-cur * step, spec) }

    // 칩 위치를 GL 렌더러에 알린다. 타일 번호(slot)마다 8개: x, y(뷰 위쪽 기준), 크기, 투명도, 링 두께(0이면 없음), 링 색 r g b.
    // 링도 GL이 같은 프레임에 그려서 영상과 어긋나지 않는다. 지운 필름의 자리는 0(그리지 않음)
    LaunchedEffect(width, step, origin, shown) {
        if (width == 0) { c.onStrip?.invoke(null); return@LaunchedEffect }
        snapshotFlow { pos.value }.collect { p ->
            val r = FloatArray(slots.size * 8 + 4)
            val ringAt = (-p / step).roundToInt().coerceIn(0, plus)
            for (i in shown.indices) {
                val slot = slots.indexOf(shown[i])
                if (slot < 0) continue
                val d = min(abs(p + i * step) / step, 1f)
                val size = chip * (1f - .1f * d)
                val local = (width - step) / 2f + p + i * step + step / 2f
                val o = slot * 8
                r[o] = origin.x + local - size / 2f
                r[o + 1] = origin.y + topPad + (chip - size) / 2f
                r[o + 2] = size
                r[o + 3] = (1f - .45f * d) * edgeFade(local, width)
                if (i == ringAt) {
                    val g = shown[i].glow()
                    r[o + 4] = ringPx; r[o + 5] = g.red; r[o + 6] = g.green; r[o + 7] = g.blue
                }
            }
            val k = slots.size * 8 // 스트립 영역(칩이 이 밖으로 그려지지 않게)
            r[k] = origin.x.toFloat(); r[k + 1] = origin.y.toFloat()
            r[k + 2] = (origin.x + width).toFloat(); r[k + 3] = origin.y + band
            c.onStrip?.invoke(r)
        }
    }
    DisposableEffect(Unit) { onDispose { c.onStrip?.invoke(null) } }

    fun rubber(over: Float, dim: Float, k: Float) = over * dim * k / (dim + k * abs(over))
    fun resist(x: Float): Float {
        val lo = -plus * step
        return when { x > 0f -> rubber(x, step * 2, .55f); x < lo -> lo - rubber(lo - x, step * 2, .55f); else -> x }
    }

    fun pick(i: Int) {
        if (i == plus) { c.openFilmMenu(); return }
        if (i == cur) return
        c.takeFilm(shown[i])
    }

    Box(
        modifier.fillMaxWidth().height(FILM_STRIP_HEIGHT).clipToBounds().onSizeChanged { width = it.width }
            .then(Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { d -> raw += d; scope.launch { pos.snapTo(resist(raw)) } },
                onDragStarted = { scope.launch { pos.stop() }; raw = pos.value },
                onDragStopped = { v ->
                    // 놓은 속도로 멈출 곳을 예측해 가까운 칸으로 붙는다(감속 0.99)
                    val target = (-(pos.value + v / 1000f * .99f / .01f) / step).roundToInt().coerceIn(0, plus)
                    val end = target
                    scope.launch {
                        var picked = false
                        pos.animateTo(-end * step, spec, initialVelocity = v) {
                            // 필름 교체(저장·LUT 교체)는 거의 도착했을 때 한 번만: 움직이는 동안 끊기지 않게. "+" 칸에 멈추면 아무것도 바꾸지 않는다
                            if (!picked && abs(value + end * step) < step * .35f) { picked = true; if (end != plus) pick(end) }
                        }
                        if (!picked && end != plus) pick(end)
                    }
                },
            )),
    ) {
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset(((width - step) / 2f + pos.value).roundToInt(), TOP_PAD.roundToPx()) },
        ) {
            repeat(plus + 1) { i ->
                val d = { min(abs(pos.value + i * step) / step, 1f) }
                val fade = { edgeFade((width - step) / 2f + pos.value + i * step + step / 2f, width) }
                val f = shown.getOrNull(i)
                Column(
                    Modifier.width(STEP).combinedClickable(onClick = { pick(i) }, onLongClick = { f?.let { c.askArchiveFilm(it) } }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (f != null) {
                        // 그림과 링은 GL이 그린다(같은 프레임에 그려야 안 어긋난다). 여기는 자리만 잡는다
                        Box(Modifier.size(CHIP))
                    } else {
                        // "+" 칸: 새 필름 만들기 / 필름 보관함
                        Box(
                            Modifier.size(CHIP).graphicsLayer { scaleX = 1f - .1f * d(); scaleY = scaleX; alpha = (1f - .45f * d()) * fade() }
                                .clip(RoundedCornerShape(18.dp)).border(1.5.dp, if (i == near) Ds.Accent else Ds.Line, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Ico.PLUS, 26.dp, if (i == near) Ds.Accent else Ds.Sub) }
                    }
                    val label = if (f == null) "새 필름" else {
                        val short = f.displayName.substringBefore(' ').filter { !it.isDigit() }.ifEmpty { f.displayName }
                        if (f.iso > 0 && short.length + 4 <= 10) "$short ${f.iso}" else short // 칸(74dp)에 안 들어가면 ISO는 뺀다
                    }
                    Text(
                        label, if (i == near) Ds.Ink else Ds.Sub, t11.copy(textAlign = TextAlign.Center, fontSize = 10.5.sp),
                        Modifier.padding(top = 5.dp).width(STEP).graphicsLayer { alpha = (1f - .45f * d()) * fade() }, softWrap = false,
                    )
                }
            }
        }
    }
}
