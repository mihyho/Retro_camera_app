package com.example.filmcamera.data

import com.example.filmcamera.formatStamp

/** 재생에서 볼 수 있는 한 장 */
data class GalleryEntry(val shot: Shot, val title: String, val meta: String)

/**
 * 재생 순서: 찍은 시각 순(오래된 것 -> 최근). 현상된 롤의 사진과 단일 촬영 사진만 들어간다.
 * 진행 중인 롤의 사진은 롤이 끝날 때까지 여기 없다(필름 모드 규칙).
 */
fun galleryEntries(devRolls: List<DevelopedRoll>, singles: List<Shot>): List<GalleryEntry> = buildList {
    devRolls.forEach { r ->
        r.shots.forEachIndexed { i, sh -> add(GalleryEntry(sh, r.film.displayName, "${i + 1}번째 프레임 / ${r.total} · ${formatStamp(sh.takenAt)}")) }
    }
    singles.forEach { add(GalleryEntry(it, it.film.displayName, "단일 촬영 · ${formatStamp(it.takenAt)}")) }
}.sortedBy { it.shot.takenAt }
