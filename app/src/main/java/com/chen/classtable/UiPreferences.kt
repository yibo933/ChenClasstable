package com.chen.classtable

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LayoutDensity(val factor: Float, val label: String) {
    LOOSE(1.08f, "宽松"),
    STANDARD(1.00f, "标准"),
    COMPACT(0.92f, "紧凑")
}

data class UiSnapshot(val scale: Float, val density: LayoutDensity)

private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(name = "wakeup_ui")

/**
 * 全局 UI 缩放/布局密度配置。
 *
 * DataStore 是唯一持久化来源；内存中保留最后一次快照，
 * 让 Activity.attachBaseContext 能同步拿到配置。
 */
object UiPreferences {

    val SCALE_VALUES = floatArrayOf(
        0.80f, 0.85f, 0.90f, 0.95f, 1.00f, 1.05f, 1.10f, 1.15f, 1.20f
    )
    const val DEFAULT_SCALE = 1.00f
    val DEFAULT_DENSITY = LayoutDensity.STANDARD

    private val SCALE_KEY = floatPreferencesKey("ui_scale")
    private val DENSITY_KEY = stringPreferencesKey("layout_density")

    @Volatile
    private var cached: UiSnapshot? = null

    fun snapshot(context: Context): UiSnapshot {
        cached?.let { return it }
        val prefs = runCatching {
            runBlocking { context.uiDataStore.data.first() }
        }.getOrNull()
        val scale = normalizeScale(prefs?.get(SCALE_KEY) ?: DEFAULT_SCALE)
        val density = runCatching {
            LayoutDensity.valueOf(prefs?.get(DENSITY_KEY) ?: DEFAULT_DENSITY.name)
        }.getOrDefault(DEFAULT_DENSITY)
        return UiSnapshot(scale, density).also { cached = it }
    }

    fun updateCache(scale: Float, density: LayoutDensity) {
        cached = UiSnapshot(normalizeScale(scale), density)
    }

    suspend fun persist(context: Context, scale: Float, density: LayoutDensity) {
        val normalized = normalizeScale(scale)
        updateCache(normalized, density)
        context.uiDataStore.edit {
            it[SCALE_KEY] = normalized
            it[DENSITY_KEY] = density.name
        }
    }

    fun normalizeScale(value: Float): Float {
        val index = SCALE_VALUES.indices.minByOrNull { abs(SCALE_VALUES[it] - value) } ?: 4
        return SCALE_VALUES[index]
    }

    fun scaleToStep(value: Float): Int {
        return SCALE_VALUES.indices.minByOrNull { abs(SCALE_VALUES[it] - value) } ?: 4
    }

    fun stepToScale(step: Int): Float =
        SCALE_VALUES[step.coerceIn(0, SCALE_VALUES.size - 1)]

    fun percentText(value: Float): String =
        "${(normalizeScale(value) * 100).roundToInt()}%"
}
