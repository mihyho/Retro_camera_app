package com.example.retrocamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.provider.MediaStore
import com.example.retrocamera.data.Film
import com.example.retrocamera.data.Mode
import java.io.File
import java.util.stream.IntStream

/** 저장 결과. 필름 모드는 file(앱 전용 저장소 기준 상대 경로), 단일 촬영은 uri(갤러리 주소) */
data class SavedPhoto(val file: String?, val uri: String?)

/** JPEG 바이트 -> LUT 적용 -> 모드에 맞는 곳에 저장. 무거우므로 반드시 백그라운드 스레드에서 호출. */
object PhotoPipeline {

    private const val STRIP_ROWS = 256
    private const val JPEG_QUALITY = 95

    fun process(context: Context, jpeg: ByteArray, rotationDegrees: Int, lut: CubeLut, mode: Mode, film: Film, grainOn: Boolean = true, stampText: String? = null, lat: Double? = null, lon: Double? = null): SavedPhoto {
        val opts = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val full = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
            ?: throw IllegalStateException("촬영본 디코딩 실패")
        val bmp = cropToFourFive(full, rotationDegrees)

        // 필름의 룩: 색감(LUT) + 명료도 + 그레인. 명료도와 그레인 값은 필름마다 정해져 있다(Film.clarity, Film.grain)
        applyLut(bmp, lut, film.clarity, if (grainOn) film.grain else 0f)

        if (stampText != null) drawStamp(bmp, stampText, rotationDegrees)

        val tmp = File.createTempFile("shot_", ".jpg", context.cacheDir)
        try {
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bmp.recycle()
            // 픽셀은 센서 방향 그대로이므로 EXIF 회전 태그로 똑바로 보이게 한다
            ExifInterface(tmp.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation(rotationDegrees).toString())
                if (lat != null && lon != null) { // 기본 갤러리 앱이 읽는 GPS 태그
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, gpsRational(lat))
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, gpsRational(lon))
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
                }
                saveAttributes()
            }
            val name = "FILM_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
                .format(java.util.Date()) + ".jpg"
            return when (mode) {
                Mode.SINGLE -> SavedPhoto(null, saveToGallery(context, tmp, name).toString())
                Mode.FILM -> SavedPhoto(saveToPrivate(context, tmp, name), null)
            }
        } finally {
            tmp.delete()
        }
    }

    /** EXIF GPS 값 형식: 도/1,분/1,초/10000 (부호는 N/S, E/W 태그가 맡는다) */
    private fun gpsRational(v: Double): String {
        val a = Math.abs(v)
        val d = a.toInt()
        val m = ((a - d) * 60).toInt()
        val s = ((a - d) * 60 - m) * 60
        return "$d/1,$m/1,${(s * 10000).toLong()}/10000"
    }

    /**
     * 촬영 비율은 4:5 고정(가로로 들면 5:4). 똑바로 세웠을 때(EXIF 회전 적용 후) 가운데를 그 비율로 자른다.
     * 픽셀은 센서 방향 그대로라 회전이 90/270이면 가로·세로를 바꿔서 자를 영역을 구한다(뷰파인더의 중앙 크롭과 같은 영역).
     */
    fun cropToFourFive(src: Bitmap, rotationDegrees: Int): Bitmap {
        val odd = ((rotationDegrees % 360 + 360) % 360) % 180 != 0
        val dw = if (odd) src.height else src.width
        val dh = if (odd) src.width else src.height
        val cw: Int; val ch: Int // 똑바로 섰을 때의 자를 크기. 세로로 든 사진은 4:5, 가로로 든 사진은 5:4(같은 비율을 눕힌 것)
        if (dw >= dh) {
            if (dh * 5 > dw * 4) { cw = dw; ch = dw * 4 / 5 } else { ch = dh; cw = dh * 5 / 4 }
        } else {
            if (dw * 5 > dh * 4) { ch = dh; cw = dh * 4 / 5 } else { cw = dw; ch = dw * 5 / 4 }
        }
        val rw = if (odd) ch else cw
        val rh = if (odd) cw else ch
        if (rw == src.width && rh == src.height) return src
        val x = (src.width - rw) / 2; val y = (src.height - rh) / 2
        val out = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888) // 가변: 뒤에서 LUT를 제자리에 적용한다
        Canvas(out).drawBitmap(src, android.graphics.Rect(x, y, x + rw, y + rh), android.graphics.Rect(0, 0, rw, rh), null)
        src.recycle()
        return out
    }

    /**
     * 날짜 각인: 사진을 똑바로 세웠을 때(EXIF 회전 적용 후) 오른쪽 아래에 오도록 그린다.
     * 픽셀은 센서 방향 그대로이므로, 똑바로 선 좌표계를 센서 방향으로 돌려서 그린다.
     */
    private fun drawStamp(bmp: Bitmap, text: String, rotationDegrees: Int) {
        val rot = (rotationDegrees % 360 + 360) % 360
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val upW = if (rot % 180 == 0) w else h
        val upH = if (rot % 180 == 0) h else w
        val m = Matrix().apply {
            setRotate(-rot.toFloat())
            when (rot) { 90 -> postTranslate(0f, h); 180 -> postTranslate(w, h); 270 -> postTranslate(w, 0f) }
        }
        val size = minOf(upW, upH) * 0.04f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF9A3C.toInt(); textSize = size; typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT; setShadowLayer(size * 0.08f, 0f, 0f, 0xAA000000.toInt())
        }
        val canvas = Canvas(bmp)
        canvas.concat(m)
        canvas.drawText(text, upW - size * 0.9f, upH - size * 0.9f, paint)
    }

    /** 흐림(세부 분리) 반경: 프리뷰 셰이더(1.5텍셀, 폭 약 1300px 기준)와 비슷한 비율로 맞춘다 */
    fun detailRadius(width: Int): Int = maxOf(2, Math.round(width * 0.0012f))

    /**
     * 필름의 룩을 입힌다: 명료도 -> 색감(LUT) -> 그레인. 색감은 흐린 이미지에 입히고 세부/노이즈는 원본 크기를 넘지 않게
     * 제한해서 색감을 입히는 과정에서 노이즈가 늘지 않게 한다(그레인은 이 뒤에 의도해서 얹는다).
     * 큰 이미지를 통째로 IntArray로 만들지 않도록 띠(strip) 단위로 처리하되, 블러에 필요한 위아래 여백 행을 함께 읽는다.
     */
    private fun applyLut(bmp: Bitmap, lut: CubeLut, clarity: Float, grain: Float) {
        val w = bmp.width
        val h = bmp.height
        val r = detailRadius(w)
        val rLarge = if (clarity != 0f) Clarity.boxRadius(w) else 0
        val margin = maxOf(r, 2 * rLarge)           // 박스 블러 2회라 여백도 2배
        val block = IntArray(w * (STRIP_ROWS + 2 * margin))
        var y = 0
        while (y < h) {
            val rows = minOf(STRIP_ROWS, h - y)
            val top = maxOf(0, y - margin)             // 블록의 첫 행(이미지 위쪽 끝에서는 여백이 없다)
            val bottom = minOf(h, y + rows + margin)   // 블록의 끝 행(미포함)
            val blockRows = bottom - top
            bmp.getPixels(block, 0, w, 0, top, w, blockRows)
            val base = BoxBlur.blurArgb(block, w, blockRows, r)           // 모두 변환 전 원본 블록에서 계산
            val large = if (rLarge > 0) Clarity.blur(block, w, blockRows, rLarge) else base
            val from0 = (y - top) * w                  // 변환할 가운데 행들의 시작/끝 인덱스
            val to0 = from0 + rows * w
            val total = to0 - from0
            val chunk = (total + 3) / 4
            IntStream.range(0, 4).parallel().forEach { c ->
                val from = from0 + c * chunk
                val to = minOf(to0, from + chunk)
                if (from < to) {
                    if (rLarge > 0) Clarity.applyArgb(block, large, from, to, clarity)
                    LutApplier.applyDetailSafe(block, base, from, to, lut)
                    if (grain > 0f) Grain.applyArgb(block, from, to, w, top, strength = grain)
                }
            }
            bmp.setPixels(block, from0, w, 0, y, w, rows)
            y += rows
        }
    }

    private fun exifOrientation(deg: Int) = when ((deg % 360 + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    private fun saveToGallery(context: Context, file: File, name: String): android.net.Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RetroCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("갤러리 항목 생성 실패")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // 반쪽짜리 항목을 남기지 않는다
            throw e
        }
    }

    /** 필름 모드: 앱 전용 폴더(갤러리에 안 보임). 4단계에서 롤 단위 관리/현상으로 확장한다. */
    private fun saveToPrivate(context: Context, file: File, name: String): String {
        val dir = File(context.filesDir, "rolls/pending").apply { mkdirs() }
        val dst = File(dir, name)
        file.copyTo(dst, overwrite = false)
        return "rolls/pending/$name"
    }
}
