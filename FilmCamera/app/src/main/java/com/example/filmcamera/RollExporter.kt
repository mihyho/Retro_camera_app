package com.example.filmcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.example.filmcamera.data.DevelopedRoll
import com.example.filmcamera.data.Film
import com.example.filmcamera.data.Shot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 현상: 비공개 폴더의 롤 사진을 갤러리(MediaStore)로 내보낸다. */
object RollExporter {

    /** 파일이 사라져 내보낼 수 없는 사진의 표시. 영구 재시도에 빠지지 않도록 이 값으로 완료 처리한다. */
    const val MISSING = ""

    fun displayName(film: Film, takenAt: Long, frame: Int): String =
        "FILM_%s_%s_%02d.jpg".format(film.key, SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(takenAt)), frame)

    /**
     * 전부 성공해야 uri 목록(사진 순서와 같음)을 돌려준다.
     * 중간에 실패하면 이미 만든 갤러리 항목을 지우고 예외를 던진다 -> 다음 실행에서 처음부터 다시 시도.
     */
    fun export(context: Context, roll: DevelopedRoll): List<String> {
        val resolver = context.contentResolver
        val created = ArrayList<Uri>()
        val uris = ArrayList<String>()
        try {
            roll.shots.forEachIndexed { i, shot ->
                val src = File(context.filesDir, shot.file)
                if (!src.exists()) { uris += MISSING; return@forEachIndexed }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName(roll.film, shot.takenAt, i + 1))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FilmCamera")
                    put(MediaStore.Images.Media.DATE_TAKEN, shot.takenAt)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("갤러리 항목 생성 실패")
                created += uri
                resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uris += uri.toString()
            }
        } catch (e: Exception) {
            created.forEach { runCatching { resolver.delete(it, null, null) } }
            throw e
        }
        return uris
    }

    /** 내보내기가 끝난 롤의 비공개 원본 삭제 */
    fun deletePrivateFiles(context: Context, roll: DevelopedRoll) {
        roll.shots.forEach { runCatching { File(context.filesDir, it.file).delete() } }
    }
}

/** 사진 썸네일. 내보내기 전에는 비공개 파일, 후에는 갤러리 uri에서 읽는다. */
object ThumbLoader {
    // 장수가 아니라 바이트로 한도를 둔다: 재생의 한 장 보기는 큰 그림(약 5MB)을 넣으므로 장수 기준이면 수백 MB가 된다
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(context: Context, shot: Shot, px: Int): Bitmap? {
        val uri = shot.uri
        val key = (if (!uri.isNullOrEmpty()) uri else shot.file) + "@$px"
        cache.get(key)?.let { return it }
        val bmp = runCatching {
            if (!uri.isNullOrEmpty()) context.contentResolver.loadThumbnail(Uri.parse(uri), Size(px, px), null)
            else decodeFile(File(context.filesDir, shot.file), px)
        }.getOrNull()
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decodeFile(f: File, px: Int): Bitmap? {
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        val raw = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val deg = when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
    }
}
