package com.example.retrocamera.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onZoomBy: (Float) -> Unit, onZoomToggle: () -> Unit,
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

    fun openPage(p: Panel) { c.exposureOpen = false; c.closeDrawer(); c.panel = p }

    Box(Modifier.fillMaxSize().background(Ds.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        CameraLayout(
            top = { TopBar(c, onSettings = { openPage(Panel.Settings) }, onLibrary = { openPage(Panel.Gallery) }) },
            viewfinder = { Viewfinder(c, glView, onZoomBy, onZoomToggle, onLoad = c::openLoad) },
            bottom = { land ->
                BottomBar(
                    c, land, onLibrary = { openPage(Panel.Gallery) }, onShutter = onShutter,
                    onGrid = { if (s.mode == Mode.FILM && s.roll == null) c.openLoad() else if (c.drawerOpen) c.closeDrawer() else c.openDrawer() },
                )
            },
        )

        SlideRight(base == Panel.Gallery) { LibraryPage(c, onClose = { c.panel = null }) }
        SlideRight(base == Panel.Settings) { SettingsPage(c, onClose = { c.panel = null }) }
        SlideUp(base is Panel.Develop) {
            (base as? Panel.Develop)?.let { p -> s.devRolls.firstOrNull { it.start == p.rollStart }?.let { DevelopModal(c, it) } }
        }
        SlideRight(viewer != null) { (viewer ?: lastViewer[0])?.let { DetailPage(c, it) } }
        SlideUp(c.loadOpen) { FilmLoadModal(c) }

        c.alert?.let { a -> IosAlert(a.title, a.msg, a.ok, onCancel = { c.alert = null }, onOk = a.onOk) }

        c.toast?.let { t ->
            LaunchedEffect(t.id) { delay(2400); c.dismissToast(t.id) }
            Box(Modifier.fillMaxSize().padding(top = 52.dp), contentAlignment = Alignment.TopCenter) { Hud(t.text) }
        }
    }
}

/** 오른쪽에서 밀려 들어오는 페이지(300ms) */
@Composable
private fun SlideRight(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = slideInHorizontally(tween(300)) { it }, exit = slideOutHorizontally(tween(300)) { it }) {
        Box(Modifier.fillMaxSize().background(Ds.Bg)) { content() }
    }
}

/** 아래에서 올라오는 모달(300ms) */
@Composable
private fun SlideUp(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = slideInVertically(tween(300)) { it }, exit = slideOutVertically(tween(300)) { it }) {
        Box(Modifier.fillMaxSize().background(Ds.Bg)) { content() }
    }
}
