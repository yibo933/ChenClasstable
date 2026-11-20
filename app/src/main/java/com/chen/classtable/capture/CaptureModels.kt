package com.chen.classtable.capture

import org.json.JSONArray
import org.json.JSONObject

/** 单个文档（主页面/iframe）的采集结果。 */
data class CapturedDocument(
    val level: Int,
    val url: String,
    val title: String,
    val html: String,
    /** OK / EMPTY / CROSS_ORIGIN / INACCESSIBLE / ERROR */
    val status: String,
    val message: String = ""
)

/** 一次采集的完整结果：主页面与各层 iframe 分开保存，不拼接。 */
data class CaptureResult(
    val requestId: String,
    val mainUrl: String,
    val capturedAt: Long,
    val documents: List<CapturedDocument>,
    val diagnostics: List<String>
) {

    fun primaryDocument(): CapturedDocument? {
        val ok = documents.filter { it.status == "OK" && it.html.isNotBlank() }
        return ok.firstOrNull { PagePrecheck.check(it.html, it.url).ok } ?: ok.firstOrNull()
    }

    fun primaryHtml(): String = primaryDocument()?.html ?: ""

    fun summary(): String {
        val ok = documents.count { it.status == "OK" }
        val cross = documents.count { it.status == "CROSS_ORIGIN" }
        val empty = documents.count { it.status == "EMPTY" }
        val errors = diagnostics.size
        return "文档 ${documents.size} 个（可读 $ok / 跨域 $cross / 空白 $empty / 诊断 $errors）"
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (d in documents) {
            arr.put(
                JSONObject().apply {
                    put("level", d.level)
                    put("url", d.url)
                    put("title", d.title)
                    put("html", d.html)
                    put("status", d.status)
                    put("message", d.message)
                }
            )
        }
        val diag = JSONArray()
        diagnostics.forEach { diag.put(it) }
        return JSONObject().apply {
            put("requestId", requestId)
            put("mainUrl", mainUrl)
            put("capturedAt", capturedAt)
            put("documents", arr)
            put("diagnostics", diag)
        }.toString()
    }

    companion object {
        fun fromJson(text: String?): CaptureResult? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(text)
                val docs = mutableListOf<CapturedDocument>()
                val arr = o.optJSONArray("documents") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    docs.add(
                        CapturedDocument(
                            level = d.optInt("level", 0),
                            url = d.optString("url", ""),
                            title = d.optString("title", ""),
                            html = d.optString("html", ""),
                            status = d.optString("status", "OK"),
                            message = d.optString("message", "")
                        )
                    )
                }
                val diag = mutableListOf<String>()
                val da = o.optJSONArray("diagnostics") ?: JSONArray()
                for (i in 0 until da.length()) diag.add(da.optString(i))
                CaptureResult(
                    requestId = o.optString("requestId", ""),
                    mainUrl = o.optString("mainUrl", ""),
                    capturedAt = o.optLong("capturedAt", 0L),
                    documents = docs,
                    diagnostics = diag
                )
            }.getOrNull()
        }
    }
}
