package com.chen.classtable.parser

import com.chen.classtable.model.Course
import com.chen.classtable.model.WeekBean
import com.chen.classtable.rule.CourseRules
import com.chen.classtable.rule.WeekParser

/**
 * 正方教务：优先解析「列表课表」（kblist_table，星期/节次/课表信息），
 * 列表表不存在时才回退通用「星期 × 节次」网格。
 */
open class ZFParser(source: String, systemName: String = "正方教务") :
    GenericHtmlParser(source, systemName) {

    override fun generateCourseList(): List<Course> {
        val list = ZFListParser.parse(source, maxWeek)
        if (list.isNotEmpty()) return CourseRules.mergeAdjacentNodes(list)
        return super.generateCourseList()
    }
}

/** 新正方 */
class NewZFParser(source: String) : ZFParser(source, "新正方")

/** 金智教务 */
class JZParser(source: String) : GenericHtmlParser(source, "金智教务")

/** 强智教务 */
class QZParser(source: String) : GenericHtmlParser(source, "强智教务")

/** URP 教务 */
class URPParser(source: String) : GenericHtmlParser(source, "URP教务")

/** 青果教务 */
class QGParser(source: String) : GenericHtmlParser(source, "青果教务")

/** 树维教务 */
class SWParser(source: String) : GenericHtmlParser(source, "树维教务")

/** Kingosoft */
class KingosoftParser(source: String) : GenericHtmlParser(source, "Kingosoft")

/** 清华 THU */
class THUParser(source: String) : GenericHtmlParser(source, "清华THU")

/** EAMS：表格 + TaskActivity JS 兜底 */
class EAMSParser(source: String) : GenericHtmlParser(source, "EAMS") {

    override fun generateCourseList(): List<Course> {
        val list = super.generateCourseList()
        if (list.isNotEmpty()) return list
        return parseTaskActivity(source)
    }

    private fun parseTaskActivity(src: String): List<Course> {
        val out = ArrayList<Course>()
        val re = Regex(
            "new\\s+TaskActivity\\s*\\(\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*," +
                "\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*\\)"
        )
        for (m in re.findAll(src)) {
            val name = m.groupValues[1]
            val teacher = m.groupValues[2]
            val room = m.groupValues[3]
            val weeksStr = m.groupValues[4]
            val day = m.groupValues[5].toIntOrNull() ?: continue
            val start = m.groupValues[6].toIntOrNull() ?: continue
            if (name.isEmpty() || day !in 1..7 || start < 1) continue
            val weeks = WeekParser.parse(weeksStr, 20).ifEmpty {
                mutableListOf(WeekBean(1, 20, 0))
            }
            for (w in weeks) {
                out.add(Course(name, day, room, teacher, start, start, w.start, w.end, w.type, 0f, "", "", ""))
            }
        }
        return out
    }
}
