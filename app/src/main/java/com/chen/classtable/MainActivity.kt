package com.chen.classtable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.chen.classtable.model.Course
import com.chen.classtable.store.CourseStore

/**
 * 主界面（Material 3）：
 *  - 无顶部 WakeUp 栏（edge-to-edge）
 *  - 底部标准 NavigationBar（非悬浮）切换 今日 / 课表 / 设置
 *  - 课表页复刻原 App：第N周 周X + 日期、星期/日期条、时间轴 + 课程网格
 *  - 设置页进入独立 Activity（课表设置 / 外观）
 *  - 预测性返回：非首页返回今日，今日再返回才退出
 */
class MainActivity : BaseActivity() {

    private var page = 0
    private var selectedDay = AppSettings.todayDayOfWeek()
    private var selectedWeek = 1

    private var layoutMode = AdaptiveLayoutManager.Mode.PHONE
    private var currentWidthDp = 0
    private var switchingLayout = false
    private var lastActiveTableId: String? = null

    private lateinit var pages: List<View>
    private lateinit var pageContainer: View
    private var bottomNav: BottomNavigationView? = null

    private var tabletSidebar: View? = null
    private var tabletTableScroll: View? = null
    private var tabletTableList: LinearLayout? = null
    private var tabletTablesLabel: View? = null
    private var tabletAppName: View? = null
    private var tabletNavToday: View? = null
    private var tabletNavSchedule: View? = null
    private var tabletNavSettings: View? = null
    private var tabletNavTodayLabel: TextView? = null
    private var tabletNavScheduleLabel: TextView? = null
    private var tabletNavSettingsLabel: TextView? = null
    private var tabletDetailContainer: View? = null
    private var tabletDetail: TabletDetailPane? = null

    // 今日
    private lateinit var tvTodayDate: TextView
    private lateinit var tvTodayMeta: TextView
    private lateinit var cardEmpty: MaterialCardView
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMsg: TextView
    private lateinit var btnEmptyAction: MaterialButton
    private lateinit var llTodayCourses: LinearLayout

    // 课表
    private lateinit var tvScheduleTitle: TextView
    private lateinit var tvScheduleDate: TextView
    private lateinit var tvScheduleSummary: TextView
    private lateinit var weekStrip: WeekStripView
    private lateinit var swipeLayout: SwipeLayout
    private lateinit var scheduleContent: View
    private lateinit var noTableBox: View
    private lateinit var btnCreateTable: MaterialButton
    private lateinit var scheduleGrid: ScheduleGridView
    private lateinit var scheduleScroll: ScrollView
    private lateinit var emptyOverlay: View
    private lateinit var btnQuickImport: MaterialButton

    private var backCallback: android.window.OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        selectedWeek = savedInstanceState?.getInt("wakeup_week", AppSettings.currentWeekNow())
            ?: AppSettings.currentWeekNow()
        selectedDay = savedInstanceState?.getInt("wakeup_day", AppSettings.todayDayOfWeek())
            ?: AppSettings.todayDayOfWeek()
        lastActiveTableId = CourseStore.activeTable(this)?.id

        val desired = if (AppSettings.darkMode) AppCompatDelegate.MODE_NIGHT_YES
        else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != desired) {
            AppCompatDelegate.setDefaultNightMode(desired)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val width = AdaptiveLayoutManager.windowWidthDp(this)
        layoutMode = AdaptiveLayoutManager.modeFor(width)
        currentWidthDp = width
        setContentView(
            if (AdaptiveLayoutManager.isTablet(layoutMode)) R.layout.activity_main_tablet
            else R.layout.activity_main
        )

        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        bind()
        if (AdaptiveLayoutManager.isTablet(layoutMode)) setupTabletChrome() else setupBottomNav()
        setupSchedulePage()
        setupSettingsPage()
        applyWindowInsets()
        if (AdaptiveLayoutManager.isTablet(layoutMode)) configureTabletMode(layoutMode)
        val initialPage = savedInstanceState?.getInt("wakeup_page", 0)
            ?: if (AdaptiveLayoutManager.isTablet(layoutMode)) 1 else 0
        showPage(initialPage, animate = false)
        registerPredictiveBack()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (switchingLayout || !::pageContainer.isInitialized) return
        val width = AdaptiveLayoutManager.windowWidthDp(this)
        currentWidthDp = width
        val next = AdaptiveLayoutManager.modeFor(width)
        if (next == layoutMode) return
        if (AdaptiveLayoutManager.isTablet(next) != AdaptiveLayoutManager.isTablet(layoutMode)) {
            reapplyLayout(next)
        } else {
            layoutMode = next
            configureTabletMode(next)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("wakeup_page", page)
        outState.putInt("wakeup_week", selectedWeek)
        outState.putInt("wakeup_day", selectedDay)
        super.onSaveInstanceState(outState)
    }

    private fun bind() {
        tabletDetail = null
        tabletDetailContainer = null
        tabletSidebar = null
        tabletTableList = null
        pageContainer = findViewById(R.id.pageContainer)
        pages = listOf(
            findViewById(R.id.pageToday),
            findViewById(R.id.pageSchedule),
            findViewById(R.id.pageSettings)
        )
        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        tvTodayDate = findViewById(R.id.tvTodayDate)
        tvTodayMeta = findViewById(R.id.tvTodayMeta)
        cardEmpty = findViewById(R.id.cardEmpty)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyMsg = findViewById(R.id.tvEmptyMsg)
        btnEmptyAction = findViewById(R.id.btnGoImport)
        llTodayCourses = findViewById(R.id.llTodayCourses)

        tvScheduleTitle = findViewById(R.id.tvScheduleTitle)
        tvScheduleDate = findViewById(R.id.tvScheduleDate)
        tvScheduleSummary = findViewById(R.id.tvScheduleSummary)
        weekStrip = findViewById(R.id.weekStrip)
        swipeLayout = findViewById(R.id.pageSchedule)
        scheduleGrid = findViewById(R.id.scheduleGrid)
        scheduleScroll = findViewById(R.id.scheduleScroll)
        scheduleContent = findViewById(R.id.scheduleContent)
        noTableBox = findViewById(R.id.noTableBox)
        btnCreateTable = findViewById(R.id.btnCreateTable)
        emptyOverlay = findViewById(R.id.emptyOverlay)
        btnQuickImport = findViewById(R.id.btnQuickImport)

        tabletSidebar = findViewById(R.id.tabletSidebar)
        tabletTableScroll = findViewById(R.id.tabletTableScroll)
        tabletTableList = findViewById(R.id.tabletTableList)
        tabletTablesLabel = findViewById(R.id.tabletTablesLabel)
        tabletAppName = findViewById(R.id.tabletAppName)
        tabletNavToday = findViewById(R.id.tabletNavToday)
        tabletNavSchedule = findViewById(R.id.tabletNavSchedule)
        tabletNavSettings = findViewById(R.id.tabletNavSettings)
        tabletNavTodayLabel = findViewById(R.id.tabletNavTodayLabel)
        tabletNavScheduleLabel = findViewById(R.id.tabletNavScheduleLabel)
        tabletNavSettingsLabel = findViewById(R.id.tabletNavSettingsLabel)
        tabletDetailContainer = findViewById(R.id.tabletDetailPane)

        val root = findViewById<View>(R.id.mainRoot)
        root.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            if (switchingLayout) return@addOnLayoutChangeListener
            val widthDp = ((right - left) / resources.displayMetrics.density).toInt()
            if (widthDp > 0) root.post { onWindowWidthChanged(widthDp) }
        }
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.mainRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (AdaptiveLayoutManager.isTablet(layoutMode)) {
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                root.setPadding(0, 0, 0, 0)
                pageContainer.setPadding(0, bars.top, 0, 0)
                bottomNav?.setPadding(0, 0, 0, bars.bottom)
            }
            insets
        }
    }

    // ---------------- 底部导航 ----------------

    private fun setupBottomNav() {
        bottomNav?.setOnItemSelectedListener { item ->
            val index = pageOf(item.itemId)
            if (index != page) showPage(index, syncNav = false) else refreshPage()
            true
        }
    }

    private fun pageOf(itemId: Int): Int = when (itemId) {
        R.id.nav_today -> 0
        R.id.nav_schedule -> 1
        R.id.nav_settings -> 2
        else -> 0
    }

    private fun menuIdOf(index: Int): Int = when (index) {
        1 -> R.id.nav_schedule
        2 -> R.id.nav_settings
        else -> R.id.nav_today
    }

    private fun showPage(index: Int, animate: Boolean = true, syncNav: Boolean = true) {
        val old = pages[page]
        val target = pages[index]
        if (old !== target) {
            if (animate) {
                old.animate().alpha(0f).translationY(-dp(6).toFloat()).setDuration(130)
                    .withEndAction {
                        old.visibility = View.GONE
                        old.alpha = 1f
                        old.translationY = 0f
                    }.start()
                target.alpha = 0f
                target.translationY = dp(10).toFloat()
                target.visibility = View.VISIBLE
                target.animate().alpha(1f).translationY(0f).setDuration(190).start()
            } else {
                pages.forEach { it.visibility = View.GONE }
                target.visibility = View.VISIBLE
            }
        }
        page = index
        updateTabletDetailVisibility()
        if (syncNav) {
            bottomNav?.let { nav ->
                if (nav.selectedItemId != menuIdOf(index)) nav.selectedItemId = menuIdOf(index)
            }
        }
        updateTabletNavigation()
        refreshPage()
    }

    // ---------------- 平板自适应布局 ----------------

    private fun onWindowWidthChanged(widthDp: Int) {
        if (switchingLayout) return
        if (kotlin.math.abs(widthDp - currentWidthDp) < 2) return
        currentWidthDp = widthDp
        val next = AdaptiveLayoutManager.modeFor(widthDp)
        if (next == layoutMode) return
        if (AdaptiveLayoutManager.isTablet(next) != AdaptiveLayoutManager.isTablet(layoutMode)) {
            reapplyLayout(next)
        } else {
            layoutMode = next
            configureTabletMode(next)
        }
    }

    private fun reapplyLayout(mode: AdaptiveLayoutManager.Mode) {
        if (switchingLayout) return
        switchingLayout = true
        val keepPage = page
        val keepWeek = selectedWeek
        val keepDay = selectedDay
        layoutMode = mode
        setContentView(
            if (AdaptiveLayoutManager.isTablet(mode)) R.layout.activity_main_tablet
            else R.layout.activity_main
        )
        bind()
        if (AdaptiveLayoutManager.isTablet(mode)) setupTabletChrome() else setupBottomNav()
        setupSchedulePage()
        setupSettingsPage()
        applyWindowInsets()
        if (AdaptiveLayoutManager.isTablet(mode)) configureTabletMode(mode)
        selectedWeek = keepWeek
        selectedDay = keepDay
        showPage(keepPage, animate = false)
        switchingLayout = false
    }

    private fun setupTabletChrome() {
        tabletNavToday?.setOnClickListener { showPage(0) }
        tabletNavSchedule?.setOnClickListener { showPage(1) }
        tabletNavSettings?.setOnClickListener { showPage(2) }

        val detailRoot = findViewById<View>(R.id.tabletDetailRoot)
        tabletDetail = TabletDetailPane(
            this,
            detailRoot,
            onEdit = { course ->
                val index = CourseStore.load(this).indexOf(course)
                if (index >= 0) openCourseEditor(index)
                else Toast.makeText(this, "课程已变化，请重试", Toast.LENGTH_SHORT).show()
            },
            onDelete = { course -> confirmDeleteCourse(course, selectedWeek) },
            onClose = { updateTabletOverview() }
        )
        configureTabletMode(layoutMode)
        renderTabletTables()
        updateTabletNavigation()
        updateTabletOverview()
    }

    private fun configureTabletMode(mode: AdaptiveLayoutManager.Mode) {
        if (!AdaptiveLayoutManager.isTablet(mode)) return
        val showLabels = mode != AdaptiveLayoutManager.Mode.COMPACT
        val sidebar = tabletSidebar ?: return
        sidebar.layoutParams = (sidebar.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(AdaptiveLayoutManager.sidebarWidthDp(mode))
        }
        tabletAppName?.visibility = if (showLabels) View.VISIBLE else View.GONE
        tabletTablesLabel?.visibility = if (showLabels) View.VISIBLE else View.GONE
        tabletTableScroll?.visibility = if (showLabels) View.VISIBLE else View.GONE
        tabletNavTodayLabel?.visibility = if (showLabels) View.VISIBLE else View.GONE
        tabletNavScheduleLabel?.visibility = if (showLabels) View.VISIBLE else View.GONE
        tabletNavSettingsLabel?.visibility = if (showLabels) View.VISIBLE else View.GONE

        updateTabletDetailVisibility()
        sidebar.requestLayout()
    }

    /** 右侧详情栏只属于课表页；今日/设置页隐藏，让内容区自然扩展。 */
    private fun updateTabletDetailVisibility() {
        val supported = AdaptiveLayoutManager.isTablet(layoutMode) &&
            AdaptiveLayoutManager.showsDetail(layoutMode)
        val visible = supported && page == 1
        tabletDetailContainer?.visibility = if (visible) View.VISIBLE else View.GONE
        if (supported) {
            tabletDetailContainer?.layoutParams =
                (tabletDetailContainer!!.layoutParams as LinearLayout.LayoutParams).apply {
                    width = dp(AdaptiveLayoutManager.detailWidthDp(layoutMode))
                }
        } else {
            tabletDetail?.clear()
        }
    }

    private fun renderTabletTables() {
        val list = tabletTableList ?: return
        val active = CourseStore.activeTable(this)?.id
        val onSurface = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface)
        val activeText = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorOnSecondaryContainer
        )
        val activeBg = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorSecondaryContainer
        )
        list.removeAllViews()
        for (table in CourseStore.tables(this)) {
            val selected = table.id == active
            val row = TextView(this).apply {
                text = table.name
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(10), 0)
                minHeight = dp(44)
                setTextColor(if (selected) activeText else onSurface)
                if (selected) {
                    setBackgroundColor(activeBg)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { if (!selected) activateTable(table.id) }
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply { bottomMargin = dp(2) }
            )
        }
    }

    private fun updateTabletNavigation() {
        val labels = listOf(
            tabletNavToday to tabletNavTodayLabel,
            tabletNavSchedule to tabletNavScheduleLabel,
            tabletNavSettings to tabletNavSettingsLabel
        )
        val onSurface = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface)
        val selectedText = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val selectedBg = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        labels.forEachIndexed { index, pair ->
            val row = pair.first ?: return@forEachIndexed
            val label = pair.second
            val selected = index == page
            row.setBackgroundColor(if (selected) selectedBg else Color.TRANSPARENT)
            label?.setTextColor(if (selected) selectedText else onSurface)
            label?.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun activateTable(id: String) {
        if (CourseStore.activeTable(this)?.id == id) return
        CourseStore.setActive(this, id)
        onActiveTableChanged()
    }

    private fun onActiveTableChanged() {
        AppSettings.load(this)
        selectedWeek = AppSettings.currentWeekNow()
        selectedDay = AppSettings.todayDayOfWeek()
        lastActiveTableId = CourseStore.activeTable(this)?.id
        tabletDetail?.clear()
        refreshSchedule()
        refreshToday()
        renderTabletTables()
        updateTabletOverview()
    }

    private fun showCourseDetails(course: Course) {
        if (page == 1 && AdaptiveLayoutManager.showsDetail(layoutMode) && tabletDetail != null) {
            tabletDetail?.showCourse(course)
            tabletDetailContainer?.visibility = View.VISIBLE
        } else {
            showCourseDetail(course)
        }
    }

    private fun updateTabletOverview() {
        val pane = tabletDetail ?: return
        if (pane.selectedCourse != null) return
        val courses = CourseStore.load(this)
        val now = System.currentTimeMillis()
        val todayWeek = AppSettings.weekForDate(now)
        val today = courses.filter {
            CourseStore.weekActive(it, todayWeek) && it.day == AppSettings.todayDayOfWeek()
        }
        val summary = when {
            courses.isEmpty() -> "还没有课表"
            !AppSettings.semesterStarted() -> "还未开学"
            !hasUpcomingCourse(courses, now) -> "本学期的课程已全部完成"
            today.isEmpty() -> "今天没有课"
            else -> "今天 ${today.size} 节课 · ${today.joinToString("、") { it.name }.take(36)}"
        }
        val start = java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.CHINA)
            .format(java.util.Date(AppSettings.startDateMillis))
        pane.showOverview(
            selectedWeek.coerceIn(1, AppSettings.maxWeek),
            selectedDay,
            "开学 $start · 共 ${AppSettings.maxWeek} 周",
            summary
        )
    }
    private fun refreshPage() {
        when (page) {
            0 -> refreshToday()
            1 -> refreshSchedule()
            else -> Unit
        }
    }

    // ---------------- 今日 ----------------

    private fun refreshToday() {
        AppSettings.load(this)
        val courses = CourseStore.load(this)
        updateTabletOverview()
        val now = System.currentTimeMillis()
        val dow = AppSettings.todayDayOfWeek()
        val rawWeek = AppSettings.rawWeekForDate(now)
        val todayWeek = rawWeek.coerceIn(1, AppSettings.maxWeek)
        val started = AppSettings.semesterStarted()
        tvTodayDate.text = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
            .format(java.util.Date())
        val baseMeta = when {
            !started -> "${AppSettings.weekdayName(dow)} · 还未开学"
            rawWeek > AppSettings.maxWeek -> "${AppSettings.weekdayName(dow)} · 学期已结束"
            else -> "${AppSettings.weekdayName(dow)} · 第 ${rawWeek.coerceAtLeast(1)} 教学周"
        }
        cardEmpty.visibility = View.GONE
        llTodayCourses.removeAllViews()
        if (courses.isEmpty()) {
            tvTodayMeta.text = "$baseMeta · 还没有课表"
            showEmpty("还没有课表", "前往课表创建或导入一个课表", "前往课表")
            return
        }
        if (!started) {
            val start = java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.CHINA)
                .format(java.util.Date(AppSettings.startDateMillis))
            tvTodayMeta.text = "$baseMeta · 等待开学"
            showEmpty("还没开学呢！", "开学时间：$start", "查看课表")
            return
        }
        if (!hasUpcomingCourse(courses, now)) {
            tvTodayMeta.text = "$baseMeta · 全部完成"
            showEmpty("本学期的课程已全部完成", "本学期课表已结束", "查看课表")
            return
        }
        val today = courses
            .filter { CourseStore.weekActive(it, todayWeek) && it.day == dow }
            .sortedBy { it.startNode }
        if (today.isEmpty()) {
            tvTodayMeta.text = "$baseMeta · 今天没有课"
            showEmpty("今天没有课", "去课表看看其他日期的安排", "查看课表")
            return
        }
        tvTodayMeta.text = "$baseMeta · 今天 ${today.size} 节课"
        for (c in today) llTodayCourses.addView(courseRow(c))
    }

    /** 从现在到学期结束，是否还有未结束的课程出现。 */
    private fun hasUpcomingCourse(courses: List<Course>, now: Long): Boolean {
        val end = AppSettings.semesterEndMillis()
        if (now >= end) return false
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while (cal.timeInMillis < end && guard < AppSettings.maxWeek * 7 + 8) {
            val dayStart = cal.timeInMillis
            val week = AppSettings.rawWeekForDate(dayStart)
            if (week in 1..AppSettings.maxWeek) {
                val day = ((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                for (c in courses) {
                    if (c.day != day || !CourseStore.weekActive(c, week)) continue
                    val endMinutes = courseEndMinute(c)
                    if (dayStart + endMinutes * 60_000L > now) return true
                }
            }
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        return false
    }

    private fun courseEndMinute(c: Course): Int {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(c.endTime)
        if (m != null) {
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) return h * 60 + min
        }
        return AppSettings.periodEndMinute(c.endNode)
    }

    private fun showEmpty(title: String, msg: String, action: String) {
        cardEmpty.visibility = View.VISIBLE
        tvEmptyTitle.text = title
        tvEmptyMsg.text = msg
        btnEmptyAction.text = action
        btnEmptyAction.setOnClickListener { showPage(1) }
    }

    private fun courseRow(c: Course): View {
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val palette = com.chen.classtable.rule.CoursePalette
            .pairFor(c.name, AppSettings.accentIndex, dark)
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(palette.container)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        val timeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, dp(8), 0)
            addView(TextView(this@MainActivity).apply {
                text = AppSettings.periodStart(c.startNode)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.content)
            })
            addView(TextView(this@MainActivity).apply {
                text = AppSettings.periodEnd(c.endNode)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(palette.content)
                alpha = 0.68f
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(14), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = c.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.content)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val parts = mutableListOf<String>()
            if (c.room.isNotBlank()) parts.add(c.room)
            if (c.teacher.isNotBlank()) parts.add(c.teacher)
            parts.add("第${c.startNode}-${c.endNode}节")
            parts.add(CourseStore.weekLabel(c))
            addView(TextView(this@MainActivity).apply {
                text = parts.joinToString(" · ")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(palette.content)
                alpha = 0.78f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (c.note.isNotBlank()) {
                addView(TextView(this@MainActivity).apply {
                    text = "备注：${c.note}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(palette.content)
                    alpha = 0.72f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
        }
        row.addView(timeCol)
        row.addView(content)
        card.addView(row)
        // 今日课程卡不再打开详情，备注直接显示在卡片上。
        return card
    }

    // ---------------- 课表 ----------------

    private fun setupSchedulePage() {
        weekStrip.onDaySelected = { day ->
            selectedDay = day
            refreshSchedule()
        }
        swipeLayout.onSwipe = { dir ->
            selectedWeek = (selectedWeek + dir).coerceIn(1, AppSettings.maxWeek)
            refreshSchedule()
        }
        scheduleGrid.onCourseClick = { showCourseDetails(it) }
        scheduleGrid.onEmptyClick = { day, period -> showAddCourseDialog(day, period) }
        findViewById<View>(R.id.btnAdd).setOnClickListener { openImport() }
        btnQuickImport.setOnClickListener { openImport() }
        findViewById<View>(R.id.btnToday).setOnClickListener {
            selectedDay = AppSettings.todayDayOfWeek()
            selectedWeek = AppSettings.currentWeekNow()
            refreshSchedule()
            scrollToCurrentPeriod()
        }
        findViewById<View>(R.id.btnMore).setOnClickListener { showTableManager() }
        btnCreateTable.setOnClickListener { showCreateTableDialog() }
    }

    // ---------------- 多课表管理 ----------------

    private fun showTableManager() {
        val list = CourseStore.tables(this)
        if (list.isEmpty()) {
            showCreateTableDialog()
            return
        }
        val active = CourseStore.activeTable(this)
        val items = list.map { (if (it.id == active?.id) "✓ " else "") + it.name }.toMutableList()
        items.add("＋ 新建课表")
        items.add("🗑 删除当前课表")
        MaterialAlertDialogBuilder(this)
            .setTitle("课表管理")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < list.size -> activateTable(list[which].id)
                    which == list.size -> showCreateTableDialog()
                    else -> confirmDeleteTable()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showCreateTableDialog() {
        val input = EditText(this).apply { hint = "课表名称，如：我的课表" }
        val box = LinearLayout(this).apply {
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("新建课表")
            .setView(box)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "我的课表" }
                CourseStore.createTable(this, name)
                onActiveTableChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteTable() {
        val active = CourseStore.activeTable(this) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("删除课表")
            .setMessage("确定删除「${active.name}」及其全部课程？")
            .setPositiveButton("删除") { _, _ ->
                CourseStore.deleteTable(this, active.id)
                onActiveTableChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun scrollToCurrentPeriod() {
        scheduleScroll.post {
            scheduleScroll.smoothScrollTo(0, scheduleGrid.currentPeriodOffset())
        }
    }

    private var pendingExportTable: CourseStore.TableInfo? = null

    private fun showExportTableDialog() {
        val tables = CourseStore.tables(this)
        if (tables.isEmpty()) {
            Toast.makeText(this, "还没有课表，无法导出", Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        var dialog: AlertDialog? = null
        for (table in tables) {
            val row = TextView(this).apply {
                text = table.name
                textSize = 15f
                setTextColor(MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface))
                setPadding(dp(22), dp(12), dp(22), dp(12))
                isClickable = true
                isFocusable = true
                val out = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                setOnClickListener {
                    dialog?.dismiss()
                    exportTable(table)
                }
            }
            box.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(box) }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("导出课表")
            .setView(scroll)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
    }

    private fun exportTable(table: CourseStore.TableInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingExportTable = table
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_EXPORT_PERMISSION
            )
            return
        }
        try {
            val json = TimetableJson.encode(this, table)
            val name = TimetableExporter.filenameFor(table.name)
            val uri = TimetableExporter.write(this, name, json)
            if (uri == null) {
                Toast.makeText(this, "导出失败：无法写入 Download 目录", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "已导出到 Download/$name", Toast.LENGTH_LONG).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "导出失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_EXPORT_PERMISSION) {
            val table = pendingExportTable
            pendingExportTable = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && table != null) {
                exportTable(table)
            } else {
                Toast.makeText(this, "没有存储权限，无法写入 Download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openImport() {
        startActivity(Intent(this, ImportActivity::class.java))
    }

    private fun refreshSchedule() {
        AppSettings.load(this)
        val hasTable = CourseStore.hasTables(this)
        scheduleContent.visibility = if (hasTable) View.VISIBLE else View.GONE
        noTableBox.visibility = if (hasTable) View.GONE else View.VISIBLE
        if (!hasTable) return

        val table = CourseStore.activeTable(this)
        val courses = CourseStore.load(this)
        val maxPeriod = courses.maxOfOrNull { maxOf(it.startNode, it.endNode) } ?: 0
        if (maxPeriod > AppSettings.dayPeriods) {
            AppSettings.ensurePeriodCapacity(this, maxPeriod)
        }
        if (!AppSettings.showWeekend && selectedDay > 5) selectedDay = 5
        selectedWeek = selectedWeek.coerceIn(1, AppSettings.maxWeek)
        val week = selectedWeek
        tvScheduleTitle.text = "第${week}周 ${AppSettings.weekdayName(selectedDay)}"
        tvScheduleDate.text = "${table?.name ?: ""} · ${AppSettings.fullDate(week, selectedDay)}"
        weekStrip.week = week
        weekStrip.selectedDay = selectedDay
        weekStrip.todayDay = AppSettings.todayDayOfWeek()
        weekStrip.showWeekend = AppSettings.showWeekend
        scheduleGrid.week = week
        scheduleGrid.showWeekend = AppSettings.showWeekend
        scheduleGrid.paletteOffset = AppSettings.accentIndex
        scheduleGrid.courses = courses
        val showTable = AppSettings.showScheduleTable
        scheduleScroll.visibility = View.VISIBLE
        scheduleGrid.showTableGrid = showTable
        weekStrip.compactAxis = false
        emptyOverlay.visibility = if (courses.isEmpty()) View.VISIBLE else View.GONE
        updateScheduleSummary()
        val selected = tabletDetail?.selectedCourse
        if (selected != null && !CourseStore.weekActive(selected, week)) {
            tabletDetail?.clear()
        }
        updateTabletOverview()
    }

    private fun updateScheduleSummary() {
        val courses = CourseStore.load(this)
        val today = AppSettings.todayDayOfWeek()
        val week = AppSettings.currentWeekNow()
        val active = courses
            .filter { CourseStore.weekActive(it, week) && it.day == today }
            .sortedBy { it.startNode }
        if (active.isEmpty()) {
            tvScheduleSummary.text = "今天没有课"
            return
        }
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        fun startOf(c: Course): Int = AppSettings.periodStartMinute(c.startNode)
        fun endOf(c: Course): Int = AppSettings.periodEndMinute(c.endNode)
        val current = active.firstOrNull { now in startOf(it)..endOf(it) }
        val upcoming = active.firstOrNull { now < startOf(it) }
        val suffix = when {
            current != null -> "正在上 · ${current.name}"
            upcoming != null -> "下一节 ${AppSettings.periodStart(upcoming.startNode)} · ${upcoming.name}"
            else -> "今日课程已结束"
        }
        tvScheduleSummary.text = "今天 ${active.size} 节课 · $suffix"
    }

    // ---------------- 课程：详情/删除、空白格添加 ----------------

    private fun showCourseDetail(c: Course, readOnly: Boolean = false) {
        val index = CourseStore.load(this).indexOf(c)
        CourseDetailSheet(
            this,
            c,
            onEdit = {
                if (!readOnly) {
                    if (index >= 0) openCourseEditor(index)
                    else Toast.makeText(this, "课程已变化，请重试", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                if (!readOnly) confirmDeleteCourse(c, selectedWeek)
            },
            readOnly = readOnly
        ).show()
    }

    private fun openCourseEditor(index: Int) {
        tabletDetail?.clear()
        startActivity(
            Intent(this, CourseEditActivity::class.java)
                .putExtra(CourseEditActivity.EXTRA_INDEX, index)
        )
    }

    private fun confirmDeleteCourse(c: Course, week: Int) {
        val onSurface = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorOnSurface
        )
        val onVariant = MaterialColors.getColor(
            window.decorView,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        fun option(title: String, subtitle: String, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(12), dp(22), dp(12))
                isClickable = true
                isFocusable = true
                val out = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                setOnClickListener {
                    dialog?.dismiss()
                    action()
                }
            }
            row.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(onSurface)
            })
            row.addView(TextView(this).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(onVariant)
                setPadding(0, dp(3), 0, 0)
            })
            box.addView(row)
        }

        option("仅删除第 $week 周", "保留其他周次") {
            confirmDeleteScope(c, week, onlyThisWeek = true)
        }
        box.addView(View(this).apply {
            setBackgroundColor(MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOutlineVariant))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })
        option("删除所有周", "删除整个课程，不可撤销") {
            confirmDeleteScope(c, week, onlyThisWeek = false)
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("删除「${c.name}」")
            .setView(box)
            .setNegativeButton("取消", null)
            .create()
        dialog?.show()
    }

    private fun confirmDeleteScope(c: Course, week: Int, onlyThisWeek: Boolean) {
        val title = if (onlyThisWeek) "确认删除本周课程" else "确认删除所有周"
        val message = if (onlyThisWeek) {
            "确定删除「${c.name}」第 $week 周的课程吗？其他周次会保留。"
        } else {
            "确定删除「${c.name}」的所有周次吗？此操作不可撤销。"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确认删除") { _, _ ->
                if (onlyThisWeek) deleteCourseThisWeek(c, week)
                else deleteCourseAllWeeks(c)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteCourseThisWeek(c: Course, week: Int) {
        if (!CourseStore.weekActive(c, week)) {
            Toast.makeText(this, "当前周没有这门课程", Toast.LENGTH_SHORT).show()
            return
        }
        val all = CourseStore.load(this)
        val index = all.indexOf(c)
        if (index < 0) {
            Toast.makeText(this, "课程已变化，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        all.removeAt(index)
        val replacements = mutableListOf<Course>()
        if (c.startWeek < week) {
            val end = week - 1
            if (hasActiveWeek(c.startWeek, end, c.type)) {
                replacements.add(c.copy(endWeek = end))
            }
        }
        if (week < c.endWeek) {
            val start = week + 1
            if (hasActiveWeek(start, c.endWeek, c.type)) {
                replacements.add(c.copy(startWeek = start))
            }
        }
        all.addAll(index, replacements)
        CourseStore.save(this, all)
        tabletDetail?.clear()
        refreshSchedule()
        refreshToday()
        Toast.makeText(this, "已删除第 $week 周", Toast.LENGTH_SHORT).show()
    }

    /** 判断 start..end 内是否还有符合单双周类型的有效周。 */
    private fun hasActiveWeek(start: Int, end: Int, type: Int): Boolean {
        if (start > end) return false
        return when (type) {
            1 -> (start..end).any { it % 2 == 1 }
            2 -> (start..end).any { it % 2 == 0 }
            else -> true
        }
    }

    private fun deleteCourseAllWeeks(c: Course) {
        val all = CourseStore.load(this)
        // “仅删除某周”会把课程拆成多个周次区间；删除所有周必须一并清理这些区间。
        val remaining = all.filterNot { sameCourseSeries(it, c) }
        CourseStore.save(this, remaining)
        tabletDetail?.clear()
        refreshSchedule()
        refreshToday()
        Toast.makeText(this, "已删除所有周", Toast.LENGTH_SHORT).show()
    }

    private fun sameCourseSeries(a: Course, b: Course): Boolean =
        a.name == b.name &&
            a.day == b.day &&
            a.room == b.room &&
            a.teacher == b.teacher &&
            a.startNode == b.startNode &&
            a.endNode == b.endNode &&
            a.credit == b.credit &&
            a.note == b.note &&
            a.startTime == b.startTime &&
            a.endTime == b.endTime

    private fun showAddCourseDialog(day: Int, period: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply { hint = "课程名称" }
        val room = EditText(this).apply { hint = "教室（可选）" }
        val teacher = EditText(this).apply { hint = "教师（可选）" }
        val startSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item,
                (1..AppSettings.dayPeriods).map { "第 $it 节" }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection((period - 1).coerceIn(0, AppSettings.dayPeriods - 1))
        }
        val endSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item,
                (1..AppSettings.dayPeriods).map { "第 $it 节" }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection((period - 1).coerceIn(0, AppSettings.dayPeriods - 1))
        }
        box.addView(name)
        box.addView(room)
        box.addView(teacher)
        box.addView(TextView(this).apply { text = "开始节次" })
        box.addView(startSpinner)
        box.addView(TextView(this).apply { text = "结束节次" })
        box.addView(endSpinner)

        MaterialAlertDialogBuilder(this)
            .setTitle("${AppSettings.weekdayName(day)} · 添加课程")
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("添加") { _, _ ->
                val title = name.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "课程名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val start = startSpinner.selectedItemPosition + 1
                val end = (endSpinner.selectedItemPosition + 1).coerceAtLeast(start)
                val all = CourseStore.load(this)
                all.add(
                    Course(
                        name = title, day = day,
                        room = room.text.toString().trim(),
                        teacher = teacher.text.toString().trim(),
                        startNode = start, endNode = end,
                        startWeek = 1, endWeek = AppSettings.maxWeek, type = 0
                    )
                )
                CourseStore.save(this, all)
                refreshSchedule()
                refreshToday()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------- 设置 ----------------

    private fun setupSettingsPage() {
        findViewById<View>(R.id.cardScheduleSettings).setOnClickListener {
            startActivity(Intent(this, ScheduleSettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardAppearance).setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }
        findViewById<View>(R.id.cardExport).setOnClickListener { showExportTableDialog() }
        findViewById<View>(R.id.cardAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    // ---------------- 返回（预测性） ----------------

    private fun registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = android.window.OnBackInvokedCallback { handleBack() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
        }
    }

    private fun handleBack() {
        if (tabletDetail?.selectedCourse != null) {
            tabletDetail?.clear()
            updateTabletOverview()
            return
        }
        if (page != 0) showPage(0) else finish()
    }

    @Deprecated("Deprecated in API 33")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleBack()
        else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        AppSettings.load(this)
        if (!::pageContainer.isInitialized) return
        val activeId = CourseStore.activeTable(this)?.id
        if (lastActiveTableId != null && activeId != null && activeId != lastActiveTableId) {
            selectedDay = AppSettings.todayDayOfWeek()
            selectedWeek = AppSettings.currentWeekNow()
            tabletDetail?.clear()
        }
        lastActiveTableId = activeId
        renderTabletTables()
        showPage(page, animate = false)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_EXPORT_PERMISSION = 4102
    }
}
