package com.example.retrocamera.data

/** 롤 잠금 규칙. 상태를 직접 바꾸지 않고 새 AppState를 돌려주는 순수 함수라 JVM에서 테스트할 수 있다. */
object RollEngine {

    val ROLL_LENGTHS = listOf(24, 36)

    /** 필름 모드에서 롤이 장전돼 있으면 필름을 바꿀 수 없다. 단일 모드는 항상 자유. */
    fun isLocked(s: AppState): Boolean = s.mode == Mode.FILM && s.roll != null

    fun setMode(s: AppState, mode: Mode) = s.copy(mode = mode)

    fun loadRoll(s: AppState, film: Film, total: Int, now: Long): AppState {
        check(s.roll == null) { "이미 장전된 롤이 있습니다" }
        require(total in ROLL_LENGTHS) { "롤 길이는 24 또는 36장" }
        return s.copy(roll = Roll(film, total, emptyList(), now))
    }

    /** 단일 모드의 필름 선택. 진행 중인 롤에는 영향이 없다. */
    fun selectSingleFilm(s: AppState, film: Film) = s.copy(singleFilm = film)

    /**
     * 필름 모드 촬영 기록. 마지막 장이면 롤을 현상 목록으로 옮기고 잠금을 푼다.
     * @return 새 상태와 "이번 장으로 롤이 끝났는지"
     */
    fun recordFilmShot(s: AppState, shot: Shot, now: Long): Pair<AppState, Boolean> {
        val roll = checkNotNull(s.roll) { "장전된 롤이 없습니다" }
        val shots = roll.shots + shot
        if (shots.size < roll.total) return s.copy(roll = roll.copy(shots = shots)) to false
        val developed = DevelopedRoll(roll.film, roll.total, shots, roll.start, now, exported = false)
        return s.copy(roll = null, devRolls = listOf(developed) + s.devRolls) to true
    }

    fun recordSingleShot(s: AppState, shot: Shot) = s.copy(singleShots = listOf(shot) + s.singleShots)

    /** 갤러리 내보내기가 끝난 롤의 사진 uri를 채우고 exported 표시 */
    fun markExported(s: AppState, rollStart: Long, uris: List<String>): AppState = s.copy(
        devRolls = s.devRolls.map { r ->
            if (r.start != rollStart) r
            else r.copy(shots = r.shots.mapIndexed { i, sh -> sh.copy(uri = uris[i]) }, exported = true)
        },
    )

    /**
     * 진행 중인 롤을 버린다(포기하고 초기화): 롤을 해제해 필름 잠금을 풀고, 이미 현상된 롤/단일 촬영/설정은 그대로 둔다.
     * 사진 파일 삭제는 호출하는 쪽이 한다 - 상태를 먼저 지워야 삭제 도중 앱이 죽어도 롤이 되살아나지 않는다.
     */
    fun discardRoll(s: AppState): AppState {
        check(s.roll != null) { "버릴 롤이 없습니다" }
        return s.copy(roll = null)
    }

    /**
     * 재생에서 사진 한 장을 지운다(단일 촬영 또는 현상된 롤의 한 장). 롤의 마지막 장을 지우면 롤 기록도 함께 없앤다.
     * 갤러리로 내보내기가 안 끝난 롤은 내보내기가 장 수 기준으로 uri를 채우므로 건드리지 않는다(그대로 반환).
     */
    fun deleteShot(s: AppState, shot: Shot): AppState {
        fun Shot.same() = file == shot.file && takenAt == shot.takenAt
        if (s.singleShots.any { it.same() }) return s.copy(singleShots = s.singleShots.filterNot { it.same() })
        val rolls = s.devRolls.map { r ->
            if (!r.exported || r.shots.none { it.same() }) r else r.copy(shots = r.shots.filterNot { it.same() })
        }.filter { it.shots.isNotEmpty() || !it.exported }
        return s.copy(devRolls = rolls)
    }

    /** 상태가 아직 필요로 하는 앱 전용 폴더의 사진 파일(상대 경로): 진행 중인 롤 + 갤러리로 내보내기가 안 끝난 현상 롤 */
    fun referencedPrivateFiles(s: AppState): Set<String> =
        (s.roll?.shots.orEmpty() + s.devRolls.filter { !it.exported }.flatMap { it.shots }).map { it.file }.toSet()

    /**
     * 십자키 좌/우로 고를 다음 필름. 롤이 장전돼 있으면 null(잠김).
     * 필름 모드에서 아직 고른 필름이 없으면 첫 필름(▶) 또는 마지막 필름(◀)부터 시작한다.
     */
    fun nextFilm(s: AppState, pending: Film?, step: Int): Film? {
        if (isLocked(s)) return null
        val all = Film.entries
        val cur = if (s.mode == Mode.SINGLE) s.singleFilm else pending
        if (cur == null) return if (step > 0) all.first() else all.last()
        return all[(all.indexOf(cur) + step + all.size) % all.size]
    }

    fun pendingExports(s: AppState): List<DevelopedRoll> = s.devRolls.filter { !it.exported }

    fun setGrid(s: AppState, v: Boolean) = s.copy(grid = v)
    fun setStamp(s: AppState, v: Boolean) = s.copy(stamp = v)
    fun setVolumeShutter(s: AppState, v: Boolean) = s.copy(volumeShutter = v)
    fun setGrain(s: AppState, v: Boolean) = s.copy(grainOn = v)
    fun setGrainLook(s: AppState, amount: Float = s.grainAmount, size: Float = s.grainSize) =
        s.copy(grainAmount = amount.coerceIn(0f, 2f), grainSize = size.coerceIn(0.5f, 4f))
    fun setRecordLocation(s: AppState, v: Boolean) = s.copy(recordLocation = v)
}
