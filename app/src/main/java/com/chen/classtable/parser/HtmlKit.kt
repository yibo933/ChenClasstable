package com.chen.classtable.parser

import com.chen.classtable.rule.ChineseNumber

/**
 * HTML 表格/文本工具（技术实现）：
 * 解析 table/tr/td、展开 rowspan/colspan、去标签、识别星期/节次。
 */
object HtmlKit {

    data class Cell(val html: String, val text: String, val rowspan: Int, val colspan: Int)

    data class Grid(val rows: MutableList<MutableList<Cell?>>)

    private val TABLE = Regex("<table[^>]*>([\\s\\S]*?)</table>", RegexOption.IGNORE_CASE)
    private val ROW = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
    private val CELL = Regex("<t[dh]([^>]*)>([\\s\\S]*?)</t[dh]>", RegexOption.IGNORE_CASE)
    private val ROWSPAN = Regex("rowspan\\s*=\\s*['\"]?(\\d+)", RegexOption.IGNORE_CASE)
    private val COLSPAN = Regex("colspan\\s*=\\s*['\"]?(\\d+)", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    fun decode(s: String): String {
        var r = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&ldquo;", "“").replace("&rdquo;", "”").replace("&middot;", "·")
            .replace("&mdash;", "—").replace("&hellip;", "…")
        r = Regex("&#(x?)([0-9a-fA-F]+);").replace(r) { m ->
            val cp = if (m.groupValues[1].isEmpty()) m.groupValues[2].toInt()
            else m.groupValues[2].toInt(16)
            String(Character.toChars(cp))
        }
        return r
    }

    fun stripTags(s: String): String =
        decode(TAG.replace(BR.replace(s, "\n"), " ")).replace('\u00a0', ' ').trim()

    fun lines(cellHtml: String): List<String> {
        val s = decode(BR.replace(cellHtml, "\n"))
        return TAG.replace(s, " ").split("\n")
            .map { it.replace('\u00a0', ' ').trim() }
            .filter { it.isNotEmpty() }
    }

    fun tables(html: String): List<Grid> {
        val grids = ArrayList<Grid>()
        for (tm in TABLE.findAll(html)) {
            val grid = parseTable(tm.groupValues[1])
            if (grid.rows.isNotEmpty()) grids.add(grid)
        }
        return grids
    }

    private fun parseTable(tableHtml: String): Grid {
        data class Raw(val html: String, val text: String, val rs: Int, val cs: Int)
        val rawRows = ArrayList<ArrayList<Raw>>()
        for (rm in ROW.findAll(tableHtml)) {
            val cells = ArrayList<Raw>()
            for (cm in CELL.findAll(rm.groupValues[1])) {
                val attrs = cm.groupValues[1]
                val rs = ROWSPAN.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val cs = COLSPAN.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val rawHtml = cm.groupValues[2]
                cells.add(Raw(rawHtml, stripTags(rawHtml), maxOf(1, rs), maxOf(1, cs)))
            }
            if (cells.isNotEmpty()) rawRows.add(cells)
        }
        val grid = Grid(ArrayList())
        for ((r, cells) in rawRows.withIndex()) {
            while (grid.rows.size <= r) grid.rows.add(ArrayList())
            var c = 0
            for (cell in cells) {
                while (c < grid.rows[r].size && grid.rows[r][c] != null) c++
                for (dr in 0 until cell.rs) {
                    val rr = r + dr
                    while (grid.rows.size <= rr) grid.rows.add(ArrayList())
                    for (dc in 0 until cell.cs) {
                        val cc = c + dc
                        while (grid.rows[rr].size <= cc) grid.rows[rr].add(null)
                        grid.rows[rr][cc] = Cell(cell.html, cell.text, cell.rs, cell.cs)
                    }
                }
                c += cell.cs
            }
        }
        for (row in grid.rows) {
            while (row.isNotEmpty() && row.last() == null) row.removeAt(row.size - 1)
        }
        return grid
    }

    fun dayOf(text: String): Int {
        val t = text.replace("星期", "周").replace("礼拜", "周")
        val m = Regex("周([一二三四五六日天1-7])").find(t) ?: return -1
        return when (m.groupValues[1]) {
            "一", "1" -> 1
            "二", "2" -> 2
            "三", "3" -> 3
            "四", "4" -> 4
            "五", "5" -> 5
            "六", "6" -> 6
            "日", "天", "7" -> 7
            else -> -1
        }
    }

    fun nodeOf(text: String): Int {
        Regex("(?:第\\s*)?(\\d+)\\s*节?").find(text)?.let {
            val n = it.groupValues[1].toInt()
            if (n in 1..30) return n
        }
        val cn = ChineseNumber.parse(text)
        return if (cn in 1..30) cn else -1
    }
}
