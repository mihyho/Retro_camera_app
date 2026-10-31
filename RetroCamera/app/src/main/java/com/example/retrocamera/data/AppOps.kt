package com.example.retrocamera.data

/** 상태를 직접 바꾸지 않고 새 AppState를 돌려주는 순수 함수 모음이라 JVM에서 테스트할 수 있다. */
object AppOps {

    fun selectFilm(s: AppState, film: Film) = s.copy(film = film)

    fun recordShot(s: AppState, shot: Shot) = s.copy(shots = listOf(shot) + s.shots)

    /** 사진 한 장을 목록에서 뺀다(파일/갤러리 항목 삭제는 호출하는 쪽이 한다) */
    fun deleteShot(s: AppState, shot: Shot): AppState = s.copy(shots = s.shots.filterNot { it.uri == shot.uri && it.takenAt == shot.takenAt })

    /** 십자키 좌/우로 고를 다음 필름 */
    fun nextFilm(s: AppState, step: Int): Film {
        val all = Film.entries
        return all[(all.indexOf(s.film) + step + all.size) % all.size]
    }

    fun setGrid(s: AppState, v: Boolean) = s.copy(grid = v)
    fun setStamp(s: AppState, v: Boolean) = s.copy(stamp = v)
    fun setVolumeShutter(s: AppState, v: Boolean) = s.copy(volumeShutter = v)
    fun setGrain(s: AppState, v: Boolean) = s.copy(grainOn = v)
    fun setGrainLook(s: AppState, amount: Float = s.grainAmount, size: Float = s.grainSize) =
        s.copy(grainAmount = amount.coerceIn(0f, 2f), grainSize = size.coerceIn(0.5f, 4f))
    fun setRecordLocation(s: AppState, v: Boolean) = s.copy(recordLocation = v)
}
