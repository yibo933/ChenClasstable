package com.chen.classtable.parser

import com.chen.classtable.model.Course
import com.chen.classtable.model.WeekBean
import com.chen.classtable.rule.WeekParser

/**
 * 正方「列表课表」（kblist_table）解析：
 *  星期 | 节次 | 课表信息
 * 每个星期行后跟若干课程行；节次单元格可能 rowspan 到下一行（下一行只有课程信息）。
 * 课程信息格式：课程名 周数：... 校区:... 上课地点：... 教师 ：... 教学班：... 学分：...
 */
internal object ZFListParser {

    private val TABLE = Regex(
        "<table[^>]*\\bid\\s*=\\s*[\"']?kblist_table[\"']?[^>]*>([\\s\\S]*?)</table>",
        RegexOption.IGNORE_CASE
    )
    private val ROW = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
    private val CELL = Regex("<t[dh]([^>]*)>([\\s\\S]*?)</t[dh]>", RegexOption.IGNORE_CASE)
    private val SPAN = Regex("(\\d+)\\s*[-~至]\\s*(\\d+)")
    private val SINGLE = Regex("^\\d+$")
    private val WEEKS = Regex("周数\\s*[:：]\\s*([^\\s]+)")
    private val ROOM = Regex("上课地点\\s*[:：]\\s*(.*?)\\s+(?:教师|教学班|$)")
    private val TEACHER = Regex("教师\\s*[:：]\\s*(.*?)\\s+(?:教学班|$)")
    private val CREDIT = Regex("学分\\s*[:：]\\s*([0-9.]+)")
    private val SPACE = Regex("\\s+")

    fun parse(source: String, maxWeek: Int): List<Course> {
        val body = TABLE.find(source)?.groupValues?.get(1) ?: return emptyList()
        val out = ArrayList<Course>()
        var day = 0
        var startNode = 0
        var endNode = 0
        for (rm in ROW.findAll(body)) {
            val cells = CELL.findAll(rm.groupValues[1]).map {
                SPACE.replace(HtmlKit.stripTags(it.groupValues[2]), " ").trim()
            }.toList()
            if (cells.isEmpty()) continue

            // 星期行（可能带 rowspan，单元格文本只有“星期一”）
            if (cells.size == 1) {
                val d = HtmlKit.dayOf(cells[0])
                if (d > 0) {
                    day = d
                    startNode = 0
                    endNode = 0
                    continue
                }
            }
            if (day == 0) continue

            val info: String
            if (cells.size >= 2) {
                val first = cells[0]
                val span = SPAN.find(first)
                if (span != null) {
                    startNode = span.groupValues[1].toIntOrNull() ?: 0
                    endNode = span.groupValues[2].toIntOrNull() ?: startNode
                } else if (SINGLE.matches(first)) {
                    startNode = first.toIntOrNull() ?: 0
                    endNode = startNode
                }
                info = cells.last()
            } else {
                info = cells[0]
            }
            if (startNode < 1) continue
            if (endNode < startNode) endNode = startNode
            out.addAll(parseInfo(info, day, startNode, endNode, maxWeek))
        }
        return out
    }

    private fun parseInfo(
        info: String,
        day: Int,
        startNode: Int,
        endNode: Int,
        maxWeek: Int
    ): List<Course> {
        val text = SPACE.replace(info, " ").trim()
        if (text.isEmpty()) return emptyList()
        val name = text.substringBefore("周数").trim()
            .trim('■', '★', '●', '◆', '*', ' ', '\u3000')
        if (name.isEmpty()) return emptyList()

        val weeksText = WEEKS.find(text)?.groupValues?.get(1) ?: ""
        val weeks = WeekParser.parse(weeksText, maxWeek).ifEmpty {
            listOf(WeekBean(1, maxWeek, 0))
        }
        val room = ROOM.find(text)?.groupValues?.get(1)?.trim() ?: ""
        val teacher = TEACHER.find(text)?.groupValues?.get(1)?.trim() ?: ""
        val credit = CREDIT.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return weeks.map {
            Course(
                name = name, day = day, room = room, teacher = teacher,
                startNode = startNode, endNode = endNode,
                startWeek = it.start, endWeek = it.end, type = it.type,
                credit = credit, note = "", startTime = "", endTime = ""
            )
        }
    }
}
