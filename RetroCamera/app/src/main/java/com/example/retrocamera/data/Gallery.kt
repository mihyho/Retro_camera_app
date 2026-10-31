package com.example.retrocamera.data

/** 사진 보관함의 한 묶음. 사진은 최신순 */
data class LibraryGroup(val title: String, val meta: String, val shots: List<Shot>)

private fun Shot.sameAs(o: Shot) = uri == o.uri && takenAt == o.takenAt

fun libraryGroups(s: AppState): List<LibraryGroup> = buildList {
    if (s.shots.isNotEmpty()) add(LibraryGroup("사진", "${s.shots.size}장", s.shots))
}

/** shot이 들어 있는 묶음과 그 안의 위치. 없으면 null */
fun findInGroups(groups: List<LibraryGroup>, shot: Shot): Pair<LibraryGroup, Int>? {
    groups.forEach { g -> val i = g.shots.indexOfFirst { it.sameAs(shot) }; if (i >= 0) return g to i }
    return null
}
