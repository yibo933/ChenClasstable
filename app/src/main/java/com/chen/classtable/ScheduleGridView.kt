package com.chen.classtable

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import com.chen.classtable.model.Course
import com.chen.classtable.rule.CoursePalette
import com.chen.classtable.store.CourseStore
import kotlin.math.abs

/**
 * 课表网格：左侧时间轴 + 星期列 + 低饱和课程卡 + 当前时间线。
 * 课程数据/周次过滤逻辑不变，只重绘样式。
 */
class ScheduleGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var courses: List<Course> = emptyList()
        set(value) { field = value; invalidate() }

    var week: Int = 1
        set(value) { field = value; invalidate() }

    var showWeekend: Boolean = true
        set(value) { field = value; requestLayout(); invalidate() }

    var paletteOffset: Int = 0

    /** 是否显示表格网格线；关闭后只隐藏表格线，课程卡与时间轴仍然显示。 */
    var showTableGrid: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var onSwipe: ((Int) -> Unit)? = null
    var onCourseClick: ((Course) -> Unit)? = null
    var onEmptyClick: ((Int, Int) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f

    private val dark: Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun color(attr: Int): Int = MaterialColors.getColor(this, attr)

    private val axisPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        color = color(com.google.android.material.R.attr.colorOnSurface)
        isFakeBoldText = true
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(8.5f)
        color = color(com.google.android.material.R.attr.colorOnSurfaceVariant)
    }
    private val gridPaint = Paint().apply {
        color = color(com.google.android.material.R.attr.colorOutlineVariant)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val courseText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(9.5f)
        isFakeBoldText = true
    }
    private val courseSub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(7.8f)
    }
    private val courseWeek = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(7f)
    }
    private val courseNote = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(6.8f)
        isFakeBoldText = true
    }
    private val courseBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(com.google.android.material.R.attr.colorSecondary)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }
    private val nowDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(com.google.android.material.R.attr.colorSecondary)
    }
    private val nowText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = sp(7.5f)
        color = color(com.google.android.material.R.attr.colorSecondary)
        isFakeBoldText = true
    }

    private val rowHeight = dp(58f).toFloat()
    private val axisWidth = dp(52f).toFloat()
    private val edgePadding = dp(6f).toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (rowHeight * AppSettings.dayPeriods).toInt() + 1
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val periods = AppSettings.dayPeriods
        val cols = if (showWeekend) 7 else 5
        val colWidth = (width - axisWidth - edgePadding) / cols.toFloat()

        if (showTableGrid) {
            for (r in 0..periods) {
                canvas.drawLine(axisWidth, rowHeight * r, width - edgePadding, rowHeight * r, gridPaint)
            }
            for (c in 0..cols) {
                canvas.drawLine(
                    axisWidth + colWidth * c, 0f,
                    axisWidth + colWidth * c, rowHeight * periods, gridPaint
                )
            }
            canvas.drawLine(axisWidth, 0f, axisWidth, rowHeight * periods, gridPaint)
        }

        // 时间轴是课程时间上下文，不随表格线一起隐藏。
        for (p in 1..periods) {
            val cy = rowHeight * (p - 1)
            canvas.drawText(p.toString(), axisWidth / 2, cy + sp(17f), axisPaint)
            canvas.drawText(AppSettings.periodStart(p), axisWidth / 2, cy + sp(33f), timePaint)
            canvas.drawText(AppSettings.periodEnd(p), axisWidth / 2, cy + sp(44f), timePaint)
        }

        for (c in courses) {
            if (!CourseStore.weekActive(c, week)) continue
            if (!showWeekend && c.day > 5) continue
            val day = c.day.coerceIn(1, cols)
            val start = c.startNode.coerceIn(1, periods)
            val end = c.endNode.coerceIn(start, periods)
            val rect = RectF(
                axisWidth + colWidth * (day - 1) + dp(2f),
                rowHeight * (start - 1) + dp(2f),
                axisWidth + colWidth * day - dp(2f),
                rowHeight * end - dp(2f)
            )
            if (rect.width() < dp(10f) || rect.height() < dp(14f)) continue
            val colors = CoursePalette.pairFor(c.name, paletteOffset, dark)
            courseBg.color = colors.container
            canvas.drawRoundRect(rect, dp(10f), dp(10f), courseBg)

            canvas.save()
            canvas.clipRect(rect)
            courseText.color = colors.content
            courseSub.color = colors.content
            courseWeek.color = colors.content
            courseNote.color = colors.content
            courseSub.alpha = 190
            courseWeek.alpha = 150
            courseNote.alpha = 210
            val maxTextWidth = rect.width() - dp(5f)
            val narrow = rect.width() < dp(34f)
            courseText.textSize = sp(
                if (narrow) 8.0f else if (c.name.length > 6) 8.2f else 9.4f
            )
            if (narrow) {
                val t = TextUtils.ellipsize(c.name, courseText, maxTextWidth, TextUtils.TruncateAt.END)
                canvas.drawText(t.toString(), rect.centerX(), rect.top + sp(15f), courseText)
                if (c.note.isNotBlank()) {
                    // 窄卡片只显示“备”标记，完整备注在详情面板中展示
                    canvas.drawText("备", rect.right - sp(7f), rect.top + sp(11f), courseNote)
                }
                if (rect.height() >= dp(44f)) {
                    val week = TextUtils.ellipsize(
                        CourseStore.weekLabel(c), courseWeek, maxTextWidth, TextUtils.TruncateAt.END
                    )
                    canvas.drawText(week.toString(), rect.centerX(), rect.bottom - dp(4f), courseWeek)
                }
            } else {
                val nameLines = wrapText(c.name, courseText, maxTextWidth, 2)
                var y = rect.top + sp(13f)
                for (line in nameLines) {
                    if (y > rect.bottom - dp(3f)) break
                    canvas.drawText(line, rect.centerX(), y, courseText)
                    y += sp(11f)
                }
                val meta = mutableListOf<String>()
                if (c.room.isNotBlank()) meta.add(c.room)
                if (c.teacher.isNotBlank()) meta.add(c.teacher)
                for (line in meta) {
                    if (y > rect.bottom - dp(2f)) break
                    val t = TextUtils.ellipsize(line, courseSub, maxTextWidth, TextUtils.TruncateAt.END)
                    canvas.drawText(t.toString(), rect.centerX(), y, courseSub)
                    y += sp(9.5f)
                }
                if (c.note.isNotBlank() && rect.height() >= dp(70f) && y < rect.bottom - dp(14f)) {
                    val noteText = TextUtils.ellipsize(
                        "备注：${c.note}", courseNote, maxTextWidth, TextUtils.TruncateAt.END
                    )
                    canvas.drawText(noteText.toString(), rect.centerX(), y, courseNote)
                    y += sp(9f)
                }
                val weekLabel = CourseStore.weekLabel(c)
                if (rect.height() >= dp(48f) && y < rect.bottom - dp(1f)) {
                    val t = TextUtils.ellipsize(weekLabel, courseWeek, maxTextWidth, TextUtils.TruncateAt.END)
                    canvas.drawText(t.toString(), rect.centerX(), rect.bottom - dp(5f), courseWeek)
                }
            }
            canvas.restore()
        }

        if (showTableGrid) drawCurrentTime(canvas, cols, colWidth, periods)
    }

    private fun drawCurrentTime(canvas: Canvas, cols: Int, colWidth: Float, periods: Int) {
        if (week != AppSettings.currentWeekNow()) return
        val today = AppSettings.todayDayOfWeek()
        if (!showWeekend && today > 5) return
        if (today > cols) return
        val now = currentMinutes()
        if (now < AppSettings.periodStartMinute(1)) return
        var y: Float? = null
        for (p in 1..periods) {
            val start = AppSettings.periodStartMinute(p)
            val end = AppSettings.periodEndMinute(p)
            if (now in start until end) {
                y = rowHeight * (p - 1) +
                    rowHeight * (now - start).toFloat() / (end - start).coerceAtLeast(1)
                break
            }
            if (now < start) {
                y = rowHeight * (p - 1)
                break
            }
        }
        val lineY = y ?: return
        canvas.drawLine(axisWidth, lineY, width - edgePadding, lineY, nowPaint)
        canvas.drawCircle(axisWidth, lineY, dp(3f), nowDot)
        canvas.drawText("现在", width - dp(6f), lineY - dp(4f), nowText)
    }

    private fun currentMinutes(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length && lines.size < maxLines) {
            var end = start + 1
            var lastFit = start + 1
            while (end <= text.length) {
                val candidate = text.substring(start, end)
                if (paint.measureText(candidate) <= maxWidth) {
                    lastFit = end
                    end++
                } else break
            }
            if (lastFit <= start) lastFit = start + 1
            var line = text.substring(start, lastFit)
            if (lastFit < text.length && lines.size == maxLines - 1) {
                line = line.dropLast(1) + "…"
            }
            lines.add(line)
            start = lastFit
        }
        return lines
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > dp(56f) && abs(dx) > abs(dy)) {
                    onSwipe?.invoke(if (dx < 0) 1 else -1)
                    return true
                }
                val day = dayAt(event.x)
                val period = periodAt(event.y)
                if (day != null && period != null) {
                    val hit = courses.firstOrNull {
                        CourseStore.weekActive(it, week) &&
                            it.day == day && period >= it.startNode && period <= it.endNode
                    }
                    if (hit != null) onCourseClick?.invoke(hit)
                    else onEmptyClick?.invoke(day, period)
                    performClick()
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dayAt(x: Float): Int? {
        if (x < axisWidth) return null
        val cols = if (showWeekend) 7 else 5
        val cw = (width - axisWidth - edgePadding) / cols.toFloat()
        val d = ((x - axisWidth) / cw).toInt() + 1
        return d.coerceIn(1, cols)
    }

    private fun periodAt(y: Float): Int? {
        val p = (y / rowHeight).toInt() + 1
        return p.coerceIn(1, AppSettings.dayPeriods)
    }

    fun currentPeriodOffset(): Int {
        val now = currentMinutes()
        var target = 1
        for (p in 1..AppSettings.dayPeriods) {
            val start = AppSettings.periodStartMinute(p)
            val end = AppSettings.periodEndMinute(p)
            if (now in start until end) { target = p; break }
            if (start > now) { target = p; break }
            target = p
        }
        return ((target - 1) * rowHeight).toInt()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
