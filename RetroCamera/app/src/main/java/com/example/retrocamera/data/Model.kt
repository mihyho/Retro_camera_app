package com.example.retrocamera.data

/**
 * 필름 보정값(라이트룸 방식). 범위는 노출 -5~+5 EV, 나머지는 -100~+100이고 0이 기본값(보정 없음)이다.
 * 텍스처·명료도는 세부 처리(촬영/프리뷰 셰이더), 나머지는 색 변환이라 3D LUT 하나로 구워진다.
 */
data class FilmAdjust(
    val exposure: Float = 0f, val contrast: Float = 0f, val highlights: Float = 0f, val shadows: Float = 0f,
    val whites: Float = 0f, val blacks: Float = 0f,
    val temperature: Float = 0f, val tint: Float = 0f, val vibrance: Float = 0f, val saturation: Float = 0f,
    val texture: Float = 0f, val clarity: Float = 0f, val dehaze: Float = 0f,
) {
    val isNeutral get() = this == FilmAdjust()

    /** 명료도 보정(-100~+100)을 Film.clarity 단위로: +100 = 선명하게 0.8, -100 = 흐리게 0.8 */
    val clarityAmount get() = clarity / 100f * 0.8f

    /** 텍스처 보정(-100~+100)을 Film.texture 단위로 */
    val textureAmount get() = texture / 100f * 0.6f
}

/** 사용자가 만든 필름. base는 "기본 필름 불러오기"로 고른 출발점(없으면 보정 없는 원본에서 시작) */
data class CustomFilm(val key: String, val name: String, val accent: Long, val baseKey: String?, val adjust: FilmAdjust)

/**
 * 필름 프리셋. 색은 ARGB Long으로 두어 Compose/Android 없이도 테스트할 수 있게 한다.
 * 기본 제공 필름은 companion의 상수이고, 사용자가 만든 필름은 [setCustom]으로 목록 뒤에 붙는다.
 */
class Film(
    val key: String,
    val displayName: String,
    val label: String,
    /** 0이면 표시하지 않는다(사용자가 만든 필름) */
    val iso: Int,
    val accent: Long,
    val soft: Long,
    val ink: Long,
    val description: String,
    /** 명료도: 음수면 가우시안 흐림 효과를 그만큼 섞는다(뚜렷한 경계는 보호), 양수면 선명하게. -0.40 = 질감/면에 흐림 40% 혼합(약간) */
    val clarity: Float = -0.40f,
    /** 필름 그레인 세기(0~1 진폭 기준). 0.02 = 약하게. 0이면 없음 */
    val grain: Float = 0.020f,
    /** 텍스처: 미세한 질감 강조(양수)/완화(음수). 0이면 없음 */
    val texture: Float = 0f,
    /** 사용자가 만든 필름이면 그 정의(LUT를 만들 때 쓴다) */
    val custom: CustomFilm? = null,
) {
    /** assets/luts 아래의 .cube 파일 이름(기본 제공 필름만) */
    val lutAsset: String get() = "luts/$key.cube"

    // 같은 키면 같은 필름: 사용자가 필름을 더 만들면 목록이 다시 만들어지지만, 이미 고른 필름과 계속 같게 본다
    override fun equals(other: Any?) = other is Film && other.key == key
    override fun hashCode() = key.hashCode()
    override fun toString() = "Film($key)"

    companion object {
        val TUNGSTEN = Film("tungsten", "Tungsten", "TUNGSTEN 320", 320, 0xFF2F5D9E, 0xFFE3ECF8, 0xFF1D3A63, "푸른 그림자, 차갑게 가라앉은 하늘")
        val GOLD = Film("gold", "Seoul Gold", "SEOUL GOLD 200", 200, 0xFF8A5A2B, 0xFFF3E9DE, 0xFF55361A, "노랗게 익은 햇빛, 따뜻한 피부톤")
        val FOREST = Film("forest", "Forest", "FOREST 400", 400, 0xFF3F7A3A, 0xFFE3F0E1, 0xFF244A21, "짙어진 초록, 바랜 하이라이트")
        val EKTAR = Film("ektar100", "Ektar100", "Ektar100", 100, 0xFFB3261E, 0xFFF8E3E1, 0xFF6E1712, "진한 대비와 선명한 색")
        val KGOLD = Film("kgold200", "KGold200", "KGold200", 200, 0xFFB8862B, 0xFFF8EFD8, 0xFF6B4E14, "따뜻한 노랑, 부드러운 하이라이트")
        val PORTRA = Film("kportra800", "KPortra800", "KPortra800", 800, 0xFFC27A5A, 0xFFF8E8E0, 0xFF73402A, "들뜬 그림자, 부드러운 피부톤")
        val PROVIA = Film("provia100f", "Provia100F", "Provia100F", 100, 0xFF2F7DB5, 0xFFE0EEF8, 0xFF1B4766, "정확하고 깨끗한 색")
        val AUTUMN = Film("warmautumn", "WarmAutumn", "WarmAutumn", 200, 0xFFB5651D, 0xFFF8E6D2, 0xFF6B3A0E, "초록이 노랗게 물드는 가을 색")
        val CHROME = Film("classicchrome", "CLASSIC CHROME", "CLASSIC CHROME", 200, 0xFF4A6B6E, 0xFFE4EDEE, 0xFF26383A, "탁하게 가라앉은 채도, 청록빛 그림자")
        val LEICA_STD = Film("leicastd", "LeicaSTD", "LeicaSTD", 100, 0xFFB5322A, 0xFFF8E4E2, 0xFF6B1B16, "절제된 색, 살짝 따뜻한 초록")
        val LEICA_VIV = Film("leicaviv", "LeicaVIV", "LeicaVIV", 100, 0xFFC4452B, 0xFFF9E6DF, 0xFF70230F, "조금 더 짙고 선명한 초록")
        val FPROVIA = Film("fprovia100f", "FProvia100F", "FProvia100F", 100, 0xFF3B82B8, 0xFFE2EEF8, 0xFF1E4A6B, "깨끗하고 정확한 색")
        val KCHROME = Film("kchrome", "KCHROME", "KCHROME", 64, 0xFFC99A1E, 0xFFF9F0D4, 0xFF6E5409, "진한 노랑, 따뜻한 중간톤")
        val CINE = Film("cine500", "Cine500", "Cine500", 500, 0xFF2F8F99, 0xFFE0F0F1, 0xFF1B5359, "청록빛 그림자, 영화 같은 대비")
        val FADED = Film("faded100", "Faded100", "Faded100", 100, 0xFFA8957A, 0xFFF3EDE3, 0xFF5E5039, "바랜 90년대 사진, 들뜬 검정", grain = 0.030f)
        val NIGHT = Film("night1600", "Night1600", "Night1600", 1600, 0xFFC0392B, 0xFFF8E3E0, 0xFF6E1F17, "붉게 쏠린 밤 색, 거친 입자", grain = 0.045f)
        val builtIn: List<Film> = listOf(
            TUNGSTEN, GOLD, FOREST, EKTAR, KGOLD, PORTRA, PROVIA, AUTUMN, CHROME, LEICA_STD, LEICA_VIV, FPROVIA, KCHROME, CINE, FADED, NIGHT,
        )

        @Volatile private var all: List<Film> = builtIn

        /** 기본 제공 필름 + 사용자가 만든 필름(만든 순서) */
        val entries: List<Film> get() = all

        /** 사용자가 만든 필름 목록을 바꾼다. 앞에서 만든 필름을 base로 쓸 수 있으니 순서대로 푼다 */
        fun setCustom(defs: List<CustomFilm>) {
            val out = ArrayList<Film>(builtIn)
            defs.forEach { d -> out += fromCustom(d, out.firstOrNull { it.key == d.baseKey }) }
            all = out
        }

        fun fromKey(key: String): Film = all.first { it.key == key }

        private fun fromCustom(d: CustomFilm, base: Film?): Film {
            val tint = Integer.toUnsignedLong(d.accent.toInt()) and 0xFFFFFFFFL
            return Film(
                d.key, d.name, d.name, 0, tint, tint, tint, if (base != null) "${base.displayName}에서 만든 필름" else "직접 만든 필름",
                clarity = ((base?.clarity ?: 0f) + d.adjust.clarityAmount).coerceIn(-1f, 1f),
                grain = base?.grain ?: 0.020f,
                texture = ((base?.texture ?: 0f) + d.adjust.textureAmount).coerceIn(-1f, 1f),
                custom = d,
            )
        }
    }
}

enum class Mode { FILM, SINGLE }

/** file은 앱 전용 저장소 기준 상대 경로, uri는 갤러리(MediaStore)로 내보낸 뒤의 주소(내보내기 전에는 null) */
data class Shot(val film: Film, val file: String, val takenAt: Long, val uri: String? = null, val lat: Double? = null, val lon: Double? = null)

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
    /** 촬영본에 필름 그레인을 넣을지(기본 켬) */
    val grainOn: Boolean = true,
    /** 찍을 때 위치를 사진(EXIF)과 사진 뒷면에 기록할지(기본 켬, 위치 권한이 있어야 실제로 기록된다) */
    val recordLocation: Boolean = true,
    /** 사용자가 만든 필름(만든 순서) */
    val customFilms: List<CustomFilm> = emptyList(),
    /** 필름 메뉴에서 지운 필름의 키. 이미 찍은 사진이 가리키는 필름은 목록에서만 숨기고 정의는 남긴다 */
    val hiddenFilms: Set<String> = emptySet(),
    /** 필름 메뉴의 순서(지운 필름 포함 전체 키). 비어 있거나 모르는 키는 기본 순서를 따른다 */
    val filmOrder: List<String> = emptyList(),
)
