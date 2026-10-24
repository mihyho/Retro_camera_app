package com.example.retrocamera

import java.util.Locale

/** 카메라 확대 계산(순수 함수). 핀치/더블탭의 목표 배율과 표시 문자열. */
object ZoomMath {

    /** 더블탭으로 확대할 때의 배율 */
    const val DOUBLE_TAP_RATIO = 2f

    fun clamp(ratio: Float, min: Float, max: Float): Float = ratio.coerceIn(min, max)

    /** 현재 배율에 핀치 배율(두 손가락 간격의 비)을 곱한 목표 배율. 범위를 넘지 않는다. */
    fun pinch(current: Float, scale: Float, min: Float, max: Float): Float = clamp(current * scale, min, max)

    /** 더블탭: 확대 중이면 최소 배율로, 아니면 2배(최대 배율이 2배보다 작으면 최대 배율)로 */
    fun doubleTapTarget(current: Float, min: Float, max: Float): Float =
        if (current > min * 1.05f) min else minOf(DOUBLE_TAP_RATIO, max).coerceAtLeast(min)

    /** 표시용: 1.0× / 2.5× / 10× */
    fun format(ratio: Float): String =
        if (ratio >= 10f) String.format(Locale.US, "%.0f×", ratio) else String.format(Locale.US, "%.1f×", ratio)
}
