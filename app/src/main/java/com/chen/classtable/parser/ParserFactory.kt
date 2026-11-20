package com.chen.classtable.parser

import com.chen.classtable.model.Course
import com.chen.classtable.registry.ImportTypeRegistry

/** 解析器工厂：importType → 具体解析器（业务分类见 ImportTypeRegistry）。 */
object ParserFactory {

    fun create(importType: String?, source: String): IParser =
        when (ImportTypeRegistry.kindOf(importType)) {
            ImportTypeRegistry.Kind.THU -> THUParser(source)
            ImportTypeRegistry.Kind.NEW_ZF -> NewZFParser(source)
            ImportTypeRegistry.Kind.ZF -> ZFParser(source)
            ImportTypeRegistry.Kind.JZ -> JZParser(source)
            ImportTypeRegistry.Kind.QZ -> QZParser(source)
            ImportTypeRegistry.Kind.URP -> URPParser(source)
            ImportTypeRegistry.Kind.QG -> QGParser(source)
            ImportTypeRegistry.Kind.SW -> SWParser(source)
            ImportTypeRegistry.Kind.KINGOSOFT -> KingosoftParser(source)
            ImportTypeRegistry.Kind.EAMS -> EAMSParser(source)
            else -> GenericHtmlParser(source)
        }

    fun parse(importType: String?, html: String): List<Course> =
        runCatching { create(importType, html).generateCourseList() }.getOrDefault(emptyList())
}
