package com.example.filmcamera.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RollEngineTest {

    private fun shot(film: Film, i: Int) = Shot(film, "rolls/pending/$i.jpg", 1000L + i)

    @Test
    fun lockOnlyInFilmModeWithRoll() {
        var s = AppState(mode = Mode.FILM)
        assertFalse("롤 없으면 잠금 없음", RollEngine.isLocked(s))
        s = RollEngine.loadRoll(s, Film.GOLD, 24, 1)
        assertTrue("롤 장전 후 필름 모드는 잠금", RollEngine.isLocked(s))
        s = RollEngine.setMode(s, Mode.SINGLE)
        assertFalse("단일 모드에서는 롤이 있어도 자유", RollEngine.isLocked(s))
        s = RollEngine.setMode(s, Mode.FILM)
        assertTrue("다시 필름 모드로 오면 잠금 유지", RollEngine.isLocked(s))
    }

    @Test
    fun cannotLoadSecondRollWhileLocked() {
        val s = RollEngine.loadRoll(AppState(), Film.GOLD, 24, 1)
        try { RollEngine.loadRoll(s, Film.FOREST, 36, 2); fail() } catch (_: IllegalStateException) {}
    }

    @Test
    fun rejectsInvalidRollLength() {
        try { RollEngine.loadRoll(AppState(), Film.GOLD, 30, 1); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun countsDownAndFinishesAtLastShot() {
        var s = RollEngine.loadRoll(AppState(), Film.FOREST, 24, 1)
        for (i in 1..23) {
            val (n, finished) = RollEngine.recordFilmShot(s, shot(Film.FOREST, i), 10)
            s = n
            assertFalse(finished)
            assertEquals(24 - i, s.roll!!.remaining)
        }
        val (done, finished) = RollEngine.recordFilmShot(s, shot(Film.FOREST, 24), 99)
        assertTrue(finished)
        assertNull("롤 종료 시 잠금 해제", done.roll)
        assertFalse(RollEngine.isLocked(done))
        assertEquals(1, done.devRolls.size)
        assertEquals(24, done.devRolls[0].shots.size)
        assertFalse("내보내기 전", done.devRolls[0].exported)
        assertEquals(1, RollEngine.pendingExports(done).size)
    }

    @Test
    fun singleShotsDoNotTouchRoll() {
        var s = RollEngine.loadRoll(AppState(), Film.GOLD, 36, 1)
        s = RollEngine.setMode(s, Mode.SINGLE)
        s = RollEngine.selectSingleFilm(s, Film.TUNGSTEN)
        s = RollEngine.recordSingleShot(s, shot(Film.TUNGSTEN, 1))
        assertEquals(36, s.roll!!.remaining)
        assertEquals(Film.GOLD, s.roll!!.film)
        assertEquals(1, s.singleShots.size)
    }

    @Test
    fun markExportedFillsUris() {
        var s = RollEngine.loadRoll(AppState(), Film.GOLD, 24, 5)
        for (i in 1..24) s = RollEngine.recordFilmShot(s, shot(Film.GOLD, i), 50).first
        s = RollEngine.markExported(s, 5, (1..24).map { "content://media/$it" })
        assertTrue(s.devRolls[0].exported)
        assertEquals("content://media/7", s.devRolls[0].shots[6].uri)
        assertTrue(RollEngine.pendingExports(s).isEmpty())
    }

    @Test
    fun storeRoundTripSurvivesRestart() {
        val f = File.createTempFile("state", ".json")
        val store = StateStore(f)
        var s = RollEngine.loadRoll(AppState(grid = false), Film.TUNGSTEN, 36, 7)
        for (i in 1..5) s = RollEngine.recordFilmShot(s, shot(Film.TUNGSTEN, i), 8).first
        s = RollEngine.recordSingleShot(s, shot(Film.GOLD, 99).copy(uri = "content://x/1"))
        store.save(s)
        val reloaded = StateStore(f).load() // 새 인스턴스 = 앱 재시작
        assertEquals(s, reloaded)
        assertEquals(31, reloaded.roll!!.remaining)
        f.delete()
    }

    @Test
    fun storeSurvivesFinishedRollWithPendingExport() {
        val f = File.createTempFile("state", ".json")
        var s = RollEngine.loadRoll(AppState(), Film.FOREST, 24, 1)
        for (i in 1..24) s = RollEngine.recordFilmShot(s, shot(Film.FOREST, i), 2).first
        StateStore(f).save(s)
        val reloaded = StateStore(f).load()
        assertNull(reloaded.roll)
        assertEquals("재시작 후에도 내보내기 재시도 대상", 1, RollEngine.pendingExports(reloaded).size)
        f.delete()
    }

    @Test
    fun corruptFileFallsBackToDefault() {
        val f = File.createTempFile("state", ".json")
        f.writeText("{ not json")
        assertEquals(AppState(), StateStore(f).load())
        assertNotNull(f)
        f.delete()
    }

    @Test
    fun missingFileGivesDefault() {
        assertEquals(AppState(), StateStore(File("does/not/exist.json")).load())
    }

    @Test
    fun discardRollClearsRollAndUnlocks() {
        var s = RollEngine.loadRoll(AppState(), Film.GOLD, 24, 1)
        for (i in 1..5) s = RollEngine.recordFilmShot(s, shot(Film.GOLD, i), 9).first
        assertTrue(RollEngine.isLocked(s))
        val d = RollEngine.discardRoll(s)
        assertNull("롤이 해제된다", d.roll)
        assertFalse("필름 잠금이 풀린다", RollEngine.isLocked(d))
        // 버린 뒤에는 새 필름을 다시 장전할 수 있다
        val again = RollEngine.loadRoll(d, Film.FOREST, 36, 20)
        assertEquals(36, again.roll!!.remaining)
    }

    @Test
    fun discardRollKeepsEverythingElse() {
        var s = RollEngine.loadRoll(AppState(grid = true, stamp = false), Film.TUNGSTEN, 24, 1)
        s = RollEngine.recordSingleShot(s, shot(Film.GOLD, 90))
        s = RollEngine.setMode(s, Mode.SINGLE)
        val dev = RollEngine.loadRoll(AppState(), Film.FOREST, 24, 3)
        var done = dev
        for (i in 1..24) done = RollEngine.recordFilmShot(done, shot(Film.FOREST, 100 + i), 50).first
        s = s.copy(devRolls = done.devRolls)
        val d = RollEngine.discardRoll(s)
        assertEquals("단일 촬영 사진 유지", s.singleShots, d.singleShots)
        assertEquals("현상된 롤 유지", s.devRolls, d.devRolls)
        assertEquals(Mode.SINGLE, d.mode)
        assertEquals(s.grid, d.grid)
        assertEquals(s.stamp, d.stamp)
    }

    @Test
    fun discardWithoutRollIsRejected() {
        try { RollEngine.discardRoll(AppState()); fail() } catch (_: IllegalStateException) {}
    }

    @Test
    fun discardedRollSurvivesRestart() {
        // 버린 뒤 앱을 다시 열어도(상태 파일을 다시 읽어도) 롤이 되살아나지 않는다
        val f = File.createTempFile("state", ".json")
        var s = RollEngine.loadRoll(AppState(), Film.GOLD, 24, 1)
        for (i in 1..3) s = RollEngine.recordFilmShot(s, shot(Film.GOLD, i), 9).first
        StateStore(f).save(RollEngine.discardRoll(s))
        assertNull(StateStore(f).load().roll)
        f.delete()
    }

    @Test
    fun referencedPrivateFilesCoverRollAndUnexportedDevelopedRolls() {
        var s = RollEngine.loadRoll(AppState(), Film.GOLD, 24, 1)
        for (i in 1..24) s = RollEngine.recordFilmShot(s, shot(Film.GOLD, i), 9).first      // 현상됨(내보내기 전)
        s = RollEngine.loadRoll(s, Film.FOREST, 24, 2)
        for (i in 31..33) s = RollEngine.recordFilmShot(s, shot(Film.FOREST, i), 9).first   // 진행 중 3장
        val ref = RollEngine.referencedPrivateFiles(s)
        assertEquals(24 + 3, ref.size)
        assertTrue("rolls/pending/1.jpg" in ref && "rolls/pending/33.jpg" in ref)
        // 내보내기가 끝난 롤의 파일은 더 이상 필요 없다
        val exported = RollEngine.markExported(s, 1, (1..24).map { "content://x/$it" })
        assertEquals(3, RollEngine.referencedPrivateFiles(exported).size)
        // 버리면 진행 중이던 3장은 참조에서 빠진다(고아 파일로 정리 대상)
        assertEquals(24, RollEngine.referencedPrivateFiles(RollEngine.discardRoll(s)).size)
    }

    @Test
    fun volumeShutterDefaultsOnAndSurvivesRestart() {
        assertTrue("기본값은 켬", AppState().volumeShutter)
        val f = File.createTempFile("state", ".json")
        StateStore(f).save(RollEngine.setVolumeShutter(AppState(), false))
        assertFalse("끈 설정이 재시작 뒤에도 유지된다", StateStore(f).load().volumeShutter)
        f.delete()
    }

    @Test
    fun oldStateFileWithoutVolumeShutterReadsAsOn() {
        // 이 설정이 생기기 전에 저장된 상태 파일
        val old = """{"version":1,"mode":"FILM","roll":null,"singleFilm":"gold","singleShots":[],"devRolls":[],"grid":false,"stamp":true}"""
        val s = StateStore.fromJson(org.json.JSONObject(old))
        assertTrue(s.volumeShutter)
        assertEquals(Mode.FILM, s.mode)
    }

    @Test
    fun nextFilmIsNullWhileRollIsLoaded() {
        val s = RollEngine.loadRoll(AppState(), Film.GOLD, 24, 1)
        assertNull("롤이 있으면 필름을 바꿀 수 없다", RollEngine.nextFilm(s, null, 1))
        // 단일 모드에서는 롤이 있어도 자유롭다
        assertNotNull(RollEngine.nextFilm(RollEngine.setMode(s, Mode.SINGLE), null, 1))
    }

    @Test
    fun nextFilmWrapsAroundInSingleMode() {
        val all = Film.entries
        val first = AppState(mode = Mode.SINGLE, singleFilm = all.first())
        val last = AppState(mode = Mode.SINGLE, singleFilm = all.last())
        assertEquals(all.last(), RollEngine.nextFilm(first, null, -1))
        assertEquals(all.first(), RollEngine.nextFilm(last, null, 1))
        assertEquals(all[1], RollEngine.nextFilm(first, null, 1))
    }

    @Test
    fun nextFilmStartsFromTheEndsWhenNothingIsPicked() {
        val s = AppState(mode = Mode.FILM)
        assertEquals("첫 ▶ 는 첫 필름(건너뛰지 않는다)", Film.entries.first(), RollEngine.nextFilm(s, null, 1))
        assertEquals(Film.entries.last(), RollEngine.nextFilm(s, null, -1))
        assertEquals("고른 필름이 있으면 그 다음", Film.entries[1], RollEngine.nextFilm(s, Film.entries.first(), 1))
    }

    @Test
    fun galleryEntriesAreOldestFirstAndSkipTheRunningRoll() {
        var s = RollEngine.loadRoll(AppState(), Film.FOREST, 24, 1)
        for (i in 1..24) s = RollEngine.recordFilmShot(s, Shot(Film.FOREST, "rolls/pending/$i.jpg", 1000L + i), 50).first // 현상된 롤: 1001..1024
        val singles = listOf(Shot(Film.GOLD, "u2", 1500L, "u2"), Shot(Film.TUNGSTEN, "u1", 500L, "u1")) // 최신순으로 저장됨
        val e = galleryEntries(s.devRolls, singles)
        assertEquals(26, e.size)
        assertEquals("가장 오래된 것이 앞", 500L, e.first().shot.takenAt)
        assertEquals("가장 최근이 뒤", 1500L, e.last().shot.takenAt)
        assertEquals(e.map { it.shot.takenAt }, e.map { it.shot.takenAt }.sorted())
        // 아직 찍는 중인 롤의 사진은 들어가지 않는다
        val running = RollEngine.recordFilmShot(RollEngine.loadRoll(AppState(), Film.GOLD, 24, 2), Shot(Film.GOLD, "x.jpg", 9L), 9).first
        assertTrue(galleryEntries(running.devRolls, running.singleShots).isEmpty())
    }
}
