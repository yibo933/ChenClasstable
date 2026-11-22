package com.chen.classtable

import com.chen.classtable.model.Course
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * 轻量 PDF 课表解析器。
 * 只读取 PDF 内容流里的文本和坐标，不执行 PDF、不依赖第三方 PDF 引擎。
 *
 * 支持本应用测试样本的两种版式：
 * 1. 星期横向排列（一周 7 列）；
 * 2. 星期纵向排列（星期为行，节次为列）。
 */
object PdfTimetableParser {

    private const val EPS = 0.5f
    private val weekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    private val weekdayRegex = Regex("(\\d{1,2})\\s*[-~]\\s*(\\d{1,2})\\s*节")

    private data class Item(val x: Float, val y: Float, val text: String)

    fun parse(bytes: ByteArray, maxWeek: Int): List<Course> {
        require(bytes.size <= 20 * 1024 * 1024) { "PDF 文件过大（最大 20MB）" }
        val pages = extractPages(bytes)
        require(pages.isNotEmpty()) { "PDF 中没有可读取的文本页面" }

        val headers = pages.flatten().filter { it.text in weekdays }
        require(headers.size >= 2) { "PDF 中没有识别到星期信息" }
        val horizontal = headers.map { it.x.toInt() }.distinct().size >= 3
        val courses = if (horizontal) parseHorizontal(pages, maxWeek)
        else parseVertical(pages, maxWeek)

        val unique = LinkedHashMap<String, Course>()
        for (c in courses) {
            val key = listOf(
                c.name, c.day, c.startNode, c.endNode, c.startWeek, c.endWeek, c.type
            ).joinToString("|")
            unique.putIfAbsent(key, c)
        }
        val result = unique.values.toList()
        require(result.isNotEmpty()) { "PDF 中没有解析到课程" }
        return result
    }

    // ---------------- PDF 内容流 ----------------

    private fun extractPages(bytes: ByteArray): List<List<Item>> {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val out = mutableListOf<List<Item>>()
        var from = 0
        var count = 0
        while (count < 80) {
            val streamAt = raw.indexOf("stream", from)
            if (streamAt < 0) break
            var dataStart = streamAt + 6
            if (dataStart < raw.length && raw[dataStart] == '\r') dataStart++
            if (dataStart < raw.length && raw[dataStart] == '\n') dataStart++
            val end = raw.indexOf("endstream", dataStart)
            if (end < 0) break
            val encoded = raw.substring(dataStart, end).trimEnd('\r', '\n', ' ').toByteArray(Charsets.ISO_8859_1)
            val inflated = inflate(encoded)
            if (inflated != null) {
                val content = String(inflated, Charsets.ISO_8859_1)
                if (content.contains("Tj") || content.contains("TJ")) {
                    val items = parseItems(content)
                    if (items.isNotEmpty()) out.add(items)
                }
            }
            from = end + 9
            count++
        }
        return out
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    continue
                }
                out.write(buffer, 0, n)
            }
            if (out.size() == 0) null else out.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun parseItems(content: String): List<Item> {
        val items = mutableListOf<Item>()
        var x = 0f
        var y = 0f
        for (line in content.split('\n')) {
            val tm = Regex("([-\\d.]+) ([-\\d.]+) ([-\\d.]+) ([-\\d.]+) ([-\\d.]+) ([-\\d.]+) Tm")
                .find(line)
            if (tm != null) {
                x = tm.groupValues[5].toFloatOrNull() ?: x
                y = tm.groupValues[6].toFloatOrNull() ?: y
            }
            val td = Regex("([-\\d.]+) ([-\\d.]+) Td").find(line)
            if (td != null) {
                x += td.groupValues[1].toFloatOrNull() ?: 0f
                y += td.groupValues[2].toFloatOrNull() ?: 0f
            }
            if (line.contains("Tj") || line.contains("TJ")) {
                for (literal in pdfStrings(line)) {
                    val text = decodeText(literal).replace("\u0000", "").trim()
                    if (text.isNotEmpty()) items.add(Item(x, y, text))
                }
            }
        }
        return items
    }

    private fun pdfStrings(line: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] != '(') {
                i++
                continue
            }
            i++
            val buf = StringBuilder()
            while (i < line.length && line[i] != ')') {
                if (line[i] == '\\') {
                    buf.append(line[i])
                    i++
                    if (i < line.length) {
                        buf.append(line[i])
                        i++
                    }
                } else {
                    buf.append(line[i])
                    i++
                }
            }
            result.add(buf.toString())
            i++
        }
        return result
    }

    private fun decodeText(literal: String): String {
        val bytes = unescapeLiteral(literal)
        if (bytes.size < 2 || bytes.size % 2 != 0) {
            return bytes.toString(Charsets.ISO_8859_1)
        }
        return runCatching { bytes.toString(Charsets.UTF_16BE) }
            .getOrElse { bytes.toString(Charsets.ISO_8859_1) }
    }

    private fun unescapeLiteral(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') {
                out.write(c.code)
                i++
                continue
            }
            i++
            if (i >= s.length) break
            when (val e = s[i]) {
                'n' -> out.write('\n'.code)
                'r' -> out.write('\r'.code)
                't' -> out.write('\t'.code)
                'b' -> out.write('\b'.code)
                'f' -> out.write('\u000C'.code)
                '(', ')', '\\' -> out.write(e.code)
                '\r', '\n' -> {
                    if (e == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                }
                in '0'..'7' -> {
                    var oct = ""
                    var n = 0
                    while (n < 3 && i < s.length && s[i] in '0'..'7') {
                        oct += s[i]
                        i++
                        n++
                    }
                    i--
                    out.write((oct.toIntOrNull(8) ?: 0) and 0xff)
                }
                else -> out.write(e.code)
            }
            i++
        }
        return out.toByteArray()
    }

    // ---------------- 版式一：星期横向 ----------------

    private fun parseHorizontal(pages: List<List<Item>>, maxWeek: Int): List<Course> {
        val headerItems = pages.flatten().filter { it.text in weekdays }
        val centers = weekdays.map { day ->
            headerItems.filter { it.text == day }.map { it.x }.average().toFloat()
        }
        val result = mutableListOf<Course>()
        for (page in pages) {
            for (day in 1..7) {
                val cx = centers[day - 1]
                val dayItems = page
                    .filter { item ->
                        val nearest = centers.indices.minByOrNull {
                            kotlin.math.abs(centers[it] - item.x)
                        } ?: 0
                        nearest == day - 1 &&
                            kotlin.math.abs(item.x - cx) <= 75f &&
                            item.text !in weekdays &&
                            item.text != "时间段" &&
                            item.text != "节次"
                    }
                    .sortedByDescending { it.y }
                val markerIndexes = dayItems.indices.filter { i ->
                    weekdayRegex.containsMatchIn(dayItems[i].text)
                }
                for ((n, markerIndex) in markerIndexes.withIndex()) {
                    var start = markerIndex
                    var prevY = dayItems[markerIndex].y
                    var j = markerIndex - 1
                    val lowerBound = if (n > 0) markerIndexes[n - 1] + 1 else 0
                    while (j >= lowerBound) {
                        val gap = prevY - dayItems[j].y
                        if (gap > 20f || isNoiseText(dayItems[j].text)) break
                        start = j
                        prevY = dayItems[j].y
                        j--
                    }
                    val nextStart = if (n + 1 < markerIndexes.size) {
                        var s = markerIndexes[n + 1]
                        var pY = dayItems[s].y
                        var k = s - 1
                        while (k >= markerIndex + 1) {
                            val gap = pY - dayItems[k].y
                            if (gap > 20f) break
                            s = k
                            pY = dayItems[k].y
                            k--
                        }
                        s
                    } else dayItems.size

                    val safeStart = start.coerceIn(0, markerIndex)
                    val safeEnd = nextStart.coerceIn(markerIndex + 1, dayItems.size)
                    val title = dayItems.subList(safeStart, markerIndex)
                        .map { it.text }
                        .filterNot { isNoiseText(it) }
                        .joinToString("")
                        .replace("■", "")
                        .replace("★", "")
                        .trim()
                    if (title.isBlank()) continue
                    val details = dayItems.subList(markerIndex, safeEnd)
                        .joinToString("") { it.text }
                    val marker = weekdayRegex.find(dayItems[markerIndex].text) ?: continue
                    val startNode = marker.groupValues[1].toIntOrNull()?.coerceIn(1, 20) ?: 1
                    val endNode = marker.groupValues[2].toIntOrNull()?.coerceIn(startNode, 20) ?: startNode
                    addCourses(result, title, day, startNode, endNode, details, maxWeek)
                }
            }
        }
        return result
    }
    private fun isNoiseText(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return true
        if (t in weekdays) return true
        if (t.startsWith(":") || t.startsWith("/") || t.startsWith("：")) return true
        if (t.startsWith("学班") || t.startsWith("班:")) return true
        if (t.firstOrNull()?.isDigit() == true) return true
        return listOf(
            "时间段", "节次", "上午", "下午", "晚上", "教学班", "考核方式",
            "周学时", "总学时", "学分", "理论:", "实践:", "校区:", "场地:", "地点:",
            "教师:", "选课备注"
        ).any { t.contains(it) }
    }

    // ---------------- 版式二：星期纵向 ----------------

    private fun parseVertical(pages: List<List<Item>>, maxWeek: Int): List<Course> {
        val result = mutableListOf<Course>()
        for (page in pages) {
            val headers = page.filter { it.text in weekdays }.sortedByDescending { it.y }
            if (headers.isEmpty()) continue
            val lowerBounds = FloatArray(headers.size)
            val upperBounds = FloatArray(headers.size)
            for (i in headers.indices) {
                // 这些 PDF 的星期标题位于该天区域的顶部/中部，下一天标题才是分界。
                lowerBounds[i] = if (i + 1 < headers.size) headers[i + 1].y
                else Float.NEGATIVE_INFINITY
                upperBounds[i] = if (i > 0) headers[i].y
                else Float.POSITIVE_INFINITY
            }
            val periodLabels = page.filter {
                it.x < 130f && Regex("^\\d{1,2}(?:\\s*-\\s*\\d{1,2})?$").matches(it.text)
            }
            val titles = page.filter { it.x in 140f..300f && it.text !in weekdays }
            val detailsItems = page.filter { it.x > 300f && it.text !in weekdays }
            for (h in headers.indices) {
                val day = weekdays.indexOf(headers[h].text) + 1
                if (day !in 1..7) continue
                val regionTitles = titles.filter { it.y < upperBounds[h] && it.y >= lowerBounds[h] }
                for (titleItem in regionTitles) {
                    val title = titleItem.text.replace("■", "").replace("★", "").trim()
                    if (title.isBlank()) continue
                    val period = periodLabels
                        .filter { kotlin.math.abs(it.y - titleItem.y) <= 35f }
                        .minByOrNull { kotlin.math.abs(it.y - titleItem.y) }
                    val periodMatch = period?.text?.let { Regex("(\\d{1,2})(?:\\s*-\\s*(\\d{1,2}))?").find(it) }
                    val startNode = periodMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 20) ?: 1
                    val endNode = periodMatch?.groupValues?.get(2)?.toIntOrNull()?.coerceIn(startNode, 20) ?: startNode
                    val detail = detailsItems
                        .filter { kotlin.math.abs(it.y - titleItem.y) <= 35f }
                        .sortedByDescending { it.y }
                        .joinToString("") { it.text }
                    addCourses(result, title, day, startNode, endNode, detail, maxWeek)
                }
            }
        }
        return result
    }

    private fun addCourses(
        out: MutableList<Course>,
        title: String,
        day: Int,
        startNode: Int,
        endNode: Int,
        details: String,
        maxWeek: Int
    ) {
        val room = Regex("/(?:场地|地点)\\s*[:：]\\s*([^/]+)")
            .find(details)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), "")?.trim().orEmpty()
        val teacher = Regex("/教师\\s*[:：]\\s*([^/]+)")
            .find(details)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), "")?.trim().orEmpty()

        val ranges = Regex("(\\d{1,2})\\s*(?:-\\s*(\\d{1,2}))?\\s*周\\s*(?:[（(]\\s*(单|双)\\s*[）)])?")
            .findAll(details)
            .mapNotNull { m ->
                val s = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val e = m.groupValues[2].toIntOrNull() ?: s
                val type = when (m.groupValues[3]) {
                    "单" -> 1
                    "双" -> 2
                    else -> 0
                }
                Triple(s.coerceIn(1, 60), e.coerceIn(s.coerceIn(1, 60), 60), type)
            }
            .toList()
        if (ranges.isEmpty()) {
            out.add(Course(title, day, room, teacher, startNode, endNode, 1, maxWeek.coerceAtLeast(1), 0))
        } else {
            for ((s, e, type) in ranges) {
                out.add(Course(title, day, room, teacher, startNode, endNode, s, e, type))
            }
        }
    }
}
