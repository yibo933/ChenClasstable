package com.chen.classtable

import android.app.Activity
import android.content.res.Configuration
import androidx.core.view.WindowCompat

object UiUtils {
    /** 让状态栏/导航栏图标颜色跟随浅色/深色主题，避免白底白图标。 */
    fun applySystemBarAppearance(activity: Activity) {
        val night = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }
}
