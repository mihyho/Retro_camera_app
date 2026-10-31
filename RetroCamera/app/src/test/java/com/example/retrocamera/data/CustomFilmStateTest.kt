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
        FilmAdjust(exposure = .35f, contrast = 12f, highlights = -30f, temperature = 18f, saturation = 8f, texture = 20f, clarity = -15f, dehaze = 10f),
    )

    @Test
    fun customFilmsHiddenFilmsAndShotsSurviveSaveAndLoad() {
        Film.setCustom(listOf(def))
        val custom = Film.fromKey("custom_1")
        val shot = Shot(custom, "content://x/1", 1000L, "content://x/1", 37.5, 126.9)
        val state = AppState(
            mode = Mode.SINGLE, singleFilm = custom, singleShots = listOf(shot),
            customFilms = listOf(def), hiddenFilms = setOf("gold", "forest"),
        )
        val json = StateStore.toJson(state).toString()

        // 앱을 다시 켠 것처럼 등록을 비우고 불러온다: 만든 필름을 먼저 등록해야 사진의 필름 키가 풀린다
        Film.setCustom(emptyList())
        val back = StateStore.fromJson(JSONObject(json))
        assertEquals(listOf(def), back.customFilms)
        assertEquals(setOf("gold", "forest"), back.hiddenFilms)
        assertEquals("custom_1", back.singleFilm.key)
        assertEquals("custom_1", back.singleShots.single().film.key)
        assertEquals(37.5, back.singleShots.single().lat!!, 1e-9)
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
}
