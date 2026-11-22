package com.chen.classtable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 顶部星期/日期条。
 *
 * 布局使用字体实际 metrics 计算高度：星期文字与日期圆之间保留固定间距，
 * 系统字体放大时整个 View 会增高，而不是让“二”和日期圆重叠。
 */
class WeekStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var week: Int = 1
        set(value) { field = value; invalidate() }

    var selectedDay: Int = AppSettings.todayDayOfWeek()
        set(value) { field = value.coerceIn(1, 7); invalidate() }

    var todayDay: Int = AppSettings.todayDayOfWeek()
        set(value) { field = value.coerceIn(1, 7); invalidate() }

    var showWeekend: Boolean = true
        set(value) { field = value; invalidate() }

    /** 隐藏表格时收缩左侧时间轴，让日期与课程列继续对齐。 */
    var compactAxis: Boolean = false
        set(value) { field = value; invalidate() }

    var onDaySelected: ((Int) -> Unit)? = null

    private fun color(attr: Int): Int = MaterialColors.getColor(this, attr)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
        color = color(com.google.android.material.R.attr.colorOnSurfaceVariant)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(15f)
        color = color(com.google.android.material.R.attr.colorOnSurface)
    }
    private val selectedBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(com.google.android.material.R.attr.colorPrimary)
    }
    private val todayDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(com.google.android.material.R.attr.colorPrimary)
    }

    private val edgePadding = dp(6f)
    private val circleRadius = dp(17f)
    private val labelTop = dp(6f)
    private val labelCircleGap = dp(5f)
    private val bottomPadding = dp(5f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val labelBaseline = labelTop - labelPaint.ascent()
        val labelBottom = labelBaseline + labelPaint.descent()
        val circleTop = labelBottom + labelCircleGap
        val desired = (circleTop + circleRadius * 2 + bottomPadding).roundToInt()
        val minHeight = dp(56f).roundToInt()
        val height = resolveSize(max(desired, minHeight), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cols = if (showWeekend) 7 else 5
        val axis = axisWidth()
        val cw = (width - axis - edgePadding) / cols.toFloat()

        // 用字体实际 metrics 推算出文字基线与日期圆心，保证大字体下不重叠。
        val labelBaseline = labelTop - labelPaint.ascent()
        val labelBottom = labelBaseline + labelPaint.descent()
        val circleTop = labelBottom + labelCircleGap
        val centerY = circleTop + circleRadius
        val dateBaseline = centerY - (datePaint.ascent() + datePaint.descent()) / 2f

        for (day in 1..cols) {
            val cx = axis + cw * (day - 0.5f)
            val selected = day == selectedDay
            val today = day == todayDay
            labelPaint.color = if (today && !selected) {
                color(com.google.android.material.R.attr.colorPrimary)
            } else {
                color(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            canvas.drawText(AppSettings.weekdayShort(day), cx, labelBaseline, labelPaint)

            val dateNum = AppSettings.dateOf(week, day)
                .get(java.util.Calendar.DAY_OF_MONTH).toString()
            if (selected) {
                canvas.drawCircle(cx, centerY, circleRadius, selectedBg)
                datePaint.color = color(com.google.android.material.R.attr.colorOnPrimary)
            } else {
                datePaint.color = color(com.google.android.material.R.attr.colorOnSurface)
            }
            canvas.drawText(dateNum, cx, dateBaseline, datePaint)

            if (today && !selected) {
                canvas.drawCircle(cx, centerY + circleRadius * 0.48f, dp(2.2f), todayDot)
            }
        }
    }

    private fun axisWidth(): Float {
        if (compactAxis) return dp(8f)
        val widthDp = (width / resources.displayMetrics.density).toInt()
        return dp(
            when {
                widthDp >= 840 -> 64f
                widthDp >= 600 -> 56f
                else -> 52f
            }
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cols = if (showWeekend) 7 else 5
            val axis = axisWidth()
            val cw = (width - axis - edgePadding) / cols.toFloat()
            if (event.x < axis) return true
            val day = (((event.x - axis) / cw).toInt() + 1).coerceIn(1, cols)
            selectedDay = day
            onDaySelected?.invoke(day)
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
