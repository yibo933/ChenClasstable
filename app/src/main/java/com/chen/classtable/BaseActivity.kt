package com.chen.classtable

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 所有页面的缩放基类。
 *
 * 只调整当前 Activity 的局部 Configuration：
 * - 不修改系统 density / 全局 DPI；
 * - 不修改 WindowManager / 状态栏 / 导航栏尺寸；
 * - UI 缩放与系统字体缩放叠加，布局密度只影响 dp 尺寸。
 */
abstract class BaseActivity : AppCompatActivity() {

    private var appliedScale: Float = 1f
    private var appliedDensity: LayoutDensity = LayoutDensity.STANDARD

    override fun attachBaseContext(newBase: Context) {
        val snap = UiPreferences.snapshot(newBase)
        appliedScale = snap.scale
        appliedDensity = snap.density
        val config = Configuration(newBase.resources.configuration)
        val baseDpi = newBase.resources.displayMetrics.densityDpi
        val factor = snap.scale * snap.density.factor
        config.densityDpi = (baseDpi * factor)
            .roundToInt()
            .coerceIn(72, 1200)
        // 布局密度只改变 dp 尺寸；通过抵消 densityFactor，让字体只受 UI 缩放 + 系统字体影响。
        config.fontScale = (config.fontScale / snap.density.factor)
            .coerceIn(0.5f, 3.0f)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onResume() {
        super.onResume()
        val snap = UiPreferences.snapshot(this)
        if (abs(snap.scale - appliedScale) > 0.001f || snap.density != appliedDensity) {
            recreate()
        }
    }
}
