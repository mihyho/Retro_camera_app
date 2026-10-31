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
    val mix: ColorMix = ColorMix(), val grade: ColorGrade = ColorGrade(),
) {
    val isNeutral get() = this == FilmAdjust()

    /** 명료도 보정(-100~+100)을 -1..+1로. 필름의 국부 대비(Film.localContrast)가 된다 */
    val clarityAmount get() = clarity / 100f

    /** 텍스처 보정(-100~+100)을 -1..+1로. 필름의 텍스처(Film.texture)가 된다 */
    val textureAmount get() = texture / 100f
}

/**
 * 색상 혼합(HSL): 빨강·주황·노랑·초록·청록·파랑·자주·보라 8가지 색 계열마다 색조/채도/광도를 -100~+100으로 조절한다(0 = 그대로).
 * 각 리스트는 8개이고 [COLORS] 순서다.
 */
data class ColorMix(
    val hue: List<Float> = List(COUNT) { 0f },
    val sat: List<Float> = List(COUNT) { 0f },
    val lum: List<Float> = List(COUNT) { 0f },
) {
    companion object {
        const val COUNT = 8
        val NAMES = listOf("빨강", "주황", "노랑", "초록", "청록", "파랑", "자주", "보라")
        /** 각 색 계열의 중심 색조(도). 이 사이의 색은 이웃한 두 계열에 부드럽게 나뉜다 */
        val CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 275f, 320f)
    }
}

/** 색 보정의 한 영역(어두운/중간/밝은): 색조(0~360도), 채도(0~100), 광도(-100~+100) */
data class GradeZone(val hue: Float = 0f, val sat: Float = 0f, val lum: Float = 0f)

/**
 * 색 보정(3-way 컬러 휠): 밝기에 따라 어두운/중간/밝은 영역에 색을 입힌다.
 * 혼합(0~100, 기본 50)은 영역 사이가 얼마나 부드럽게 섞이는지, 균형(-100~+100)은 어두운 영역과 밝은 영역을 가르는 경계를 어디로 둘지다.
 */
data class ColorGrade(
    val shadows: GradeZone = GradeZone(), val mids: GradeZone = GradeZone(), val highlights: GradeZone = GradeZone(),
    val blending: Float = 50f, val balance: Float = 0f,
)

/** 사용자가 만든 필름. base는 편집의 출발점으로 쓸 다른 필름(지금 화면에서는 항상 null: 보정 없는 원본에서 시작) */
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
    /** 필름 그레인 세기(0~1 진폭 기준). 0.02 = 약하게. 0이면 없음 */
    val grain: Float = 0.020f,
    /** 텍스처: 미세한 질감 강조(양수)/완화(음수), -1..+1(FilmDetail). 기본 필름은 예전 "부드러운 흐림"을 같은 알고리즘의 -0.45로 옮겨 쓴다 */
    val texture: Float = -0.45f,
    /** 명료도(국부 대비): 중간톤의 윤곽·입체감 강조(양수)/완화(음수), -1..+1. 0이면 없음(FilmDetail) */
    val localContrast: Float = 0f,
    /** 사용자가 만든 필름이면 그 정의(LUT를 만들 때 쓴다) */
    val custom: CustomFilm? = null,
) {
    /** assets/luts 아래의 .cube 파일 이름(기본 제공 필름만) */
    val lutAsset: String get() = "luts/$key.cube"

    /** 사용자가 만든 필름(기본 필름으로 넣은 것은 제외): 보관함에서 지울 수 있다 */
    val isUserMade: Boolean get() = custom != null && this !in builtIn

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

        // 필름 만들기로 만들어 기본 필름으로 넣은 필름. assets에 .cube 파일이 없고, 보정값(FilmAdjust)에서 LUT를 구워 쓴다
        val MONOCHROME = fromCustom(
            CustomFilm(
                "monochrome", "Monochrome", 0xFFF5F1E8, null,
                FilmAdjust(
                    exposure = -0.86f, contrast = 19f, highlights = -24f, shadows = -54f, whites = 41f, blacks = -20f,
                    vibrance = -100f, saturation = -100f, texture = 23f, clarity = 28f, dehaze = 11f,
                ),
            ),
            null, "흑백, 짙은 그림자와 또렷한 질감",
        )
        val DREAMING = fromCustom(
            CustomFilm(
                "dreaming", "Dreaming", 0xFFD971B8, null,
                FilmAdjust(
                    exposure = 0.75f, contrast = -9f, highlights = -27f, shadows = 14f, whites = 17f, blacks = -37f,
                    temperature = 11f, tint = 6f, vibrance = 12f, saturation = 8f, texture = 9f, clarity = -66f, dehaze = -5f,
                    mix = ColorMix(
                        hue = listOf(0f, 0f, 0f, 0f, -14f, -10f, 0f, 0f),
                        sat = listOf(0f, 0f, 0f, 0f, 10f, 20f, 0f, 0f),
                        lum = listOf(0f, 0f, 0f, 0f, 9f, 14f, 0f, 0f),
                    ),
                    grade = ColorGrade(
                        shadows = GradeZone(175.13477f, 10.953357f, 0f),
                        highlights = GradeZone(7.468963f, 15.195896f, 0f),
                    ),
                ),
            ),
            null, "부드럽고 몽환적인 색, 은은한 청록과 살구빛",
        )

        val builtIn: List<Film> = listOf(
            TUNGSTEN, GOLD, FOREST, EKTAR, KGOLD, PORTRA, PROVIA, AUTUMN, CHROME, LEICA_STD, LEICA_VIV, FPROVIA, KCHROME, CINE, FADED, NIGHT,
            MONOCHROME, DREAMING,
        )

        /** 예전에 사용자 필름으로 만들었다가 기본 필름이 된 것: 옛 키 → 새 키. 옛 키로 저장된 사진·롤은 새 필름으로 이어진다 */
        val MIGRATED: Map<String, String> = mapOf("custom_1793413564303" to "monochrome", "custom_1793413677878" to "dreaming")

        @Volatile private var all: List<Film> = builtIn

        /** 기본 제공 필름 + 사용자가 만든 필름(만든 순서) */
        val entries: List<Film> get() = all

        /** 사용자가 만든 필름 목록을 바꾼다. 앞에서 만든 필름을 base로 쓸 수 있으니 순서대로 푼다 */
        fun setCustom(defs: List<CustomFilm>) {
            val out = ArrayList<Film>(builtIn)
            defs.forEach { d -> out += fromCustom(d, out.firstOrNull { it.key == d.baseKey }) }
            all = out
        }

        fun fromKey(key: String): Film = all.firstOrNull { it.key == key } ?: all.first { it.key == MIGRATED[key] }

        private fun fromCustom(d: CustomFilm, base: Film?, description: String? = null): Film {
            val tint = Integer.toUnsignedLong(d.accent.toInt()) and 0xFFFFFFFFL
            return Film(
                d.key, d.name, d.name, 0, tint, tint, tint, description ?: if (base != null) "${base.displayName}에서 만든 필름" else "직접 만든 필름",
                grain = base?.grain ?: 0.020f,
                texture = ((base?.texture ?: 0f) + d.adjust.textureAmount).coerceIn(-1f, 1f),
                localContrast = ((base?.localContrast ?: 0f) + d.adjust.clarityAmount).coerceIn(-1f, 1f),
                custom = d,
            )
        }
    }
}

enum class Mode { FILM, SINGLE }

/** file은 앱 전용 저장소 기준 상대 경로, uri는 갤러리(MediaStore)로 내보낸 뒤의 주소(내보내기 전에는 null) */
data class Shot(val film: Film, val file: String, val takenAt: Long, val uri: String? = null, val lat: Double? = null, val lon: Double? = null,
    /** 찍을 때의 그레인 양(필름 고유 세기에 곱한 배율, 0이면 그레인 없음)과 알갱이 크기 배율. 옛 사진은 null */
    val grainAmount: Float? = null, val grainSize: Float? = null)

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
    /** 그레인 양(필름 고유 값에 곱하는 배율, 0..2) / 알갱이 크기 배율(0.5..4) / 거칠기(0..1) */
    val grainAmount: Float = 1f,
    val grainSize: Float = 1f,
    /** 찍을 때 위치를 사진(EXIF)과 사진 뒷면에 기록할지(기본 켬, 위치 권한이 있어야 실제로 기록된다) */
    val recordLocation: Boolean = true,
    /** 사용자가 만든 필름(만든 순서) */
    val customFilms: List<CustomFilm> = emptyList(),
    /** 필름 메뉴에서 보관한 필름의 키(보관함에 들어간다). 이미 찍은 사진이 가리키는 필름은 목록에서만 숨기고 정의는 남긴다 */
    val hiddenFilms: Set<String> = emptySet(),
    /** 필름 보관함에서 완전히 지운 내 필름의 키. 그 필름으로 찍은 사진이 가리키므로 정의는 남기고 어디에도 보이지 않게만 한다 */
    val purgedFilms: Set<String> = emptySet(),
    /** 필름 메뉴의 순서(지운 필름 포함 전체 키). 비어 있거나 모르는 키는 기본 순서를 따른다 */
    val filmOrder: List<String> = emptyList(),
)
