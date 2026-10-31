package com.example.retrocamera.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsTest {

    private fun shot(film: Film, i: Int) = Shot(film, "content://media/$i", 1000L + i)

    @Test
    fun recordedShotsAreNewestFirstAndDeleteRemovesOne() {
        var s = AppState()
        for (i in 1..3) s = AppOps.recordShot(s, shot(Film.GOLD, i))
        assertEquals(listOf(3, 2, 1), s.shots.map { (it.takenAt - 1000).toInt() })
        s = AppOps.deleteShot(s, shot(Film.GOLD, 2))
        assertEquals(listOf(3, 1), s.shots.map { (it.takenAt - 1000).toInt() })
        assertEquals("없는 사진을 지워도 그대로", s, AppOps.deleteShot(s, shot(Film.GOLD, 9)))
    }

    @Test
    fun selectFilmChangesCurrentFilm() {
        assertEquals(Film.FOREST, AppOps.selectFilm(AppState(), Film.FOREST).film)
    }

    @Test
    fun nextFilmWrapsAround() {
        val all = Film.entries
        assertEquals(all[1 % all.size], AppOps.nextFilm(AppState(film = all[0]), 1))
        assertEquals(all.last(), AppOps.nextFilm(AppState(film = all[0]), -1))
        assertEquals(all[0], AppOps.nextFilm(AppState(film = all.last()), 1))
    }

    @Test
    fun storeRoundTripSurvivesRestart() {
        val f = File.createTempFile("state", ".json")
        var s = AppState(film = Film.TUNGSTEN)
        for (i in 1..5) s = AppOps.recordShot(s, shot(Film.TUNGSTEN, i))
        StateStore(f).save(s)
        assertEquals(s, StateStore(f).load()) // 새 인스턴스 = 앱 재시작
        f.delete()
    }

    @Test
    fun corruptFileFallsBackToDefault() {
        val f = File.createTempFile("state", ".json")
        f.writeText("{ not json")
        assertEquals(AppState(), StateStore(f).load())
        f.delete()
    }

    @Test
    fun missingFileGivesDefault() {
        val f = File.createTempFile("state", ".json"); f.delete()
        assertEquals(AppState(), StateStore(f).load())
    }

    @Test
    fun volumeShutterAndGrainDefaultOnAndSurviveRestart() {
        assertTrue(AppState().volumeShutter); assertTrue(AppState().grainOn)
        val f = File.createTempFile("state", ".json")
        StateStore(f).save(AppOps.setGrain(AppOps.setVolumeShutter(AppState(), false), false))
        val back = StateStore(f).load()
        assertFalse(back.volumeShutter); assertFalse(back.grainOn)
        f.delete()
    }

    /** 필름 롤(24/36장) 모드가 있던 시절의 상태 파일: 단일 촬영과 내보낸 롤의 사진만 한 목록으로 합쳐 읽고, 롤 모드 전용 값은 버린다 */
    @Test
    fun legacyRollStateFileKeepsExportedPhotosOnly() {
        val old = """{"version":1,"mode":"FILM","singleFilm":"forest",
            "roll":{"film":"gold","total":24,"start":1,"shots":[{"film":"gold","file":"rolls/pending/a.jpg","takenAt":5,"uri":null}]},
            "singleShots":[{"film":"gold","file":"content://s/1","takenAt":10,"uri":"content://s/1"}],
            "devRolls":[{"film":"forest","total":24,"start":1,"end":2,"exported":true,"shots":[
                {"film":"forest","file":"rolls/pending/b.jpg","takenAt":20,"uri":"content://r/1"},
                {"film":"forest","file":"rolls/pending/c.jpg","takenAt":30,"uri":""}]}],
            "grid":false,"stamp":true}"""
        val s = StateStore.fromJson(JSONObject(old))
        assertEquals(Film.FOREST, s.film)
        assertEquals("내보낸 사진만, 최신순", listOf("content://r/1", "content://s/1"), s.shots.map { it.uri })
    }
}
