package com.chen.classtable

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.chen.classtable.capture.CaptureResult
import com.chen.classtable.capture.PagePrecheck
import com.chen.classtable.importer.ImportTypeGuesser
import com.chen.classtable.importer.Importer
import com.chen.classtable.model.Course
import com.chen.classtable.registry.ImportTypeRegistry
import com.chen.classtable.store.CourseStore

/**
 * 导入界面：
 *  - WebView 登录校园网/教务系统 → JS 采集 HTML → 本地解析（不上传服务器）；
 *  - 或直接粘贴 HTML 本地解析；
 *  - 自动按页面特征猜测教务类型，也可手动选择。
 */
class ImportActivity : BaseActivity() {

    private lateinit var spinner: Spinner
    private lateinit var etHtml: EditText
    private lateinit var etUrl: EditText
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        UiUtils.applySystemBarAppearance(this)
        spinner = findViewById(R.id.spinnerTypes)
        etHtml = findViewById(R.id.etHtml)
        etUrl = findViewById(R.id.etUrl)
        tvResult = findViewById(R.id.tvResult)

        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            ImportTypeRegistry.DISPLAY_TYPES.map { it.first }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<Button>(R.id.btnWebView).setOnClickListener { openWebView(false) }
        findViewById<Button>(R.id.btnDemoLogin).setOnClickListener { openWebView(true) }
        findViewById<Button>(R.id.btnParse).setOnClickListener { parse(etHtml.text.toString()) }
        findViewById<Button>(R.id.btnLoadMock).setOnClickListener {
            etHtml.setText(MOCK_HTML)
            tvResult.text = "已载入示例 HTML，点击「解析并导入」"
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }

    private fun openWebView(demo: Boolean) {
        val intent = Intent(this, WebViewLoginActivity::class.java)
        intent.putExtra(
            WebViewLoginActivity.EXTRA_URL,
            if (demo) WebViewLoginActivity.DEMO_ASSET else etUrl.text.toString().trim()
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_WEBVIEW)
    }

    @Deprecated("Deprecated in API 33")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_WEBVIEW || resultCode != RESULT_OK) return
        val url = data?.getStringExtra(WebViewLoginActivity.EXTRA_FINAL_URL) ?: ""
        val capturePath = data?.getStringExtra(WebViewLoginActivity.EXTRA_CAPTURE_PATH)
        val captureJson = if (!capturePath.isNullOrBlank()) {
            val text = runCatching { java.io.File(capturePath).readText() }.getOrNull()
            runCatching { java.io.File(capturePath).delete() }
            text
        } else {
            data?.getStringExtra(WebViewLoginActivity.EXTRA_CAPTURE_JSON)
        }
        var html = data?.getStringExtra(WebViewLoginActivity.EXTRA_HTML)

        if (!captureJson.isNullOrBlank()) {
            val capture = CaptureResult.fromJson(captureJson)
            if (capture == null) {
                tvResult.text = "❌ 采集结果无效（JSON 解析失败），请重新采集"
                return
            }
            val primary = capture.primaryDocument()
            val verdict = primary?.let { PagePrecheck.check(it.html, it.url) }
            if (primary == null || verdict == null || !verdict.ok) {
                tvResult.text = "❌ 采集不可用：${verdict?.message ?: "未采集到可用文档"}\n${capture.summary()}"
                Toast.makeText(this, verdict?.message ?: "采集不可用", Toast.LENGTH_LONG).show()
                return
            }
            html = primary.html
            tvResult.text = "采集成功：${capture.summary()}，正在本地解析…"
        }

        if (html.isNullOrBlank()) {
            tvResult.text = "采集失败：页面为空"
            return
        }
        val guessed = runCatching { ImportTypeGuesser.guess(html, url) }.getOrDefault("generic")
        val index = ImportTypeRegistry.DISPLAY_TYPES.indexOfFirst { it.second == guessed }
        if (index >= 0) spinner.setSelection(index)
        tvResult.text = "采集成功（${html.length} 字符），自动识别类型：$guessed，正在本地解析…"
        parse(html)
    }

    private fun parse(html: String) {
        if (html.isBlank()) {
            tvResult.text = "页面为空：请粘贴课表 HTML 或先登录采集页面"
            return
        }
        try {
            val type = ImportTypeRegistry.DISPLAY_TYPES[spinner.selectedItemPosition].second
            val courses: List<Course> = Importer.parse(type, html)
            if (courses.isEmpty()) {
                val msg = "未解析到课程（类型：$type），请检查 HTML 是否为课表页"
                tvResult.text = "❌ $msg"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return
            }
            if (CourseStore.hasTables(this)) {
                askImportMode(courses)
            } else {
                finishImport(Importer.importAsNewTable(this, courses, nextTableName()))
            }
        } catch (t: Throwable) {
            val msg = "本地解析异常：${t.javaClass.simpleName}: ${t.message ?: ""}"
            tvResult.text = "❌ $msg"
            Toast.makeText(this, msg.take(200), Toast.LENGTH_LONG).show()
        }
    }

    /** 已有课表时询问：覆盖当前课表 还是 新建课表导入。 */
    private fun askImportMode(courses: List<Course>) {
        val active = CourseStore.activeTable(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("已有课表，选择导入方式")
            .setMessage(
                "当前课表：「${active?.name ?: ""}」\n" +
                    "本次解析到 ${courses.size} 个时段。\n\n" +
                    "覆盖导入：替换当前课表的全部课程；\n" +
                    "新建导入：创建一个新课表并放入这些课程。"
            )
            .setPositiveButton("覆盖当前课表") { _, _ ->
                finishImport(Importer.overwrite(this, courses))
            }
            .setNeutralButton("新建课表导入") { _, _ ->
                finishImport(Importer.importAsNewTable(this, courses, nextTableName()))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun nextTableName(): String {
        val names = CourseStore.tables(this).map { it.name }.toSet()
        val base = "导入课表"
        if (base !in names) return base
        var i = 2
        while ("$base $i" in names) i++
        return "$base $i"
    }

    private fun finishImport(r: Importer.Result) {
        tvResult.text = "${if (r.success) "✅" else "❌"} ${r.message}（本地解析，未上传）"
        Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show()
        if (!r.success) return
        AppSettings.load(this)
        val table = CourseStore.activeTable(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("导入完成")
            .setMessage(
                "课程已保存到「${table?.name ?: "当前课表"}」。\n" +
                    "现在设置该课表的开学时间、学期周数和节次时间吗？"
            )
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(this, ScheduleSettingsActivity::class.java))
                finish()
            }
            .setNegativeButton("稍后") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val REQ_WEBVIEW = 1001

        /** 示例：正方/金智风格的“星期 × 节次”课表 HTML。 */
        val MOCK_HTML = """
            <table id="kbtable">
              <tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th><th>周六</th><th>周日</th></tr>
              <tr><td>第1节</td>
                  <td>高等数学<br>张老师<br>教101<br>1-16周</td>
                  <td></td><td>线性代数<br>赵老师<br>教203<br>1-16周</td>
                  <td></td><td>大学物理<br>王老师<br>物理楼201<br>1-8,10-16周</td><td></td><td></td></tr>
              <tr><td>第2节</td>
                  <td>高等数学<br>张老师<br>教101<br>1-16周</td>
                  <td></td><td>线性代数<br>赵老师<br>教203<br>1-16周</td>
                  <td></td><td>大学物理<br>王老师<br>物理楼201<br>1-8,10-16周</td><td></td><td></td></tr>
              <tr><td>第3节</td>
                  <td></td><td>大学英语<br>李老师<br>文科楼302<br>1-16周(单)</td>
                  <td></td><td>数据结构<br>陈老师<br>实验楼401<br>1-16周</td><td></td><td></td><td></td></tr>
              <tr><td>第4节</td>
                  <td></td><td>大学英语<br>李老师<br>文科楼302<br>1-16周(单)</td>
                  <td></td><td>数据结构<br>陈老师<br>实验楼401<br>1-16周</td><td></td><td></td><td></td></tr>
              <tr><td>第5节</td>
                  <td></td><td></td><td></td><td></td>
                  <td>体育<br>刘老师<br>体育馆<br>1-16周</td><td></td><td></td></tr>
            </table>
        """.trimIndent()
    }
}
