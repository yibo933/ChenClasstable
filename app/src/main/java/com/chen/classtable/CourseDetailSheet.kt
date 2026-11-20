package com.chen.classtable

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.chen.classtable.model.Course
import com.chen.classtable.rule.CoursePalette
import com.chen.classtable.store.CourseStore

/** 课程详情底部面板（真实课程数据）。 */
class CourseDetailSheet(
    private val activity: MainActivity,
    private val course: Course,
    private val onEdit: () -> Unit,
    private val onDelete: () -> Unit,
    private val readOnly: Boolean = false
) {

    fun show() {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.bottom_sheet_course_detail, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val colorBar = view.findViewById<View>(R.id.courseColorBar)
        val title = view.findViewById<TextView>(R.id.tvCourseTitle)
        val weeks = view.findViewById<TextView>(R.id.tvCourseWeeks)
        val day = view.findViewById<TextView>(R.id.tvCourseDay)
        val time = view.findViewById<TextView>(R.id.tvCourseTime)
        val room = view.findViewById<TextView>(R.id.tvCourseRoom)
        val teacher = view.findViewById<TextView>(R.id.tvCourseTeacher)
        val note = view.findViewById<TextView>(R.id.tvCourseNote)
        val scroll = view.findViewById<MaxHeightScrollView>(R.id.sheetScroll)
        val btnEdit = view.findViewById<MaterialButton>(R.id.btnEditCourse)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteCourse)
        val sheetActions = view.findViewById<View>(R.id.sheetActions)
        sheetActions.visibility = if (readOnly) View.GONE else View.VISIBLE

        val dark = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = CoursePalette.pairFor(course.name, AppSettings.accentIndex, dark)
        colorBar.setBackgroundColor(palette.content)

        title.text = course.name
        weeks.text = CourseStore.weekLabel(course)
        day.text = AppSettings.weekdayName(course.day)
        val start = if (course.startTime.isNotBlank()) course.startTime
        else AppSettings.periodStart(course.startNode)
        val end = if (course.endTime.isNotBlank()) course.endTime
        else AppSettings.periodEnd(course.endNode)
        time.text = "第${course.startNode}-${course.endNode}节 · $start - $end"
        room.text = course.room.ifBlank { "未填写地点" }
        teacher.text = course.teacher.ifBlank { "未填写教师" }
        val onVariant = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
        if (course.note.isBlank()) {
            note.text = "暂无备注"
            note.setTextColor(onVariant)
        } else {
            note.text = course.note
            note.setTextColor(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
            )
        }

        btnEdit.setOnClickListener {
            dialog.dismiss()
            onEdit()
        }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true

            val root = view.findViewById<View>(R.id.sheetRoot)
            val baseLeft = root.paddingLeft
            val baseTop = root.paddingTop
            val baseRight = root.paddingRight
            val baseBottom = root.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                )
                v.setPadding(baseLeft, baseTop, baseRight, baseBottom + bars.bottom)
                insets
            }
            root.requestApplyInsets()

            val screenHeight = activity.resources.displayMetrics.heightPixels
            scroll.maxHeightPx = (screenHeight * 0.48f).toInt()
        }
        dialog.show()
    }
}
