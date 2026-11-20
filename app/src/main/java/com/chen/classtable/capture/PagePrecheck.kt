package com.chen.classtable.capture

/** 交付前的轻量预检：登录页 / 错误页 / 空白页 / 缺少课程数据。 */
object PagePrecheck {

    data class Verdict(val ok: Boolean, val kind: String, val message: String)

    private val SCRIPT_STYLE = Regex("<script[\\s\\S]*?</script>|<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val SPACE = Regex("\\s+")

    private val COURSE_WORDS = listOf(
        "星期", "周一", "周二", "周三", "周四", "周五", "周六", "周日",
        "节次", "课程", "课表", "教学班", "上课时间", "任课教师"
    )

    /** 课表结构：表格行/单元格，或 course/timetable/schedule 等容器类名。 */
    private val STRUCTURE = Regex(
        "<(table|tr|td)\\b|<[^>]*(class|id)\\s*=\\s*[\"'][^\"']*" +
            "(course|timetable|schedule|kcb|kb-table)[^\"']*",
        RegexOption.IGNORE_CASE
    )

    /** 课程证据：周次区间/单双周/教师/教室/时间等，避免把空白课表标题误判为有数据。 */
    private val EVIDENCE = Regex(
        "\\d+\\s*[-~至]\\s*\\d+\\s*周|单周|双周|教师|老师|教\\d+|楼|\\d{1,2}:\\d{2}",
        RegexOption.IGNORE_CASE
    )

    fun htmlToText(html: String): String =
        SPACE.replace(TAG.replace(SCRIPT_STYLE.replace(html, " "), " "), " ").trim()

    fun hasCourseData(html: String): Boolean {
        val text = htmlToText(html)
        if (text.length < 20) return false
        val lower = text.lowercase()
        val hits = COURSE_WORDS.count { lower.contains(it.lowercase()) }
        val hasStructure = STRUCTURE.containsMatchIn(html)
        val hasEvidence = EVIDENCE.containsMatchIn(text)
        // 需要“课表结构 + 课程证据”，避免未加载的标题/空白课表被误判
        return hasStructure && hits >= 2 && hasEvidence
    }

    fun check(html: String, url: String): Verdict {
        val text = htmlToText(html)
        val lower = text.lowercase()
        if (text.length < 20 && html.length < 300) {
            return Verdict(false, "blank", "页面为空或未完成加载，未采集到有效内容")
        }
        val courseHits = COURSE_WORDS.count { lower.contains(it.lowercase()) }
        val hasCourse = hasCourseData(html)
        val login = (lower.contains("登录") || lower.contains("登陆") ||
            lower.contains("login") || lower.contains("统一身份认证")) &&
            (lower.contains("密码") || lower.contains("password") ||
                lower.contains("用户名") || lower.contains("username") ||
                lower.contains("验证码") || lower.contains("captcha") ||
                lower.contains("login"))
        val error = listOf(
            "404", "500", "系统错误", "访问出错", "无权限", "页面不存在",
            "服务器错误", "error"
        ).any { lower.contains(it) } && !hasCourse
        if (login && !hasCourse) {
            return Verdict(false, "login", "当前仍停留在登录页，请先完成登录后再采集")
        }
        if (error) {
            return Verdict(false, "error", "检测到错误页/无权限页面，请检查登录状态或地址")
        }
        if (!hasCourse) {
            return Verdict(false, "no_course", "页面中未发现课程数据（可能尚未加载或不是课表页）")
        }
        return Verdict(true, "ok", "已发现课程数据")
    }
}
