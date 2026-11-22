package com.chen.classtable

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.chen.classtable.model.Course
import com.chen.classtable.store.CourseStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 小陈课表专用 JSON 格式（导出/导入必须使用同一格式）。 */
object TimetableJson {

    const val APP_ID = "ChenClasstable"
    const val FORMAT = "chen-classtable"
    const val VERSION = 1

    data class Parsed(
        val name: String,
        val settings: AppSettings.TableSettings?,
        val courses: List<Course>
    )

    fun encode(context: Context, table: CourseStore.TableInfo): String {
        val settings = AppSettings.readTableSettings(context, table.id)
        val courses = CourseStore.load(context, table.id)
        val root = JSONObject()
            .put("app", APP_ID)
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
            .put("table", JSONObject().put("name", table.name))
            .put("settings", JSONObject()
                .put("startDateMillis", settings.startDateMillis)
                .put("maxWeek", settings.maxWeek)
                .put("dayPeriods", settings.dayPeriods)
                .put("showWeekend", settings.showWeekend)
                .put("showScheduleTable", settings.showScheduleTable)
                .put("periods", JSONArray().apply {
                    for (i in 0 until settings.dayPeriods) {
                        put(JSONObject()
                            .put("start", settings.periodStarts.getOrElse(i) { 8 * 60 })
                            .put("end", settings.periodEnds.getOrElse(i) { 8 * 60 + 45 }))
                    }
                }))
        val array = JSONArray()
        for (c in courses) {
            array.put(JSONObject()
                .put("name", c.name)
                .put("day", c.day)
                .put("room", c.room)
                .put("teacher", c.teacher)
                .put("startNode", c.startNode)
                .put("endNode", c.endNode)
                .put("startWeek", c.startWeek)
                .put("endWeek", c.endWeek)
                .put("type", c.type)
                .put("credit", c.credit.toDouble())
                .put("note", c.note)
                .put("startTime", c.startTime)
                .put("endTime", c.endTime))
        }
        root.put("courses", array)
        return root.toString(2)
    }

    fun decode(text: String): Parsed {
        val root = JSONObject(text)
        require(root.optString("app") == APP_ID) { "不是小陈课表导出的 JSON 文件" }
        require(root.optString("format") == FORMAT) { "JSON 格式不是 chen-classtable" }
        require(root.optInt("version", 0) in 1..VERSION) { "不支持的 JSON 版本" }
        val table = root.optJSONObject("table")
        val name = table?.optString("name")?.trim()?.takeIf { it.isNotEmpty() } ?: "导入课表"

        val settingsObj = root.optJSONObject("settings")
        val settings = settingsObj?.let { s ->
            val dayPeriods = s.optInt("dayPeriods", 10).coerceIn(1, AppSettings.MAX_PERIODS)
            val starts = IntArray(AppSettings.MAX_PERIODS)
            val ends = IntArray(AppSettings.MAX_PERIODS)
            val periods = s.optJSONArray("periods")
            for (i in 0 until AppSettings.MAX_PERIODS) {
                val defStart = 8 * 60 + i * 55
                val p = periods?.optJSONObject(i)
                val start = p?.optInt("start", defStart) ?: defStart
                val end = p?.optInt("end", start + 45) ?: (start + 45)
                starts[i] = start.coerceIn(0, 24 * 60 - 2)
                ends[i] = end.coerceIn(starts[i] + 1, 24 * 60 - 1)
            }
            AppSettings.TableSettings(
                startDateMillis = s.optLong("startDateMillis", System.currentTimeMillis()),
                maxWeek = s.optInt("maxWeek", 20).coerceIn(1, 60),
                dayPeriods = dayPeriods,
                periodStarts = starts,
                periodEnds = ends,
                showWeekend = s.optBoolean("showWeekend", true),
                showScheduleTable = s.optBoolean("showScheduleTable", true)
            )
        }

        val coursesArray = root.optJSONArray("courses")
            ?: throw IllegalArgumentException("JSON 缺少 courses 课程数组")
        require(coursesArray.length() <= 5000) { "课程数量异常" }
        val courses = mutableListOf<Course>()
        for (i in 0 until coursesArray.length()) {
            val o = coursesArray.optJSONObject(i) ?: continue
            val courseName = o.optString("name").trim()
            if (courseName.isEmpty()) continue
            val day = o.optInt("day", 1).coerceIn(1, 7)
            val startNode = o.optInt("startNode", 1).coerceIn(1, 20)
            val endNode = o.optInt("endNode", startNode).coerceIn(startNode, 20)
            val startWeek = o.optInt("startWeek", 1).coerceIn(1, 60)
            val endWeek = o.optInt("endWeek", startWeek).coerceIn(startWeek, 60)
            courses.add(Course(
                name = courseName,
                day = day,
                room = o.optString("room"),
                teacher = o.optString("teacher"),
                startNode = startNode,
                endNode = endNode,
                startWeek = startWeek,
                endWeek = endWeek,
                type = o.optInt("type", 0).coerceIn(0, 2),
                credit = o.optDouble("credit", 0.0).toFloat(),
                note = o.optString("note"),
                startTime = o.optString("startTime"),
                endTime = o.optString("endTime")
            ))
        }
        require(courses.isNotEmpty()) { "JSON 中没有有效课程" }
        return Parsed(name, settings, courses)
    }
}

/** 写入系统 Download 目录。Android 10+ 使用 MediaStore，旧版本使用公共目录。 */
object TimetableExporter {

    fun filenameFor(tableName: String): String {
        val safe = tableName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
            .ifBlank { "课表" }
            .take(40)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "小陈课表_${safe}_$stamp.json"
    }

    fun write(context: Context, filename: String, content: String): Uri? {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }
}
