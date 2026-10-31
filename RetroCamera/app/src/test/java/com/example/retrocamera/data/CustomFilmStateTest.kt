package com.example.retrocamera.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFilmStateTest {

    @After fun cleanUp() = Film.setCustom(emptyList())

    private val def = CustomFilm(
        "custom_1", "나의 여름", 0xFFE8B84AL, null,
        FilmAdjust(
            exposure = .35f, contrast = 12f, highlights = -30f, temperature = 18f, saturation = 8f, texture = 20f, clarity = -15f, dehaze = 10f,
            mix = ColorMix(hue = List(8) { if (it == 3) 25f else 0f }, sat = List(8) { if (it == 5) -40f else 0f }, lum = List(8) { if (it == 0) 15f else 0f }),
            grade = ColorGrade(shadows = GradeZone(210f, 40f, 10f), mids = GradeZone(30f, 12f, 0f), highlights = GradeZone(45f, 25f, -5f), blending = 65f, balance = -20f),
        ),
    )

    @Test
    fun customFilmsHiddenFilmsAndShotsSurviveSaveAndLoad() {
        Film.setCustom(listOf(def))
        val custom = Film.fromKey("custom_1")
        val shot = Shot(custom, "content://x/1", 1000L, 37.5, 126.9)
        val state = AppState(
            film = custom, shots = listOf(shot),
            customFilms = listOf(def), hiddenFilms = setOf("gold", "forest", "custom_9"), purgedFilms = setOf("custom_9"),
            filmOrder = listOf("forest", "gold", "custom_1"),
        )
        val json = StateStore.toJson(state).toString()

        // 앱을 다시 켠 것처럼 등록을 비우고 불러온다: 만든 필름을 먼저 등록해야 사진의 필름 키가 풀린다
        Film.setCustom(emptyList())
        val back = StateStore.fromJson(JSONObject(json))
        assertEquals(listOf(def), back.customFilms)
        assertEquals(setOf("gold", "forest", "custom_9"), back.hiddenFilms)
        assertEquals(setOf("custom_9"), back.purgedFilms)
        assertEquals(listOf("forest", "gold", "custom_1"), back.filmOrder)
        assertEquals("custom_1", back.film.key)
        assertEquals("custom_1", back.shots.single().film.key)
        assertEquals(37.5, back.shots.single().lat!!, 1e-9)
        assertTrue(Film.entries.any { it.key == "custom_1" })
    }

    @Test
    fun oldStateWithoutFilmFieldsStillLoads() {
        val old = StateStore.toJson(AppState()).apply { remove("customFilms"); remove("hiddenFilms"); remove("recordLocation") }
        val back = StateStore.fromJson(JSONObject(old.toString()))
        assertTrue(back.customFilms.isEmpty())
        assertTrue(back.hiddenFilms.isEmpty())
        assertTrue(back.recordLocation)
    }

    @Test
    fun adjustValuesRoundTripExactly() {
        val a = def.adjust
        val back = StateStore.fromJson(JSONObject(StateStore.toJson(AppState(customFilms = listOf(def))).toString())).customFilms.single().adjust
        assertEquals(a, back)
        assertFalse(back.isNeutral)
    }

    @Test
    fun monochromeAndDreamingAreBuiltInAndOldUserCopiesMigrateWithoutDuplicates() {
        assertTrue(Film.builtIn.containsAll(listOf(Film.MONOCHROME, Film.DREAMING)))
        assertFalse(Film.MONOCHROME.isUserMade)
        // 옛 사용자 필름 키로 저장된 상태를 읽으면 기본 필름으로 이어지고, 같은 이름의 필름이 둘이 되지 않는다
        val old = CustomFilm("custom_1793413564303", "Monochrome", 0xFFF5F1E8L, null, FilmAdjust())
        Film.setCustom(listOf(old))
        val json = StateStore.toJson(AppState(film = Film.fromKey("custom_1793413564303"), customFilms = listOf(old)))
        Film.setCustom(emptyList())
        val back = StateStore.fromJson(JSONObject(json.toString()))
        assertTrue(back.customFilms.isEmpty())
        assertEquals("monochrome", back.film.key)
        assertEquals(1, Film.entries.count { it.displayName == "Monochrome" })
        assertEquals("monochrome", Film.fromKey("custom_1793413564303").key)
    }
}
