package com.chen.classtable

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.chen.classtable.store.CourseStore

/**
 * 自定义节数与每节上课时间。
 * 默认 10 节；可增减到 1..20，并单独修改每一节的开始/结束时间。
 *
 * 响应式规则：
 * - 有效宽度 >= 360dp 且字体不超大：单行布局；
 * - 有效宽度 < 360dp 或系统字体 >= 1.25：两行紧凑布局。
 */
class PeriodTimesActivity : BaseActivity() {

    private lateinit var tvPeriodCount: TextView
    private lateinit var llPeriodTimes: LinearLayout

    private var count: Int = 10
    private val starts = IntArray(AppSettings.MAX_PERIODS)
    private val ends = IntArray(AppSettings.MAX_PERIODS)

    private var onSurface = 0
    private var onVariant = 0
    private var primary = 0
    private var outline = 0
    private var compactMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContentView(R.layout.activity_period_times)
        UiUtils.applySystemBarAppearance(this)
        val table = CourseStore.activeTable(this)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            subtitle = table?.let { "当前课表：${it.name}" } ?: "当前课表：未选择"
            setSubtitleTextColor(
                MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            setNavigationOnClickListener { finish() }
        }

        tvPeriodCount = findViewById(R.id.tvPeriodCount)
        llPeriodTimes = findViewById(R.id.llPeriodTimes)
        count = AppSettings.dayPeriods.coerceIn(1, AppSettings.MAX_PERIODS)
        for (i in 0 until AppSettings.MAX_PERIODS) {
            starts[i] = AppSettings.periodStartMinute(i + 1)
            ends[i] = AppSettings.periodEndMinute(i + 1)
        }
        ensureTimes()

        onSurface = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface)
        onVariant = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        primary = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimary)
        outline = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOutlineVariant)

        findViewById<View>(R.id.btnMinus).setOnClickListener {
            if (count > 1) {
                count--
                render()
            }
        }
        findViewById<View>(R.id.btnPlus).setOnClickListener {
            if (count < AppSettings.MAX_PERIODS) {
                count++
                ensureTimes()
                render()
            }
        }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        compactMode = shouldUseCompact(estimatedContentWidthDp(), resources.configuration.fontScale)
        render()
        llPeriodTimes.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncCompactMode()
        }
    }

    private fun ensureTimes() {
        for (i in 0 until AppSettings.MAX_PERIODS) {
            if (ends[i] <= starts[i] || starts[i] !in 0 until 24 * 60) {
                val prevEnd = if (i > 0) ends[i - 1] else 8 * 60
                starts[i] = (prevEnd + 10).coerceIn(0, 24 * 60 - 2)
                ends[i] = (starts[i] + 45).coerceIn(starts[i] + 1, 24 * 60 - 1)
            }
        }
    }

    private fun render() {
        tvPeriodCount.text = count.toString()
        llPeriodTimes.removeAllViews()
        val compact = useCompactLayout()
        for (i in 0 until count) {
            val row = if (compact) buildCompactRow(i) else buildWideRow(i)
            llPeriodTimes.addView(row)
            if (i < count - 1) {
                llPeriodTimes.addView(View(this).apply {
                    setBackgroundColor(outline)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply { marginStart = dp(40) }
                })
            }
        }
    }

    /** 依据实际内容宽度和系统字体决定是否切换两行布局。 */
    private fun useCompactLayout(): Boolean = compactMode

    private fun shouldUseCompact(contentWidthDp: Float, fontScale: Float): Boolean =
        contentWidthDp < 300f || fontScale >= 1.25f

    /** 估算卡片内容宽度（屏幕宽 - 页面左右 padding）。 */
    private fun estimatedContentWidthDp(): Float {
        val dm = resources.displayMetrics
        return dm.widthPixels / dm.density - 40f
    }

    /** 布局完成后用真实内容宽度再校正一次，兼容分屏/自由窗口。 */
    private fun syncCompactMode() {
        val dm = resources.displayMetrics
        val actual = (llPeriodTimes.width - llPeriodTimes.paddingLeft - llPeriodTimes.paddingRight) / dm.density
        val expected = shouldUseCompact(actual, resources.configuration.fontScale)
        if (expected != compactMode) {
            compactMode = expected
            render()
        }
    }

    private fun buildWideRow(index: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(makeBadge(index))
        row.addView(
            makeLabel(index),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10) }
        )
        val timeGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f)
        }
        timeGroup.addView(makeTimeChip(index, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        timeGroup.addView(makeDash(), LinearLayout.LayoutParams(dp(14), LinearLayout.LayoutParams.WRAP_CONTENT))
        timeGroup.addView(makeTimeChip(index, false), LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(timeGroup)
        return row
    }

    private fun buildCompactRow(index: Int): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(makeBadge(index))
        top.addView(
            makeLabel(index),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10) }
        )
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        bottom.addView(makeTimeChip(index, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(makeDash(), LinearLayout.LayoutParams(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT))
        bottom.addView(makeTimeChip(index, false), LinearLayout.LayoutParams(0, dp(48), 1f))
        outer.addView(top)
        outer.addView(bottom)
        return outer
    }

    private fun makeBadge(index: Int): TextView = TextView(this).apply {
        text = (index + 1).toString()
        gravity = Gravity.CENTER
        setTextColor(primary)
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        background = getDrawable(R.drawable.bg_number_badge)
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
    }

    private fun makeLabel(index: Int): TextView = TextView(this).apply {
        text = "第 ${index + 1} 节"
        setTextColor(onSurface)
        textSize = 14f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(4), 0, 0, 0)
    }

    private fun makeTimeChip(index: Int, isStart: Boolean): TextView = TextView(this).apply {
        text = AppSettings.formatMinutes(if (isStart) starts[index] else ends[index])
        gravity = Gravity.CENTER
        setTextColor(primary)
        setTypeface(null, Typeface.BOLD)
        background = getDrawable(R.drawable.bg_time_chip)
        minHeight = dp(48)
        setPadding(dp(4), 0, dp(4), 0)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, 9, 13, 1, TypedValue.COMPLEX_UNIT_SP
        )
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val current = if (isStart) starts[index] else ends[index]
            pickTime(index, isStart, current)
        }
    }

    private fun makeDash(): TextView = TextView(this).apply {
        text = "–"
        setTextColor(onVariant)
        textSize = 13f
        gravity = Gravity.CENTER
        maxLines = 1
    }

    private fun pickTime(index: Int, isStart: Boolean, current: Int) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current / 60)
            .setMinute(current % 60)
            .setTitleText(if (isStart) "第 ${index + 1} 节开始时间" else "第 ${index + 1} 节结束时间")
            .build()
        picker.addOnPositiveButtonClickListener {
            val minutes = picker.hour * 60 + picker.minute
            if (isStart) {
                starts[index] = minutes.coerceAtMost(24 * 60 - 2)
                if (ends[index] <= starts[index]) {
                    ends[index] = (starts[index] + 45).coerceAtMost(24 * 60 - 1)
                }
            } else {
                ends[index] = minutes
            }
            render()
        }
        picker.show(supportFragmentManager, "period_time_picker")
    }

    private fun save() {
        var invalid = -1
        for (i in 0 until count) {
            if (ends[i] <= starts[i]) {
                invalid = i
                break
            }
        }
        if (invalid >= 0) {
            Toast.makeText(this, "第 ${invalid + 1} 节的结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show()
            return
        }
        for (i in 1 until count) {
            if (starts[i] < ends[i - 1]) {
                Toast.makeText(
                    this,
                    "第 ${i + 1} 节的开始时间不能早于第 $i 节的结束时间",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        val maxUsed = CourseStore.load(this)
            .maxOfOrNull { maxOf(it.startNode, it.endNode) } ?: 0
        if (maxUsed > count) {
            MaterialAlertDialogBuilder(this)
                .setTitle("节数不足")
                .setMessage("当前课表最晚到第 $maxUsed 节，至少需要 $maxUsed 节。请先删除高节次课程，或把节数设置为 $maxUsed 节及以上。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        AppSettings.setPeriodTimes(this, starts, ends)
        AppSettings.setDayPeriods(this, count)
        Toast.makeText(this, "节次与时间已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
