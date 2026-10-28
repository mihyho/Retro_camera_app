package com.example.retrocamera.data

import com.example.retrocamera.formatStamp

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

/** 사진 보관함의 한 묶음(단일 촬영 또는 현상된 롤). 사진은 최신순 */
data class LibraryGroup(val title: String, val meta: String, val shots: List<Shot>)

private fun Shot.sameAs(o: Shot) = file == o.file && takenAt == o.takenAt

fun libraryGroups(s: AppState): List<LibraryGroup> = buildList {
    if (s.singleShots.isNotEmpty()) add(LibraryGroup("단일 촬영", "${s.singleShots.size}장", s.singleShots))
    s.devRolls.forEach { r ->
        add(LibraryGroup(r.film.displayName, "${formatStamp(r.end).drop(1).replace(' ', '.')} · ${r.shots.size}장", r.shots))
    }
}

/** shot이 들어 있는 묶음과 그 안의 위치. 없으면 null */
fun findInGroups(groups: List<LibraryGroup>, shot: Shot): Pair<LibraryGroup, Int>? {
    groups.forEach { g -> val i = g.shots.indexOfFirst { it.sameAs(shot) }; if (i >= 0) return g to i }
    return null
}
