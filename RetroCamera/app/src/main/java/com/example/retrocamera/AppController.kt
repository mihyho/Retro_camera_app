package com.example.retrocamera

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.retrocamera.data.AppState
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.Mode
import com.example.retrocamera.data.RollEngine
import com.example.retrocamera.data.Shot
import com.example.retrocamera.data.StateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class ToastMsg(val id: Int, val text: String)

/** 가운데 카메라 화면(프리뷰 창) 안에 뜨는 패널. 상단/하단 플레이트는 그대로다. */
sealed interface Panel {
    data class Develop(val rollStart: Long) : Panel
    data object Gallery : Panel
    data object Settings : Panel
    /** 사진 크게 보기. 닫으면 back(열기 전 패널)으로 돌아간다. */
    data class Viewer(val shot: com.example.retrocamera.data.Shot, val title: String, val meta: String, val back: Panel?) : Panel
}

/** 날짜 각인 형식: '26 10 07 */
fun formatStamp(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "'%02d %02d %02d".format(c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** 앱 상태(롤/모드/설정)를 들고 있고, 바뀔 때마다 즉시 디스크에 저장한다. UI 전용 상태(토스트 등)는 저장하지 않는다. */
class AppController(private val appContext: Context) {

    private val store = StateStore(File(appContext.filesDir, "state.json"))

    var state by mutableStateOf(store.load())
        private set

    var toast by mutableStateOf<ToastMsg?>(null)
        private set
    private var toastSeq = 0

    var pendingFilm by mutableStateOf<Film?>(null)
    var pendingLength by mutableIntStateOf(24)
    var flashTrigger by mutableIntStateOf(0)
        private set
    var drawerOpen by mutableStateOf(false)
        private set
    var panel by mutableStateOf<Panel?>(null)

    /** 디카 메뉴(필름/설정/재생)에서 십자키로 고른 줄. 메뉴를 새로 열 때 CameraScreen이 처음 위치로 되돌린다 */
    var menuIndex by mutableIntStateOf(0)
    fun moveMenu(delta: Int, count: Int) { if (count > 0) menuIndex = (menuIndex + delta).coerceIn(0, count - 1) }

    /** 필름 버리기 확인 창: null = 닫힘, 0 = 취소 선택(기본), 1 = 버리기 선택 */
    var discardAsk by mutableStateOf<Int?>(null)

    fun askDiscard() { deleteTarget = null; discardAsk = 0 }

    /** 재생에서 지우려는 사진: 있으면 discardAsk 창은 "사진 삭제" 확인이다 */
    var deleteTarget by mutableStateOf<Shot?>(null)

    fun askDeletePhoto(shot: Shot) { deleteTarget = shot; discardAsk = 0 }

    /** 사진 한 장 삭제: 상태를 먼저 바꾸고(앱이 죽어도 지워진 사진이 목록에 남지 않게) 실제 파일은 뒤에 지운다 */
    fun deletePhoto(count: Int) {
        val shot = deleteTarget ?: return
        discardAsk = null
        deleteTarget = null
        val next = RollEngine.deleteShot(state, shot)
        if (next == state) { showToast("아직 갤러리로 내보내는 중이라 지울 수 없어요"); return }
        if (!tryCommit(next)) return
        menuIndex = menuIndex.coerceAtMost(count - 2).coerceAtLeast(0)
        if (count <= 1) playSingle = false
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (!shot.uri.isNullOrEmpty()) appContext.contentResolver.delete(android.net.Uri.parse(shot.uri), null, null)
                else File(appContext.filesDir, shot.file).delete()
            }
        }
        showToast("사진을 삭제했어요")
    }

    /** 재생: true = 사진 한 장 보기, false = 3열 목록 */
    var playSingle by mutableStateOf(true)

    /** 카메라 확대 상태(실제 카메라가 알려 준 값). 확대를 못 하는 카메라면 최대 배율이 1 */
    var zoomRatio by mutableFloatStateOf(1f)
        private set
    var zoomMax by mutableFloatStateOf(1f)
        private set

    fun updateZoom(ratio: Float, max: Float) {
        zoomRatio = ratio
        zoomMax = max
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val exporting = HashSet<Long>()

    private val luts = HashMap<Film, CubeLut>()

    fun lutFor(film: Film): CubeLut =
        luts.getOrPut(film) { appContext.assets.open(film.lutAsset).use { CubeLut.parse(it) } }

    /** 프리뷰/촬영에 쓰는 필름. 필름 모드에서 롤이 없으면 첫 필름(디자인 기준)을 보여 준다. */
    val currentFilm: Film
        get() = if (state.mode == Mode.FILM) state.roll?.film ?: Film.entries.first() else state.singleFilm

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
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
    }

    /** 서랍에서 필름을 뽑아 끼운다. 단일 모드는 바로 교체, 필름 모드는 장전할 필름으로 고른다. */
    fun takeFilm(film: Film) {
        if (state.mode == Mode.SINGLE) commit(RollEngine.selectSingleFilm(state, film)) else pendingFilm = film
        drawerOpen = false
    }

    /** 십자키 좌우: 다음/이전 필름으로. 롤이 장전돼 있으면 잠금 안내만 한다 */
    fun cycleFilm(step: Int) {
        val next = RollEngine.nextFilm(state, pendingFilm, step)
        if (next == null) { lockedFilmTapped(); return }
        takeFilm(next)
        showToast(next.displayName)
    }

    /** 롤이 장전된 채 다른 필름을 누른 경우 */
    fun lockedFilmTapped() {
        val r = state.roll ?: return
        showToast("롤을 다 찍어야 바꿀 수 있어요 · 남은 ${r.remaining}장")
    }

    fun triggerFlash() {
        flashTrigger++
    }

    fun setMode(mode: Mode) {
        val s = state
        if (s.mode == mode) return
        commit(RollEngine.setMode(s, mode))
        showToast(
            when {
                mode == Mode.SINGLE -> "단일 촬영 · 필름 롤은 그대로 멈춰 있어요"
                s.roll != null -> "${s.roll.film.displayName} 롤로 돌아왔어요 · 남은 ${s.roll.remaining}장"
                else -> "필름 모드 · 필름을 넣어 주세요"
            },
        )
    }

    fun loadPendingRoll() {
        val film = pendingFilm ?: return
        commit(RollEngine.loadRoll(state, film, pendingLength, System.currentTimeMillis()))
        pendingFilm = null
        showToast("${film.displayName} ${state.roll!!.total}장 장전됨")
    }

    /** 사진 파일이 저장된 뒤에 호출한다(저장 성공 -> 카운트 감소 순서). */
    fun onPhotoSaved(saved: SavedPhoto, mode: Mode, film: Film, rollStart: Long? = null) {
        val now = System.currentTimeMillis()
        if (mode == Mode.FILM) {
            // 찍는 동안 롤을 버렸거나(버린 뒤 새 롤을 넣은 경우 포함) 롤이 바뀌었으면 이 사진은 새 롤에 섞이지 않게 지운다
            if (state.roll == null || (rollStart != null && state.roll?.start != rollStart)) {
                saved.file?.let { f -> scope.launch(Dispatchers.IO) { File(appContext.filesDir, f).delete() } }
                return
            }
            val (next, finished) = RollEngine.recordFilmShot(state, Shot(film, saved.file!!, now), now)
            if (!tryCommit(next)) {
                scope.launch(Dispatchers.IO) { File(appContext.filesDir, saved.file).delete() }
                return
            }
            if (finished) {
                val rollStart = next.devRolls.first().start
                exportPending() // 갤러리로 내보내기(백그라운드)
                scope.launch {
                    delay(650) // 마지막 셔터 직후 잠깐 뒤에 현상 화면으로
                    panel = Panel.Develop(rollStart)
                }
            }
        } else {
            if (!tryCommit(RollEngine.recordSingleShot(state, Shot(film, saved.uri!!, now, saved.uri)))) {
                scope.launch(Dispatchers.IO) { runCatching { appContext.contentResolver.delete(android.net.Uri.parse(saved.uri), null, null) } }
            }
        }
    }

    /**
     * 다 찍은 롤을 갤러리로 내보낸다. 앱 시작 시에도 호출되어, 내보내기 도중 앱이 죽었던 롤을 이어서 처리한다.
     * 전부 성공해야 exported=true로 기록하고 비공개 원본을 지운다.
     */
    fun exportPending() {
        for (roll in RollEngine.pendingExports(state)) {
            if (!exporting.add(roll.start)) continue
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { RollExporter.export(appContext, roll) } }
                result.onSuccess { uris ->
                    commit(RollEngine.markExported(state, roll.start, uris))
                    withContext(Dispatchers.IO) { RollExporter.deletePrivateFiles(appContext, roll) }
                }
                result.onFailure { showToast("사진을 갤러리로 옮기지 못했어요 · 다음에 다시 시도해요") }
                exporting.remove(roll.start)
            }
        }
    }

    /**
     * 필름 버리기: 진행 중인 롤을 포기하고 초기화한다(찍은 사진은 모두 삭제, 되돌릴 수 없음).
     * 롤 상태를 먼저 저장해서 지우고, 파일은 그 뒤에 지운다 - 삭제 도중 앱이 죽어도 롤이 되살아나지 않고,
     * 남은 파일은 다음 실행 때 cleanOrphanFiles()가 정리한다. 단일 촬영/현상된 롤/설정은 건드리지 않는다.
     */
    fun discardRoll() {
        discardAsk = null
        val roll = state.roll ?: return
        val files = roll.shots.map { it.file }
        val name = roll.film.displayName
        commit(RollEngine.discardRoll(state))
        pendingFilm = null
        panel = null
        scope.launch(Dispatchers.IO) { files.forEach { File(appContext.filesDir, it).delete() } }
        showToast("$name 필름을 버렸어요 · 새 필름을 골라 넣을 수 있어요")
    }

    /** 상태가 참조하지 않는 비공개 사진(버리는 도중 앱이 죽어 남은 파일 등)을 지운다. 앱 시작 시 호출. */
    fun cleanOrphanFiles() {
        val referenced = RollEngine.referencedPrivateFiles(state)
        scope.launch(Dispatchers.IO) {
            File(appContext.filesDir, "rolls/pending").listFiles()?.forEach {
                if ("rolls/pending/${it.name}" !in referenced) it.delete()
            }
        }
    }

    fun stampText(): String = formatStamp(System.currentTimeMillis())

    // ── 패널 (모두 가운데 프리뷰 창 안에서만 열린다) ──

    /** 이미 열려 있으면 닫는다(하단 버튼 토글) */
    fun togglePanel(target: Panel) {
        panel = if (panel == target) null else target
    }

    fun openViewer(shot: Shot, title: String, meta: String) {
        panel = Panel.Viewer(shot, title, meta, back = panel)
    }

    fun closeViewer(v: Panel.Viewer) {
        panel = v.back
    }

    fun setGrid(v: Boolean) = commit(RollEngine.setGrid(state, v))
    fun setStamp(v: Boolean) = commit(RollEngine.setStamp(state, v))
    fun setVolumeShutter(v: Boolean) = commit(RollEngine.setVolumeShutter(state, v))
    fun setGrain(v: Boolean) = commit(RollEngine.setGrain(state, v))

    // ── 디버그 전용(설정 화면의 "프로토타입 테스트") ──

    /** 남은 장수를 가짜 사진으로 채워 롤을 끝낸다(현상 흐름 테스트용) */
    fun debugFinishRoll() {
        val roll = state.roll ?: return
        scope.launch {
            repeat(roll.remaining) { i ->
                val saved = withContext(Dispatchers.IO) { DebugPhotos.make(appContext, roll.film, roll.shots.size + i) }
                onPhotoSaved(saved, Mode.FILM, roll.film)
            }
        }
        panel = null
    }

    /** 앱 데이터를 지운 것과 같은 상태로 되돌린다(롤 잠금 해제, 사진 목록 초기화) */
    fun debugWipe() {
        commit(AppState())
        File(appContext.filesDir, "rolls").deleteRecursively()
        pendingFilm = null
        panel = null
        showToast("데이터를 지웠어요 · 롤 잠금이 풀렸어요")
    }
}
