package com.chen.classtable.parser

import com.chen.classtable.model.Course

/** 解析器契约（业务接口）。 */
interface IParser {
    fun generateCourseList(): List<Course>
}
