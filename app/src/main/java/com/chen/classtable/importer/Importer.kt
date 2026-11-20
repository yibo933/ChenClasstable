package com.chen.classtable.importer

import android.content.Context
import com.chen.classtable.model.Course
import com.chen.classtable.parser.ParserFactory
import com.chen.classtable.store.CourseStore

/** 导入用例（对应原 App ImportViewModel 的核心流程）。 */
object Importer {

    data class Result(val success: Boolean, val count: Int, val message: String)

    /** 仅解析，不落库。 */
    fun parse(importType: String, html: String): List<Course> =
        ParserFactory.parse(importType, html)

    /** 覆盖当前课表。 */
    fun overwrite(context: Context, courses: List<Course>): Result {
        CourseStore.clear(context)
        CourseStore.save(context, courses)
        return Result(true, courses.size, "已覆盖当前课表：${courses.size} 个时段")
    }

    /** 新建课表导入。 */
    fun importAsNewTable(context: Context, courses: List<Course>, name: String): Result {
        val table = CourseStore.createTable(context, name)
        CourseStore.save(context, courses)
        return Result(true, courses.size, "已新建课表「${table.name}」：${courses.size} 个时段")
    }

    fun importHtml(context: Context, importType: String, html: String): Result {
        if (html.isBlank()) return Result(false, 0, "页面为空：请粘贴课表 HTML 或先登录采集页面")
        val courses = parse(importType, html)
        if (courses.isEmpty()) {
            return Result(false, 0, "未解析到课程（类型：$importType），请检查 HTML 是否为课表页")
        }
        val added = CourseStore.append(context, courses)
        return Result(true, added, "解析成功：$added 个时段，已保存")
    }

    fun importDemo(context: Context): Result {
        val added = CourseStore.append(context, CourseStore.demoCourses())
        return Result(true, added, "已载入示例课表：$added 个时段")
    }
}
