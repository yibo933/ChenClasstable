package com.chen.classtable

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.chen.classtable.store.CourseStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 应用设置（SharedPreferences 持久化）：周数/开学时间/节数/每节时间/外观/配色。
 *
 * 节次时间已升级为「每节独立可编辑」：
 * - 一天节数默认 10，可设置 1..20；
 * - 每节课有独立的开始/结束时间；
 * - 旧版本只保存“第 1 节开始 + 时长 + 课间”的数据会自动迁移生成每节时间。
 */
object AppSettings {

    private const val PREF = "wakeup_settings"
    const val MAX_PERIODS = 20

    val ACCENTS = arrayOf("#6D7CF0", "#4F9E8F", "#927ED6", "#C58D71", "#7FA7B8", "#8AA37B")

    var darkMode: Boolean = false
        private set
    var showWeekend: Boolean = true
        private set
    var showScheduleTable: Boolean = true
        private set
    var maxWeek: Int = 20
        private set
    var accentIndex: Int = 0
        private set
    var currentWeek: Int = 1
        private set

    /** 开学日期（第 1 周周一，00:00）。 */
    var startDateMillis: Long = defaultStartDate()
        private set

    /** 一天课程节数（1..20）。默认 10。 */
    var dayPeriods: Int = 10
        private set

    // 旧字段保留，供旧数据/旧代码读取；真实时间以 periodStarts/periodEnds 为准。
    var periodStartMinutes: Int = 8 * 60
        private set
    var periodDurationMinutes: Int = 45
        private set
    var breakMinutes: Int = 10
        private set

    private val periodStarts = IntArray(MAX_PERIODS)
    private val periodEnds = IntArray(MAX_PERIODS)
    private var loaded = false
    private var loadedTableId: String? = null
    private var prefix = ""

    fun load(context: Context) {
        val tableId = CourseStore.activeTable(context)?.id ?: ""
        if (loaded && loadedTableId == tableId) return
        loadedTableId = tableId
        prefix = if (tableId.isBlank()) "" else "t_${tableId}_"
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefix.isNotEmpty()) ensureScoped(p)

        // 外观是应用级设置，不随课表切换。
        darkMode = p.getBoolean("dark", false)
        accentIndex = p.getInt("accent", 0)

        // 以下设置按课表隔离。
        showWeekend = p.getBoolean(key("weekend"), true)
        showScheduleTable = p.getBoolean(key("showScheduleTable"), true)
        maxWeek = p.getInt(key("maxWeek"), 20)
        currentWeek = p.getInt(key("week"), 1)
        startDateMillis = p.getLong(key("startDate"), defaultStartDate())

        val legacyStart = p.getInt(key("periodStart"), 8 * 60)
        val legacyDuration = p.getInt(key("periodDuration"), 45).coerceIn(10, 180)
        val legacyBreak = p.getInt(key("breakMinutes"), 10).coerceIn(0, 60)
        generateTimes(legacyStart, legacyDuration, legacyBreak)
        val raw = p.getString(key("periodTimes"), null)
        if (!raw.isNullOrBlank()) parseAndApply(raw)
        val storedCount = p.getInt(key("dayPeriods"), 10)
        dayPeriods = if (raw.isNullOrBlank() && storedCount == 15) 10
        else storedCount.coerceIn(1, MAX_PERIODS)
        syncLegacyFields()
        loaded = true
    }

    private fun key(name: String): String = prefix + name

    private fun edit(context: Context): android.content.SharedPreferences.Editor =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()

    /**
     * 首次为本课表建立独立设置时，继承旧版全局设置，避免升级后设置丢失。
     * 每个课表只迁移一次，之后互不影响。
     */
    private fun ensureScoped(p: android.content.SharedPreferences) {
        if (p.getBoolean(prefix + "initialized", false)) return
        val e = p.edit()
        if (p.contains("weekend")) e.putBoolean(prefix + "weekend", p.getBoolean("weekend", true))
        if (p.contains("showScheduleTable")) {
            e.putBoolean(prefix + "showScheduleTable", p.getBoolean("showScheduleTable", true))
        }
        if (p.contains("maxWeek")) e.putInt(prefix + "maxWeek", p.getInt("maxWeek", 20))
        if (p.contains("week")) e.putInt(prefix + "week", p.getInt("week", 1))
        if (p.contains("startDate")) e.putLong(prefix + "startDate", p.getLong("startDate", defaultStartDate()))
        if (p.contains("periodStart")) e.putInt(prefix + "periodStart", p.getInt("periodStart", 8 * 60))
        if (p.contains("periodDuration")) e.putInt(prefix + "periodDuration", p.getInt("periodDuration", 45))
        if (p.contains("breakMinutes")) e.putInt(prefix + "breakMinutes", p.getInt("breakMinutes", 10))
        if (p.contains("dayPeriods")) e.putInt(prefix + "dayPeriods", p.getInt("dayPeriods", 10))
        if (p.contains("periodTimes")) e.putString(prefix + "periodTimes", p.getString("periodTimes", null))
        e.putBoolean(prefix + "initialized", true).apply()
    }

    fun setDark(context: Context, enabled: Boolean) {
        darkMode = enabled
        edit(context).putBoolean("dark", enabled).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun setShowWeekend(context: Context, enabled: Boolean) {
        showWeekend = enabled
        edit(context).putBoolean(key("weekend"), enabled).apply()
    }

    fun setShowScheduleTable(context: Context, enabled: Boolean) {
        showScheduleTable = enabled
        edit(context).putBoolean(key("showScheduleTable"), enabled).apply()
    }

    fun setMaxWeek(context: Context, value: Int) {
        maxWeek = value.coerceIn(1, 60)
        if (currentWeek > maxWeek) currentWeek = maxWeek
        edit(context).putInt(key("maxWeek"), maxWeek).putInt(key("week"), currentWeek).apply()
    }

    fun setAccent(context: Context, index: Int) {
        accentIndex = if (index in ACCENTS.indices) index else 0
        edit(context).putInt("accent", accentIndex).apply()
    }

    fun setWeek(context: Context, week: Int) {
        currentWeek = week.coerceIn(1, maxWeek)
        edit(context).putInt(key("week"), currentWeek).apply()
    }

    fun setStartDate(context: Context, millis: Long) {
        startDateMillis = millis
        edit(context).putLong(key("startDate"), millis).apply()
    }

    /** 设置一天节数（1..20），缺失的时间会自动补齐。 */
    fun setDayPeriods(context: Context, count: Int) {
        dayPeriods = count.coerceIn(1, MAX_PERIODS)
        syncLegacyFields()
        edit(context)
            .putInt(key("dayPeriods"), dayPeriods)
            .putString(key("periodTimes"), encodeTimes())
            .putInt(key("periodStart"), periodStartMinutes)
            .putInt(key("periodDuration"), periodDurationMinutes)
            .putInt(key("breakMinutes"), breakMinutes)
            .apply()
    }

    /**
     * 保存每节课的起止时间。starts/ends 至少包含 dayPeriods 项；
     * 不合法项会自动修正（结束必须晚于开始）。
     */
    fun setPeriodTimes(context: Context, starts: IntArray, ends: IntArray) {
        for (i in 0 until MAX_PERIODS) {
            val s = starts.getOrElse(i) { periodStarts[i] }.coerceIn(0, 24 * 60 - 2)
            val e = ends.getOrElse(i) { periodEnds[i] }.coerceIn(0, 24 * 60 - 1)
            periodStarts[i] = s
            periodEnds[i] = if (e > s) e else (s + 45).coerceAtMost(24 * 60 - 1)
        }
        syncLegacyFields()
        edit(context)
            .putString(key("periodTimes"), encodeTimes())
            .putInt(key("periodStart"), periodStartMinutes)
            .putInt(key("periodDuration"), periodDurationMinutes)
            .putInt(key("breakMinutes"), breakMinutes)
            .apply()
    }

    /** 旧接口：按“第 1 节开始 + 时长 + 课间”重新生成全部节次时间。 */
    fun setPeriodTime(context: Context, startMinutes: Int, durationMinutes: Int, breakMinutes: Int) {
        generateTimes(
            startMinutes.coerceIn(0, 23 * 60),
            durationMinutes.coerceIn(10, 180),
            breakMinutes.coerceIn(0, 60)
        )
        syncLegacyFields()
        edit(context)
            .putInt(key("periodStart"), periodStartMinutes)
            .putInt(key("periodDuration"), periodDurationMinutes)
            .putInt(key("breakMinutes"), this.breakMinutes)
            .putString(key("periodTimes"), encodeTimes())
            .apply()
    }

    /** 课表数据晚于当前节数时自动扩展，避免课程被截断/重叠。 */
    fun ensurePeriodCapacity(context: Context, maxPeriod: Int): Boolean {
        if (maxPeriod <= dayPeriods) return false
        dayPeriods = maxPeriod.coerceIn(1, MAX_PERIODS)
        syncLegacyFields()
        edit(context)
            .putInt(key("dayPeriods"), dayPeriods)
            .putString(key("periodTimes"), encodeTimes())
            .apply()
        return true
    }
    data class TableSettings(
        val startDateMillis: Long,
        val maxWeek: Int,
        val dayPeriods: Int,
        val periodStarts: IntArray,
        val periodEnds: IntArray,
        val showWeekend: Boolean,
        val showScheduleTable: Boolean
    )

    /** 读取任意课表的独立设置，不切换当前课表。 */
    fun readTableSettings(context: Context, tableId: String): TableSettings {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val prefix = if (tableId.isBlank()) "" else "t_${tableId}_"
        fun int(key: String, def: Int): Int =
            p.getInt(prefix + key, p.getInt(key, def))
        fun long(key: String, def: Long): Long =
            p.getLong(prefix + key, p.getLong(key, def))
        fun bool(key: String, def: Boolean): Boolean =
            p.getBoolean(prefix + key, p.getBoolean(key, def))

        val maxWeek = int("maxWeek", 20).coerceIn(1, 60)
        val dayPeriods = int("dayPeriods", 10).coerceIn(1, MAX_PERIODS)
        val starts = IntArray(MAX_PERIODS)
        val ends = IntArray(MAX_PERIODS)
        val legacyStart = int("periodStart", 8 * 60)
        val duration = int("periodDuration", 45).coerceIn(10, 180)
        val breakMin = int("breakMinutes", 10).coerceIn(0, 60)
        var s = legacyStart
        for (i in 0 until MAX_PERIODS) {
            starts[i] = s
            ends[i] = (s + duration).coerceAtMost(24 * 60 - 1)
            s += duration + breakMin
        }
        val raw = p.getString(prefix + "periodTimes", p.getString("periodTimes", null))
        if (!raw.isNullOrBlank()) {
            val parts = raw.split(';')
            for (i in 0 until MAX_PERIODS) {
                val pair = parts.getOrNull(i)?.split(',')
                val ps = pair?.getOrNull(0)?.trim()?.toIntOrNull()
                val pe = pair?.getOrNull(1)?.trim()?.toIntOrNull()
                if (ps != null && pe != null && ps in 0 until 24 * 60 && pe in (ps + 1) until 24 * 60) {
                    starts[i] = ps
                    ends[i] = pe
                }
            }
        }
        return TableSettings(
            startDateMillis = long("startDate", defaultStartDate()),
            maxWeek = maxWeek,
            dayPeriods = dayPeriods,
            periodStarts = starts,
            periodEnds = ends,
            showWeekend = bool("weekend", true),
            showScheduleTable = bool("showScheduleTable", true)
        )
    }

    /** 将导出文件中的设置写回指定课表，不要求该课表当前处于激活状态。 */
    fun saveTableSettings(context: Context, tableId: String, s: TableSettings) {
        val prefix = "t_${tableId}_"
        val count = s.dayPeriods.coerceIn(1, MAX_PERIODS)
        val encoded = (0 until MAX_PERIODS).joinToString(";") { i ->
            val start = s.periodStarts.getOrElse(i) { 8 * 60 }.coerceIn(0, 24 * 60 - 2)
            val end = s.periodEnds.getOrElse(i) { start + 45 }.coerceIn(start + 1, 24 * 60 - 1)
            "$start,$end"
        }
        val firstStart = s.periodStarts.getOrElse(0) { 8 * 60 }
        val firstEnd = s.periodEnds.getOrElse(0) { firstStart + 45 }
        val secondStart = s.periodStarts.getOrElse(1) { firstEnd + 10 }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(prefix + "initialized", true)
            .putLong(prefix + "startDate", s.startDateMillis)
            .putInt(prefix + "maxWeek", s.maxWeek.coerceIn(1, 60))
            .putInt(prefix + "week", 1)
            .putInt(prefix + "dayPeriods", count)
            .putString(prefix + "periodTimes", encoded)
            .putInt(prefix + "periodStart", firstStart)
            .putInt(prefix + "periodDuration", (firstEnd - firstStart).coerceAtLeast(1))
            .putInt(prefix + "breakMinutes", (secondStart - firstEnd).coerceAtLeast(0))
            .putBoolean(prefix + "weekend", s.showWeekend)
            .putBoolean(prefix + "showScheduleTable", s.showScheduleTable)
            .apply()
    }
    fun accentColor(): Int = Color.parseColor(ACCENTS[accentIndex])

    // ---------------- 每节时间 ----------------

    fun periodStartMinute(index: Int): Int =
        periodStarts[(index - 1).coerceIn(0, MAX_PERIODS - 1)]

    fun periodEndMinute(index: Int): Int =
        periodEnds[(index - 1).coerceIn(0, MAX_PERIODS - 1)]

    fun periodEndMinutes(index: Int): Int = periodEndMinute(index)

    private fun generateTimes(start: Int, duration: Int, breakMin: Int) {
        var s = start
        for (i in 0 until MAX_PERIODS) {
            periodStarts[i] = s
            periodEnds[i] = (s + duration).coerceAtMost(24 * 60 - 1)
            s += duration + breakMin
        }
    }

    private fun parseAndApply(raw: String): Boolean {
        val parts = raw.split(';').filter { it.isNotBlank() }
        if (parts.isEmpty()) return false
        for (i in 0 until MAX_PERIODS) {
            val p = parts.getOrNull(i)?.split(',')
            if (p != null && p.size >= 2) {
                val s = p[0].trim().toIntOrNull() ?: return false
                val e = p[1].trim().toIntOrNull() ?: return false
                if (s !in 0 until 24 * 60 || e !in 0 until 24 * 60 || e <= s) return false
                periodStarts[i] = s
                periodEnds[i] = e
            } else {
                val prevEnd = if (i > 0) periodEnds[i - 1] else 8 * 60
                periodStarts[i] = (prevEnd + 10).coerceAtMost(24 * 60 - 1)
                periodEnds[i] = (periodStarts[i] + 45).coerceAtMost(24 * 60 - 1)
            }
        }
        return true
    }

    private fun encodeTimes(): String = (0 until MAX_PERIODS).joinToString(";") {
        "${periodStarts[it]},${periodEnds[it]}"
    }

    private fun syncLegacyFields() {
        periodStartMinutes = periodStarts[0]
        periodDurationMinutes = (periodEnds[0] - periodStarts[0]).coerceAtLeast(1)
        breakMinutes = (periodStarts[1] - periodEnds[0]).coerceAtLeast(0)
    }

    // ---------------- 时间计算 ----------------

    private fun defaultStartDate(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -dow)
        return cal.timeInMillis
    }

    fun weekStart(week: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDateMillis
        cal.add(Calendar.DAY_OF_MONTH, (week - 1) * 7)
        return cal
    }

    fun dateOf(week: Int, day: Int): Calendar {
        val cal = weekStart(week)
        cal.add(Calendar.DAY_OF_MONTH, day - 1)
        return cal
    }

    fun shortDate(week: Int, day: Int): String =
        SimpleDateFormat("M/d", Locale.CHINA).format(dateOf(week, day).time)

    fun fullDate(week: Int, day: Int): String =
        SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(dateOf(week, day).time)

    fun todayDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    }

    /** 不限制到 1..maxWeek 的真实周序号：开学前 <=0，超出学期 >maxWeek。 */
    fun rawWeekForDate(millis: Long): Int {
        val start = Calendar.getInstance().apply {
            timeInMillis = startDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val target = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val days = ((target.timeInMillis - start.timeInMillis) / 86_400_000L).toInt()
        return Math.floorDiv(days, 7) + 1
    }

    fun weekForDate(millis: Long): Int =
        rawWeekForDate(millis).coerceIn(1, maxWeek)

    fun currentWeekNow(): Int = weekForDate(System.currentTimeMillis())

    fun semesterStarted(): Boolean = System.currentTimeMillis() >= startDateMillis

    fun semesterEndMillis(): Long =
        startDateMillis + maxWeek * 7L * 86_400_000L

    fun weekdayName(day: Int): String =
        arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(day - 1) { "周一" }

    fun weekdayShort(day: Int): String =
        arrayOf("一", "二", "三", "四", "五", "六", "日")
            .getOrElse(day - 1) { "一" }

    /** 第 index 节（1-based）开始时间，格式 HH:mm。 */
    fun periodStart(index: Int): String = formatMinutes(periodStartMinute(index))

    fun periodEnd(index: Int): String = formatMinutes(periodEndMinute(index))

    fun formatMinutes(minutes: Int): String {
        val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
        return String.format(Locale.CHINA, "%02d:%02d", m / 60, m % 60)
    }

    fun currentPeriodStartMinutes(): Int = periodStartMinute(1)

    fun applySavedNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
