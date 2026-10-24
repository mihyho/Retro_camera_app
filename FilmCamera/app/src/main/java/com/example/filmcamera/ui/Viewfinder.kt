package com.example.filmcamera.ui

import android.opengl.GLSurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.example.filmcamera.data.Film

@Composable
fun ViewfinderZone(
    glView: GLSurfaceView,
    label: String,
    grid: Boolean,
    stampOn: Boolean,
    stampText: String,
    flashTrigger: Int,
    zoom: Float,
    zoomEnabled: Boolean,
    onZoomBy: (Float) -> Unit,
    onZoomToggle: () -> Unit,
    noFilm: Boolean,
    pendingFilm: Film?,
    pendingLength: Int,
    onPendingLength: (Int) -> Unit,
    onLoad: () -> Unit,
    onDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // LCD 프레임: 얇은 검은 베젤 6dp. 3:4 화면이 남는 영역 안에 들어가도록 폭을 정한다.
        val previewW = minOf(maxWidth - 12.dp, (maxHeight - 12.dp) * 3f / 4f)
        val previewH = previewW * 4f / 3f
        val bezel = Color(0xFF050506)

        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bezel)
                .border(1.dp, Color(0xFF2C2C31), RoundedCornerShape(12.dp))
                .padding(6.dp),
        ) {
            Box(Modifier.size(previewW, previewH)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
                    AndroidView({ glView }, Modifier.fillMaxSize())
                    // 확대 제스처: 핀치로 확대/축소, 두 번 탭하면 1배 <-> 2배. 서랍/패널이 떠 있을 때는 받지 않는다.
                    if (zoomEnabled) {
                        Box(
                            Modifier.fillMaxSize()
                                .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> if (z != 1f) onZoomBy(z) } }
                                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onZoomToggle() }) },
                        )
                    }
                    if (grid) GridLines()
                    if (stampOn) {
                        Text(
                            stampText, color = Color(0xFFFF9A3C),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .08.em),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp),
                            softWrap = false,
                        )
                    }
                    Text(
                        label, color = LcdOrange,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .06.em),
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 10.dp),
                    )
                    if (noFilm) NoFilmOverlay(pendingFilm, pendingLength, onPendingLength, onLoad, onDrawer)
                    panel() // 현상/사진/설정/필름 메뉴는 이 창 안에서만 뜬다
                    Flash(flashTrigger)
                    ZoomHud(zoom, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                }
                // SurfaceView는 Compose 클립이 안 먹으므로, 베젤 색으로 모서리를 덮는다
                Canvas(Modifier.fillMaxSize()) {
                    val r = 4.dp.toPx()
                    val inner = Path().apply {
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
                    }
                    clipPath(inner, ClipOp.Difference) {
                        drawRect(bezel, topLeft = Offset(-2f, -2f), size = Size(size.width + 4f, size.height + 4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLines() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = .45f)
        val w = 1.5.dp.toPx()
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), w)
            drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), w)
        }
    }
}

@Composable
private fun Flash(trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            alpha.snapTo(.95f)
            alpha.animateTo(0f, tween(380))
        }
    }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.White))
}

/** 필름 모드 + 장전된 롤 없음 (LCD 스타일) */
@Composable
private fun NoFilmOverlay(
    pendingFilm: Film?, pendingLength: Int,
    onPendingLength: (Int) -> Unit, onLoad: () -> Unit, onDrawer: () -> Unit,
) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    @Composable
    fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(3.dp))
                .background(if (selected) LcdBlue else Color(0xFF16212C))
                .clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = if (enabled) Color(0xFFE4ECF2) else Color(0xFF5F6F7C), style = mono.copy(fontSize = 13.sp), softWrap = false) }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B1118).copy(alpha = .82f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text("NO FILM", color = LcdOrange, style = mono.copy(fontSize = 15.sp, letterSpacing = .2.em))
        Text("필름을 골라 넣어 주세요", color = Color(0xFFE4ECF2), style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 12.sp))
        Chip(pendingFilm?.let { "필름: ${it.displayName}" } ?: "필름: 고르기", selected = pendingFilm != null, onClick = onDrawer)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 36).forEach { n -> Chip("${n}장", n == pendingLength) { onPendingLength(n) } }
        }
        Chip(if (pendingFilm != null) "${pendingFilm.displayName} ${pendingLength}장 장전" else "필름을 골라 주세요", selected = false, enabled = pendingFilm != null, onClick = onLoad)
        Text(
            "넣으면 다 찍을 때까지 바꿀 수 없고, 사진은 롤이 끝나면 현상돼요", color = Color(0xFF93A5B3),
            style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}

/** 확대 배율 표시: 확대 중에는 계속 보이고, 1배로 돌아오면 잠시 뒤 사라진다 */
@Composable
private fun ZoomHud(zoom: Float, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(zoom) {
        visible = true
        if (zoom <= 1.02f) {
            kotlinx.coroutines.delay(1200)
            visible = false
        }
    }
    if (!visible) return
    Text(
        com.example.filmcamera.ZoomMath.format(zoom), color = Color.White,
        style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .08.em),
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
