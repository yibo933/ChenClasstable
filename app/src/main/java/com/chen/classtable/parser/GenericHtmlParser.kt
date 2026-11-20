package com.chen.classtable.parser

import com.chen.classtable.model.Course
import com.chen.classtable.model.WeekBean
import com.chen.classtable.rule.CourseRules
import com.chen.classtable.rule.WeekParser

/**
 * 通用教务课表解析器（技术实现）：面向“星期 × 节次”HTML 表格。
 * 各学校解析器只覆盖 systemName，必要时重写 parseJs。
 */
open class GenericHtmlParser(
    protected val source: String,
    val systemName: String = "通用"
) : IParser {

    var maxWeek: Int = 20

    override fun generateCourseList(): List<Course> {
        val out = ArrayList<Course>()
        for (grid in HtmlKit.tables(source)) {
            if (!looksLikeSchedule(grid)) continue
            out.addAll(parseGrid(grid))
        }
        if (out.isEmpty()) out.addAll(parseJs(source))
        return CourseRules.mergeAdjacentNodes(out)
    }

    private fun looksLikeSchedule(grid: HtmlKit.Grid): Boolean {
        if (grid.rows.size < 3) return false
        var maxCols = 0
        var dayHits = 0
        for (row in grid.rows) {
            maxCols = maxOf(maxCols, row.size)
            for (c in row) if (c != null && HtmlKit.dayOf(c.text) > 0) dayHits++
        }
        return maxCols >= 5 && (dayHits >= 2 || maxCols >= 7)
    }

    private fun parseGrid(grid: HtmlKit.Grid): List<Course> {
        val out = ArrayList<Course>()
        var headerRow = -1
        var colDay = LinkedHashMap<Int, Int>()
        for (r in grid.rows.indices) {
            val m = LinkedHashMap<Int, Int>()
            grid.rows[r].forEachIndexed { c, cell ->
                val d = cell?.let { HtmlKit.dayOf(it.text) } ?: -1
                if (d > 0) m[c] = d
            }
            if (m.size >= 2) {
                headerRow = r
                colDay = m
                break
            }
        }
        var assumed = false
        if (headerRow < 0) {
            headerRow = 0
            for (c in 1..7) colDay[c] = c
            assumed = true
        }
        for (r in headerRow + 1 until grid.rows.size) {
            val row = grid.rows[r]
            if (row.isEmpty()) continue
            var node = HtmlKit.nodeOf(row.firstOrNull()?.text ?: "")
            if (node <= 0) node = if (assumed) r else r - headerRow
            val seen = HashSet<HtmlKit.Cell>()
            row.forEachIndexed { c, cell ->
                val day = colDay[c]
                if (day != null && cell != null && cell.text.isNotEmpty() && seen.add(cell)) {
                    out.addAll(parseCell(cell, day, node))
                }
            }
        }
        return out
    }

    private fun parseCell(cell: HtmlKit.Cell, day: Int, node: Int): List<Course> {
        val lines = HtmlKit.lines(cell.html).filter { line ->
            val t = line.trim()
            t.isNotEmpty() && !t.matches(Regex("[\\-—–_\\s]+")) &&
                !t.contains("无课") && !t.contains("课间") && !t.contains("午休") &&
                !t.contains("休息") && !t.contains("晚自习") && !t.contains("空闲")
        }
        if (lines.isEmpty()) return emptyList()
        val name = lines[0]
        var teacher = ""
        var room = ""
        var note = ""
        var credit = 0f
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.contains("学分")) {
                Regex("学分\\s*[:：]?\\s*([0-9.]+)").find(line)?.let {
                    credit = it.groupValues[1].toFloatOrNull() ?: 0f
                }
                continue
            }
            when {
                isRoom(line) -> if (room.isEmpty()) room = line
                teacher.isEmpty() && line.length <= 12 -> teacher = line
                note.isEmpty() -> note = line
            }
        }
        val weeks = WeekParser.parse(cell.text, maxWeek).ifEmpty {
            mutableListOf(WeekBean(1, maxWeek, 0))
        }
        return weeks.map { w ->
            Course(name, day, room, teacher, node, node, w.start, w.end, w.type, credit, note, "", "")
        }
    }

    private fun isRoom(s: String): Boolean =
        listOf("室", "楼", "教", "号", "馆", "场", "机房", "实验").any { s.contains(it) }

    /** JS 数组数据（部分系统课表在 var kbList/TaskActivity 中），基类为空实现。 */
    protected open fun parseJs(src: String): List<Course> = emptyList()
}
