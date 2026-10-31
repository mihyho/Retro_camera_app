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
import com.example.retrocamera.Panel
import com.example.retrocamera.ThumbLoader
import com.example.retrocamera.data.Film
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
    data class Photos(val shots: List<Shot>) : LibItem
    data object Empty : LibItem
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPage(c: AppController, onClose: () -> Unit) {
    val s = c.state
    val groups = libraryGroups(s)
    val items = buildList {
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
                    is LibItem.Photos -> Row(Modifier.fillMaxWidth().padding(bottom = 1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        item.shots.forEach { shot ->
                            val picked = shot in selected
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).combinedClickable(
                                    onClick = { if (selecting) selected = if (picked) selected - shot else selected + shot else c.openViewer(shot) },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selected = if (picked) selected - shot else selected + shot },
                                ),
                            ) {
                                Thumb(shot, 480, Modifier.fillMaxSize())
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
                        Text("찍은 사진이 여기에 표시돼요.", Ds.Sub, t13.copy(textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }
}

// ───────────────────────── 사진 보기 ─────────────────────────

private fun dateTime(ms: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(ms))

fun shareShots(c: AppController, context: Context, shots: List<Shot>) {
    val uris = shots.map { Uri.parse(it.uri) }
    if (uris.isEmpty()) return
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
    val bmp by produceState<android.graphics.Bitmap?>(null, shot.uri) {
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
                if (flip <= 90f) bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
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
                Text(if (f.iso > 0) "${f.displayName}  ISO ${f.iso}" else f.displayName, Ds.Ink, t17, Modifier.padding(start = 10.dp))
            }
            Text(f.description, Ds.Sub, t13, Modifier.padding(top = 4.dp))
        }
        BackField("그레인") {
            val a = shot.grainAmount
            Text(
                when {
                    a == null -> "기록 없음"
                    a <= 0f -> "없음"
                    else -> "양 ${Math.round(a * 100)}%  ·  크기 ${"%.1f".format(shot.grainSize ?: 1f)}배"
                },
                if (a == null) Ds.Sub else Ds.Ink, t17,
            )
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
    val storage by produceState("계산 중", s.shots) {
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
                if (s.grainOn) {
                    SliderRow("그레인 양", "${Math.round(s.grainAmount * 100)}%", Math.round(s.grainAmount * 10), 0, 20) { c.setGrainLook(amount = it / 10f) }
                    SliderRow("그레인 크기", "%.1f배".format(s.grainSize), Math.round(s.grainSize * 2), 1, 8) { c.setGrainLook(size = it / 2f) }
                }
                SwitchRow("사진에 위치 기록", s.recordLocation) { c.setRecordLocation(it) }
                SwitchRow("볼륨 버튼 촬영", s.volumeShutter, last = true) { c.setVolumeShutter(it) }
            }
            SectionNote(
                "날짜 각인과 그레인은 저장되는 사진에도 똑같이 들어가요. 그레인 양은 필름마다 정해진 세기에 곱해지고, 크기는 알갱이 굵기를 정해요. 볼륨 버튼 촬영을 켜면 볼륨 키로 셔터를 눌러요. " +
                    "위치 기록을 켜면 찍은 곳이 사진 파일에 들어가서 기본 갤러리에서도 보이지만, 사진을 공유할 때 위치도 함께 가요." +
                    if (s.recordLocation && !c.locationGranted) " 지금은 위치 권한이 없어서 위치가 기록되지 않아요." else "",
                if (s.recordLocation && !c.locationGranted) Ds.Danger else Ds.Sub,
            )

            SectionHeader("저장 공간")
            Group {
                ValueRow("앱 보관함", storage)
                ActionRow("촬영 설정 초기화") { c.resetSettings() }
                ActionRow("모든 사진 지우기", Ds.Danger, last = true) { c.askWipe() }
            }
            SectionNote("사진은 기기 갤러리(Pictures/RetroCamera)에 저장돼요. 앱에서 지우면 갤러리에서도 지워져요.")
        }
    }
}
