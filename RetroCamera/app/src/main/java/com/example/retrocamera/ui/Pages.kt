package com.example.retrocamera.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retrocamera.AppController
import com.example.retrocamera.BuildConfig
import com.example.retrocamera.Panel
import com.example.retrocamera.ThumbLoader
import com.example.retrocamera.data.DevelopedRoll
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.Mode
import com.example.retrocamera.data.Roll
import com.example.retrocamera.data.Shot
import com.example.retrocamera.data.findInGroups
import com.example.retrocamera.data.libraryGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────── 사진(보관함) ─────────────────────────

private const val LIB_COLS = 4

private sealed interface LibItem {
    data class Header(val text: String) : LibItem
    data class Pending(val roll: Roll) : LibItem
    data class Photos(val shots: List<Shot>) : LibItem
    data object Empty : LibItem
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPage(c: AppController, onClose: () -> Unit) {
    val s = c.state
    val groups = libraryGroups(s)
    val items = buildList {
        s.roll?.let { add(LibItem.Header("현상 대기")); add(LibItem.Pending(it)) }
        groups.forEach { g ->
            add(LibItem.Header("${g.title} · ${g.meta}"))
            g.shots.chunked(LIB_COLS).forEach { add(LibItem.Photos(it)) }
        }
        if (isEmpty()) add(LibItem.Empty)
    }
    // 사진을 꾹 누르면 선택 모드: 탭으로 더 고르고, 위쪽 "삭제"로 한 번에 지운다
    var selected by remember { mutableStateOf(emptySet<Shot>()) }
    val selecting = selected.isNotEmpty()
    val haptic = LocalHapticFeedback.current
    BackHandler(enabled = selecting) { selected = emptySet() }
    Column(Modifier.fillMaxSize().background(Ds.Bg)) {
        if (selecting) NavBar(
            "${selected.size}장 선택", left = "취소", onLeft = { selected = emptySet() },
            right = "삭제", rightColor = Ds.Danger, onRight = { c.askDeletePhotos(selected.toList()) { selected = emptySet() } },
        ) else NavBar("사진", left = "카메라", leftChevron = true, onLeft = onClose)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(items) { item ->
                when (item) {
                    is LibItem.Header -> SectionHeader(item.text)
                    is LibItem.Pending -> PendingCard(item.roll)
                    is LibItem.Photos -> Row(Modifier.fillMaxWidth().padding(bottom = 1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        item.shots.forEach { shot ->
                            val picked = shot in selected
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).combinedClickable(
                                    onClick = { if (selecting) selected = if (picked) selected - shot else selected + shot else c.openViewer(shot) },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selected = if (picked) selected - shot else selected + shot },
                                ),
                            ) {
                                Thumb(shot, 240, Modifier.fillMaxSize())
                                if (picked) Box(Modifier.fillMaxSize().background(Ds.Accent.copy(alpha = .28f)))
                                if (selecting) Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                                        .background(if (picked) Ds.Accent else Color.Black.copy(alpha = .3f))
                                        .border(1.5.dp, if (picked) Ds.Accent else Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (picked) Icon(Ico.CHECK, 14.dp, Ds.OnAccent) }
                            }
                        }
                        repeat(LIB_COLS - item.shots.size) { Box(Modifier.weight(1f)) }
                    }
                    LibItem.Empty -> Column(Modifier.fillMaxWidth().padding(top = 80.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Ico.IMAGES, 40.dp, Ds.Sub)
                        Text("사진 없음", Ds.Ink, t17m)
                        Text("현상했거나 단일 촬영한 사진이 여기에 표시돼요.", Ds.Sub, t13.copy(textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }
}

/** 현상 대기: 사진은 보여 주지 않고 진행 상황만 */
@Composable
private fun PendingCard(roll: Roll) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(roll.film.accent)))
            Text(roll.film.displayName, Ds.Ink, t17, Modifier.weight(1f).padding(start = 10.dp))
            Text("${roll.shots.size} / ${roll.total}장", Ds.Sub, t13)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ds.Line)) {
            Box(Modifier.fillMaxWidth(roll.shots.size.toFloat() / roll.total).height(2.dp).background(Ds.Accent))
        }
        Text("다 찍으면 현상돼요. 그전에는 사진을 볼 수 없어요.", Ds.Sub, t13)
    }
}

// ───────────────────────── 사진 보기 ─────────────────────────

private fun dateTime(ms: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(ms))

/** 갤러리(MediaStore)로 내보낸 사진만 공유할 수 있다. 아직 내보내는 중이면 안내한다 */
fun shareShots(c: AppController, context: Context, shots: List<Shot>) {
    val uris = shots.mapNotNull { it.uri?.takeIf { u -> u.isNotEmpty() }?.let(Uri::parse) }
    if (shots.isEmpty() || uris.size != shots.size) { c.showToast("아직 갤러리로 내보내는 중이에요 · 잠시 후 다시 시도해 주세요"); return }
    val intent = (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)))
        .setType("image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("photo", uris[0]).also { cd -> uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) } }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        .onFailure { c.showToast("내보내지 못했어요 · 다시 시도해 주세요") }
}

@Composable
fun DetailPage(c: AppController, v: Panel.Viewer) {
    val context = LocalContext.current
    val groups = libraryGroups(c.state)
    val (group, index) = findInGroups(groups, v.shot) ?: return // 방금 지운 사진이면 그리지 않는다
    val shot = v.shot
    val bmp by produceState<android.graphics.Bitmap?>(null, shot.uri, shot.file) {
        value = withContext(Dispatchers.IO) { ThumbLoader.load(context, shot, 1600) }
    }
    var flipped by remember(shot) { mutableStateOf(false) } // 다른 사진으로 넘기면 앞면부터
    val flip by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = .8f, stiffness = 280f), label = "flip")
    val go = rememberUpdatedState { step: Int -> group.shots.getOrNull(index + step)?.let { c.panel = Panel.Viewer(it, v.back) } }
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar(group.title, left = "사진", leftChevron = true, onLeft = { c.closeViewer(v) }, sub = dateTime(shot.takenAt))
        // 사진을 한 번 누르면 뒤집혀서 촬영 정보가 보이고, 다시 누르면 앞면으로 돌아온다
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(shot) { detectTapGestures(onTap = { flipped = !flipped }) }
                .pointerInput(shot) {
                    var dx = 0f
                    detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx < -80f) go.value(1) else if (dx > 80f) go.value(-1) }) { _, d -> dx += d }
                },
            contentAlignment = Alignment.Center,
        ) {
            val ar = bmp?.let { it.width.toFloat() / it.height } ?: .8f
            Box(Modifier.aspectRatio(ar).graphicsLayer { rotationY = flip; cameraDistance = 14f * density }) {
                if (flip <= 90f) bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                else PhotoBack(shot, Modifier.graphicsLayer { rotationY = 180f }) // 뒤집힌 면이 거울처럼 보이지 않게 다시 돌려 둔다
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().height(44.dp).background(Ds.Bg), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).tap { shareShots(c, context, listOf(shot)) }, contentAlignment = Alignment.Center) { Icon(Ico.EXPORT, 24.dp, Ds.AccentInk) }
            Text("${index + 1} / ${group.shots.size}", Ds.Ink, t15.copy(textAlign = TextAlign.Center), Modifier.weight(1f), softWrap = false)
            Box(Modifier.size(44.dp).tap { c.askDeletePhoto(shot) }, contentAlignment = Alignment.Center) { Icon(Ico.TRASH, 24.dp, Ds.Danger) }
        }
    }
}

private fun dateTimeFull(ms: Long) = SimpleDateFormat("yyyy.MM.dd (E) HH:mm:ss", Locale.KOREA).format(Date(ms))

/** 사진 뒷면: 촬영 일시, 필름, 위치. 위치는 셔터를 누를 때 알려진 대략적인 좌표고, 주소는 기기의 지오코더가 알려 줄 때만 붙는다 */
@Composable
private fun PhotoBack(shot: Shot, modifier: Modifier) {
    val context = LocalContext.current
    val address by produceState<String?>(null, shot.lat, shot.lon) {
        val la = shot.lat; val lo = shot.lon
        value = if (la == null || lo == null) null else withContext(Dispatchers.IO) {
            runCatching { android.location.Geocoder(context, Locale.KOREA).getFromLocation(la, lo, 1)?.firstOrNull()?.getAddressLine(0) }.getOrNull()
        }
    }
    val f = shot.film
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Ds.Bg).border(1.dp, Ds.Line, RoundedCornerShape(6.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        BackField("촬영 일시") { Text(dateTimeFull(shot.takenAt), Ds.Ink, t17) }
        BackField("필름") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(f.glow()))
                Text("${f.displayName}  ISO ${f.iso}", Ds.Ink, t17, Modifier.padding(start = 10.dp))
            }
            Text(f.description, Ds.Sub, t13, Modifier.padding(top = 4.dp))
        }
        BackField("촬영 위치") {
            val la = shot.lat; val lo = shot.lon
            if (la == null || lo == null) Text("위치 정보 없음", Ds.Sub, t17)
            else {
                address?.let { Text(it, Ds.Ink, t17) }
                Text(
                    "%.4f°%s  %.4f°%s".format(Math.abs(la), if (la >= 0) "N" else "S", Math.abs(lo), if (lo >= 0) "E" else "W"),
                    if (address == null) Ds.Ink else Ds.Sub, if (address == null) t17 else t13, Modifier.padding(top = if (address == null) 0.dp else 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BackField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, Ds.Sub, TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp), Modifier.padding(bottom = 6.dp))
        content()
    }
}

// ───────────────────────── 설정 ─────────────────────────

@Composable
fun SettingsPage(c: AppController, onClose: () -> Unit) {
    val s = c.state
    val roll = s.roll
    val storage by produceState("계산 중", s.singleShots, s.devRolls, s.roll) {
        value = withContext(Dispatchers.IO) { c.storageSummary() }.let { (n, b) -> "${n}장 · %.1fMB".format(b / 1048576.0) }
    }
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar("설정", right = "완료", onRight = onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionHeader("촬영")
            Group {
                SwitchRow("격자", s.grid) { c.setGrid(it) }
                SwitchRow("날짜 각인", s.stamp) { c.setStamp(it) }
                SwitchRow("필름 그레인", s.grainOn) { c.setGrain(it) }
                SwitchRow("사진에 위치 기록", s.recordLocation) { c.setRecordLocation(it) }
                SwitchRow("볼륨 버튼 촬영", s.volumeShutter, last = true) { c.setVolumeShutter(it) }
            }
            SectionNote(
                "날짜 각인과 그레인은 저장되는 사진에도 똑같이 들어가요. 볼륨 버튼 촬영을 켜면 볼륨 키로 셔터를 눌러요. " +
                    "위치 기록을 켜면 찍은 곳이 사진 파일에 들어가서 기본 갤러리에서도 보이지만, 사진을 공유할 때 위치도 함께 가요." +
                    if (s.recordLocation && !c.locationGranted) " 지금은 위치 권한이 없어서 위치가 기록되지 않아요." else "",
                if (s.recordLocation && !c.locationGranted) Ds.Danger else Ds.Sub,
            )

            SectionHeader("필름 롤")
            Group {
                ValueRow("현재 롤", if (roll == null) "없음" else "${roll.film.displayName} · ${roll.shots.size}/${roll.total}장", last = roll == null && !BuildConfig.DEBUG)
                if (roll != null) {
                    if (BuildConfig.DEBUG) ActionRow("남은 장 모두 찍기") { c.debugFinishRoll() }
                    ActionRow("필름 버리기", Ds.Danger, last = true) { c.askDiscard() }
                } else {
                    ActionRow("필름 넣기", last = true) { c.panel = null; c.openLoad() }
                }
            }
            if (BuildConfig.DEBUG) SectionNote("\"남은 장 모두 찍기\"는 남은 장을 가짜 사진으로 채워 현상 과정을 바로 확인하는 테스트 기능이에요.")

            SectionHeader("저장 공간")
            Group {
                ValueRow("앱 보관함", storage)
                ActionRow("촬영 설정 초기화") { c.resetSettings() }
                ActionRow("모든 사진과 롤 지우기", Ds.Danger, last = true) { c.askWipe() }
            }
            SectionNote("롤은 기기에 저장돼요. 앱 데이터를 지우거나 다시 설치하면 진행 중인 롤은 초기화되고 잠금이 풀려요. 현상된 사진은 기기 갤러리에도 남아요.")
        }
    }
}

// ───────────────────────── 필름 넣기(모달) ─────────────────────────

@Composable
fun FilmLoadModal(c: AppController) {
    val chosen = c.pendingFilm
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar("필름 넣기", left = "취소", onLeft = { c.loadOpen = false }, right = "장전", rightEnabled = chosen != null, onRight = c::loadPendingRoll)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                Row(Modifier.widthIn(max = 260.dp).fillMaxWidth().height(29.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, Ds.AccentBorder, RoundedCornerShape(5.dp))) {
                    listOf(24, 36).forEach { n ->
                        val on = c.pendingLength == n
                        Box(Modifier.weight(1f).fillMaxSize().background(if (on) Ds.Accent else Ds.Bg).tap { c.pendingLength = n }, contentAlignment = Alignment.Center) {
                            Text("${n}장", if (on) Ds.OnAccent else Ds.AccentInk, t13)
                        }
                    }
                }
            }
            SectionHeader("필름")
            Group {
                Film.entries.forEachIndexed { i, f ->
                    Column(Modifier.fillMaxWidth().tap { c.pendingFilm = f }) {
                        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(f.accent)))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(f.displayName, Ds.Ink, t17, softWrap = false)
                                Text(f.description, Ds.Sub, t12, softWrap = false)
                            }
                            Text("ISO ${f.iso}", Ds.Sub, t13)
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { if (chosen == f) Icon(Ico.CHECK, 20.dp, Ds.AccentInk) }
                        }
                        if (i != Film.entries.lastIndex) Hairline()
                    }
                }
            }
        }
    }
}

// ───────────────────────── 현상 완료(모달) ─────────────────────────

@Composable
fun DevelopModal(c: AppController, roll: DevelopedRoll) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar("현상 완료")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp)) {
            Text("${roll.film.displayName} · ${roll.total}장", Ds.Ink, TextStyle(fontFamily = Sans, fontSize = 26.sp, fontWeight = FontWeight.Light))
            Text(
                if (roll.exported) "한 롤을 다 찍었어요. 사진이 갤러리에 추가됐고 필름 잠금이 풀렸어요." else "한 롤을 다 찍었어요. 사진을 갤러리로 옮기는 중이에요…",
                Ds.Sub, t15.copy(lineHeight = 21.sp), Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            roll.shots.chunked(6).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEachIndexed { k, shot ->
                        Column(Modifier.weight(1f).tap { c.openViewer(shot) }) {
                            Thumb(shot, 200, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                            Text("%02d".format(r * 6 + k + 1), Ds.Sub, TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp), Modifier.padding(top = 2.dp))
                        }
                    }
                    repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Hairline()
        Column(Modifier.fillMaxWidth().background(Ds.Bg)) {
            val btn = @Composable { text: String, weight: FontWeight, last: Boolean, onClick: () -> Unit ->
                Box(Modifier.fillMaxWidth().height(44.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) {
                    Text(text, Ds.AccentInk, sansStyle(17.sp, weight))
                }
                if (!last) Hairline()
            }
            btn("새 필름 넣기", FontWeight.Medium, false) { c.panel = null; c.openLoad() }
            btn("단일 촬영으로 계속", FontWeight.Normal, false) { c.panel = null; c.setMode(Mode.SINGLE) }
            btn("롤 전체 내보내기", FontWeight.Normal, true) { shareShots(c, context, roll.shots) }
        }
    }
}
