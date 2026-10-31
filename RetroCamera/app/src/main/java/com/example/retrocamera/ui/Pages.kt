package com.example.retrocamera.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    Column(Modifier.fillMaxSize().background(Ds.Bg)) {
        NavBar("사진", left = "카메라", leftChevron = true, onLeft = onClose)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(items) { item ->
                when (item) {
                    is LibItem.Header -> SectionHeader(item.text)
                    is LibItem.Pending -> PendingCard(item.roll)
                    is LibItem.Photos -> Row(Modifier.fillMaxWidth().padding(bottom = 1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        item.shots.forEach { shot ->
                            Thumb(shot, 240, Modifier.weight(1f).aspectRatio(1f).tap { c.openViewer(shot) })
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
    val go = rememberUpdatedState { step: Int -> group.shots.getOrNull(index + step)?.let { c.panel = Panel.Viewer(it, v.back) } }
    Column(Modifier.fillMaxSize().background(Ds.Bg2)) {
        NavBar(group.title, left = "사진", leftChevron = true, onLeft = { c.closeViewer(v) }, sub = dateTime(shot.takenAt))
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(shot) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx < -80f) go.value(1) else if (dx > 80f) go.value(-1) }) { _, d -> dx += d }
            },
            contentAlignment = Alignment.Center,
        ) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
        Hairline()
        Row(Modifier.fillMaxWidth().height(44.dp).background(Ds.Bg), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).tap { shareShots(c, context, listOf(shot)) }, contentAlignment = Alignment.Center) { Icon(Ico.EXPORT, 24.dp, Ds.AccentInk) }
            Text("${index + 1} / ${group.shots.size}", Ds.Ink, t15.copy(textAlign = TextAlign.Center), Modifier.weight(1f), softWrap = false)
            Box(Modifier.size(44.dp).tap { c.askDeletePhoto(shot) }, contentAlignment = Alignment.Center) { Icon(Ico.TRASH, 24.dp, Ds.Danger) }
        }
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
                SwitchRow("볼륨 버튼 촬영", s.volumeShutter, last = true) { c.setVolumeShutter(it) }
            }
            SectionNote("날짜 각인과 그레인은 저장되는 사진에도 똑같이 들어가요. 볼륨 버튼 촬영을 켜면 볼륨 키로 셔터를 눌러요.")

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
