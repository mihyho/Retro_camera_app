package com.example.retrocamera.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.retrocamera.ThumbLoader
import com.example.retrocamera.data.DevelopedRoll
import com.example.retrocamera.data.Shot
import com.example.retrocamera.formatStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val devGrain by lazy { grainTile(180, 0x000000, .20f, 44L) }

/**
 * 롤 종료(현상) 화면 — 디자인의 "한 롤을 다 찍었어요". 상·하단 플레이트는 그대로 두고 가운데 프리뷰 창 안에만 뜬다.
 * 풀스크린 프로토타입을 창 크기(약 3:4)에 맞춰 줄였고, 밀착 인화지는 창 안에서 스크롤된다.
 */
@Composable
fun DevelopPanel(roll: DevelopedRoll, onNewFilm: () -> Unit, onSingle: () -> Unit, onOpen: (Shot, String, String) -> Unit) {
    Column(
        Modifier.fillMaxSize().drawBehind { drawRect(PFLight.Bg); drawTile(devGrain) }.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "현상 완료 · ${roll.film.displayName}${roll.total}장", color = Color(0xFF3D472B), softWrap = false,
            style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE1EECC)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            "한 롤을 다 찍었어요", color = PFLight.Text,
            style = TextStyle(fontFamily = PFFonts.Heading, fontSize = 27.sp, lineHeight = 30.sp, letterSpacing = (-.015).em),
        )
        Text(
            if (roll.exported) "이제 사진을 볼 수 있어요. 필름 잠금도 풀렸으니 새 필름을 골라 넣으세요."
            else "사진을 갤러리로 옮기는 중이에요…",
            color = PFLight.Neutral700,
            style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 13.sp, lineHeight = 19.sp),
        )
        // 밀착 인화지
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PFLight.Inverse).padding(8.dp)) {
            LazyVerticalGrid(
                GridCells.Fixed(6), Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(roll.shots) { i, shot ->
                    ContactFrame(i, shot) {
                        onOpen(shot, roll.film.displayName, "${i + 1}번째 프레임 / ${roll.total} · ${formatStamp(roll.end)}")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(PFLight.Accent)
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onNewFilm),
                contentAlignment = Alignment.Center,
            ) {
                Text("새 필름 고르기", color = PFLight.Bg,
                    style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            }
            Box(
                Modifier.fillMaxWidth().height(42.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onSingle),
                contentAlignment = Alignment.Center,
            ) {
                Text("단일 촬영으로 계속 찍기", color = PFLight.Accent,
                    style = TextStyle(fontFamily = PFFonts.Figtree, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

/** 프레임 하나. 프레임마다 55ms 간격으로 밝았다가(과노출) 제 밝기로, 작았다가 제 크기로 나타난다. */
@Composable
private fun ContactFrame(index: Int, shot: Shot, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<android.graphics.Bitmap?>(null, shot.uri, shot.file) {
        value = withContext(Dispatchers.IO) { ThumbLoader.load(context, shot, 160) }
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 17) * 55L) // 화면 밖에서 늦게 들어온 프레임이 오래 기다리지 않게 상한
        appear.animateTo(1f, tween(600))
    }
    Column(
        Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth().aspectRatio(3f / 4f)
                .graphicsLayer {
                    alpha = appear.value
                    val s = .85f + .15f * appear.value
                    scaleX = s; scaleY = s
                }
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0E0E0E)),
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            // brightness(3) 흉내: 흰 막이 걷히며 제 밝기가 드러난다
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - appear.value) * .7f }.background(Color.White))
        }
        Text(
            "%02d".format(index + 1), color = PF.Accent400, softWrap = false,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold),
        )
    }
}
