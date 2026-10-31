package com.example.retrocamera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import com.example.retrocamera.data.Shot

/** 사진 썸네일: 갤러리(MediaStore) uri에서 읽는다. */
object ThumbLoader {
    // 장수가 아니라 바이트로 한도를 둔다: 사진 한 장 보기는 큰 그림(약 5MB)을 넣으므로 장수 기준이면 수백 MB가 된다
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(context: Context, shot: Shot, px: Int): Bitmap? {
        val key = shot.uri + "@$px"
        cache.get(key)?.let { return it }
        val uri = Uri.parse(shot.uri)
        val bmp = runCatching {
            // 큰 보기는 시스템 썸네일(저해상도 캐시일 수 있음)이 아니라 원본을 목표 크기로 직접 디코딩한다(회전도 자동 적용)
            if (px >= 800) runCatching { decodeUri(context, uri, px) }.getOrNull()
                ?: context.contentResolver.loadThumbnail(uri, Size(px, px), null)
            else context.contentResolver.loadThumbnail(uri, Size(px, px), null)
        }.getOrNull()
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decodeUri(context: Context, uri: Uri, px: Int): Bitmap =
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            if (long > px) { val k = px.toFloat() / long; dec.setTargetSize(Math.round(info.size.width * k), Math.round(info.size.height * k)) }
            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
}
