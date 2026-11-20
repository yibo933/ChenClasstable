package com.chen.classtable.store

import android.content.Context
import com.chen.classtable.model.Course
import com.chen.classtable.model.WeekBean
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 多课表存储（SharedPreferences）：
 *  - tables：课表列表（id + 名称）
 *  - activeTable：当前课表 id
 *  - courses_<id>：每个课表的课程
 * 兼容旧版单课表数据（自动迁移为「我的课表」）。
 */
object CourseStore {

    data class TableInfo(val id: String, val name: String)

    private const val PREF = "wakeup_repro"
    private const val KEY_TABLES = "tables"
    private const val KEY_ACTIVE = "activeTable"
    private const val KEY_OLD = "courses"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun coursesKey(id: String) = "courses_$id"

    // ---------------- 课表（多表） ----------------

    fun tables(context: Context): List<TableInfo> {
        migrateIfNeeded(context)
        val text = prefs(context).getString(KEY_TABLES, "") ?: ""
        if (text.isBlank()) return emptyList()
        return text.split("\n").mapNotNull { line ->
            val f = line.split("\t")
            if (f.size < 2) null else TableInfo(f[0], dec(f[1]))
        }
    }

    fun hasTables(context: Context): Boolean = tables(context).isNotEmpty()

    fun activeTable(context: Context): TableInfo? {
        val list = tables(context)
        if (list.isEmpty()) return null
        val id = prefs(context).getString(KEY_ACTIVE, "") ?: ""
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    fun setActive(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACTIVE, id).apply()
    }

    fun createTable(context: Context, name: String): TableInfo {
        migrateIfNeeded(context)
        val list = tables(context).toMutableList()
        val id = System.currentTimeMillis().toString()
        val info = TableInfo(id, name.ifBlank { "我的课表" })
        list.add(info)
        saveTableList(context, list)
        setActive(context, id)
        return info
    }

    fun deleteTable(context: Context, id: String) {
        val list = tables(context).toMutableList()
        list.removeAll { it.id == id }
        saveTableList(context, list)
        prefs(context).edit().remove(coursesKey(id)).apply()
        if (prefs(context).getString(KEY_ACTIVE, "") == id) {
            val next = list.firstOrNull()?.id ?: ""
            prefs(context).edit().putString(KEY_ACTIVE, next).apply()
        }
    }

    private fun saveTableList(context: Context, list: List<TableInfo>) {
        val text = list.joinToString("\n") { "${it.id}\t${enc(it.name)}" }
        prefs(context).edit().putString(KEY_TABLES, text).apply()
    }

    private fun migrateIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.contains(KEY_TABLES)) return
        val old = p.getString(KEY_OLD, "") ?: ""
        val id = System.currentTimeMillis().toString()
        val info = TableInfo(id, "我的课表")
        if (old.isNotEmpty()) {
            p.edit()
                .putString(KEY_TABLES, "${info.id}\t${enc(info.name)}")
                .putString(KEY_ACTIVE, id)
                .putString(coursesKey(id), old)
                .remove(KEY_OLD)
                .apply()
        } else {
            p.edit().putString(KEY_TABLES, "").putString(KEY_ACTIVE, "").apply()
        }
    }

    // ---------------- 课程（当前课表） ----------------

    fun load(context: Context): MutableList<Course> {
        val table = activeTable(context) ?: return mutableListOf()
        val p = prefs(context)
        val text = p.getString(coursesKey(table.id), "") ?: p.getString(KEY_OLD, "") ?: ""
        val out = mutableListOf<Course>()
        if (text.isEmpty()) return out
        for (line in text.split("\n")) {
            if (line.isBlank()) continue
            val f = line.split("\t")
            if (f.size < 9) continue
            runCatching {
                out.add(
                    Course(
                        name = dec(f[0]),
                        day = f[1].toInt(),
                        room = dec(f[2]),
                        teacher = dec(f[3]),
                        startNode = f[4].toInt(),
                        endNode = f[5].toInt(),
                        startWeek = f[6].toInt(),
                        endWeek = f[7].toInt(),
                        type = f[8].toInt(),
                        credit = f.getOrNull(9)?.toFloatOrNull() ?: 0f,
                        note = f.getOrNull(10)?.let { dec(it) } ?: "",
                        startTime = f.getOrNull(11)?.let { dec(it) } ?: "",
                        endTime = f.getOrNull(12)?.let { dec(it) } ?: ""
                    )
                )
            }
        }
        return out
    }

    fun save(context: Context, courses: List<Course>) {
        val table = activeTable(context) ?: createTable(context, "我的课表")
        val text = courses.joinToString("\n") { c ->
            listOf(
                enc(c.name), c.day.toString(), enc(c.room), enc(c.teacher),
                c.startNode.toString(), c.endNode.toString(),
                c.startWeek.toString(), c.endWeek.toString(), c.type.toString(),
                c.credit.toString(), enc(c.note), enc(c.startTime), enc(c.endTime)
            ).joinToString("\t")
        }
        prefs(context).edit().putString(coursesKey(table.id), text).apply()
    }

    fun append(context: Context, courses: List<Course>): Int {
        val all = load(context)
        all.addAll(courses)
        save(context, all)
        return courses.size
    }

    fun clear(context: Context) {
        val table = activeTable(context) ?: return
        prefs(context).edit().remove(coursesKey(table.id)).apply()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

    fun weekActive(c: Course, week: Int): Boolean {
        if (week < c.startWeek || week > c.endWeek) return false
        if (c.type == 1 && week % 2 == 0) return false
        if (c.type == 2 && week % 2 == 1) return false
        return true
    }

    fun weekLabel(c: Course): String = buildString {
        append(c.startWeek)
        if (c.endWeek != c.startWeek) append("-").append(c.endWeek)
        if (c.type == 1) append("(单)")
        if (c.type == 2) append("(双)")
        append("周")
    }

    fun weeksOf(c: Course): List<WeekBean> = listOf(WeekBean(c.startWeek, c.endWeek, c.type))

    /** 示例课表（用于无 HTML 时演示 UI）。 */
    fun demoCourses(): List<Course> = listOf(
        Course("高等数学", 1, "教101", "张老师", 1, 2, 1, 16, 0, 4f),
        Course("高等数学", 3, "教101", "张老师", 1, 2, 1, 16, 0, 4f),
        Course("大学英语", 2, "文科楼302", "李老师", 3, 4, 1, 16, 1, 3f),
        Course("线性代数", 4, "教203", "赵老师", 1, 2, 1, 16, 0, 3f),
        Course("大学物理", 5, "物理楼201", "王老师", 5, 6, 2, 16, 2, 3f),
        Course("数据结构", 3, "实验楼401", "陈老师", 5, 6, 1, 16, 0, 4f),
        Course("体育", 7, "体育馆", "刘老师", 7, 8, 1, 16, 0, 1f)
    )
}
