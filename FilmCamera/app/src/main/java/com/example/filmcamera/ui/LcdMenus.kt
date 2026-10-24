package com.example.filmcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filmcamera.data.GalleryEntry
import com.example.filmcamera.data.Roll

/** 십자키/SET 입력 */
enum class Key { UP, DOWN, LEFT, RIGHT, SET }

private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
private val hintStyle = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 11.sp, lineHeight = 15.sp)
private val DangerRed = Color(0xFFE0624D)
private val ValueOrange = Color(0xFFFFB066)

/** 디카 메뉴의 한 줄. onKey: -1 = 왼쪽 키, 0 = SET 또는 터치, +1 = 오른쪽 키 */
data class MenuItem(
    val label: String,
    val value: String? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    /** 켜고 끄는 항목: ◀▶ 키로도 바뀐다(그 밖의 항목은 SET에서만 동작) */
    val toggle: Boolean = false,
    val onKey: (Int) -> Unit,
)

/** 디카 메뉴 공통 틀: 위 제목줄, 가운데 내용, 아래 도움말줄 */
@Composable
fun LcdScaffold(title: String, hint: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(PFLight.Bg)) {
        Row(Modifier.fillMaxWidth().background(PFLight.Surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = PFLight.Text, style = mono.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            Text("닫기", color = PFLight.Neutral700, style = mono.copy(fontSize = 11.sp),
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose).padding(4.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        Text(hint, color = PFLight.Neutral700, style = hintStyle, modifier = Modifier.fillMaxWidth().background(PFLight.Surface).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun MenuRow(item: MenuItem, selected: Boolean, onTap: () -> Unit) {
    val fg = when {
        !item.enabled -> PFLight.Neutral400
        item.danger -> DangerRed
        else -> PFLight.Text
    }
    Row(
        Modifier.fillMaxWidth().background(if (selected) LcdBlue else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, color = fg, style = mono.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        item.value?.let { Text(it, color = if (item.enabled) ValueOrange else PFLight.Neutral400, style = mono.copy(fontSize = 13.sp)) }
    }
}

/** 설정 메뉴 */
@Composable
fun SettingsMenu(items: List<MenuItem>, selected: Int, onSelect: (Int) -> Unit, note: String, onClose: () -> Unit) {
    LcdScaffold("설정  Set up", "▲▼ 이동 · ◀▶ 변경 · SET 선택 · MENU 닫기", onClose) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            items.forEachIndexed { i, item -> MenuRow(item, i == selected) { onSelect(i); if (item.enabled) item.onKey(0) } }
            Text(note, color = PFLight.Neutral700, style = hintStyle, modifier = Modifier.padding(12.dp))
        }
    }
}

/** 필름 버리기 확인 창. choice: 0 = 취소(기본), 1 = 버리기 */
@Composable
fun DiscardConfirm(roll: Roll, choice: Int, onChoose: (Int) -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .65f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PFLight.Bg)
                .border(1.dp, PFLight.Neutral400, RoundedCornerShape(6.dp))
                .clickable(remember { MutableInteractionSource() }, null) { /* 카드 안 탭이 닫힘으로 새지 않게 */ }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("필름을 버릴까요?", color = PFLight.Text, style = mono.copy(fontSize = 15.sp))
            Text(
                if (roll.shots.isEmpty()) "아직 찍은 사진이 없어요. ${roll.film.displayName} 필름을 빼내고 새로 고를 수 있어요."
                else "${roll.film.displayName} 롤에 찍은 ${roll.shots.size}장의 사진이 모두 삭제되고 되돌릴 수 없어요. 필름 잠금이 풀려요.",
                color = PFLight.Neutral700, style = hintStyle.copy(fontSize = 12.sp, lineHeight = 18.sp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((i, text) in listOf("취소", "버리기").withIndex()) {
                    val on = choice == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                            .background(if (on) (if (i == 1) DangerRed else LcdBlue) else PFLight.Surface)
                            .clickable(remember { MutableInteractionSource() }, null) { onChoose(i); if (i == 0) onCancel() else onConfirm() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(text, color = PFLight.Text, style = mono.copy(fontSize = 14.sp)) }
                }
            }
            Text("◀▶ 고르기 · SET 확인", color = PFLight.Neutral700, style = hintStyle)
        }
    }
}

// ───────────────────────── 재생(디카 스타일) ─────────────────────────

const val GALLERY_COLS = 3

/** 사진 한 장을 LCD 가득 보여 준다. ◀▶(또는 옆으로 밀기)로 넘기고, SET/▼(또는 탭)으로 목록으로 */
@Composable
fun PlaybackView(entries: List<GalleryEntry>, index: Int, pendingCount: Int, onStep: (Int) -> Unit, onIndex: () -> Unit) {
    val i = index.coerceIn(0, entries.lastIndex)
    val e = entries[i]
    val context = androidx.compose.ui.platform.LocalContext.current
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, e.shot.uri, e.shot.file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.filmcamera.ThumbLoader.load(context, e.shot, 1280) }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(entries.size) {
                var sum = 0f
                detectHorizontalDragGestures(
                    onDragStart = { sum = 0f },
                    onDragEnd = { if (sum > 80f) onStep(-1) else if (sum < -80f) onStep(1) },
                ) { _, d -> sum += d }
            }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onIndex),
    ) {
        bmp?.let {
            androidx.compose.foundation.Image(
                it.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Text("${i + 1}/${entries.size}", color = LcdOrange, style = mono.copy(fontSize = 12.sp), modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
        Text(e.title, color = LcdOrange, style = mono.copy(fontSize = 12.sp), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(e.meta, color = Color(0xFFFFB066), style = mono.copy(fontSize = 11.sp))
            if (pendingCount > 0) Text("현상 대기 중인 사진 ${pendingCount}장", color = PFLight.Neutral700, style = hintStyle)
            Text("◀▶ 넘기기 · SET 목록 · 촬영 돌아가기", color = PFLight.Neutral700, style = hintStyle)
        }
    }
}

private sealed interface GItem {
    data class Pending(val roll: Roll) : GItem
    data class Row(val first: Int, val count: Int) : GItem // entries[first until first+count]
    data object Empty : GItem
}

/** 3열 목록(고른 사진은 주황 테두리). 진행 중인 롤은 사진 없이 칸 수만 보여 준다 */
@Composable
fun GalleryMenu(
    pendingRoll: Roll?,
    entries: List<GalleryEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val items = remember(pendingRoll, entries.size) {
        buildList<GItem> {
            if (pendingRoll != null) add(GItem.Pending(pendingRoll))
            var i = 0
            while (i < entries.size) { val c = minOf(GALLERY_COLS, entries.size - i); add(GItem.Row(i, c)); i += c }
            if (pendingRoll == null && entries.isEmpty()) add(GItem.Empty)
        }
    }
    val listState = rememberLazyListState()
    // 고른 사진이 있는 줄이 항상 보이게 스크롤한다
    LaunchedEffect(selected, items) {
        val idx = items.indexOfFirst { it is GItem.Row && selected in it.first until it.first + it.count }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LcdScaffold("재생  Index", "▲▼◀▶ 고르기 · SET 크게 보기 · 촬영 돌아가기", onClose) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items.size) { idx ->
                when (val it = items[idx]) {
                    is GItem.Pending -> PendingCard(it.roll)
                    is GItem.Row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (k in 0 until GALLERY_COLS) {
                            val flat = it.first + k
                            if (k < it.count) {
                                val on = flat == selected
                                Thumb(
                                    entries[flat].shot, 240,
                                    Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(2.dp))
                                        .then(if (on) Modifier.border(2.5.dp, LcdOrange, RoundedCornerShape(2.dp)) else Modifier)
                                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(flat); onOpen(flat) },
                                )
                            } else Box(Modifier.weight(1f))
                        }
                    }
                    GItem.Empty -> Text("NO IMAGE\n아직 찍은 사진이 없어요. 필름을 넣고 찍어 보세요.", color = PFLight.Neutral700, style = hintStyle.copy(fontSize = 13.sp), modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
