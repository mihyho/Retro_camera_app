package com.example.retrocamera.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.retrocamera.AppController
import com.example.retrocamera.Panel
import com.example.retrocamera.data.Mode
import kotlinx.coroutines.delay

/**
 * 화면 구성: 카메라(상단 바 · 뷰파인더 · 하단) 위에 페이지(보관함/설정/사진 보기)와 모달(필름 넣기/현상 완료)이 열린다.
 * 기존 십자키/MENU/EV 입력은 터치(스와이프, 필름 그리드, 노출 슬라이더)로 바뀌었고 기능은 그대로 있다.
 */
@Composable
fun CameraScreen(
    c: AppController, glView: GLSurfaceView, onShutter: () -> Unit,
    onZoomBy: (Float) -> Unit, onZoomStep: () -> Unit,
) {
    val s = c.state
    val panel = c.panel
    val viewer = panel as? Panel.Viewer
    val base = viewer?.back ?: panel
    // 닫히는 애니메이션 동안에도 내용이 남게 마지막 사진 보기를 기억한다
    val lastViewer = remember { arrayOfNulls<Panel.Viewer>(1) }
    if (viewer != null) lastViewer[0] = viewer

    BackHandler(enabled = c.alert != null) { c.alert = null }
    BackHandler(enabled = c.alert == null && c.loadOpen) { c.loadOpen = false }
    BackHandler(enabled = c.alert == null && !c.loadOpen && panel != null) { if (viewer != null) c.closeViewer(viewer) else c.panel = null }
    BackHandler(enabled = panel == null && !c.loadOpen && c.drawerOpen) { c.closeDrawer() }
    BackHandler(enabled = panel == null && !c.loadOpen && c.exposureOpen) { c.exposureOpen = false }

    LaunchedEffect(c.currentFilm) { Ds.liveFilm = c.currentFilm }

    fun openPage(p: Panel) { c.exposureOpen = false; c.closeDrawer(); c.panel = p }

    Box(Modifier.fillMaxSize().background(Ds.Bg).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        CameraBody(
            c, glView, topBar = { TopBar(c, onSettings = { openPage(Panel.Settings) }, onLibrary = { openPage(Panel.Gallery) }) },
            onLibrary = { openPage(Panel.Gallery) }, onShutter = onShutter,
            onGrid = { if (s.mode == Mode.FILM && s.roll == null) c.openLoad() else if (c.drawerOpen) c.closeDrawer() else c.openDrawer() },
            onZoomBy = onZoomBy, onZoomStep = onZoomStep, onLoad = c::openLoad,
        )

        // 페이지가 열리면 뒤의 카메라가 어두워진다(페이지와 같은 스프링으로 따라 움직여서 한 동작처럼 보인다)
        val pageOpen = base == Panel.Gallery || base == Panel.Settings || viewer != null || base is Panel.Develop || c.loadOpen
        val dim by animateFloatAsState(if (pageOpen) 1f else 0f, spring(1f, 380f), label = "dim")
        if (dim > 0.001f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f * dim.coerceIn(0f, 1f))))

        SlideRight(base == Panel.Gallery) { LibraryPage(c, onClose = { c.panel = null }) }
        SlideRight(base == Panel.Settings) { SettingsPage(c, onClose = { c.panel = null }) }
        SlideUp(base is Panel.Develop) {
            (base as? Panel.Develop)?.let { p -> s.devRolls.firstOrNull { it.start == p.rollStart }?.let { DevelopModal(c, it) } }
        }
        SlideRight(viewer != null) { (viewer ?: lastViewer[0])?.let { DetailPage(c, it) } }
        SlideUp(c.loadOpen) { FilmLoadModal(c) }

        // 알림창: 배경이 어두워지면서 카드가 살짝 커지며 나타난다. 닫힐 때도 내용이 남게 마지막 알림을 기억한다
        val lastAlert = remember { arrayOfNulls<com.example.retrocamera.AlertState>(1) }
        c.alert?.let { lastAlert[0] = it }
        val alertP by animateFloatAsState(if (c.alert != null) 1f else 0f, spring(.8f, 420f), label = "alert")
        if (alertP > 0.001f) lastAlert[0]?.let { a ->
            IosAlert(a.title, a.msg, a.ok, onCancel = { c.alert = null }, onOk = a.onOk, progress = alertP)
        }

        // 알림(HUD): 위에서 내려와 나타나고, 사라질 때는 다시 위로 올라간다
        val lastToast = remember { arrayOfNulls<String>(1) }
        c.toast?.let { t ->
            lastToast[0] = t.text
            LaunchedEffect(t.id) { delay(2400); c.dismissToast(t.id) }
        }
        val toastP by animateFloatAsState(if (c.toast != null) 1f else 0f, spring(.85f, 420f), label = "toast")
        if (toastP > 0.001f) lastToast[0]?.let { text ->
            Box(
                Modifier.fillMaxSize().padding(top = 52.dp).graphicsLayer { translationY = -(1f - toastP) * 28.dp.toPx(); alpha = toastP.coerceIn(0f, 1f) },
                contentAlignment = Alignment.TopCenter,
            ) { Hud(text) }
        }
    }
}

// 페이지는 임계 감쇠 스프링(튕김 없음)으로 들어오고 같은 길로 나간다. 도착할 때 속도가 자연스럽게 줄어든다
private val PageSpring = spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)
// 모달(아래에서 올라오는 시트)은 살짝 탄력을 줬다
private val SheetSpring = spring(dampingRatio = .9f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)

/** 오른쪽에서 밀려 들어오는 페이지 */
@Composable
private fun SlideRight(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = slideInHorizontally(PageSpring) { it }, exit = slideOutHorizontally(PageSpring) { it }) {
        Box(Modifier.fillMaxSize().background(Ds.Bg)) { content() }
    }
}

/** 아래에서 올라오는 모달 */
@Composable
private fun SlideUp(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = slideInVertically(SheetSpring) { it }, exit = slideOutVertically(PageSpring) { it }) {
        Box(Modifier.fillMaxSize().background(Ds.Bg)) { content() }
    }
}
