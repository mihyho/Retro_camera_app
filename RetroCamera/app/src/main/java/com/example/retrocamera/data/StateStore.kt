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
            put("mode", s.mode.name)
            put("roll", s.roll?.let { rollJson(it) } ?: JSONObject.NULL)
            put("singleFilm", s.singleFilm.key)
            put("singleShots", shotsJson(s.singleShots))
            put("devRolls", JSONArray().also { a ->
                s.devRolls.forEach { r ->
                    a.put(JSONObject().apply {
                        put("film", r.film.key); put("total", r.total); put("start", r.start)
                        put("end", r.end); put("exported", r.exported); put("shots", shotsJson(r.shots))
                    })
                }
            })
            put("grid", s.grid)
            put("stamp", s.stamp)
            put("volumeShutter", s.volumeShutter)
            put("grainOn", s.grainOn)
            put("recordLocation", s.recordLocation)
            put("customFilms", JSONArray().also { a -> s.customFilms.forEach { a.put(customJson(it)) } })
            put("hiddenFilms", JSONArray().also { a -> s.hiddenFilms.sorted().forEach { a.put(it) } })
            put("filmOrder", JSONArray().also { a -> s.filmOrder.forEach { a.put(it) } })
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
            })
        }

        private fun customFilms(o: JSONObject): List<CustomFilm> {
            val a = o.optJSONArray("customFilms") ?: return emptyList() // 옛 상태 파일에는 없다
            return (0 until a.length()).map { i ->
                val f = a.getJSONObject(i)
                val j = f.getJSONObject("adjust")
                fun v(k: String) = j.optDouble(k, 0.0).toFloat()
                CustomFilm(
                    f.getString("key"), f.getString("name"), f.getLong("accent"), if (f.isNull("base")) null else f.getString("base"),
                    FilmAdjust(
                        v("exposure"), v("contrast"), v("highlights"), v("shadows"), v("whites"), v("blacks"),
                        v("temperature"), v("tint"), v("vibrance"), v("saturation"), v("texture"), v("clarity"), v("dehaze"),
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

        private fun parse(o: JSONObject, customs: List<CustomFilm>): AppState = AppState(
            mode = Mode.valueOf(o.getString("mode")),
            roll = if (o.isNull("roll")) null else o.getJSONObject("roll").let { r ->
                Roll(Film.fromKey(r.getString("film")), r.getInt("total"), shots(r.getJSONArray("shots")), r.getLong("start"))
            },
            singleFilm = Film.fromKey(o.getString("singleFilm")),
            singleShots = shots(o.getJSONArray("singleShots")),
            devRolls = o.getJSONArray("devRolls").let { a ->
                (0 until a.length()).map { i ->
                    val r = a.getJSONObject(i)
                    DevelopedRoll(
                        Film.fromKey(r.getString("film")), r.getInt("total"), shots(r.getJSONArray("shots")),
                        r.getLong("start"), r.getLong("end"), r.getBoolean("exported"),
                    )
                }
            },
            grid = o.getBoolean("grid"),
            stamp = o.getBoolean("stamp"),
            volumeShutter = o.optBoolean("volumeShutter", true), // 옛 상태 파일에는 없으므로 기본 켬
            grainOn = o.optBoolean("grainOn", true),
            recordLocation = o.optBoolean("recordLocation", true),
            customFilms = customs,
            hiddenFilms = o.optJSONArray("hiddenFilms")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            filmOrder = o.optJSONArray("filmOrder")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )

        private fun rollJson(r: Roll) = JSONObject().apply {
            put("film", r.film.key); put("total", r.total); put("start", r.start); put("shots", shotsJson(r.shots))
        }

        private fun shotsJson(list: List<Shot>) = JSONArray().also { a ->
            list.forEach { s ->
                a.put(JSONObject().apply {
                    put("film", s.film.key); put("file", s.file); put("takenAt", s.takenAt)
                    put("uri", s.uri ?: JSONObject.NULL)
                    if (s.lat != null && s.lon != null) { put("lat", s.lat); put("lon", s.lon) } // 촬영 위치(없으면 생략). 사진 파일(EXIF)에는 넣지 않는다
                })
            }
        }

        private fun shots(a: JSONArray) = (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            Shot(Film.fromKey(s.getString("film")), s.getString("file"), s.getLong("takenAt"),
                if (s.isNull("uri")) null else s.getString("uri"),
                if (s.has("lat")) s.getDouble("lat") else null, if (s.has("lon")) s.getDouble("lon") else null)
        }
    }
}
