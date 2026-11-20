package com.chen.classtable.importer

/**
 * 根据采集到的 HTML/URL 自动猜测教务系统类型（业务规则）。
 * 猜不出时回退通用解析器。
 */
object ImportTypeGuesser {

    fun guess(html: String, url: String = ""): String {
        val s = (url + "\n" + html).lowercase()
        return when {
            s.contains("taskactivity") -> "eams"
            s.contains("jwxt") && s.contains("eams") -> "jz"
            s.contains("coursetableforstd") || s.contains("eams") -> "jz"
            s.contains("kingosoft") || s.contains("金智") -> "kingosoft"
            s.contains("zfsoft") || s.contains("正方") || s.contains("kbtable") -> "zf"
            s.contains("强智") || s.contains("qz_") || s.contains("qzdatas") -> "qz"
            s.contains("urp") || s.contains("urp_") -> "urp"
            s.contains("shuwei") || s.contains("树维") -> "shuwei_new"
            s.contains("青果") || s.contains("qingguo") -> "qg"
            s.contains("thu") || s.contains("tsinghua") -> "thu"
            else -> "generic"
        }
    }
}
