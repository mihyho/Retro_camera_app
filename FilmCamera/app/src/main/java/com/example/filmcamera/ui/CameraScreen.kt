package com.example.filmcamera.ui

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filmcamera.AppController
import com.example.filmcamera.BuildConfig
import com.example.filmcamera.Panel
import com.example.filmcamera.data.Film
import com.example.filmcamera.data.galleryEntries
import com.example.filmcamera.data.Mode
import kotlinx.coroutines.delay

/**
 * 구형 디카 몸체(고정) + LCD. 갤러리/설정/필름 메뉴는 모두 LCD 안에서만 바뀐다.
 * 십자키는 메뉴가 열려 있으면 메뉴를 움직이고(▲▼ 이동, ◀▶ 값 변경, SET 선택), 아니면 확대(▲▼)와 필름 바꾸기(◀▶)다.
 */
@Composable
fun CameraScreen(
    c: AppController, glView: GLSurfaceView, onShutter: () -> Unit,
    onZoomBy: (Float) -> Unit, onZoomToggle: () -> Unit,
) {
    val s = c.state
    val film = s.mode == Mode.FILM
    val roll = s.roll
    val total = roll?.total ?: 24
    val remaining = roll?.remaining ?: total
    val noFilm = film && roll == null

    val filmName = when {
        !film -> s.singleFilm.displayName
        roll != null -> "${roll.film.displayName} · $remaining"
        else -> c.pendingFilm?.displayName ?: "필름 고르기"
    }
    val label = when {
        noFilm -> ""
        film -> "${c.currentFilm.label} $remaining/$total"
        else -> "${c.currentFilm.label} SINGLE"
    }

    // ── 메뉴 내용(화면과 십자키가 같은 목록을 본다) ──
    val filmRows = Film.entries.map { f ->
        val locked = film && roll != null && roll.film != f
        val cur = when {
            !film -> s.singleFilm == f
            roll != null -> roll.film == f
            else -> c.pendingFilm == f
        }
        val rowLabel = when {
            cur -> if (film && roll != null) "장전됨 ${remaining}장" else "사용 중"
            locked -> "잠김"
            else -> "SET"
        }
        FilmRow(f, cur, locked, rowLabel)
    }
    val pickFilm = { i: Int ->
        val r = filmRows[i]
        when {
            r.current -> c.closeDrawer()
            r.locked -> c.lockedFilmTapped()
            else -> c.takeFilm(r.film)
        }
    }
    val settingsItems = buildList {
        add(MenuItem("격자 표시", if (s.grid) "켬" else "끔") { c.setGrid(!s.grid) })
        add(MenuItem("날짜 각인", if (s.stamp) "켬" else "끔") { c.setStamp(!s.stamp) })
        add(MenuItem("볼륨 버튼 촬영", if (s.volumeShutter) "켬" else "끔") { c.setVolumeShutter(!s.volumeShutter) })
        if (roll != null) add(MenuItem("필름 버리기", "SET", danger = true) { if (it == 0) c.askDiscard() })
        if (BuildConfig.DEBUG) {
            add(MenuItem("남은 장 모두 찍기", "TEST", enabled = roll != null) { if (it == 0) c.debugFinishRoll() })
            add(MenuItem("앱 데이터 지우기", "TEST") { if (it == 0) c.debugWipe() })
        }
    }
    val entries = galleryEntries(s.devRolls, s.singleShots)

    // 메뉴를 새로 열면 처음 줄(필름 메뉴는 지금 필름)로. 사진을 크게 봤다가 돌아와도 고른 사진은 유지한다.
    val base = (c.panel as? Panel.Viewer)?.back ?: c.panel
    val menuKey = when {
        c.drawerOpen -> "film"
        base == Panel.Settings -> "settings"
        base == Panel.Gallery -> "gallery"
        else -> ""
    }
    LaunchedEffect(menuKey) {
        c.menuIndex = when (menuKey) {
            "film" -> filmRows.indexOfFirst { it.current }.coerceAtLeast(0)
            "gallery" -> entries.lastIndex.coerceAtLeast(0) // 디카처럼 가장 최근 사진부터
            else -> 0
        }
    }

    fun press(key: Key) {
        val ask = c.discardAsk
        when {
            ask != null -> when (key) {
                Key.LEFT, Key.RIGHT -> c.discardAsk = 1 - ask
                Key.SET -> if (ask == 1) c.discardRoll() else c.discardAsk = null
                else -> {}
            }
            c.drawerOpen -> when (key) {
                Key.UP -> c.moveMenu(-1, filmRows.size)
                Key.DOWN -> c.moveMenu(1, filmRows.size)
                Key.SET -> pickFilm(c.menuIndex.coerceIn(0, filmRows.size - 1))
                else -> {}
            }
            base == Panel.Settings && c.panel !is Panel.Viewer -> {
                val i = c.menuIndex.coerceIn(0, settingsItems.size - 1)
                when (key) {
                    Key.UP -> c.moveMenu(-1, settingsItems.size)
                    Key.DOWN -> c.moveMenu(1, settingsItems.size)
                    Key.LEFT -> if (settingsItems[i].value in listOf("켬", "끔")) settingsItems[i].onKey(-1)
                    Key.RIGHT -> if (settingsItems[i].value in listOf("켬", "끔")) settingsItems[i].onKey(1)
                    Key.SET -> if (settingsItems[i].enabled) settingsItems[i].onKey(0)
                }
            }
            c.panel == Panel.Gallery && entries.isNotEmpty() ->
                if (c.playSingle) when (key) { // 한 장 보기: ◀ 오래된 쪽, ▶ 최근 쪽
                    Key.LEFT -> c.moveMenu(-1, entries.size)
                    Key.RIGHT -> c.moveMenu(1, entries.size)
                    Key.DOWN, Key.SET -> c.playSingle = false
                    Key.UP -> {}
                } else when (key) { // 목록
                    Key.UP -> c.moveMenu(-GALLERY_COLS, entries.size)
                    Key.DOWN -> c.moveMenu(GALLERY_COLS, entries.size)
                    Key.LEFT -> c.moveMenu(-1, entries.size)
                    Key.RIGHT -> c.moveMenu(1, entries.size)
                    Key.SET -> c.playSingle = true
                }
            c.panel is Panel.Viewer -> if (key == Key.SET) c.closeViewer(c.panel as Panel.Viewer)
            c.panel != null -> if (key == Key.SET) c.panel = null
            else -> when (key) {
                Key.UP -> onZoomBy(1.25f)
                Key.DOWN -> onZoomBy(0.8f)
                Key.LEFT -> c.cycleFilm(-1)
                Key.RIGHT -> c.cycleFilm(1)
                Key.SET -> c.openDrawer()
            }
        }
    }

    BackHandler(enabled = c.discardAsk != null) { c.discardAsk = null }
    BackHandler(enabled = c.discardAsk == null && c.panel != null) {
        (c.panel as? Panel.Viewer)?.let { c.closeViewer(it) } ?: run { c.panel = null }
    }
    BackHandler(enabled = c.drawerOpen && c.discardAsk == null) { c.closeDrawer() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraBody(
            mode = s.mode, playActive = c.panel == Panel.Gallery, filmName = filmName,
            onMode = c::setMode,
            onMenu = { c.discardAsk = null; c.closeDrawer(); c.togglePanel(Panel.Settings) },
            onDisp = { c.setGrid(!s.grid) },
            onUp = { press(Key.UP) }, onDown = { press(Key.DOWN) },
            onLeft = { press(Key.LEFT) }, onRight = { press(Key.RIGHT) },
            onSet = { press(Key.SET) },
            onFilm = c::openDrawer,
            onShoot = { c.discardAsk = null; c.panel = null; c.closeDrawer() },
            onPlay = { c.discardAsk = null; c.closeDrawer(); c.playSingle = true; c.panel = Panel.Gallery },
            onShutter = onShutter,
        ) {
            ViewfinderZone(
                glView = glView, label = label, grid = s.grid, stampOn = s.stamp, stampText = c.stampText(),
                flashTrigger = c.flashTrigger, noFilm = noFilm,
                zoom = c.zoomRatio, zoomEnabled = c.zoomMax > 1.01f && c.panel == null && !c.drawerOpen,
                onZoomBy = onZoomBy, onZoomToggle = onZoomToggle,
                pendingFilm = c.pendingFilm, pendingLength = c.pendingLength,
                onPendingLength = { c.pendingLength = it }, onLoad = c::loadPendingRoll,
                onDrawer = c::openDrawer,
                modifier = Modifier.weight(1f),
                panel = {
                    when (val p = c.panel) {
                        is Panel.Develop -> s.devRolls.firstOrNull { it.start == p.rollStart }?.let { r ->
                            DevelopPanel(
                                r,
                                onNewFilm = { c.panel = null; c.openDrawer() },
                                onSingle = { c.panel = null; c.setMode(Mode.SINGLE) },
                                onOpen = c::openViewer,
                            )
                        }
                        Panel.Gallery ->
                            if (c.playSingle && entries.isNotEmpty()) {
                                PlaybackView(
                                    entries, c.menuIndex, pendingCount = s.roll?.shots?.size ?: 0,
                                    onStep = { c.moveMenu(it, entries.size) }, onIndex = { c.playSingle = false },
                                )
                            } else {
                                GalleryMenu(
                                    pendingRoll = s.roll, entries = entries,
                                    selected = c.menuIndex, onSelect = { c.menuIndex = it },
                                    onOpen = { c.menuIndex = it; c.playSingle = true },
                                    onClose = { c.panel = null },
                                )
                            }
                        Panel.Settings -> SettingsMenu(
                            items = settingsItems, selected = c.menuIndex, onSelect = { c.menuIndex = it },
                            note = "롤은 기기에 저장돼요. 앱 데이터를 지우거나 다시 설치하면 진행 중인 롤은 초기화되고 잠금이 풀려요.",
                            onClose = { c.panel = null },
                        )
                        is Panel.Viewer -> ViewerPanel(p.shot, p.title, p.meta, onClose = { c.closeViewer(p) })
                        null -> {}
                    }
                    if (c.drawerOpen) {
                        val hint = when {
                            !film -> "◀▶ 키로도 바꿔요"
                            roll != null -> "${roll.film.displayName} 롤을 다 찍을 때까지 잠겨요"
                            else -> "넣을 필름을 고르세요"
                        }
                        FilmMenu(hint, filmRows, c.menuIndex, onPick = { c.menuIndex = it; pickFilm(it) }, onClose = c::closeDrawer)
                    }
                    val ask = c.discardAsk
                    if (ask != null && roll != null) {
                        DiscardConfirm(
                            roll, ask, onChoose = { c.discardAsk = it },
                            onCancel = { c.discardAsk = null }, onConfirm = c::discardRoll,
                        )
                    }
                },
            )
        }
        Toast(c, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars).padding(top = 56.dp))
    }
}

@Composable
private fun Toast(c: AppController, modifier: Modifier) {
    val t = c.toast ?: return
    LaunchedEffect(t.id) {
        delay(2200)
        c.dismissToast(t.id)
    }
    Box(modifier) {
        Text(
            t.text, color = PF.Light,
            style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(PF.ToastBg).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
