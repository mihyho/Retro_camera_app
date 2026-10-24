package com.example.filmcamera

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
import com.example.filmcamera.data.Mode
import com.example.filmcamera.ui.CameraScreen
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
            if (granted) startCamera() else controller.showToast("카메라 권한이 필요합니다")
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
        renderer.onContextRecreated = { if (imageCapture != null) startCamera() }
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        setContent {
            // 필름(모드)이 바뀌면 프리뷰 LUT도 교체
            LaunchedEffect(controller.currentFilm) {
                renderer.setLut(controller.lutFor(controller.currentFilm))
                renderer.setClarity(controller.currentFilm.clarity)
            }
            CameraScreen(controller, glView, onShutter = ::takePhoto, onZoomBy = ::zoomBy, onZoomToggle = ::zoomToggle)
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
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
            val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            camera = cam
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

    /** 더블탭: 1배 <-> 2배 */
    private fun zoomToggle() {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value ?: return
        zoomTarget = ZoomMath.doubleTapTarget(zs.zoomRatio, zs.minZoomRatio, zs.maxZoomRatio)
        cam.cameraControl.setZoomRatio(zoomTarget)
    }

    private fun takePhoto() {
        // 패널(현상/사진/설정)이 떠 있으면 셔터는 카메라 화면으로 돌아오기만 한다
        if (controller.panel != null || controller.drawerOpen || controller.discardAsk != null) { controller.panel = null; controller.closeDrawer(); controller.discardAsk = null; return }
        val capture = imageCapture ?: return
        val s = controller.state
        val mode = s.mode
        val film = controller.currentFilm
        val rollStart = s.roll?.start
        if (mode == Mode.FILM) {
            val roll = s.roll
            if (roll == null) { controller.showToast("필름을 먼저 넣어 주세요"); return }
            if (roll.remaining - inFlightFilm <= 0) return // 마지막 장 처리 중
            inFlightFilm++
        }
        controller.triggerFlash()
        val lut = controller.lutFor(film)

        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
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
                    val result = runCatching { PhotoPipeline.process(applicationContext, bytes, rotation, lut, mode, film) }
                    runOnUiThread {
                        if (mode == Mode.FILM) inFlightFilm--
                        result.onSuccess { controller.onPhotoSaved(it, mode, film, rollStart) }
                        result.onFailure {
                            Log.e("FilmCamera", "저장 실패", it)
                            controller.showToast("저장 실패: ${it.message}")
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("FilmCamera", "촬영 실패", exception)
                if (mode == Mode.FILM) inFlightFilm--
                controller.showToast("촬영 실패")
            }
        })
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
