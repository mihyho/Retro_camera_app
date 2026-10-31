package com.example.retrocamera.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retrocamera.AlertState
import com.example.retrocamera.AppController
import com.example.retrocamera.BoxBlur
import com.example.retrocamera.Clarity
import com.example.retrocamera.FilmLook
import com.example.retrocamera.LutApplier
import com.example.retrocamera.PhotoPipeline
import com.example.retrocamera.Texture
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.FilmAdjust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────── 보정 결과 만들기(비파괴) ─────────────────────────

/** 편집 화면의 미리보기. 보정값을 LUT로 굽고, 텍스처/명료도와 함께 항상 원본 픽셀에서 새로 계산한다(화질이 누적해서 나빠지지 않는다) */
object EditorRender {
    /** 긴 변이 maxSide 이하가 되게 줄인 편집용 사본. 슬라이더를 끌 때 바로 따라오게 작게 쓴다 */
    fun proxy(src: AppController.EditorSource, maxSide: Int = 800): AppController.EditorSource {
        val longSide = maxOf(src.w, src.h)
        if (longSide <= maxSide) return src
        val k = maxSide.toFloat() / longSide
        val nw = maxOf(1, (src.w * k).roundToInt()); val nh = maxOf(1, (src.h * k).roundToInt())
        val scaled = Bitmap.createScaledBitmap(Bitmap.createBitmap(src.px, src.w, src.h, Bitmap.Config.ARGB_8888), nw, nh, true)
        val px = IntArray(nw * nh)
        scaled.getPixels(px, 0, nw, 0, 0, nw, nh)
        return AppController.EditorSource(px, nw, nh)
    }

    /** 촬영본과 같은 순서(명료도 → 텍스처 → 색감 LUT)로 처리한다 */
    fun render(src: AppController.EditorSource, a: FilmAdjust): Bitmap {
        val w = src.w; val h = src.h
        val px = src.px.copyOf()
        val lut = FilmLook.bake(null, a)
        val clarity = a.clarityAmount
        val texture = a.textureAmount
        val base = BoxBlur.blurArgb(src.px, w, h, PhotoPipeline.detailRadius(w))
        val large = if (clarity != 0f) Clarity.blur(src.px, w, h, Clarity.boxRadius(w)) else base
        val chunk = (px.size + 3) / 4
        IntStream.range(0, 4).parallel().forEach { c ->
            val from = c * chunk
            val to = minOf(px.size, from + chunk)
            if (from < to) {
                if (clarity != 0f) Clarity.applyArgb(px, large, from, to, clarity)
                if (texture != 0f) Texture.applyArgb(px, base, from, to, texture)
                LutApplier.applyDetailSafe(px, base, from, to, lut)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

// ───────────────────────── 새 필름 메뉴 ─────────────────────────

@Composable
private fun MenuRow(title: String, sub: String, icon: Ico, last: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().tap(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Ds.Bg2), contentAlignment = Alignment.Center) { Icon(icon, 24.dp, Ds.Accent) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, Ds.Ink, t17m)
                Text(sub, Ds.Sub, t13, Modifier.padding(top = 2.dp))
            }
        }
        if (!last) Hairline(Modifier.padding(start = 74.dp))
    }
}

/** "+" 칸을 누르면 열리는 메뉴: 지웠던 기본 필름을 다시 넣거나, 새 필름을 만든다 */
@Composable
fun FilmMenuModal(c: AppController) {
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar("필름 추가", left = "닫기", onLeft = { c.closeMaker() })
        Column(Modifier.padding(top = 18.dp)) {
            Group {
                MenuRow("기본 필름 불러오기", "지웠던 기본 필름을 다시 넣어요", Ico.IMAGES) { c.maker = AppController.Maker.RESTORE }
                MenuRow("새로운 필름 만들기", "사진을 보정해서 나만의 필름을 만들어요", Ico.EXPOSURE, last = true) { c.maker = AppController.Maker.SOURCE }
            }
        }
    }
}

/** 기본 제공 필름 전부를 보여 주고, 지운 필름을 누르면 제자리에 다시 넣는다. 이미 있는 필름은 "이미 들어있습니다"를 띄운다 */
@Composable
fun RestoreFilmModal(c: AppController) {
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar("기본 필름 불러오기", left = "뒤로", leftChevron = true, onLeft = { c.maker = AppController.Maker.MENU })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionNote("지운 필름을 누르면 원래 자리에 다시 들어가요.")
            // 지운 필름이 영영 사라지지 않게, 내가 만든 필름도 아래에 같이 보여 준다
            val mine = Film.entries.filter { it !in Film.builtIn }
            listOf("기본 필름" to Film.builtIn, "내가 만든 필름" to mine).filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                SectionHeader(title)
                Group {
                    list.forEachIndexed { i, f ->
                        val inside = f.key !in c.state.hiddenFilms
                        Column(Modifier.fillMaxWidth().tap { c.restoreFilm(f) }) {
                            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(f.glow()))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(f.displayName, if (inside) Ds.Sub else Ds.Ink, t17, softWrap = false)
                                    Text(f.description, Ds.Sub, t12, softWrap = false)
                                }
                                Text(if (inside) "들어있음" else "다시 넣기", if (inside) Ds.Sub else Ds.Accent, t13)
                            }
                            if (i != list.lastIndex) Hairline()
                        }
                    }
                }
            }
        }
    }
}

/** 보정할 사진을 고른다: 지금 촬영하거나, 가지고 있는 사진을 불러온다 */
@Composable
fun FilmSourceModal(c: AppController) {
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar("사진 고르기", left = "뒤로", leftChevron = true, onLeft = { c.maker = AppController.Maker.MENU })
        Column(Modifier.padding(top = 18.dp)) {
            SectionNote("이 사진으로 색감을 보정해서 필름을 만들어요.")
            Group {
                MenuRow("지금 촬영하기", "필터 없는 원본으로 찍어요", Ico.CAMERA) { c.maker = AppController.Maker.CAPTURE }
                MenuRow("사진 불러오기", "기기에 있는 사진에서 골라요", Ico.IMAGES, last = true) { c.onPickPhoto?.invoke() }
            }
        }
    }
}

/** 카메라 화면 위의 안내: 셔터를 누르면 사진이 저장되지 않고 편집 화면으로 간다 */
@Composable
fun CaptureBanner(c: AppController, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Ds.Glass).border(1.dp, Ds.Line, RoundedCornerShape(18.dp)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("필름을 만들 사진을 찍어 주세요", Ds.Ink, t13)
        Box(Modifier.height(36.dp).tap { c.closeMaker() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("취소", Ds.Accent, t13.copy(fontWeight = FontWeight.Medium)) }
    }
}

// ───────────────────────── 편집 화면 ─────────────────────────

private class ParamDef(
    val label: String, val min: Float, val max: Float, val decimals: Int, val unit: String,
    val get: (FilmAdjust) -> Float, val set: (FilmAdjust, Float) -> FilmAdjust,
) {
    fun round(v: Float): Float = (if (decimals > 0) Math.round(v * 100f) / 100f else Math.round(v).toFloat()).coerceIn(min, max)
    fun format(v: Float): String = when {
        decimals > 0 -> if (abs(v) < 0.005f) "0.00$unit" else "%+.2f$unit".format(v)
        else -> if (v.roundToInt() == 0) "0" else "%+d".format(v.roundToInt())
    }
}

private class Group2(val title: String?, val params: List<ParamDef>)
private class PanelSection(val title: String, val groups: List<Group2>)

private val SECTIONS = listOf(
    PanelSection("빛", listOf(Group2(null, listOf(
        ParamDef("노출", -5f, 5f, 2, " EV", { it.exposure }, { a, v -> a.copy(exposure = v) }),
        ParamDef("대비", -100f, 100f, 0, "", { it.contrast }, { a, v -> a.copy(contrast = v) }),
        ParamDef("밝은 영역", -100f, 100f, 0, "", { it.highlights }, { a, v -> a.copy(highlights = v) }),
        ParamDef("어두운 영역", -100f, 100f, 0, "", { it.shadows }, { a, v -> a.copy(shadows = v) }),
        ParamDef("흰색 계열", -100f, 100f, 0, "", { it.whites }, { a, v -> a.copy(whites = v) }),
        ParamDef("검정 계열", -100f, 100f, 0, "", { it.blacks }, { a, v -> a.copy(blacks = v) }),
    )))),
    PanelSection("색상", listOf(
        Group2("화이트 밸런스", listOf(
            ParamDef("색온도", -100f, 100f, 0, "", { it.temperature }, { a, v -> a.copy(temperature = v) }),
            ParamDef("색조", -100f, 100f, 0, "", { it.tint }, { a, v -> a.copy(tint = v) }),
        )),
        Group2("색상 조정", listOf(
            ParamDef("생동감", -100f, 100f, 0, "", { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
            ParamDef("채도", -100f, 100f, 0, "", { it.saturation }, { a, v -> a.copy(saturation = v) }),
        )),
    )),
    PanelSection("효과", listOf(Group2(null, listOf(
        ParamDef("텍스처", -100f, 100f, 0, "", { it.texture }, { a, v -> a.copy(texture = v) }),
        ParamDef("명료도", -100f, 100f, 0, "", { it.clarity }, { a, v -> a.copy(clarity = v) }),
        ParamDef("디헤이즈", -100f, 100f, 0, "", { it.dehaze }, { a, v -> a.copy(dehaze = v) }),
    )))),
)

/** 보정값과 되돌리기/다시 실행 기록. 슬라이더를 끄는 한 번의 조작이 기록 한 칸이다 */
private class EditorState {
    var adjust by mutableStateOf(FilmAdjust())
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    private val undo = ArrayList<FilmAdjust>()
    private val redo = ArrayList<FilmAdjust>()
    private var editStart: FilmAdjust? = null

    fun begin() { editStart = adjust }
    fun end() {
        val s = editStart ?: return
        editStart = null
        if (s != adjust) { undo += s; redo.clear(); sync() }
    }
    /** 한 번에 바뀌는 조작(숫자 입력, 초기화) */
    fun change(a: FilmAdjust) { if (a == adjust) return; undo += adjust; redo.clear(); adjust = a; sync() }
    fun undoStep() { if (undo.isEmpty()) return; redo += adjust; adjust = undo.removeAt(undo.lastIndex); sync() }
    fun redoStep() { if (redo.isEmpty()) return; undo += adjust; adjust = redo.removeAt(redo.lastIndex); sync() }
    private fun sync() { canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty() }
}

/** 가운데(0)가 기준인 가로 슬라이더. 끌면 값이 바뀌고, 두 번 빠르게 누르면 기본값으로 돌아간다. 값을 누르면 숫자를 직접 입력한다 */
@Composable
private fun AdjustSlider(def: ParamDef, value: Float, onStart: () -> Unit, onChange: (Float) -> Unit, onEnd: () -> Unit, onReset: () -> Unit, onEdit: () -> Unit) {
    val changed = abs(value) > 0.0001f
    // 제스처 블록은 한 번만 만들어지므로, 그 안에서 쓰는 값은 항상 최신 값을 읽어야 한다
    val latest by rememberUpdatedState(value)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(def.label, Ds.Ink, t15, Modifier.weight(1f))
            Box(Modifier.height(32.dp).tap(onClick = onEdit).padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Text(def.format(value), if (changed) Ds.Accent else Ds.Sub, t15.copy(fontWeight = if (changed) FontWeight.Medium else FontWeight.Normal))
            }
        }
        var widthPx by remember { mutableFloatStateOf(1f) }
        val accent = Ds.Accent
        val line = Ds.Line
        Box(
            Modifier.fillMaxWidth().height(40.dp).onSizeChanged { widthPx = it.width.toFloat() }.pointerInput(def) {
                var lastTap = 0L
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val track = (size.width - 2 * 14.dp.toPx()).coerceAtLeast(1f)
                    val startValue = latest
                    var moved = false
                    onStart()
                    horizontalDrag(down.id) { ch ->
                        if (!moved && abs(ch.position.x - down.position.x) > slop) moved = true
                        if (moved) {
                            val raw = startValue + (ch.position.x - down.position.x) / track * (def.max - def.min)
                            var v = def.round(raw)
                            if (abs(v) < (def.max - def.min) * 0.012f) v = 0f // 가운데 근처에서는 0에 붙는다
                            onChange(v)
                            ch.consume()
                        }
                    }
                    onEnd()
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < 300) onReset()
                        lastTap = now
                    }
                }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = 14.dp.toPx()
                val y = size.height / 2
                val x0 = pad; val x1 = size.width - pad
                val f = ((value - def.min) / (def.max - def.min)).coerceIn(0f, 1f)
                val tx = x0 + (x1 - x0) * f
                val cx = (x0 + x1) / 2
                drawLine(line, Offset(x0, y), Offset(x1, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White.copy(alpha = .5f), Offset(cx, y - 7.dp.toPx()), Offset(cx, y + 7.dp.toPx()), 2.dp.toPx())
                if (changed) drawLine(accent, Offset(cx, y), Offset(tx, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(Color.Black.copy(alpha = .35f), 11.dp.toPx(), Offset(tx, y + 1.dp.toPx()))
                drawCircle(if (changed) accent else Color.White, 10.dp.toPx(), Offset(tx, y))
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFFE8B84AL, 0xFFF0B83CL, 0xFFE88A3AL, 0xFFE5534BL, 0xFFD971B8L, 0xFF8E7CF0L,
    0xFF5C9DEBL, 0xFF3FB0B8L, 0xFF6CBF67L, 0xFFE0906EL, 0xFFC9B8A0L, 0xFFF5F1E8L,
)

@Composable
fun FilmEditor(c: AppController, source: AppController.EditorSource) {
    val st = remember(source) { EditorState() }
    val proxy = remember(source) { EditorRender.proxy(source) }
    val original = remember(proxy) { Bitmap.createBitmap(proxy.px, proxy.w, proxy.h, Bitmap.Config.ARGB_8888).asImageBitmap() }
    var shown by remember(proxy) { mutableStateOf<ImageBitmap?>(null) }
    // 보정값이 바뀔 때마다 원본 픽셀에서 다시 계산한다. 계산 중에 값이 또 바뀌면 이전 계산은 버린다
    LaunchedEffect(proxy) {
        snapshotFlow { st.adjust }.collectLatest { a ->
            shown = withContext(Dispatchers.Default) { EditorRender.render(proxy, a).asImageBitmap() }
        }
    }
    var showOriginal by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<ParamDef?>(null) }
    var open by remember { mutableStateOf(setOf("빛")) }

    BackHandler(enabled = !naming && entry == null && c.alert == null) {
        if (st.adjust.isNeutral) c.closeMaker()
        else c.alert = AlertState("만들기를 그만둘까요?", "지금까지 한 보정이 사라져요.", "그만두기") { c.alert = null; c.closeMaker() }
    }

    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize().background(Ds.Bg2)) {
        val topBar: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).tap {
                    if (st.adjust.isNeutral) c.closeMaker()
                    else c.alert = AlertState("만들기를 그만둘까요?", "지금까지 한 보정이 사라져요.", "그만두기") { c.alert = null; c.closeMaker() }
                }, contentAlignment = Alignment.Center) { Icon(Ico.CHEVRON_LEFT, 24.dp, Ds.Ink) }
                Box(Modifier.size(44.dp).tap(enabled = st.canUndo) { st.undoStep() }, contentAlignment = Alignment.Center) { Icon(Ico.UNDO, 22.dp, if (st.canUndo) Ds.Ink else Ds.Line) }
                Box(Modifier.size(44.dp).tap(enabled = st.canRedo) { st.redoStep() }, contentAlignment = Alignment.Center) { Icon(Ico.REDO, 22.dp, if (st.canRedo) Ds.Ink else Ds.Line) }
                Spacer(Modifier.weight(1f))
                // 누르고 있는 동안 원본을 보여 준다
                Box(
                    Modifier.size(44.dp).pointerInput(Unit) { detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ico.COMPARE, 24.dp, if (showOriginal) Ds.Accent else Ds.Ink) }
                Box(Modifier.height(44.dp).tap { naming = true }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("추가", Ds.Accent, t17m)
                }
            }
        }
        val preview: @Composable (Modifier) -> Unit = { mod ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(mod.clipToBounds(), contentAlignment = Alignment.Center) {
                Image(
                    if (showOriginal) original else shown ?: original, null,
                    Modifier.fillMaxSize().padding(8.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val lim = size.width * (scale - 1f) / 2f
                                val limY = size.height * (scale - 1f) / 2f
                                offset = if (scale <= 1.001f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-lim, lim), (offset.y + pan.y).coerceIn(-limY, limY))
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) },
                    contentScale = ContentScale.Fit,
                )
                if (showOriginal) Text("원본", Ds.Ink, t13, Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Ds.Glass).padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        val panel: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.background(Ds.Bg).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                if (!st.adjust.isNeutral) Box(Modifier.fillMaxWidth().height(36.dp).tap { st.change(FilmAdjust()) }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("모두 초기화", Ds.Sub, t13)
                }
                SECTIONS.forEach { sec ->
                    val isOpen = sec.title in open
                    Hairline()
                    Row(Modifier.fillMaxWidth().height(48.dp).tap { open = if (isOpen) open - sec.title else open + sec.title }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sec.title, Ds.Ink, t17m, Modifier.weight(1f))
                        Icon(Ico.CHEVRON_DOWN, 20.dp, Ds.Sub, Modifier.graphicsLayer { rotationZ = if (isOpen) 180f else 0f })
                    }
                    if (isOpen) sec.groups.forEach { g ->
                        g.title?.let { Text(it, Ds.Sub, t11.copy(letterSpacing = 1.sp), Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)) }
                        g.params.forEach { p ->
                            AdjustSlider(
                                p, p.get(st.adjust),
                                onStart = { st.begin() }, onChange = { v -> st.adjust = p.set(st.adjust, v) }, onEnd = { st.end() },
                                onReset = { st.change(p.set(st.adjust, 0f)) }, onEdit = { entry = p },
                            )
                        }
                    }
                }
            }
        }
        if (!land) {
            Column(Modifier.fillMaxSize()) {
                topBar()
                preview(Modifier.weight(1f).fillMaxWidth())
                panel(Modifier.fillMaxWidth().fillMaxHeight(.46f))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) { topBar(); preview(Modifier.weight(1f).fillMaxWidth()) }
                panel(Modifier.width(340.dp).fillMaxHeight())
            }
        }

        entry?.let { def ->
            NumberEntry(def, def.get(st.adjust), onDone = { v -> st.change(def.set(st.adjust, def.round(v))); entry = null }, onCancel = { entry = null })
        }
        if (naming) NameFilm(c, st.adjust, onCancel = { naming = false })
    }
}

/** 수치를 눌렀을 때 뜨는 직접 입력 창 */
@Composable
private fun NumberEntry(def: ParamDef, value: Float, onDone: (Float) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(if (def.decimals > 0) "%.2f".format(value) else value.roundToInt().toString()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).tap(onClick = onCancel), contentAlignment = Alignment.Center) {
        Column(Modifier.width(290.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2620)).tap { }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${def.label} (${def.format(def.min).trim()} ~ ${def.format(def.max).trim()})", Ds.Sub, t13)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    text, { text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(8) },
                    Modifier.weight(1f).focusRequester(focus).border(1.dp, Ds.Line, RoundedCornerShape(8.dp)).padding(12.dp),
                    textStyle = t17.copy(color = Ds.Ink), singleLine = true, cursorBrush = SolidColor(Ds.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                // 숫자 자판에 -가 없는 기기를 위한 부호 바꾸기
                Box(Modifier.padding(start = 8.dp).size(44.dp).clip(RoundedCornerShape(8.dp)).background(Ds.Bg2).tap { text = if (text.startsWith("-")) text.drop(1) else "-$text" }, contentAlignment = Alignment.Center) {
                    Text("±", Ds.Ink, t17)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.height(40.dp).tap { onDone(0f) }.padding(end = 12.dp), contentAlignment = Alignment.CenterStart) { Text("초기화", Ds.Sub, t15) }
                Row {
                    Box(Modifier.height(40.dp).tap(onClick = onCancel).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("취소", Ds.Sub, t15) }
                    Box(Modifier.height(40.dp).tap { text.toFloatOrNull()?.let(onDone) ?: onCancel() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("확인", Ds.Accent, t15.copy(fontWeight = FontWeight.Medium)) }
                }
            }
        }
    }
}

/** "추가"를 누르면 뜨는 창: 필름 이름과 포인트 색을 정한다 */
@Composable
private fun NameFilm(c: AppController, adjust: FilmAdjust, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(SWATCHES[0]) }
    var hue by remember { mutableFloatStateOf(-1f) } // 색상 막대를 만졌으면 그 색
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val accent = if (hue >= 0f) Color.hsv(hue, .62f, .95f) else Color(color)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).tap(onClick = onCancel), contentAlignment = Alignment.Center) {
        Column(Modifier.width(310.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2620)).tap { }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("필름 추가", Ds.Ink, t17m)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("이름", Ds.Sub, t13)
                BasicTextField(
                    name, { name = it.take(14) },
                    Modifier.fillMaxWidth().focusRequester(focus).border(1.dp, Ds.Line, RoundedCornerShape(8.dp)).padding(12.dp),
                    textStyle = t17.copy(color = Ds.Ink), singleLine = true, cursorBrush = SolidColor(accent),
                    decorationBox = { inner -> Box { if (name.isEmpty()) Text("예: 나의 여름", Ds.Sub, t17); inner() } },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("포인트 색", Ds.Sub, t13)
                SWATCHES.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { sw ->
                            val on = hue < 0f && color == sw
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(sw)).then(if (on) Modifier.border(3.dp, Ds.Ink, CircleShape) else Modifier)
                                    .tap { color = sw; hue = -1f },
                            )
                        }
                    }
                }
                // 색상 막대: 목록에 없는 색은 여기서 고른다
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, .62f, .95f) }))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                hue = (down.position.x / size.width * 360f).coerceIn(0f, 359f)
                                horizontalDrag(down.id) { ch -> hue = (ch.position.x / size.width * 360f).coerceIn(0f, 359f); ch.consume() }
                            }
                        },
                ) {
                    if (hue >= 0f) Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.White, 11.dp.toPx(), Offset(hue / 360f * size.width, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(accent))
                    Text(name.trim().ifEmpty { "이름 없음" }, Ds.Ink, t15, Modifier.padding(start = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.height(40.dp).tap(onClick = onCancel).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("취소", Ds.Sub, t15) }
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(accent).tap {
                        val n = name.trim()
                        when {
                            n.isEmpty() -> c.showToast("필름 이름을 적어 주세요")
                            Film.entries.any { it.displayName.equals(n, ignoreCase = true) && it.key !in c.state.hiddenFilms } -> c.showToast("필름이 이미 들어있습니다.")
                            else -> c.addCustomFilm(n, Integer.toUnsignedLong(accent.toArgb()), adjust)
                        }
                    }.padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("추가", Ds.OnAccent, t15.copy(fontWeight = FontWeight.Medium)) }
            }
        }
    }
}

// ───────────────────────── 필름 메뉴(그리드) 아래 버튼줄 ─────────────────────────

@Composable
private fun BarButton(label: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Ds.Glass).border(1.dp, Ds.Line, RoundedCornerShape(21.dp))
            .tap(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, if (!enabled) Ds.Line else if (danger) Ds.Danger else Ds.Ink, t15.copy(fontWeight = FontWeight.Medium), softWrap = false) }
}

@Composable
private fun BarArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Ds.Glass).border(1.dp, Ds.Line, CircleShape).tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Ico.CHEVRON_LEFT, 22.dp, if (enabled) Ds.Ink else Ds.Line, Modifier.graphicsLayer { rotationZ = if (left) 0f else 180f }) }
}

/**
 * 필름 메뉴(그리드)가 열려 있는 동안 필름 스트립 대신 보이는 줄. 필름을 꾹 눌러 고르면 취소 / 앞으로·뒤로(한 개만 골랐을 때) / 삭제가 나온다.
 */
@Composable
fun FilmMenuBar(c: AppController, modifier: Modifier = Modifier) {
    val sel = c.gridSelection
    val films = c.visibleFilms
    Box(modifier.fillMaxWidth().height(FILM_STRIP_HEIGHT), contentAlignment = Alignment.Center) {
        if (sel.isEmpty()) {
            Text("필름을 꾹 누르면 골라서 지우거나 순서를 바꿀 수 있어요", Ds.Sub, t13.copy(textAlign = TextAlign.Center), Modifier.padding(horizontal = 28.dp))
        } else {
            val one = sel.singleOrNull()
            val i = films.indexOfFirst { it.key == one }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BarButton("취소") { c.clearGridSelection() }
                if (one != null) {
                    BarArrow(left = true, enabled = i > 0) { c.moveSelectedFilm(-1) }
                    BarArrow(left = false, enabled = i in 0 until films.lastIndex) { c.moveSelectedFilm(1) }
                }
                BarButton(if (sel.size == 1) "삭제" else "삭제 ${sel.size}", danger = true) { c.askDeleteFilms(films.filter { it.key in sel }) }
            }
        }
    }
}
