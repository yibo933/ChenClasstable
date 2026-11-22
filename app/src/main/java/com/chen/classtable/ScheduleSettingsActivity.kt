package com.chen.classtable

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.chen.classtable.store.CourseStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 课表设置（独立 Activity）：开学时间/节次与时间/学期周数/显示周末。 */
class ScheduleSettingsActivity : BaseActivity() {

    private lateinit var tvStartDate: TextView
    private lateinit var tvMaxWeek: TextView
    private lateinit var tvPeriodTime: TextView
    private lateinit var swWeekend: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var swShowTable: com.google.android.material.materialswitch.MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContentView(R.layout.activity_schedule_settings)
        AdaptiveLayoutManager.applyReadableWidth(this, 720)
        UiUtils.applySystemBarAppearance(this)
        val table = CourseStore.activeTable(this)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            subtitle = table?.let { "当前课表：${it.name}" } ?: "当前课表：未选择"
            setSubtitleTextColor(
                MaterialColors.getColor(
                    window.decorView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            setNavigationOnClickListener { finish() }
        }

        tvStartDate = findViewById(R.id.tvStartDate)
        tvMaxWeek = findViewById(R.id.tvMaxWeek)
        tvPeriodTime = findViewById(R.id.tvPeriodTime)
        swWeekend = findViewById(R.id.swWeekend)
        swWeekend.isChecked = AppSettings.showWeekend
        swWeekend.setOnCheckedChangeListener { _, checked ->
            AppSettings.setShowWeekend(this, checked)
        }
        swShowTable = findViewById(R.id.swShowTable)
        swShowTable.isChecked = AppSettings.showScheduleTable
        swShowTable.setOnCheckedChangeListener { _, checked ->
            AppSettings.setShowScheduleTable(this, checked)
        }
        findViewById<View>(R.id.rowShowTable).setOnClickListener {
            swShowTable.isChecked = !swShowTable.isChecked
        }

        findViewById<View>(R.id.rowStartDate).setOnClickListener { pickStartDate() }
        findViewById<View>(R.id.rowMaxWeek).setOnClickListener { pickMaxWeek() }
        findViewById<View>(R.id.rowPeriodTime).setOnClickListener {
            startActivity(Intent(this, PeriodTimesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (::swWeekend.isInitialized) swWeekend.isChecked = AppSettings.showWeekend
        if (::swShowTable.isInitialized) swShowTable.isChecked = AppSettings.showScheduleTable
        tvStartDate.text = SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(AppSettings.startDateMillis)
        tvMaxWeek.text = "${AppSettings.maxWeek} 周"
        tvPeriodTime.text = "${AppSettings.dayPeriods} 节 · ${AppSettings.periodStart(1)} 起"
    }

    private fun pickStartDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = AppSettings.startDateMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val c = Calendar.getInstance()
                c.set(year, month, day, 0, 0, 0)
                c.set(Calendar.MILLISECOND, 0)
                AppSettings.setStartDate(this, c.timeInMillis)
                refresh()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickMaxWeek() {
        val inputLayout = TextInputLayout(this).apply {
            hint = "学期周数（1-60）"
            helperText = "请输入 1 到 60 之间的整数"
        }
        val input = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            setText(AppSettings.maxWeek.toString())
            setSelection(text?.length ?: 0)
            isSingleLine = true
        }
        inputLayout.addView(input)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(inputLayout)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("设置学期周数")
            .setView(box)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val value = input.text?.toString()?.trim()?.toIntOrNull()
                if (value == null || value !in 1..60) {
                    inputLayout.error = "请输入 1-60 之间的整数"
                    return@setOnClickListener
                }
                inputLayout.error = null
                AppSettings.setMaxWeek(this, value)
                refresh()
                dialog.dismiss()
            }
            input.requestFocus()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            input.postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
        dialog.show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
