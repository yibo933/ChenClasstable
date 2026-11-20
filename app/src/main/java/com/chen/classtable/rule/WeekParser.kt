package com.chen.classtable.rule

import com.chen.classtable.model.WeekBean

/**
 * 周次字符串解析（业务规则）：
 * 全周/单周/双周/单双周、前N周/后N周、第1-16周、1~16、1—16、1至16、
 * 第3周、1-16周(单)、1-16周（双）、1-8,10-16周、第一周至第十六周、1,3,5-9周(单)。
 */
object WeekParser {

    private val RANGE = Regex("([0-9一二三四五六七八九十百]+)\\s*(?:-\\s*([0-9一二三四五六七八九十百]+))?")
    private val PREFIX = Regex("(?:前|后)\\s*([0-9一二三四五六七八九十百]+)\\s*周")

    fun parse(courseWeeks: String?, maxWeek: Int): MutableList<WeekBean> {
        val max = if (maxWeek > 0) maxWeek else 20
        val result = mutableListOf<WeekBean>()
        if (courseWeeks == null) return result
        var raw = courseWeeks.trim()
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace('（', '(')
            .replace('）', ')')
            .replace('—', '-')
            .replace('–', '-')
            .replace('至', '-')
            .replace('~', '-')
        if (raw.isEmpty()) return result

        if (raw.contains("全周") || raw.contains("全学期")) {
            result.add(WeekBean(1, max, 0))
            return result
        }
        if (raw.contains("单双周")) {
            result.add(WeekBean(1, max, 0))
            return result
        }
        if ((raw == "单周" || raw.startsWith("单周")) && !raw.any { it.isDigit() }) {
            result.add(WeekBean(1, max, 1))
            return result
        }
        if ((raw == "双周" || raw.startsWith("双周")) && !raw.any { it.isDigit() }) {
            result.add(WeekBean(2, max, 2))
            return result
        }

        PREFIX.find(raw)?.let { m ->
            val n = ChineseNumber.parse(m.groupValues[1])
            if (n > 0) {
                if (raw.startsWith("后")) result.add(WeekBean(maxOf(1, max - n + 1), max, 0))
                else result.add(WeekBean(1, minOf(n, max), 0))
                return result
            }
        }

        var globalType = 0
        val s = raw.replace("第", "")
        val tokens = s.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
        // 仅单段周次才把“单/双”当作全局类型；多段（如 1-4周,6-8周(双),9-16周）
        // 必须由每一段自己的标记决定，否则会把所有段都误判为双周。
        if (tokens.size == 1) {
            if (raw.contains("单") && !raw.contains("双")) globalType = 1
            if (raw.contains("双") && !raw.contains("单")) globalType = 2
        }
        for (token in tokens) {
            var t = token.trim()
            if (t.isEmpty()) continue
            var type = globalType
            type = when {
                t.contains("单") && t.contains("双") -> 0
                t.contains("单") -> 1
                t.contains("双") -> 2
                else -> type
            }
            t = t.replace(Regex("\\([^)]*\\)"), "")
                .replace("单周", "").replace("双周", "")
                .replace("单", "").replace("双", "")
                .replace("周", "")
            for (m in RANGE.findAll(t)) {
                val a = ChineseNumber.parse(m.groupValues[1])
                var b = if (m.groupValues[2].isEmpty()) a else ChineseNumber.parse(m.groupValues[2])
                if (a <= 0) continue
                if (b < a) b = a
                if (b > max) b = max
                if (a > max) continue
                add(result, WeekBean(a, b, type))
            }
        }
        if (result.isEmpty()) {
            val cleaned = s.replace(Regex("[^0-9一二三四五六七八九十百-]"), " ")
            for (m in RANGE.findAll(cleaned)) {
                val a = ChineseNumber.parse(m.groupValues[1])
                var b = if (m.groupValues[2].isEmpty()) a else ChineseNumber.parse(m.groupValues[2])
                if (b < a) b = a
                if (a > 0) add(result, WeekBean(a, maxOf(a, minOf(b, max)), globalType))
            }
        }
        return result
    }

    private fun add(list: MutableList<WeekBean>, w: WeekBean) {
        for (x in list) {
            if (x.type == w.type && x.start <= w.end + 1 && w.start <= x.end + 1) {
                x.start = minOf(x.start, w.start)
                x.end = maxOf(x.end, w.end)
                return
            }
        }
        list.add(w)
    }

    fun format(weeks: List<WeekBean>): String {
        if (weeks.isEmpty()) return ""
        return weeks.joinToString(",") { w ->
            buildString {
                append(w.start)
                if (w.end != w.start) append("-").append(w.end)
                if (w.type == 1) append("(单)")
                if (w.type == 2) append("(双)")
            }
        }
    }
}
