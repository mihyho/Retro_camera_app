package com.example.filmcamera.data

/** 필름 프리셋. 색은 ARGB Long으로 두어 Compose/Android 없이도 테스트할 수 있게 한다. */
enum class Film(
    val key: String,
    val displayName: String,
    val label: String,
    val iso: Int,
    val accent: Long,
    val soft: Long,
    val ink: Long,
    val description: String,
    /** 명료도: 음수면 가우시안 흐림 효과를 그만큼 섞는다(뚜렷한 경계는 보호). -0.40 = 질감/면에 흐림 40% 혼합(약간) */
    val clarity: Float = -0.40f,
    /** 필름 그레인 세기(0~1 진폭 기준). 0.02 = 약하게. 0이면 없음 */
    val grain: Float = 0.020f,
) {
    TUNGSTEN("tungsten", "텅스텐", "TUNGSTEN 320", 320, 0xFF2F5D9E, 0xFFE3ECF8, 0xFF1D3A63, "푸른 그림자, 차갑게 가라앉은 하늘"),
    GOLD("gold", "골드", "GOLD 200", 200, 0xFF8A5A2B, 0xFFF3E9DE, 0xFF55361A, "노랗게 익은 햇빛, 따뜻한 피부톤"),
    FOREST("forest", "포레스트", "FOREST 400", 400, 0xFF3F7A3A, 0xFFE3F0E1, 0xFF244A21, "짙어진 초록, 바랜 하이라이트");

    /** assets/luts 아래의 .cube 파일 이름 */
    val lutAsset: String get() = "luts/$key.cube"

    companion object {
        fun fromKey(key: String): Film = entries.first { it.key == key }
    }
}

enum class Mode { FILM, SINGLE }

/** file은 앱 전용 저장소 기준 상대 경로, uri는 갤러리(MediaStore)로 내보낸 뒤의 주소(내보내기 전에는 null) */
data class Shot(val film: Film, val file: String, val takenAt: Long, val uri: String? = null)

/** 필름 모드에서 진행 중인 롤. 이게 있으면 필름 잠금이 걸린다. */
data class Roll(val film: Film, val total: Int, val shots: List<Shot>, val start: Long) {
    val remaining: Int get() = total - shots.size
}

/** 다 찍어서 현상된 롤. exported=false면 아직 갤러리로 내보내기가 끝나지 않은 것(앱 시작 시 재시도). */
data class DevelopedRoll(
    val film: Film,
    val total: Int,
    val shots: List<Shot>,
    val start: Long,
    val end: Long,
    val exported: Boolean,
)

data class AppState(
    val mode: Mode = Mode.FILM,
    val roll: Roll? = null,
    val singleFilm: Film = Film.GOLD,
    val singleShots: List<Shot> = emptyList(),
    val devRolls: List<DevelopedRoll> = emptyList(),
    val grid: Boolean = false,
    val stamp: Boolean = true,
    /** 볼륨 버튼으로 촬영(기본 켬) */
    val volumeShutter: Boolean = true,
)
