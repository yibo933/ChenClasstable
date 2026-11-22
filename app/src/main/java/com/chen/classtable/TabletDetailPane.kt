package com.chen.classtable

import android.content.res.Configuration
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.chen.classtable.model.Course
import com.chen.classtable.rule.CoursePalette
import com.chen.classtable.store.CourseStore

/** 平板右侧详情栏：默认显示周次/今日概览，选中课程后显示课程详情。 */
class TabletDetailPane(
    private val activity: MainActivity,
    root: View,
    private val onEdit: (Course) -> Unit,
    private val onDelete: (Course) -> Unit,
    private val onClose: () -> Unit
) {

    private val overview: View = root.findViewById(R.id.detailOverview)
    private val content: View = root.findViewById(R.id.detailCourseContent)
    private val tvWeek: TextView = root.findViewById(R.id.tvDetailPaneTitle)
    private val tvMeta: TextView = root.findViewById(R.id.tvDetailPaneMeta)
    private val tvToday: TextView = root.findViewById(R.id.tvDetailTodaySummary)
    private val colorBar: View = root.findViewById(R.id.detailCourseColor)
    private val tvTitle: TextView = root.findViewById(R.id.tvDetailCourseTitle)
    private val tvWeeks: TextView = root.findViewById(R.id.tvDetailWeeks)
    private val tvTime: TextView = root.findViewById(R.id.tvDetailTime)
    private val tvRoom: TextView = root.findViewById(R.id.tvDetailRoom)
    private val tvTeacher: TextView = root.findViewById(R.id.tvDetailTeacher)
    private val tvNote: TextView = root.findViewById(R.id.tvDetailNote)
    private val btnEdit: MaterialButton = root.findViewById(R.id.btnDetailEdit)
    private val btnDelete: MaterialButton = root.findViewById(R.id.btnDetailDelete)
    private val btnClose: ImageButton = root.findViewById(R.id.btnDetailClose)

    var selectedCourse: Course? = null
        private set

    init {
        btnClose.visibility = View.GONE
        btnClose.setOnClickListener {
            clear()
            onClose()
        }
        btnEdit.setOnClickListener {
            selectedCourse?.let(onEdit)
        }
        btnDelete.setOnClickListener {
            selectedCourse?.let(onDelete)
        }
    }

    fun showOverview(week: Int, day: Int, meta: String, todaySummary: String) {
        selectedCourse = null
        overview.visibility = View.VISIBLE
        content.visibility = View.GONE
        btnClose.visibility = View.GONE
        tvWeek.text = "第 $week 周 · ${AppSettings.weekdayName(day)}"
        tvMeta.text = meta
        tvToday.text = todaySummary
    }

    fun showCourse(course: Course) {
        selectedCourse = course
        overview.visibility = View.GONE
        content.visibility = View.VISIBLE
        btnClose.visibility = View.VISIBLE

        val dark = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = CoursePalette.pairFor(course.name, AppSettings.accentIndex, dark)
        colorBar.setBackgroundColor(palette.content)

        tvTitle.text = course.name
        tvWeeks.text = "${CourseStore.weekLabel(course)} · ${AppSettings.weekdayName(course.day)}"
        val start = if (course.startTime.isNotBlank()) course.startTime
        else AppSettings.periodStart(course.startNode)
        val end = if (course.endTime.isNotBlank()) course.endTime
        else AppSettings.periodEnd(course.endNode)
        tvTime.text = "第${course.startNode}-${course.endNode}节 · $start - $end"
        tvRoom.text = course.room.ifBlank { "未填写地点" }
        tvTeacher.text = course.teacher.ifBlank { "未填写教师" }
        if (course.note.isBlank()) {
            tvNote.text = "暂无备注"
            tvNote.setTextColor(
                MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
        } else {
            tvNote.text = course.note
            tvNote.setTextColor(
                MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurface)
            )
        }
    }

    fun clear() {
        selectedCourse = null
        overview.visibility = View.VISIBLE
        content.visibility = View.GONE
        btnClose.visibility = View.GONE
    }
}
