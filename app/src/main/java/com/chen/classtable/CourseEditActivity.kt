package com.chen.classtable

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.chen.classtable.model.Course
import com.chen.classtable.store.CourseStore

/** 课程编辑/复制页面，全部字段预填真实课程数据。 */
class CourseEditActivity : BaseActivity() {

    companion object {
        const val EXTRA_INDEX = "course_index"
        const val EXTRA_COPY = "course_copy"
    }

    private lateinit var nameInput: TextInputEditText
    private lateinit var roomInput: TextInputEditText
    private lateinit var teacherInput: TextInputEditText
    private lateinit var noteInput: TextInputEditText
    private lateinit var startWeekInput: TextInputEditText
    private lateinit var endWeekInput: TextInputEditText
    private lateinit var daySpinner: Spinner
    private lateinit var startSpinner: Spinner
    private lateinit var endSpinner: Spinner
    private lateinit var typeSpinner: Spinner

    private var index = -1
    private var copyMode = false
    private var origin: Course? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        UiUtils.applySystemBarAppearance(this)
        index = intent.getIntExtra(EXTRA_INDEX, -1)
        copyMode = intent.getBooleanExtra(EXTRA_COPY, false)
        val courses = CourseStore.load(this)
        origin = courses.getOrNull(index)
        if (origin == null) {
            Toast.makeText(this, "课程不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val onSurface = MaterialColors.getColor(
            findViewById(android.R.id.content) ?: window.decorView,
            com.google.android.material.R.attr.colorOnSurface
        )
        val background = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorSurface
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        val toolbar = MaterialToolbar(this).apply {
            title = if (copyMode) "复制课程" else "编辑课程"
            setTitleTextColor(onSurface)
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationIconTint(onSurface)
            setNavigationOnClickListener { finish() }
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (56 * resources.displayMetrics.density).toInt()
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
        }

        nameInput = addTextField(form, "课程名称", origin!!.name)
        roomInput = addTextField(form, "教室（可选）", origin!!.room)
        teacherInput = addTextField(form, "教师（可选）", origin!!.teacher)
        noteInput = addTextField(form, "备注（可选）", origin!!.note)

        daySpinner = addSpinner(
            form, "星期",
            (1..7).map { AppSettings.weekdayName(it) },
            (origin!!.day - 1).coerceIn(0, 6)
        )
        startSpinner = addSpinner(
            form, "开始节次",
            (1..AppSettings.dayPeriods).map { "第 $it 节" },
            (origin!!.startNode - 1).coerceIn(0, AppSettings.dayPeriods - 1)
        )
        endSpinner = addSpinner(
            form, "结束节次",
            (1..AppSettings.dayPeriods).map { "第 $it 节" },
            (origin!!.endNode - 1).coerceIn(0, AppSettings.dayPeriods - 1)
        )
        val weekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        startWeekInput = addCompactField(weekRow, "开始周", origin!!.startWeek.toString())
        weekRow.addView(TextView(this).apply {
            text = "—"
            setTextColor(onSurface)
            setPadding(dp(8), 0, dp(8), 0)
        })
        endWeekInput = addCompactField(weekRow, "结束周", origin!!.endWeek.toString())
        form.addView(weekRow)
        typeSpinner = addSpinner(
            form, "单双周",
            listOf("全周", "单周", "双周"),
            origin!!.type.coerceIn(0, 2)
        )

        val save = MaterialButton(this).apply {
            text = if (copyMode) "保存副本" else "保存修改"
            textSize = 15f
            setOnClickListener { saveCourse() }
        }
        form.addView(
            save,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply { topMargin = dp(20) }
        )
        scroll.addView(form)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)
    }

    private fun addTextField(parent: LinearLayout, hint: String, value: String): TextInputEditText {
        val layout = TextInputLayout(this).apply {
            this.hint = hint
            setPadding(0, 0, 0, 0)
        }
        val input = TextInputEditText(layout.context).apply {
            setText(value)
            setSelection(text?.length ?: 0)
            isSingleLine = true
        }
        layout.addView(input)
        parent.addView(
            layout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        )
        return input
    }

    private fun addCompactField(parent: LinearLayout, hint: String, value: String): TextInputEditText {
        val layout = TextInputLayout(this).apply {
            this.hint = hint
        }
        val input = TextInputEditText(layout.context).apply {
            setText(value)
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            isSingleLine = true
        }
        layout.addView(input)
        parent.addView(
            layout,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return input
    }

    private fun addSpinner(
        parent: LinearLayout,
        label: String,
        values: List<String>,
        selection: Int
    ): Spinner {
        val title = TextView(this).apply {
            text = label
            setTextColor(
                MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(4), dp(4), 0, dp(4))
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CourseEditActivity,
                android.R.layout.simple_spinner_item,
                values
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(selection.coerceIn(0, values.size - 1))
        }
        parent.addView(title)
        parent.addView(
            spinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        )
        return spinner
    }

    private fun saveCourse() {
        val old = origin ?: return
        val name = nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            nameInput.error = "课程名称不能为空"
            return
        }
        val startWeek = startWeekInput.text?.toString()?.toIntOrNull()?.coerceIn(1, AppSettings.maxWeek) ?: old.startWeek
        val endWeek = endWeekInput.text?.toString()?.toIntOrNull()?.coerceIn(1, AppSettings.maxWeek) ?: old.endWeek
        if (endWeek < startWeek) {
            endWeekInput.error = "结束周不能早于开始周"
            return
        }
        val start = startSpinner.selectedItemPosition + 1
        val end = (endSpinner.selectedItemPosition + 1).coerceAtLeast(start)
        val day = daySpinner.selectedItemPosition + 1
        val type = typeSpinner.selectedItemPosition
        val updated = Course(
            name = name,
            day = day,
            room = roomInput.text?.toString()?.trim().orEmpty(),
            teacher = teacherInput.text?.toString()?.trim().orEmpty(),
            startNode = start,
            endNode = end,
            startWeek = startWeek,
            endWeek = endWeek,
            type = type,
            credit = old.credit,
            note = noteInput.text?.toString()?.trim().orEmpty(),
            startTime = old.startTime,
            endTime = old.endTime
        )
        val all = CourseStore.load(this)
        if (copyMode) {
            all.add(updated)
        } else {
            if (index !in all.indices) {
                Toast.makeText(this, "课程已变化，请重试", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            all[index] = updated
        }
        CourseStore.save(this, all)
        Toast.makeText(this, if (copyMode) "已复制课程" else "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
