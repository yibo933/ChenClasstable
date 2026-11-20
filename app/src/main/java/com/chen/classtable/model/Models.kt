package com.chen.classtable.model

/** 周次区间：start/end 为周次，type 0=全周 1=单周 2=双周。 */
data class WeekBean(var start: Int, var end: Int, var type: Int)

/** 课程的上课时段（解析统一模型）。 */
data class Course(
    val name: String,
    val day: Int,
    val room: String = "",
    val teacher: String = "",
    var startNode: Int,
    var endNode: Int,
    var startWeek: Int,
    var endWeek: Int,
    var type: Int,
    var credit: Float = 0f,
    var note: String = "",
    var startTime: String = "",
    var endTime: String = ""
)
