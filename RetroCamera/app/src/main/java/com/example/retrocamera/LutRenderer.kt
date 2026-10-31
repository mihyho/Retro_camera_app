package com.example.retrocamera

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 카메라 프리뷰(OES 텍스처)에 3D LUT를 적용해 그리는 렌더러.
 * Preview.SurfaceProvider에서 onSurfaceRequested()를 호출해 연결하며, GL 호출은 GL 스레드에서만 한다.
 */
class LutRenderer(
    private val glView: GLSurfaceView,
    initialLut: CubeLut,
    private val mainExecutor: Executor,
) : GLSurfaceView.Renderer {

    @Volatile var lutEnabled = true
        set(v) { field = v; glView.requestRender() }

    @Volatile private var pendingRequest: SurfaceRequest? = null
    @Volatile private var pendingLut: CubeLut? = null
    @Volatile private var clarity = 0f
    private var lut: CubeLut = initialLut
    private var lutUploaded = false

    /** 필름의 명료도(국소 대비, 음수면 부드럽게). 필름을 바꿀 때 함께 바꾼다 */
    fun setClarity(value: Float) {
        clarity = value
        glView.requestRender()
    }

    /** 필름 교체: 다음 프레임에 GL 스레드에서 3D 텍스처를 새 LUT로 다시 올린다 */
    fun setLut(newLut: CubeLut) {
        pendingLut = newLut
        glView.requestRender()
    }
    /** 필름 그리드: 같은 실시간 영상을 필름 수만큼 타일로 나눠 각자의 LUT로 그린다. null이면 한 화면 모드 */
    class GridTile(val lut: CubeLut, val clarity: Float)
    @Volatile private var pendingGrid: List<GridTile>? = null
    @Volatile private var gridOn = false
    private var gridTiles: List<GridTile> = emptyList()
    private var gridTex = IntArray(0)
    private var gridCols = 3

    fun setGrid(tiles: List<GridTile>?, cols: Int = 3) {
        gridCols = cols
        if (tiles != null) pendingGrid = tiles
        gridOn = tiles != null
        glView.requestRender()
    }

    /** 모든 필름의 LUT를 미리 올려 둔다(그리드와 하단 필름 스트립이 같이 쓴다). 화면에는 그리지 않는다 */
    fun setTiles(tiles: List<GridTile>) {
        pendingGrid = tiles
        glView.requestRender()
    }

    fun setGridVisible(on: Boolean) {
        gridOn = on
        glView.requestRender()
    }

    /** 프리뷰를 그릴 곳 [x, y, w, h, 모서리 반지름](뷰 왼쪽 위 기준 px). null이면 뷰 전체 */
    @Volatile private var previewRect: IntArray? = null
    fun setPreview(rect: IntArray?) {
        previewRect = rect
        glView.requestRender()
    }

    /** 필름 스트립의 칸마다 [x, y(위 기준), 크기, 투명도] px를 네 개씩 받아, 그 필름 LUT로 같은 영상을 작게 그린다 */
    @Volatile private var stripRects: FloatArray? = null
    fun setStrip(rects: FloatArray?) {
        stripRects = rects
        glView.requestRender()
    }

    @Volatile private var rotationDegrees = 0
    /** 화면(UI)을 돌렸을 때 미리보기가 같이 돌아가지 않도록 더 돌려 줄 각도(시계 방향). SurfaceTexture 행렬은 세로 기준 회전만 담고 있다 */
    @Volatile private var extraRotation = 0f
    private val rotM = FloatArray(16)
    private val tmpM = FloatArray(16)

    /** GL 컨텍스트가 새로 만들어져(화면 회전 때 뷰가 옮겨지면 생긴다) 카메라 출력이 끊겼을 때: 카메라를 다시 연결하도록 알린다 */
    var onContextRecreated: (() -> Unit)? = null

    // GL 스레드 전용 상태
    private var program = 0
    private var camTex = 0
    private var lutTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var bufferW = 0
    private var bufferH = 0
    private var viewW = 1
    private var viewH = 1
    private val stMatrix = FloatArray(16)
    private val mvp = FloatArray(16)
    private val local = FloatArray(16)
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    /** 메인 스레드: 요청만 저장하고 GL 스레드가 처리하도록 깨운다 */
    fun onSurfaceRequested(request: SurfaceRequest) {
        request.setTransformationInfoListener(mainExecutor) { info ->
            rotationDegrees = info.rotationDegrees
            val steps = when (info.targetRotation) { android.view.Surface.ROTATION_90 -> 1; android.view.Surface.ROTATION_180 -> 2; android.view.Surface.ROTATION_270 -> 3; else -> 0 }
            // 후면은 화면이 한 칸 돌 때마다 rotationDegrees가 -90, 전면(좌우 반전 카메라)은 +90씩 변한다
            extraRotation = (((if (info.isMirroring) 1 else -1) * EXTRA_SIGN * 90 * steps) % 360 + 360) % 360f
            glView.requestRender()
        }
        pendingRequest = request
        glView.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // GL 컨텍스트가 새로 만들어진 것이므로 이전 객체는 모두 무효
        val wasStreaming = surfaceTexture != null && pendingRequest == null
        surfaceTexture = null // 이전 컨텍스트의 텍스처라 쓸 수 없다
        if (wasStreaming) mainExecutor.execute { onContextRecreated?.invoke() }
        program = buildProgram(VERTEX, FRAGMENT)
        val tex = IntArray(2)
        GLES30.glGenTextures(2, tex, 0)
        camTex = tex[0]
        lutTex = tex[1]

        val oes = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES30.glBindTexture(oes, camTex)
        GLES30.glTexParameteri(oes, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(oes, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(oes, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(oes, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        lutUploaded = false
        gridTex = IntArray(0) // 이전 컨텍스트의 타일 텍스처는 무효: 다음 그리기에서 다시 올린다
        if (gridTiles.isNotEmpty()) pendingGrid = gridTiles
        uploadLut()
    }

    /** GL 스레드: 그리드 타일 LUT를 각자 3D 텍스처로 올린다 */
    private fun uploadGrid(tiles: List<GridTile>) {
        if (gridTex.isNotEmpty()) GLES30.glDeleteTextures(gridTex.size, gridTex, 0)
        gridTex = IntArray(tiles.size).also { GLES30.glGenTextures(it.size, it, 0) }
        tiles.forEachIndexed { i, t -> uploadLutTo(gridTex[i], t.lut) }
        gridTiles = tiles
    }

    /** GL 스레드: lut를 lutTex(3D 텍스처)로 업로드 */
    private fun uploadLut() {
        uploadLutTo(lutTex, lut)
        lutUploaded = true
    }

    private fun uploadLutTo(tex: Int, lut: CubeLut) {
        val n = lut.size
        val buf = ByteBuffer.allocateDirect(lut.data.size * 4).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().put(lut.data)
        buf.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F, n, n, n, 0,
            GLES30.GL_RGB, GLES30.GL_FLOAT, buf,
        )
        for (p in intArrayOf(GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_TEXTURE_MAG_FILTER))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_LINEAR)
        for (p in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingLut?.let { lut = it; pendingLut = null; uploadLut() }
        pendingRequest?.let { fulfil(it); pendingRequest = null }

        pendingGrid?.let { uploadGrid(it); pendingGrid = null }
        GLES30.glViewport(0, 0, viewW, viewH)
        GLES30.glClearColor(BG_R, BG_G, BG_B, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(stMatrix)

        val pr = previewRect
        val px = pr?.get(0) ?: 0
        val pw = pr?.get(2) ?: viewW
        val ph = pr?.get(3) ?: viewH
        val py = viewH - ((pr?.get(1) ?: 0) + ph) // GL 뷰포트는 왼쪽 아래가 원점
        if (pw > 0 && ph > 0) {
            if (gridOn && gridTiles.isNotEmpty() && gridTex.size == gridTiles.size) {
                val rows = (gridTiles.size + gridCols - 1) / gridCols
                val tw = pw / gridCols; val th = ph / rows
                gridTiles.forEachIndexed { i, t ->
                    val x = px + (i % gridCols) * tw
                    val y = py + ph - (i / gridCols + 1) * th
                    drawTile(x, y, tw, th, gridTex[i], t.lut.size, t.clarity, true)
                }
            } else {
                drawTile(px, py, pw, ph, lutTex, lut.size, clarity, lutEnabled, 1f, (pr?.getOrNull(4) ?: 0).toFloat())
            }
        }

        val sr = stripRects
        if (sr != null && gridTiles.isNotEmpty() && gridTex.size == gridTiles.size) {
            // 배열 끝의 4개는 스트립 영역 [왼쪽, 위, 오른쪽, 아래]: 칩이 스트립 밖(프리뷰 위)으로 나가 그려지지 않게 자른다
            val ci = gridTiles.size * 8
            if (sr.size >= ci + 4) {
                val l = sr[ci].toInt(); val t = sr[ci + 1].toInt(); val r = sr[ci + 2].toInt(); val b = sr[ci + 3].toInt()
                GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
                GLES30.glScissor(l, viewH - b, (r - l).coerceAtLeast(0), (b - t).coerceAtLeast(0))
            }
            for (i in gridTiles.indices) {
                val o = i * 8
                if (o + 7 >= sr.size) break
                val s = sr[o + 2].toInt()
                val x = sr[o].toInt()
                if (s < 2 || x + s < 0 || x > viewW) continue
                drawTile(
                    x, viewH - (sr[o + 1].toInt() + s), s, s, gridTex[i], gridTiles[i].lut.size, gridTiles[i].clarity, true, sr[o + 3], s * 0.29f,
                    sr[o + 4], sr[o + 5], sr[o + 6], sr[o + 7],
                )
            }
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        }
    }

    /** 뷰포트 한 칸에 영상을 중앙 크롭으로 그린다. round>0이면 모서리를 둥글게(바깥은 그리지 않음), alpha<1이면 바탕색 쪽으로 흐리게 */
    private fun drawTile(x: Int, y: Int, w: Int, h: Int, tex: Int, lutSize: Int, clarity: Float, lutOn: Boolean, alpha: Float = 1f, round: Float = 0f,
        ringW: Float = 0f, ringR: Float = 0f, ringG: Float = 0f, ringB: Float = 0f,
    ) {
        GLES30.glViewport(x, y, w, h)
        // 화면 uv(0..1) -> 중앙 크롭 -> SurfaceTexture 변환.
        // 회전은 SurfaceTexture 변환 행렬에 이미 포함돼 있다(에뮬레이터에서 확인: rotationDegrees=90일 때
        // 별도 회전을 더하면 90도/180도 틀어진다). rotationDegrees는 크롭 비율 계산(가로/세로 교환)에만 쓴다.
        val rot = rotationDegrees
        val dispW = if (rot % 180 == 0) bufferW else bufferH
        val dispH = if (rot % 180 == 0) bufferH else bufferW
        val viewAspect = w.toFloat() / h
        val imgAspect = dispW.toFloat() / dispH
        val sx = if (imgAspect > viewAspect) viewAspect / imgAspect else 1f
        val sy = if (imgAspect > viewAspect) 1f else imgAspect / viewAspect
        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(local, 0, sx, sy, 1f)
        Matrix.translateM(local, 0, -0.5f, -0.5f, 0f)
        Matrix.setIdentityM(rotM, 0)
        Matrix.translateM(rotM, 0, 0.5f, 0.5f, 0f)
        Matrix.rotateM(rotM, 0, extraRotation, 0f, 0f, 1f)
        Matrix.translateM(rotM, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(tmpM, 0, rotM, 0, local, 0)
        Matrix.multiplyMM(mvp, 0, stMatrix, 0, tmpM, 0)

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uTexMatrix"), 1, false, mvp, 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uLutSize"), lutSize.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uLutOn"), if (lutOn) 1f else 0f)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uTexel"), 1f / bufferW, 1f / bufferH)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uAlpha"), alpha)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uRound"), round)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uRingW"), ringW)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uRing"), ringR, ringG, ringB)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uHalf"), w / 2f, h / 2f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uBg"), BG_R, BG_G, BG_B)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uClarity"), clarity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uClaritySigma"), Clarity.sigma(bufferW))
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uCam"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLut"), 1)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** GL 스레드: CameraX가 요청한 해상도의 SurfaceTexture를 만들어 카메라에 넘긴다 */
    private fun fulfil(request: SurfaceRequest) {
        surfaceTexture?.release()
        val res = request.resolution
        bufferW = res.width
        bufferH = res.height
        val st = SurfaceTexture(camTex).apply {
            setDefaultBufferSize(res.width, res.height)
            setOnFrameAvailableListener { glView.requestRender() }
        }
        surfaceTexture = st
        val surface = Surface(st)
        request.provideSurface(surface, mainExecutor) { surface.release() }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "셰이더 컴파일 실패: " + GLES30.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "프로그램 링크 실패: " + GLES30.glGetProgramInfoLog(p) }
        return p
    }

    private companion object {
        const val EXTRA_SIGN = 1

        // UI 바탕(Ds.Bg = 0xFF161410)과 같은 색: 스트립 자리와 흐린 칩이 바탕에 자연스럽게 섞인다
        const val BG_R = 0x16 / 255f
        const val BG_G = 0x14 / 255f
        const val BG_B = 0x10 / 255f

        const val VERTEX = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform mat4 uTexMatrix;
out vec2 vUv;
out vec2 vPos;
void main() {
    vPos = aPos;
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = (uTexMatrix * vec4(aPos * 0.5 + 0.5, 0.0, 1.0)).xy;
}"""

        // .cube 격자점은 텍셀 중심에 대응하므로 반 텍셀 보정을 해야 33점이 정확히 맞는다.
        // 색감은 흐린 이미지(상하좌우 5점 평균)에 입히고, 세부/노이즈는 원본 크기를 넘지 못하게 제한한다
        // (촬영본의 LutApplier.applyDetailSafe 와 같은 방식 -> 색감을 입혀도 노이즈가 늘지 않는다).
        const val FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
precision highp sampler3D;
uniform samplerExternalOES uCam;
uniform sampler3D uLut;
uniform float uLutSize;
uniform float uLutOn;
uniform vec2 uTexel;
uniform float uClarity;
uniform float uClaritySigma;
uniform float uAlpha;
uniform float uRound;
uniform float uRingW;
uniform vec3 uRing;
uniform vec2 uHalf;
uniform vec3 uBg;
in vec2 vUv;
in vec2 vPos;
out vec4 outColor;
vec3 grade(vec3 c) {
    vec3 p = c * ((uLutSize - 1.0) / uLutSize) + 0.5 / uLutSize;
    return texture(uLut, p).rgb;
}
void main() {
    float sd = -1000.0; // 둥근 사각형 안쪽이면 음수(가장자리까지의 거리)
    if (uRound > 0.0) {
        vec2 q = abs(vPos) * uHalf - (uHalf - vec2(uRound));
        sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRound;
        if (sd > 0.0) discard;
    }
    vec3 c0 = texture(uCam, vUv).rgb;
    vec2 o = uTexel * 1.5;
    // 세부 분리용 흐림(색감을 입힐 때 노이즈가 늘지 않게)은 원본 샘플로 만든다
    vec3 base = (c0
        + texture(uCam, vUv + vec2(o.x, 0.0)).rgb + texture(uCam, vUv - vec2(o.x, 0.0)).rgb
        + texture(uCam, vUv + vec2(0.0, o.y)).rgb + texture(uCam, vUv - vec2(0.0, o.y)).rgb) / 5.0;
    // 명료도: 가우시안 흐림을 원본에 섞되 뚜렷한 경계는 보호한다(모든 채널, 모든 밝기). 1-2-1 가중 3x3 샘플을 sigma/0.7071 간격으로 두면
    // 표준편차가 sigma인 가우시안에 가깝다(촬영본 Clarity.kt와 같은 수식: 결과 = 원본 + (흐림 - 원본) * (-clarity))
    vec3 c = c0;
    if (uClarity != 0.0) {
        vec2 s = uTexel * (uClaritySigma / 0.7071);
        vec3 blur = (
            4.0 * c0
            + 2.0 * (texture(uCam, vUv + vec2(s.x, 0.0)).rgb + texture(uCam, vUv - vec2(s.x, 0.0)).rgb
                   + texture(uCam, vUv + vec2(0.0, s.y)).rgb + texture(uCam, vUv - vec2(0.0, s.y)).rgb)
            + texture(uCam, vUv + vec2(s.x, s.y)).rgb + texture(uCam, vUv + vec2(-s.x, s.y)).rgb
            + texture(uCam, vUv + vec2(s.x, -s.y)).rgb + texture(uCam, vUv + vec2(-s.x, -s.y)).rgb
        ) / 16.0;
        // 경계 보호: 원본과 흐림의 밝기 차가 크면(뚜렷한 경계) 흐림을 섞지 않는다(촬영본 Clarity.kt 와 같은 수식)
        float dy = abs(dot(c0 - blur, vec3(0.299, 0.587, 0.114)));
        float e = clamp((dy - 0.03) / (0.14 - 0.03), 0.0, 1.0);
        float w = 1.0 - e * e * (3.0 - 2.0 * e);
        c = clamp(c0 + (blur - c0) * (-uClarity) * w, 0.0, 1.0);
    }
    vec3 gb = grade(base);
    vec3 dIn = c - base;
    vec3 graded = gb + clamp(grade(c) - gb, -abs(dIn), abs(dIn));
    vec3 rgb = mix(c, graded, uLutOn);
    if (uRingW > 0.0) rgb = mix(rgb, uRing, smoothstep(-uRingW - 0.5, -uRingW + 0.5, sd)); // 선택된 칩의 링
    outColor = vec4(mix(uBg, rgb, uAlpha), 1.0);
}"""
    }
}
