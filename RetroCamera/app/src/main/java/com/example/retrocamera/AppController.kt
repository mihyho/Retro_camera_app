package com.example.retrocamera

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.retrocamera.data.AppState
import com.example.retrocamera.data.CustomFilm
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.FilmAdjust
import com.example.retrocamera.data.AppOps
import com.example.retrocamera.data.Shot
import com.example.retrocamera.data.StateStore
import com.example.retrocamera.data.findInGroups
import com.example.retrocamera.data.libraryGroups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class ToastMsg(val id: Int, val text: String)

/** 카메라 화면 위에 열리는 페이지/모달 */
sealed interface Panel {
    /** 사진 보관함 페이지 */
    data object Gallery : Panel
    data object Settings : Panel
    /** 사진 보기. 닫으면 back(열기 전 패널)으로 돌아간다. */
    data class Viewer(val shot: Shot, val back: Panel?) : Panel
}

/** iOS 7 스타일 알림창. 오른쪽 버튼이 ok(파괴 동작)이다 */
data class AlertState(val title: String, val msg: String, val ok: String, val onOk: () -> Unit)

/** 날짜 각인 형식: 26. 10. 07. */
fun formatStamp(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d. %02d. %02d.".format(c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** 앱 상태(필름/사진/설정)를 들고 있고, 바뀔 때마다 즉시 디스크에 저장한다. UI 전용 상태(토스트 등)는 저장하지 않는다. */
class AppController(private val appContext: Context) {

    private val store = StateStore(File(appContext.filesDir, "state.json"))

    var state by mutableStateOf(store.load())
        private set

    var toast by mutableStateOf<ToastMsg?>(null)
        private set
    private var toastSeq = 0

    var flashTrigger by mutableIntStateOf(0)
        private set
    /** 필름 그리드(실시간 비교)가 열려 있는지 */
    var drawerOpen by mutableStateOf(false)
        private set
    var panel by mutableStateOf<Panel?>(null)

    var alert by mutableStateOf<AlertState?>(null)

    fun askDeletePhoto(shot: Shot) {
        alert = AlertState("사진을 삭제할까요?", "이 사진이 기기에서 삭제되고 되돌릴 수 없어요.", "삭제") { deleteFromViewer(shot) }
    }

    fun askWipe() {
        alert = AlertState("모두 지울까요?", "앱 보관함의 모든 사진이 삭제되고 되돌릴 수 없어요.", "지우기") { wipeAll() }
    }

    /** 사진 한 장 삭제: 상태를 먼저 바꾸고(앱이 죽어도 지워진 사진이 목록에 남지 않게) 실제 파일은 뒤에 지운다. true = 지웠다 */
    fun deletePhoto(shot: Shot): Boolean {
        alert = null
        if (!tryCommit(AppOps.deleteShot(state, shot))) return false
        deleteFiles(listOf(shot))
        showToast("사진을 삭제했어요")
        return true
    }

    fun askDeletePhotos(shots: List<Shot>, onDone: () -> Unit) {
        alert = AlertState("사진 ${shots.size}장을 삭제할까요?", "선택한 사진이 기기에서 삭제되고 되돌릴 수 없어요.", "삭제") { deletePhotos(shots); onDone() }
    }

    /** 여러 장 삭제: 상태를 한 번에 바꿔 저장한 뒤 파일을 지운다 */
    fun deletePhotos(shots: List<Shot>) {
        alert = null
        val next = shots.fold(state) { s, shot -> AppOps.deleteShot(s, shot) }
        if (!tryCommit(next)) return
        deleteFiles(shots)
        showToast("사진 ${shots.size}장을 삭제했어요")
    }

    private fun deleteFiles(shots: List<Shot>) {
        scope.launch(Dispatchers.IO) {
            shots.forEach { shot ->
                runCatching { appContext.contentResolver.delete(android.net.Uri.parse(shot.uri), null, null) }
            }
        }
    }

    /** 사진 보기에서 지운 뒤: 같은 묶음의 이웃 사진으로, 없으면 사진 보기를 연 곳으로 돌아간다 */
    private fun deleteFromViewer(shot: Shot) {
        val v = panel as? Panel.Viewer
        val (group, i) = findInGroups(libraryGroups(state), shot) ?: return deletePhoto(shot).let {}
        val neighbor = group.shots.getOrNull(i + 1) ?: group.shots.getOrNull(i - 1)
        if (deletePhoto(shot)) panel = if (neighbor != null) Panel.Viewer(neighbor, v?.back) else v?.back
    }

    /** 카메라 확대 상태(실제 카메라가 알려 준 값). 확대를 못 하는 카메라면 최대 배율이 1 */
    var zoomRatio by mutableFloatStateOf(1f)
        private set
    var zoomMax by mutableFloatStateOf(1f)
        private set

    // ── 전면/후면 카메라 ──
    /** true = 전면(셀카) 카메라. 앱을 켤 때마다 후면에서 시작한다 */
    var lensFront by mutableStateOf(false)
    /** MainActivity가 카메라를 다시 연결하는 함수 */
    var onFlipCamera: (() -> Unit)? = null
    fun flipCamera() { onFlipCamera?.invoke() }

    /** 카메라 권한 화면: INTRO(아직 안 물어봄/대기), DENIED(거부됨), GRANTED */
    enum class Perm { INTRO, DENIED, GRANTED }
    var perm by mutableStateOf(Perm.GRANTED)
    var onRequestPermission: (() -> Unit)? = null

    /** 카메라가 둘 이상인지(전환 버튼 사용 가능) */
    var flipAvailable by mutableStateOf(true)

    // ── 플래시: 후면은 토치, 전면은 촬영 직전 화면을 하얗게 ──
    var flashOn by mutableStateOf(false)
    /** 후면 카메라가 토치를 지원하는지. 전면은 항상 true(화면 플래시) */
    var flashAvailable by mutableStateOf(true)
    var onFlashChange: ((Boolean) -> Unit)? = null
    /** 전면 카메라 화면 플래시 중 */
    var screenFlash by mutableStateOf(false)
    fun toggleFlash() {
        if (!flashAvailable) { showToast(if (lensFront) "전면 카메라에서는 쓸 수 없어요" else "이 카메라는 플래시를 지원하지 않아요"); return }
        flashOn = !flashOn
        onFlashChange?.invoke(flashOn)
    }

    var onZoomReset: (() -> Unit)? = null
    /** GL 렌더러에 알린다: 필름 스트립 칩의 위치(칸마다 x, y, 크기, 투명도 px), 프리뷰를 그릴 곳(x, y, w, h px) */
    var onStrip: ((FloatArray?) -> Unit)? = null
    var onPreviewRect: ((IntArray?) -> Unit)? = null
    /** 스트립을 넘기는 중에 손가락에 가장 가까운 필름: 저장은 도착했을 때 한 번만 하고, 프리뷰 색감만 바로 바꾼다 */
    var onLiveFilm: ((Film) -> Unit)? = null

    // ── 노출 보정 ──
    /** 노출 조정 창이 열려 있는지(십자키가 노출을 조정한다) */
    var exposureOpen by mutableStateOf(false)
    /** 고른 노출 단계(카메라 단위). 0 = 보정 없음. 앱이 켜져 있는 동안만 기억한다 */
    var exposureIndex by mutableIntStateOf(0)
    /** 카메라가 지원하는 단계 범위와 한 단계의 EV 크기. 0..0 = 지원하지 않음 */
    var exposureRange by mutableStateOf(0..0)
        private set
    var exposureStep by mutableFloatStateOf(0f)
        private set
    /** MainActivity가 카메라에 적용하는 함수 */
    var onExposureChange: ((Int) -> Unit)? = null

    fun updateExposure(range: IntRange, step: Float) {
        exposureRange = range
        exposureStep = step
        exposureIndex = exposureIndex.coerceIn(range)
    }

    fun toggleExposure() {
        if (exposureOpen) { exposureOpen = false; return }
        if (panel != null || drawerOpen || alert != null) return // 촬영 화면에서만
        if (exposureRange.first == exposureRange.last) { showToast("이 카메라는 노출 조정을 지원하지 않아요"); return }
        exposureOpen = true
    }

    fun setExposure(index: Int) {
        val next = index.coerceIn(exposureRange)
        if (next == exposureIndex) return
        exposureIndex = next
        onExposureChange?.invoke(next)
    }

    fun moveExposure(delta: Int) {
        val next = (exposureIndex + delta).coerceIn(exposureRange)
        if (next == exposureIndex) return
        exposureIndex = next
        onExposureChange?.invoke(next)
    }

    fun updateZoom(ratio: Float, max: Float) {
        zoomRatio = ratio
        zoomMax = max
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val luts = HashMap<Film, CubeLut>()

    @Synchronized fun lutFor(film: Film): CubeLut = luts.getOrPut(film) {
        // 사용자가 만든 필름의 LUT는 보정값에서 바로 구워 낸다(파일로 저장하지 않는다)
        film.custom?.let { d -> FilmLook.bake(d.baseKey?.let { lutFor(Film.fromKey(it)) }, d.adjust) }
            ?: appContext.assets.open(film.lutAsset).use { CubeLut.parse(it) }
    }

    // ── 필름 메뉴: 보관 / 필름 보관함에서 불러오기·지우기 / 새 필름 만들기 ──

    /** 필름 메뉴(스트립·그리드·필름 넣기)에 보이는 필름. 보관한 필름은 빠진다 */
    val visibleFilms: List<Film> get() = orderedFilms().filter { it.key !in state.hiddenFilms }

    /** 보관한 필름까지 포함한 전체 필름을 저장된 순서대로. 순서에 없는 필름(새로 만든 것 등)은 기본 순서로 뒤에 붙는다 */
    private fun orderedFilms(): List<Film> {
        val order = state.filmOrder
        if (order.isEmpty()) return Film.entries
        return Film.entries.sortedBy { f -> order.indexOf(f.key).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    // ── 필름 메뉴(그리드)에서 고른 필름: 꾹 눌러 고르고, 보관하거나 순서를 바꾼다 ──
    var gridSelection by mutableStateOf<Set<String>>(emptySet())

    fun toggleGridSelect(film: Film) {
        gridSelection = if (film.key in gridSelection) gridSelection - film.key else gridSelection + film.key
    }

    fun clearGridSelection() { gridSelection = emptySet() }

    fun askArchiveFilm(film: Film) = askArchiveFilms(listOf(film))

    /** 필름을 필름 메뉴에서 빼서 보관함에 넣는다. 지우는 게 아니라 보관이라 보관함에서 언제든 다시 불러올 수 있다 */
    fun askArchiveFilms(films: List<Film>) {
        if (films.isEmpty()) return
        if (visibleFilms.size - films.size < 1) { showToast("마지막 필름은 보관할 수 없어요"); return }
        alert = AlertState(
            if (films.size == 1) "${films[0].displayName} 필름을 보관할까요?" else "필름 ${films.size}개를 보관할까요?",
            "필름 메뉴에서만 빠지고 이미 찍은 사진은 그대로예요. \"필름 보관함\"에서 다시 불러올 수 있어요.",
            "보관",
        ) { archiveFilms(films) }
    }

    fun archiveFilms(films: List<Film>) {
        alert = null
        val shown = visibleFilms
        val left = shown.filter { it !in films }
        if (left.isEmpty() || left.size == shown.size) return
        // 보관한 필름이 고른 필름이면 그 자리 가까이의 남은 필름으로
        val firstGone = shown.indexOfFirst { it in films }
        val neighbor = left[minOf(firstGone, left.lastIndex)]
        var next = state.copy(hiddenFilms = state.hiddenFilms + films.map { it.key })
        if (state.film in films) next = next.copy(film = neighbor)
        if (!tryCommit(next)) return
        gridSelection = emptySet()
        showToast(if (films.size == 1) "${films[0].displayName} 필름을 보관했어요" else "필름 ${films.size}개를 보관했어요")
    }

    /** 고른 필름 한 개를 보이는 순서에서 앞(-1)이나 뒤(+1)로 한 칸 옮긴다 */
    fun moveSelectedFilm(dir: Int) {
        val key = gridSelection.singleOrNull() ?: return
        val shown = visibleFilms
        val i = shown.indexOfFirst { it.key == key }
        val j = i + dir
        if (i < 0 || j !in shown.indices) return
        // 보관한 필름까지 포함한 전체 순서에서 이웃한 두 필름의 자리를 맞바꾼다
        val full = orderedFilms().map { it.key }.toMutableList()
        val a = full.indexOf(shown[i].key); val b = full.indexOf(shown[j].key)
        full[a] = shown[j].key; full[b] = shown[i].key
        tryCommit(state.copy(filmOrder = full))
    }

    /** 필름 보관함에 들어 있는 필름: 보관한 필름 중 완전히 지우지 않은 것 */
    val storedFilms: List<Film> get() = orderedFilms().filter { it.key in state.hiddenFilms && it.key !in state.purgedFilms }

    /** 보관함에서 필름을 꺼내 필름 메뉴에 다시 넣는다(원래 자리로). 이미 들어있으면 알려 준다 */
    fun unarchiveFilm(film: Film) {
        if (film.key !in state.hiddenFilms || film.key in state.purgedFilms) { showToast("필름이 이미 들어있습니다."); return }
        if (!tryCommit(state.copy(hiddenFilms = state.hiddenFilms - film.key))) return
        showToast("${film.displayName} 필름을 불러왔어요")
    }

    /** 내가 만든 필름만 보관함에서 완전히 지울 수 있다(기본 필름은 지울 수 없다) */
    fun askPurgeFilm(film: Film) {
        if (!film.isUserMade) { showToast("기본 필름은 지울 수 없어요"); return }
        alert = AlertState(
            "${film.displayName} 필름을 지울까요?",
            "보관함에서 완전히 사라져요. 이 필름으로 이미 찍은 사진은 그대로예요.",
            "지우기",
        ) { purgeFilm(film) }
    }

    fun purgeFilm(film: Film) {
        alert = null
        if (!film.isUserMade || film.key !in state.hiddenFilms) return
        if (!tryCommit(state.copy(purgedFilms = state.purgedFilms + film.key))) return
        showToast("${film.displayName} 필름을 지웠어요")
    }

    /** 새 필름 만들기 흐름의 단계(없으면 null) */
    enum class Maker { MENU, VAULT, SOURCE, CAPTURE, EDITOR }
    var maker by mutableStateOf<Maker?>(null)
    /** 편집할 사진(가로 w, 세로 h, ARGB) */
    class EditorSource(val px: IntArray, val w: Int, val h: Int)
    var editorSource by mutableStateOf<EditorSource?>(null)
    /** MainActivity가 기기에서 사진을 고르는 창을 여는 함수 */
    var onPickPhoto: (() -> Unit)? = null

    fun openFilmMenu() { alert = null; exposureOpen = false; drawerOpen = false; maker = Maker.MENU }
    fun closeMaker() { maker = null; editorSource = null }
    fun openEditor(src: EditorSource) { editorSource = src; maker = Maker.EDITOR }

    /** 만든 필름을 목록 끝에 넣고 바로 고른다 */
    fun addCustomFilm(name: String, accent: Long, adjust: FilmAdjust): Boolean {
        val def = CustomFilm("custom_${System.currentTimeMillis()}", name.trim(), accent, null, adjust)
        val defs = state.customFilms + def
        Film.setCustom(defs)
        if (!tryCommit(state.copy(customFilms = defs))) { Film.setCustom(state.customFilms); return false }
        val film = Film.fromKey(def.key)
        commit(AppOps.selectFilm(state, film))
        closeMaker()
        showToast("${def.name} 필름을 추가했어요")
        return true
    }

    /** 프리뷰/촬영에 쓰는 필름 */
    val currentFilm: Film get() = state.film

    private fun commit(next: AppState) {
        store.save(next) // 저장이 성공한 뒤에만 메모리 상태를 바꾼다
        state = next
    }

    /** 저장 공간 부족 등으로 상태를 못 쓰면 false (앱이 죽지 않고, 호출한 쪽이 방금 만든 사진을 지운다) */
    private fun tryCommit(next: AppState): Boolean = try {
        commit(next); true
    } catch (e: Exception) {
        showToast("저장 공간이 부족해 사진을 저장하지 못했어요"); false
    }

    fun showToast(text: String) {
        toast = ToastMsg(++toastSeq, text)
    }

    fun dismissToast(id: Int) {
        if (toast?.id == id) toast = null
    }

    fun openDrawer() {
        exposureOpen = false
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
    }

    /** 필름을 고른다 */
    fun takeFilm(film: Film) {
        commit(AppOps.selectFilm(state, film))
        drawerOpen = false
    }

    /** 십자키 좌우: 다음/이전 필름으로 */
    fun cycleFilm(step: Int) {
        val next = AppOps.nextFilm(state, step)
        takeFilm(next)
        showToast(next.displayName)
    }

    fun triggerFlash() {
        flashTrigger++
    }

    /** 사진이 갤러리에 저장된 뒤에 호출한다 */
    fun onPhotoSaved(uri: String, film: Film, lat: Double? = null, lon: Double? = null, grainAmount: Float? = null, grainSize: Float? = null) {
        val shot = Shot(film, uri, System.currentTimeMillis(), lat, lon, grainAmount, grainSize)
        if (!tryCommit(AppOps.recordShot(state, shot))) {
            scope.launch(Dispatchers.IO) { runCatching { appContext.contentResolver.delete(android.net.Uri.parse(uri), null, null) } }
        }
    }

    /** 필름 롤 모드가 있던 시절에 앱 전용 폴더에 남은 사진 파일을 지운다. 앱 시작 시 호출. */
    fun removeLegacyRollFiles() {
        scope.launch(Dispatchers.IO) { File(appContext.filesDir, "rolls").deleteRecursively() }
    }

    fun stampText(): String = formatStamp(System.currentTimeMillis())

    // ── 패널 (모두 가운데 프리뷰 창 안에서만 열린다) ──

    /** 이미 열려 있으면 닫는다(하단 버튼 토글) */
    fun togglePanel(target: Panel) {
        panel = if (panel == target) null else target
    }

    fun openViewer(shot: Shot) {
        panel = Panel.Viewer(shot, back = panel)
    }

    fun closeViewer(v: Panel.Viewer) {
        panel = v.back
    }

    fun setGrid(v: Boolean) = commit(AppOps.setGrid(state, v))
    fun setStamp(v: Boolean) = commit(AppOps.setStamp(state, v))
    fun setVolumeShutter(v: Boolean) = commit(AppOps.setVolumeShutter(state, v))
    fun setGrain(v: Boolean) = commit(AppOps.setGrain(state, v))
    fun setGrainLook(amount: Float = state.grainAmount, size: Float = state.grainSize) =
        commit(AppOps.setGrainLook(state, amount, size))

    /** 위치 권한을 갖고 있는지(MainActivity가 알려 준다) */
    var locationGranted by mutableStateOf(false)
    /** MainActivity가 위치 권한을 요청하는 함수(이미 영구 거부면 앱 설정을 연다) */
    var onRequestLocation: (() -> Unit)? = null

    /** 위치 기록을 켜면 권한이 없을 때 바로 권한을 묻는다 */
    fun setRecordLocation(v: Boolean) {
        commit(AppOps.setRecordLocation(state, v))
        if (v && !locationGranted) onRequestLocation?.invoke()
    }

    /** 촬영 설정(격자/각인/그레인/볼륨 셔터)과 노출을 처음 값으로 되돌린다. 사진은 그대로 */
    fun resetSettings() {
        val d = AppState()
        commit(state.copy(grid = d.grid, stamp = d.stamp, volumeShutter = d.volumeShutter, grainOn = d.grainOn, grainAmount = d.grainAmount, grainSize = d.grainSize, recordLocation = d.recordLocation))
        exposureIndex = 0
        onExposureChange?.invoke(0)
        onZoomReset?.invoke()
        showToast("촬영 설정을 초기화했어요")
    }

    /** 모든 사진을 지운다(설정/고른 필름은 유지). 상태를 먼저 저장해서 지우고 갤러리 항목은 그 뒤에 지운다 */
    fun wipeAll() {
        alert = null
        val shots = state.shots
        if (!tryCommit(state.copy(shots = emptyList()))) return
        panel = null
        deleteFiles(shots)
        showToast("모든 사진을 지웠어요")
    }

    /** 설정의 "앱 보관함": 사진 장수와 크기. 파일 크기를 읽어야 해서 IO 스레드에서 부른다 */
    fun storageSummary(): Pair<Int, Long> {
        val s = state
        val shots = s.shots
        var bytes = 0L
        shots.forEach { sh ->
            bytes += runCatching { appContext.contentResolver.openFileDescriptor(android.net.Uri.parse(sh.uri), "r")?.use { it.statSize } ?: 0L }.getOrDefault(0L)
        }
        return shots.size to bytes
    }
}
