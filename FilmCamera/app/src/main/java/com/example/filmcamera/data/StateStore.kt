package com.example.filmcamera.data

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
        }

        fun fromJson(o: JSONObject): AppState = AppState(
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
        )

        private fun rollJson(r: Roll) = JSONObject().apply {
            put("film", r.film.key); put("total", r.total); put("start", r.start); put("shots", shotsJson(r.shots))
        }

        private fun shotsJson(list: List<Shot>) = JSONArray().also { a ->
            list.forEach { s ->
                a.put(JSONObject().apply {
                    put("film", s.film.key); put("file", s.file); put("takenAt", s.takenAt)
                    put("uri", s.uri ?: JSONObject.NULL)
                })
            }
        }

        private fun shots(a: JSONArray) = (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            Shot(Film.fromKey(s.getString("film")), s.getString("file"), s.getLong("takenAt"),
                if (s.isNull("uri")) null else s.getString("uri"))
        }
    }
}
