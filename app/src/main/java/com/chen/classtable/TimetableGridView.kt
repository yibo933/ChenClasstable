package com.chen.classtable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.chen.classtable.model.Course
import com.chen.classtable.rule.ColorRules
import com.chen.classtable.store.CourseStore

/** 课表网格（自绘）：7 天 × 12 节，按当前周显示课程块。 */
class TimetableGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var courses: List<Course> = emptyList()

    var week: Int = 1
        set(value) {
            field = value
            invalidate()
        }

    /** 是否显示周六/周日列。 */
    var showWeekend: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** 课程配色偏移（对应“颜色设置”主题）。 */
    var paletteOffset: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val gridPaint = Paint().apply {
        color = 0xFFE0E0E0.toInt()
        strokeWidth = dp(0.8f)
        style = Paint.Style.STROKE
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = sp(9f)
        textAlign = Paint.Align.CENTER
    }
    private val courseTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val courseSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEEFFFFFF.toInt()
        textSize = sp(8f)
        textAlign = Paint.Align.CENTER
    }
    private val courseBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setCourses(list: List<Course>) {
        courses = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(34f)
        val top = dp(28f)
        val cols = if (showWeekend) 7 else 5
        val rows = 12
        val cw = (width - left) / cols.toFloat()
        val rh = (height - top) / rows.toFloat()
        if (cw <= 0f || rh <= 0f) return

        val days = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for (i in 0 until cols) {
            canvas.drawText(days[i], left + cw * (i + 0.5f), top * 0.7f, headerPaint)
        }
        for (n in 1..rows) {
            canvas.drawText(n.toString(), left * 0.5f, top + rh * (n - 0.5f) + sp(3f), nodePaint)
        }
        for (i in 0..cols) {
            canvas.drawLine(left + cw * i, top, left + cw * i, height.toFloat(), gridPaint)
        }
        for (r in 0..rows) {
            canvas.drawLine(left, top + rh * r, width.toFloat(), top + rh * r, gridPaint)
        }

        for (c in courses) {
            if (!CourseStore.weekActive(c, week)) continue
            if (!showWeekend && c.day > 5) continue
            val day = c.day.coerceIn(1, cols)
            val start = c.startNode.coerceIn(1, rows)
            val end = c.endNode.coerceIn(start, rows)
            val rect = RectF(
                left + cw * (day - 1) + dp(1.2f),
                top + rh * (start - 1) + dp(1.2f),
                left + cw * day - dp(1.2f),
                top + rh * end - dp(1.2f)
            )
            if (rect.width() < dp(6f) || rect.height() < dp(6f)) continue
            courseBgPaint.color = runCatching {
                Color.parseColor(ColorRules.colorOf(c.name.hashCode() + paletteOffset))
            }.getOrDefault(0xFF6B6B)
            val radius = dp(4f)
            canvas.drawRoundRect(rect, radius, radius, courseBgPaint)

            canvas.save()
            canvas.clipRect(rect)
            val name = TextUtils.ellipsize(
                c.name, courseTextPaint, rect.width() - dp(3f), TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(name, rect.centerX(), rect.top + sp(11f), courseTextPaint)
            var y = rect.top + sp(21f)
            for (line in listOfNotNull(
                c.room.takeIf { it.isNotBlank() },
                c.teacher.takeIf { it.isNotBlank() }
            )) {
                if (y > rect.bottom - dp(1f)) break
                val t = TextUtils.ellipsize(
                    line, courseSubPaint, rect.width() - dp(3f), TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(t, rect.centerX(), y, courseSubPaint)
                y += sp(9f)
            }
            canvas.restore()
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
