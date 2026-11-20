package com.chen.classtable

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 外观（独立 Activity）：深色模式 + 界面缩放/布局密度 + 课程配色主题。 */
class AppearanceActivity : BaseActivity() {

    private lateinit var swDark: MaterialSwitch
    private lateinit var llColors: LinearLayout
    private lateinit var sliderScale: Slider
    private lateinit var tvScaleValue: android.widget.TextView
    private lateinit var toggleDensity: MaterialButtonToggleGroup
    private lateinit var previewCard: MaterialCardView
    private lateinit var tvScaleWarning: android.widget.TextView

    private var currentScale = UiPreferences.DEFAULT_SCALE
    private var currentDensity = UiPreferences.DEFAULT_DENSITY
    private var initializing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContentView(R.layout.activity_appearance)
        UiUtils.applySystemBarAppearance(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        swDark = findViewById(R.id.swDark)
        llColors = findViewById(R.id.llColors)
        sliderScale = findViewById(R.id.sliderScale)
        tvScaleValue = findViewById(R.id.tvScaleValue)
        toggleDensity = findViewById(R.id.toggleDensity)
        previewCard = findViewById(R.id.previewCard)
        tvScaleWarning = findViewById(R.id.tvScaleWarning)

        val snap = UiPreferences.snapshot(this)
        currentScale = snap.scale
        currentDensity = snap.density
        initializing = true
        sliderScale.value = UiPreferences.scaleToStep(currentScale).toFloat()
        when (currentDensity) {
            LayoutDensity.LOOSE -> toggleDensity.check(R.id.btnDensityLoose)
            LayoutDensity.STANDARD -> toggleDensity.check(R.id.btnDensityStandard)
            LayoutDensity.COMPACT -> toggleDensity.check(R.id.btnDensityCompact)
        }
        initializing = false
        updatePreview(currentScale, currentDensity)

        swDark.isChecked = AppSettings.darkMode
        swDark.setOnCheckedChangeListener { _, checked -> AppSettings.setDark(this, checked) }
        buildColors()

        sliderScale.addOnChangeListener { _, value, _ ->
            currentScale = UiPreferences.stepToScale(value.roundToInt())
            updatePreview(currentScale, currentDensity)
        }
        sliderScale.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                if (isHighScale(currentScale) &&
                    currentScale != UiPreferences.snapshot(this@AppearanceActivity).scale
                ) {
                    showHighScaleWarning(currentScale, currentDensity)
                } else {
                    applySettings(currentScale, currentDensity)
                }
            }
        })

        toggleDensity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (initializing || !isChecked) return@addOnButtonCheckedListener
            currentDensity = when (checkedId) {
                R.id.btnDensityLoose -> LayoutDensity.LOOSE
                R.id.btnDensityCompact -> LayoutDensity.COMPACT
                else -> LayoutDensity.STANDARD
            }
            updatePreview(currentScale, currentDensity)
            applySettings(currentScale, currentDensity)
        }

        findViewById<View>(R.id.btnScaleReset).setOnClickListener {
            currentScale = UiPreferences.DEFAULT_SCALE
            currentDensity = UiPreferences.DEFAULT_DENSITY
            initializing = true
            sliderScale.value = UiPreferences.scaleToStep(currentScale).toFloat()
            toggleDensity.check(R.id.btnDensityStandard)
            initializing = false
            updatePreview(currentScale, currentDensity)
            applySettings(currentScale, currentDensity)
        }
    }

    private fun updatePreview(scale: Float, density: LayoutDensity) {
        tvScaleValue.text = UiPreferences.percentText(scale)
        tvScaleWarning.visibility = if (isHighScale(scale)) View.VISIBLE else View.GONE
        val factor = density.factor
        previewCard.scaleX = scale
        previewCard.scaleY = scale
        previewCard.post {
            previewCard.pivotX = previewCard.width / 2f
            previewCard.pivotY = previewCard.height / 2f
        }
        val content = previewCard.getChildAt(0) as? LinearLayout ?: return
        val h = (14 * factor * resources.displayMetrics.density).roundToInt()
        content.setPadding(h, 0, h, 0)
        previewCard.layoutParams = previewCard.layoutParams.apply {
            height = (96 * factor * resources.displayMetrics.density).roundToInt()
        }
        previewCard.requestLayout()
    }

    private fun isHighScale(scale: Float): Boolean = scale >= 1.10f - 0.001f

    private fun showHighScaleWarning(scale: Float, density: LayoutDensity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("高缩放提醒")
            .setMessage(
                "当前缩放为 ${UiPreferences.percentText(scale)}。" +
                    "110% 及以上缩放可能引发部分页面布局异常，请谨慎调整。\n\n是否继续应用？"
            )
            .setPositiveButton("继续应用") { _, _ -> applySettings(scale, density) }
            .setNegativeButton("取消") { _, _ ->
                val applied = UiPreferences.snapshot(this)
                currentScale = applied.scale
                initializing = true
                sliderScale.value = UiPreferences.scaleToStep(applied.scale).toFloat()
                initializing = false
                updatePreview(currentScale, currentDensity)
            }
            .show()
    }

    private fun applySettings(scale: Float, density: LayoutDensity) {
        val normalized = UiPreferences.normalizeScale(scale)
        if (normalized == UiPreferences.snapshot(this).scale &&
            density == UiPreferences.snapshot(this).density
        ) return
        UiPreferences.updateCache(normalized, density)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { UiPreferences.persist(this@AppearanceActivity, normalized, density) }
            }
            recreate()
        }
    }

    private fun buildColors() {
        llColors.removeAllViews()
        val onSurface = MaterialColors.getColor(llColors, com.google.android.material.R.attr.colorOnSurface)
        for (i in AppSettings.ACCENTS.indices) {
            val selected = i == AppSettings.accentIndex
            val swatch = View(this)
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(AppSettings.ACCENTS[i]))
                setStroke(dp(if (selected) 2 else 1), if (selected) onSurface else 0x33000000)
            }
            swatch.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) }
            swatch.setOnClickListener {
                AppSettings.setAccent(this, i)
                buildColors()
            }
            llColors.addView(swatch)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
