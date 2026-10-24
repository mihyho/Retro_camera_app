package com.example.retrocamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.retrocamera.data.Film
import java.io.File

/** 디버그 전용: 현상 흐름을 시험하기 위한 가짜 사진(필름 색 그라데이션)을 만든다. */
object DebugPhotos {
    fun make(context: Context, film: Film, n: Int): SavedPhoto {
        val bmp = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val c = film.accent.toInt()
        val shade = ((n * 37) % 100) / 100f
        Canvas(bmp).drawRect(0f, 0f, 480f, 640f, Paint().apply {
            shader = LinearGradient(0f, 0f, 480f, 640f, c, android.graphics.Color.rgb((255 * (0.6f + 0.4f * shade)).toInt(), 235, 200), Shader.TileMode.CLAMP)
        })
        val dir = File(context.filesDir, "rolls/pending").apply { mkdirs() }
        val name = "DEBUG_%d_%03d.jpg".format(System.currentTimeMillis(), n)
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return SavedPhoto("rolls/pending/$name", null)
    }
}
