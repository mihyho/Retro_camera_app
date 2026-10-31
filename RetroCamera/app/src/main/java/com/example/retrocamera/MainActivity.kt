package com.example.retrocamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.retrocamera.ui.CameraScreen
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var controller: AppController
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: LutRenderer
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null

    /** 핀치가 이어지는 동안 카메라가 줌 상태를 알려 주기 전에 다음 이벤트가 오므로, 목표 배율을 따로 들고 있는다 */
    private var zoomTarget = 1f

    private val worker = Executors.newSingleThreadExecutor()

    /** 촬영은 했지만 아직 저장/카운트 반영이 안 끝난 필름 모드 장수. 마지막 장 중복 촬영 방지용. */
    private var inFlightFilm = 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) { controller.perm = AppController.Perm.GRANTED; startCamera() } else controller.perm = AppController.Perm.DENIED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        controller = AppController(applicationContext)
        controller.exportPending() // 내보내기 도중 앱이 죽었던 롤이 있으면 이어서 처리
        controller.cleanOrphanFiles() // 필름을 버리는 도중 앱이 죽어 남은 사진 정리

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(3)
        renderer = LutRenderer(glView, controller.lutFor(controller.currentFilm), ContextCompat.getMainExecutor(this))
        controller.onFlipCamera = { controller.lensFront = !controller.lensFront; startCamera() }
        controller.onExposureChange = { camera?.cameraControl?.setExposureCompensationIndex(it) }
        controller.onFlashChange = { camera?.cameraControl?.enableTorch(it && !controller.lensFront) }
        controller.onRequestLocation = { requestLocation() }
        controller.onStrip = { renderer.setStrip(it) }
        controller.onPreviewRect = { renderer.setPreview(it) }
        controller.onLiveFilm = { f -> renderer.setLut(controller.lutFor(f)); renderer.setClarity(f.clarity) }
        controller.onZoomReset = { zoomTarget = 1f; camera?.cameraControl?.setZoomRatio(1f) }
        controller.onRequestPermission = {
            if (controller.perm == AppController.Perm.DENIED && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // "다시 묻지 않음"이면 시스템 창이 안 뜬다: 앱 설정으로 보낸다
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
            } else permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        renderer.onContextRecreated = { if (imageCapture != null) startCamera() }
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        setContent {
            // 필름(모드)이 바뀌면 프리뷰 LUT도 교체
            LaunchedEffect(controller.currentFilm) {
                renderer.setLut(controller.lutFor(controller.currentFilm))
                renderer.setClarity(controller.currentFilm.clarity)
            }
            // 모든 필름의 LUT를 미리 올린다: 필름 그리드와 하단 필름 스트립의 실시간 칩이 같이 쓴다(LUT 읽기는 백그라운드에서)
            LaunchedEffect(Unit) {
                val tiles = withContext(Dispatchers.Default) { Film.entries.map { LutRenderer.GridTile(controller.lutFor(it), it.clarity) } }
                renderer.setTiles(tiles)
            }
            LaunchedEffect(controller.drawerOpen) { renderer.setGridVisible(controller.drawerOpen) }
            CameraScreen(controller, glView, onShutter = ::takePhoto, onZoomBy = ::zoomBy, onZoomStep = ::zoomStep)
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            controller.perm = AppController.Perm.INTRO
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** 전체화면(몰입) 모드: 상태바·내비게이션 바를 숨긴다. 가장자리를 쓸면 잠깐 내려왔다가 저절로 다시 숨는다 */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() // 알림창을 내렸다 올리거나 다른 앱에서 돌아와도 다시 숨긴다
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        askLocationOnce()
        glView.onResume()
        // 앱 설정에서 권한을 허용하고 돌아온 경우
        if (controller.perm != AppController.Perm.GRANTED && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            controller.perm = AppController.Perm.GRANTED
            startCamera()
        }
    }

    override fun onPause() {
        glView.onPause()
        super.onPause()
    }

    /** 볼륨 버튼으로 촬영(설정에서 끌 수 있다). 켜져 있으면 시스템 볼륨 조절은 막는다 */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) &&
            controller.state.volumeShutter
        ) {
            if (event.repeatCount == 0) takePhoto() // 길게 눌러도 한 장만
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) &&
            controller.state.volumeShutter
        ) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            var selector = if (controller.lensFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!provider.hasCamera(selector)) { // 전면 카메라가 없는 기기
                controller.lensFront = false
                controller.showToast("전면 카메라를 찾을 수 없어요")
                selector = CameraSelector.DEFAULT_BACK_CAMERA
            }
            controller.flipAvailable = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider { request -> renderer.onSurfaceRequested(request) }
            }
            this.preview = preview
            // 4:3, 최대 약 12MP. 프리뷰 영역(3:4)과 같은 비율이라 보이는 그대로 찍힌다.
            val capture = ImageCapture.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                        )
                        .build(),
                )
                .build()
            imageCapture = capture
            applyDisplayRotation()
            provider.unbindAll()
            val cam = provider.bindToLifecycle(this, selector, preview, capture)
            camera = cam
            cam.cameraInfo.exposureState.let { es ->
                controller.updateExposure(es.exposureCompensationRange.lower..es.exposureCompensationRange.upper, es.exposureCompensationStep.toFloat())
            }
            cam.cameraControl.setExposureCompensationIndex(controller.exposureIndex) // 카메라를 다시 연결해도 고른 노출을 유지
            controller.flashAvailable = controller.lensFront || cam.cameraInfo.hasFlashUnit()
            if (!controller.flashAvailable) controller.flashOn = false
            cam.cameraControl.enableTorch(controller.flashOn && !controller.lensFront)
            cam.cameraInfo.zoomState.observe(this) { zs ->
                zoomTarget = zs.zoomRatio
                controller.updateZoom(zs.zoomRatio, zs.maxZoomRatio)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 핀치: 두 손가락 간격의 비(scale)만큼 확대/축소 */
    private fun zoomBy(scale: Float) {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value ?: return
        zoomTarget = ZoomMath.pinch(zoomTarget, scale, zs.minZoomRatio, zs.maxZoomRatio)
        cam.cameraControl.setZoomRatio(zoomTarget)
    }

    /** 배율 버튼: 1배 -> 2배 -> 3배 -> 5배 -> 1배 (카메라가 지원하는 범위까지) */
    private fun zoomStep() {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value ?: return
        zoomTarget = ZoomMath.nextStep(zs.zoomRatio, zs.minZoomRatio, zs.maxZoomRatio)
        cam.cameraControl.setZoomRatio(zoomTarget)
    }

    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            locationPrefs.edit().putBoolean("askedLocation", true).apply() // 답을 받은 뒤에만 "물어 봤다"로 친다(창이 답 없이 닫히면 다시 묻는다)
            controller.locationGranted = hasLocationPermission()
            if (!controller.locationGranted && controller.state.recordLocation) controller.showToast("위치 권한이 없어 위치를 기록하지 못해요")
        }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val locationPrefs get() = getSharedPreferences("retrocam", MODE_PRIVATE)

    /** 위치 권한 요청. 한 번 거부하고 "다시 묻지 않음"이 된 뒤에는 시스템 창이 안 뜨니 앱 설정으로 보낸다 */
    private fun requestLocation() {
        val asked = locationPrefs.getBoolean("askedLocation", false)
        if (asked &&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
        } else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    /** 위치 기록이 켜져 있으면 카메라 권한이 생긴 뒤 처음 한 번 위치 권한을 묻는다. 설정에서 켤 때는 [requestLocation]으로 다시 묻는다 */
    private fun askLocationOnce() {
        controller.locationGranted = hasLocationPermission()
        if (locationPrefs.getBoolean("askedLocation", false) || controller.locationGranted || !controller.state.recordLocation ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) return
        requestLocation()
    }

    /** 가장 최근에 알려진 위치(1시간 이내). 기록이 꺼져 있거나 권한이 없거나 위치를 모르면 null: 새로 측정하려고 셔터를 늦추지 않는다 */
    @android.annotation.SuppressLint("MissingPermission")
    private fun lastPlace(): Pair<Double, Double>? {
        if (!controller.state.recordLocation || !hasLocationPermission()) return null
        val lm = getSystemService(android.location.LocationManager::class.java) ?: return null
        val newest = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (newest == null || System.currentTimeMillis() - newest.time > 60 * 60 * 1000L) return null
        return newest.latitude to newest.longitude
    }

    private fun takePhoto() {
        // 패널(현상/사진/설정)이 떠 있으면 셔터는 카메라 화면으로 돌아오기만 한다
        if (controller.perm != AppController.Perm.GRANTED) return
        if (controller.panel != null || controller.drawerOpen || controller.alert != null || controller.loadOpen) {
            controller.panel = null; controller.closeDrawer(); controller.alert = null; controller.loadOpen = false; return
        }
        val capture = imageCapture ?: return
        val s = controller.state
        val mode = s.mode
        val film = controller.currentFilm
        val rollStart = s.roll?.start
        val grainOn = s.grainOn
        val stampText = if (s.stamp) formatStamp(System.currentTimeMillis()) else null
        val place = lastPlace() // 사진 뒷면에 보여 줄 촬영 위치(셔터를 누른 순간 기준)
        if (mode == Mode.FILM) {
            val roll = s.roll
            if (roll == null) { controller.openLoad(); return }
            if (roll.remaining - inFlightFilm <= 0) return // 마지막 장 처리 중
            inFlightFilm++
        }
        controller.triggerFlash()
        val lut = controller.lutFor(film)

        // 전면 카메라 플래시: 촬영 직전 화면을 잠깐 하얗게 해서 보조광으로 쓴다
        val screenFlash = controller.flashOn && controller.lensFront
        if (screenFlash) controller.screenFlash = true
        fun shoot() = capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes: ByteArray
                val rotation: Int
                try {
                    val buf = image.planes[0].buffer
                    bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    rotation = image.imageInfo.rotationDegrees
                } catch (e: Exception) {
                    if (mode == Mode.FILM) inFlightFilm-- // 카운터가 새면 마지막 장을 못 찍게 된다
                    controller.showToast("촬영 실패")
                    return
                } finally {
                    image.close()
                }
                worker.execute {
                    val result = runCatching { PhotoPipeline.process(applicationContext, bytes, rotation, lut, mode, film, grainOn, stampText, place?.first, place?.second) }
                    runOnUiThread {
                        if (mode == Mode.FILM) inFlightFilm--
                        result.onSuccess { controller.onPhotoSaved(it, mode, film, rollStart, place?.first, place?.second) }
                        result.onFailure {
                            Log.e("RetroCamera", "저장 실패", it)
                            controller.showToast("저장 실패: ${it.message}")
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("RetroCamera", "촬영 실패", exception)
                if (mode == Mode.FILM) inFlightFilm--
                controller.showToast("촬영 실패")
            }
        })
        if (screenFlash) {
            glView.postDelayed({ shoot(); glView.postDelayed({ controller.screenFlash = false }, 150) }, 260)
        } else shoot()
    }

    /** 화면을 돌려도 액티비티를 다시 만들지 않으므로(configChanges), 미리보기/촬영 방향을 직접 맞춘다 */
    private fun applyDisplayRotation() {
        val r = display?.rotation ?: return
        preview?.targetRotation = r
        imageCapture?.targetRotation = r
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDisplayRotation()
    }
}
