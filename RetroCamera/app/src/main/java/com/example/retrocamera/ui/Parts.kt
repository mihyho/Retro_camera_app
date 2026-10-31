package com.example.retrocamera.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retrocamera.ThumbLoader
import com.example.retrocamera.data.Shot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 0.5dp 구분선 */
@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(0.5.dp).background(Ds.Line))

/** 썸네일(백그라운드 로딩) */
@Composable
fun Thumb(shot: Shot, px: Int, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, shot.uri) {
        value = withContext(Dispatchers.IO) { ThumbLoader.load(context, shot, px) }
    }
    Box(modifier.background(Ds.Bg2)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = scale, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
    }
}

/** 페이지 위 내비 바(44): 왼쪽/가운데 제목/오른쪽 */
@Composable
fun NavBar(
    title: String, left: String? = null, onLeft: () -> Unit = {}, right: String? = null, onRight: () -> Unit = {},
    leftChevron: Boolean = false, rightEnabled: Boolean = true,
    sub: String? = null, rightColor: Color? = null,
) {
    Column(Modifier.fillMaxWidth().background(Ds.Bg)) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, Ds.Ink, if (sub != null) sansStyle(15.sp, FontWeight.Medium) else t17m, softWrap = false)
                if (sub != null) Text(sub, Ds.Sub, t11, softWrap = false)
            }
            if (left != null) Row(
                Modifier.align(Alignment.CenterStart).height(44.dp).tap(onClick = onLeft).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leftChevron) Icon(Ico.CHEVRON_LEFT, 20.dp, Ds.AccentInk)
                Text(left, Ds.AccentInk, t17, softWrap = false)
            }
            if (right != null) Box(
                Modifier.align(Alignment.CenterEnd).height(44.dp).tap(enabled = rightEnabled, onClick = onRight).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(right, if (rightEnabled) rightColor ?: Ds.AccentInk else Ds.Sub, t17m, softWrap = false) }
        }
        Hairline()
    }
}

/** iOS 스위치 51×31 */
@Composable
fun IosSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (on) 22.dp else 2.dp, tween(200), label = "knob")
    Box(
        modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) Ds.Accent else Ds.Bg)
            .then(if (on) Modifier else Modifier.border(1.5.dp, Ds.Line, RoundedCornerShape(16.dp))),
    ) {
        Box(Modifier.offset(x, 2.dp).size(27.dp).clip(CircleShape).background(Color.White).border(0.5.dp, Ds.Line, CircleShape))
    }
}

// ── 그룹 테이블(설정) ──

@Composable
fun SectionHeader(text: String) =
    Text(text, Ds.Sub, t13, Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp))

@Composable
fun SectionNote(text: String, color: Color = Ds.Sub) =
    Text(text, color, t13.copy(lineHeight = 19.sp), Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ds.Bg)) { Hairline(); content(); Hairline() }
}

/** 그룹 안 행(최소 44). 마지막 행이 아니면 아래 구분선 */
@Composable
fun Row44(last: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { content() }
        if (!last) Hairline()
    }
}

@Composable
fun SwitchRow(label: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) =
    Row44(last, onClick = { onChange(!on) }) {
        Text(label, Ds.Ink, t17, Modifier.weight(1f))
        IosSwitch(on)
    }

@Composable
fun ValueRow(label: String, value: String, last: Boolean = false) = Row44(last) {
    Text(label, Ds.Ink, t17, Modifier.weight(1f))
    Text(value, Ds.Sub, t17)
}

/** 이름과 현재 값, 그 아래 슬라이더가 있는 행. 값은 lo..hi 칸 단위 */
@Composable
fun SliderRow(label: String, valueText: String, value: Int, lo: Int, hi: Int, last: Boolean = false, onChange: (Int) -> Unit) =
    Row44(last) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Ds.Ink, t17, Modifier.weight(1f))
                Text(valueText, Ds.Sub, t17)
            }
            StepSlider(value, lo, hi, onChange)
        }
    }

@Composable
fun ActionRow(label: String, color: Color = Ds.AccentInk, last: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) =
    Row44(last, onClick = if (enabled) onClick else null) { Text(label, if (enabled) color else Ds.Sub, t17) }

// ── 알림창(iOS 7) ──

@Composable
fun IosAlert(title: String, msg: String, ok: String, onCancel: () -> Unit, onOk: () -> Unit, progress: Float = 1f) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f * progress.coerceIn(0f, 1f))).tap(onClick = onCancel), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(270.dp).graphicsLayer { val k = .9f + .1f * progress; scaleX = k; scaleY = k; alpha = progress.coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2620)).tap { /* 카드 안 탭이 닫힘으로 새지 않게 */ },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, Ds.Ink, t17m.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth())
                Text(msg, Ds.Sub, t13.copy(textAlign = TextAlign.Center, lineHeight = 18.sp), Modifier.fillMaxWidth())
            }
            Hairline()
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                Box(Modifier.weight(1f).fillMaxSize().tap(onClick = onCancel), contentAlignment = Alignment.Center) { Text("취소", Ds.AccentInk, t17) }
                Box(Modifier.width(0.5.dp).fillMaxSize().background(Ds.Line))
                Box(Modifier.weight(1f).fillMaxSize().tap(onClick = onOk), contentAlignment = Alignment.Center) { Text(ok, Ds.Danger, t17m) }
            }
        }
    }
}

/** HUD 알림: 상단 가운데 */
@Composable
fun Hud(text: String, modifier: Modifier = Modifier) {
    Text(
        text, Ds.OnAccent, t13.copy(textAlign = TextAlign.Center),
        modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(10.dp)).background(Ds.Ink.copy(alpha = .94f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 노출 슬라이더: 값은 칸(index) 단위, 손가락을 따라 가장 가까운 칸으로 붙는다 */
@Composable
fun StepSlider(value: Int, lo: Int, hi: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableFloatStateOf(1f) }
    val cur = rememberUpdatedState(onChange)
    fun at(x: Float): Int = if (hi == lo) lo else (lo + (x / width).coerceIn(0f, 1f) * (hi - lo)).let { Math.round(it) }
    val frac = if (hi == lo) 0f else (value - lo).toFloat() / (hi - lo)
    Box(
        modifier.fillMaxWidth().height(32.dp).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(lo, hi) { detectTapGestures { cur.value(at(it.x)) } }
            .pointerInput(lo, hi) { detectHorizontalDragGestures { change, _ -> cur.value(at(change.position.x)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ds.Line))
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ds.Accent))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier.offset((maxWidth - 24.dp) * frac, 0.dp).size(24.dp).clip(CircleShape).background(Color.White)
                    .border(0.5.dp, Ds.Line, CircleShape),
            )
        }
    }
}
