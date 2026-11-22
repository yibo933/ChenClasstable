package com.chen.classtable

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup

/**
 * 统一管理窗口断点和平板栏宽。
 * 只根据当前窗口可用宽度判断，不读取设备型号。
 */
object AdaptiveLayoutManager {

    const val PHONE_MAX_DP = 599
    const val COMPACT_MAX_DP = 839
    const val TWO_PANE_MAX_DP = 1199

    enum class Mode {
        PHONE,
        COMPACT,
        TWO_PANE,
        THREE_PANE
    }

    fun modeFor(widthDp: Int): Mode = when {
        widthDp <= PHONE_MAX_DP -> Mode.PHONE
        widthDp <= COMPACT_MAX_DP -> Mode.COMPACT
        widthDp <= TWO_PANE_MAX_DP -> Mode.TWO_PANE
        else -> Mode.THREE_PANE
    }

    fun isTablet(mode: Mode): Boolean = mode != Mode.PHONE

    fun sidebarWidthDp(mode: Mode): Int = when (mode) {
        Mode.PHONE -> 0
        Mode.COMPACT -> 96
        Mode.TWO_PANE -> 208
        Mode.THREE_PANE -> 232
    }

    fun detailWidthDp(mode: Mode): Int = when (mode) {
        Mode.PHONE, Mode.COMPACT -> 0
        Mode.TWO_PANE -> 280
        Mode.THREE_PANE -> 320
    }

    fun showsDetail(mode: Mode): Boolean = mode == Mode.TWO_PANE || mode == Mode.THREE_PANE

    fun showsTableList(mode: Mode): Boolean = mode == Mode.TWO_PANE || mode == Mode.THREE_PANE

    /** 给独立设置页/表单页加可读宽度，避免平板全宽拉伸；手机宽度下不产生变化。 */
    fun applyReadableWidth(activity: Activity, maxWidthDp: Int = 720) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) ?: return
        val widthDp = windowWidthDp(activity)
        val extraDp = ((widthDp - maxWidthDp).coerceAtLeast(0) / 2f)
        val extraPx = (extraDp * activity.resources.displayMetrics.density).toInt()
        content.setPadding(
            content.paddingLeft + extraPx,
            content.paddingTop,
            content.paddingRight + extraPx,
            content.paddingBottom
        )
    }

    /** 当前窗口可用宽度（dp）。优先使用系统 WindowMetrics，兼容旧版本。 */
    fun windowWidthDp(activity: Activity): Int {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            activity.window.decorView.width.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.widthPixels
        }
        val density = activity.resources.displayMetrics.density.coerceAtLeast(0.1f)
        return (px / density).toInt()
    }
}
