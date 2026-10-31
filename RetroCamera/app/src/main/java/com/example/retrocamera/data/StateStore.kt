package com.example.retrocamera.data

import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppState를 JSON 파일 하나에 저장한다. 매 촬영마다 동기적으로 save()한다.
 * 임시 파일에 쓰고 fsync한 뒤 rename하므로, 저장 도중 앱이 죽어도 이전 상태나 새 상태 중 하나는 온전히 남는다.
 */
class StateStore(private val file: File) {

    fun load(): AppState {
        if (!file.exists()) return AppState()
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            // 손상된 파일: 사진은 건드리지 않고 상태만 초기화 (정책: 데이터 문제 시 초기화 허용)
            AppState()
        }
    }

    fun save(state: AppState) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(toJson(state).toString().toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "상태 저장 실패" }
        }
    }

    companion object {
        private const val VERSION = 1

        fun toJson(s: AppState): JSONObject = JSONObject().apply {
            put("version", VERSION)
            put("film", s.film.key)
            put("shots", shotsJson(s.shots))
            put("grid", s.grid)
            put("stamp", s.stamp)
            put("volumeShutter", s.volumeShutter)
            put("grainOn", s.grainOn)
            put("grainAmount", s.grainAmount.toDouble())
            put("grainSize", s.grainSize.toDouble())
            put("recordLocation", s.recordLocation)
            put("customFilms", JSONArray().also { a -> s.customFilms.forEach { a.put(customJson(it)) } })
            put("hiddenFilms", JSONArray().also { a -> s.hiddenFilms.sorted().forEach { a.put(it) } })
            put("filmOrder", JSONArray().also { a -> s.filmOrder.forEach { a.put(it) } })
            put("purgedFilms", JSONArray().also { a -> s.purgedFilms.sorted().forEach { a.put(it) } })
        }

        private fun customJson(f: CustomFilm) = JSONObject().apply {
            put("key", f.key); put("name", f.name); put("accent", f.accent); put("base", f.baseKey ?: JSONObject.NULL)
            val a = f.adjust
            put("adjust", JSONObject().apply {
                put("exposure", a.exposure.toDouble()); put("contrast", a.contrast.toDouble()); put("highlights", a.highlights.toDouble())
                put("shadows", a.shadows.toDouble()); put("whites", a.whites.toDouble()); put("blacks", a.blacks.toDouble())
                put("temperature", a.temperature.toDouble()); put("tint", a.tint.toDouble()); put("vibrance", a.vibrance.toDouble())
                put("saturation", a.saturation.toDouble()); put("texture", a.texture.toDouble()); put("clarity", a.clarity.toDouble())
                put("dehaze", a.dehaze.toDouble())
                put("mix", JSONObject().apply {
                    put("hue", floats(a.mix.hue)); put("sat", floats(a.mix.sat)); put("lum", floats(a.mix.lum))
                })
                put("grade", JSONObject().apply {
                    put("shadows", zoneJson(a.grade.shadows)); put("mids", zoneJson(a.grade.mids)); put("highlights", zoneJson(a.grade.highlights))
                    put("blending", a.grade.blending.toDouble()); put("balance", a.grade.balance.toDouble())
                })
            })
        }

        private fun floats(l: List<Float>) = JSONArray().also { a -> l.forEach { a.put(it.toDouble()) } }

        private fun zoneJson(z: GradeZone) = JSONObject().apply { put("hue", z.hue.toDouble()); put("sat", z.sat.toDouble()); put("lum", z.lum.toDouble()) }

        private fun floatList(a: JSONArray?, n: Int): List<Float> = List(n) { i -> a?.optDouble(i, 0.0)?.toFloat() ?: 0f }

        private fun zone(o: JSONObject?) = if (o == null) GradeZone() else GradeZone(o.optDouble("hue", 0.0).toFloat(), o.optDouble("sat", 0.0).toFloat(), o.optDouble("lum", 0.0).toFloat())

        private fun customFilms(o: JSONObject): List<CustomFilm> {
            val a = o.optJSONArray("customFilms") ?: return emptyList() // 옛 상태 파일에는 없다
            // 기본 필름이 된 옛 사용자 필름(Film.MIGRATED)은 이제 기본 필름이라 따로 읽지 않는다(중복 방지)
            return (0 until a.length()).filter { a.getJSONObject(it).getString("key") !in Film.MIGRATED }.map { i ->
                val f = a.getJSONObject(i)
                val j = f.getJSONObject("adjust")
                fun v(k: String) = j.optDouble(k, 0.0).toFloat()
                CustomFilm(
                    f.getString("key"), f.getString("name"), f.getLong("accent"), if (f.isNull("base")) null else f.getString("base"),
                    FilmAdjust(
                        v("exposure"), v("contrast"), v("highlights"), v("shadows"), v("whites"), v("blacks"),
                        v("temperature"), v("tint"), v("vibrance"), v("saturation"), v("texture"), v("clarity"), v("dehaze"),
                        // 옛 상태 파일에는 색상 혼합/색 보정이 없다
                        mix = j.optJSONObject("mix")?.let { m ->
                            ColorMix(floatList(m.optJSONArray("hue"), ColorMix.COUNT), floatList(m.optJSONArray("sat"), ColorMix.COUNT), floatList(m.optJSONArray("lum"), ColorMix.COUNT))
                        } ?: ColorMix(),
                        grade = j.optJSONObject("grade")?.let { g ->
                            ColorGrade(zone(g.optJSONObject("shadows")), zone(g.optJSONObject("mids")), zone(g.optJSONObject("highlights")),
                                g.optDouble("blending", 50.0).toFloat(), g.optDouble("balance", 0.0).toFloat())
                        } ?: ColorGrade(),
                    ),
                )
            }
        }

        fun fromJson(o: JSONObject): AppState {
            // 사용자가 만든 필름을 먼저 등록해야 아래에서 필름 키를 풀 수 있다
            val customs = customFilms(o)
            Film.setCustom(customs)
            return parse(o, customs)
        }

        /**
         * 필름 롤(24/36장) 모드가 있던 옛 상태 파일도 읽는다: 단일 촬영과 갤러리로 내보낸 롤의 사진을 한 목록으로 합치고,
         * 롤 모드의 진행 중인 롤과 아직 내보내지 못한 사진(앱 전용 폴더에만 있던 것)은 가져오지 않는다.
         */
        private fun parse(o: JSONObject, customs: List<CustomFilm>): AppState = AppState(
            film = Film.fromKey(if (o.has("film")) o.getString("film") else o.getString("singleFilm")),
            shots = if (o.has("shots")) shots(o.getJSONArray("shots")) else legacyShots(o),
            grid = o.getBoolean("grid"),
            stamp = o.getBoolean("stamp"),
            volumeShutter = o.optBoolean("volumeShutter", true), // 옛 상태 파일에는 없으므로 기본 켬
            grainOn = o.optBoolean("grainOn", true),
            grainAmount = o.optDouble("grainAmount", 1.0).toFloat().coerceIn(0f, 2f),
            grainSize = o.optDouble("grainSize", 1.0).toFloat().coerceIn(0.5f, 4f),
            recordLocation = o.optBoolean("recordLocation", true),
            customFilms = customs,
            hiddenFilms = o.optJSONArray("hiddenFilms")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            purgedFilms = o.optJSONArray("purgedFilms")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            filmOrder = o.optJSONArray("filmOrder")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )

        private fun shotsJson(list: List<Shot>) = JSONArray().also { a ->
            list.forEach { s ->
                a.put(JSONObject().apply {
                    put("film", s.film.key); put("uri", s.uri); put("takenAt", s.takenAt)
                    if (s.grainAmount != null) { put("grainAmount", s.grainAmount.toDouble()); put("grainSize", (s.grainSize ?: 1f).toDouble()) }
                    if (s.lat != null && s.lon != null) { put("lat", s.lat); put("lon", s.lon) } // 촬영 위치(없으면 생략). 사진 파일(EXIF)에는 넣지 않는다
                })
            }
        }

        private fun shots(a: JSONArray): List<Shot> = (0 until a.length()).mapNotNull { i ->
            val s = a.getJSONObject(i)
            val uri = if (s.isNull("uri")) "" else s.getString("uri")
            if (uri.isEmpty()) null // 갤러리에 없는 사진(옛 롤 모드의 앱 전용 파일)은 보여 줄 수 없다
            else Shot(Film.fromKey(s.getString("film")), uri, s.getLong("takenAt"),
                if (s.has("lat")) s.getDouble("lat") else null, if (s.has("lon")) s.getDouble("lon") else null,
                if (s.has("grainAmount")) s.getDouble("grainAmount").toFloat() else null, if (s.has("grainSize")) s.getDouble("grainSize").toFloat() else null)
        }

        private fun legacyShots(o: JSONObject): List<Shot> {
            val all = ArrayList<Shot>()
            o.optJSONArray("singleShots")?.let { all += shots(it) }
            o.optJSONArray("devRolls")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).optJSONArray("shots")?.let { all += shots(it) }
            }
            return all.sortedByDescending { it.takenAt }
        }
    }
}
