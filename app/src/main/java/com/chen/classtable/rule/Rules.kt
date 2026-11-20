package com.chen.classtable.rule

import com.chen.classtable.model.Course

/** 中文数字解析（一/二/十/百/千）。 */
object ChineseNumber {
    fun parse(s: String?): Int {
        if (s.isNullOrEmpty()) return -1
        if (s.matches(Regex("\\d+"))) return s.toInt()
        val digits = "零一二三四五六七八九"
        var result = 0
        var number = 0
        for (c in s) {
            val d = digits.indexOf(c)
            when {
                d >= 0 -> number = d
                c == '十' -> {
                    result += (if (number == 0) 1 else number) * 10
                    number = 0
                }
                c == '百' -> {
                    result += (if (number == 0) 1 else number) * 100
                    number = 0
                }
                c == '千' -> {
                    result += (if (number == 0) 1 else number) * 1000
                    number = 0
                }
                c.isDigit() -> return s.filter { it.isDigit() }.toInt()
            }
        }
        result += number
        return if (result > 0) result else -1
    }
}

/** 课程 9 色轮询。 */
object ColorRules {
    val COLORS = arrayOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
        "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE"
    )

    fun colorOf(baseId: Int): String {
        var i = baseId % COLORS.size
        if (i < 0) i += COLORS.size
        return COLORS[i]
    }
}

/** 课程领域规则：节次边界/step/星期/周次。 */
object CourseRules {

    fun fixNodeBounds(startNode: Int, endNode: Int): Pair<Int, Int> {
        var start = startNode
        var end = endNode
        if (start > end) {
            val t = start
            start = end
            end = t
        }
        if (start > 60) start = 1
        if (end > 60) end = start
        if (start < 1) start = 1
        if (end < start) end = start
        return start to end
    }

    fun stepOf(startNode: Int, endNode: Int): Int {
        val (a, b) = fixNodeBounds(startNode, endNode)
        return maxOf(1, b - a + 1)
    }

    fun normalizeDay(day: Int): Int = if (day < 1) 1 else day

    fun normalizeWeek(week: Int): Int = if (week < 1) 1 else week

    /** 同一门课、同一天相邻节次合并（用于解析输出）。 */
    fun mergeAdjacentNodes(courses: List<Course>): List<Course> {
        if (courses.isEmpty()) return courses
        val grouped = LinkedHashMap<String, MutableList<IntArray>>()
        val keys = LinkedHashMap<String, Course>()
        for (c in courses) {
            val key = listOf(c.name, c.day, c.room, c.teacher, c.startWeek, c.endWeek, c.type, c.credit, c.note).joinToString("|")
            grouped.getOrPut(key) { mutableListOf() }.add(intArrayOf(c.startNode, c.endNode))
            keys.putIfAbsent(key, c)
        }
        val out = ArrayList<Course>()
        for ((key, ranges) in grouped) {
            val base = keys.getValue(key)
            ranges.sortBy { it[0] }
            val merged = ArrayList<IntArray>()
            for (r in ranges) {
                val last = merged.lastOrNull()
                if (last == null || last[1] + 1 < r[0]) {
                    merged.add(intArrayOf(r[0], r[1]))
                } else {
                    last[1] = maxOf(last[1], r[1])
                }
            }
            for (m in merged) {
                out.add(base.copy(startNode = m[0], endNode = m[1]))
            }
        }
        return out
    }
}
