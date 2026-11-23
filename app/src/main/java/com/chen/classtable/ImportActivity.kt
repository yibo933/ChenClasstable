package com.chen.classtable

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.chen.classtable.capture.CaptureResult
import com.chen.classtable.importer.ImportTypeGuesser
import com.chen.classtable.importer.Importer
import com.chen.classtable.model.Course
import com.chen.classtable.store.CourseStore
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 导入页：
 *  - 小陈课表 JSON 备份；
 *  - PDF 课表；
 *  - WebView 登录采集。
 *
 * 三种方式解析出课程后都先询问「覆盖当前课表」还是「新建课表」，
 * 不再直接创建新表。
 */
class ImportActivity : BaseActivity() {

    private lateinit var tvResult: TextView
    private val jsonPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importJson(uri)
    }
    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(uri)
    }
    private val webLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) handleWebCapture(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContentView(R.layout.activity_import)
        UiUtils.applySystemBarAppearance(this)
        AdaptiveLayoutManager.applyReadableWidth(this, 900)

        tvResult = findViewById(R.id.tvResult)
        findViewById<MaterialButton>(R.id.btnImportJson).setOnClickListener {
            jsonPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        findViewById<MaterialButton>(R.id.btnImportPdf).setOnClickListener {
            pdfPicker.launch(arrayOf("application/pdf"))
        }
        findViewById<MaterialButton>(R.id.btnWebView).setOnClickListener {
            val url = findViewById<EditText>(R.id.etWebUrl).text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请先粘贴教务系统课表地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            webLogin.launch(
                Intent(this, WebViewLoginActivity::class.java)
                    .putExtra(WebViewLoginActivity.EXTRA_URL, url)
            )
        }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { finish() }
    }

    private fun importJson(uri: Uri) {
        showStatus("正在读取并校验 JSON…")
        runCatching {
            val bytes = readBytes(uri, 8 * 1024 * 1024)
            val parsed = TimetableJson.decode(bytes.toString(Charsets.UTF_8))
            offerImport(
                courses = normalizeCourses(parsed.courses),
                settings = parsed.settings,
                baseName = parsed.name,
                sourceLabel = "JSON"
            )
        }.onFailure { e ->
            showError("JSON 导入失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun importPdf(uri: Uri) {
        showStatus("正在本机解析 PDF…")
        runCatching {
            val bytes = readBytes(uri, 20 * 1024 * 1024)
            val courses = normalizeCourses(PdfTimetableParser.parse(bytes, AppSettings.maxWeek))
            val rawName = displayName(uri)?.substringBeforeLast(".")?.trim()
                ?.takeIf { it.isNotBlank() } ?: "PDF课表"
            offerImport(courses, null, rawName, "PDF")
        }.onFailure { e ->
            showError("PDF 导入失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun handleWebCapture(data: Intent?) {
        showStatus("正在解析网页采集结果…")
        runCatching {
            val json = data?.getStringExtra(WebViewLoginActivity.EXTRA_CAPTURE_PATH)
                ?.let { File(it).takeIf { f -> f.isFile }?.readText() }
                ?: data?.getStringExtra(WebViewLoginActivity.EXTRA_CAPTURE_JSON)
            val capture = CaptureResult.fromJson(json)
                ?: throw IllegalArgumentException("采集结果为空或已损坏")
            val primary = capture.primaryDocument()
                ?: throw IllegalArgumentException("没有读取到可用的课表页面")
            val type = ImportTypeGuesser.guess(primary.html, primary.url)
            val courses = normalizeCourses(Importer.parse(type, primary.html))
            if (courses.isEmpty()) {
                val diag = capture.diagnostics.take(6).joinToString("；")
                throw IllegalArgumentException(
                    "网页中未解析到课程（识别类型：$type）" +
                        if (diag.isNotBlank()) "；$diag" else ""
                )
            }
            val baseName = primary.title.trim().take(24).ifBlank { "网页导入课表" }
            offerImport(courses, null, baseName, "网页采集")
        }.onFailure { e ->
            showError("网页导入失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 所有导入方式的统一入口：先询问覆盖还是新建。
     * 没有现有课表时只提供新建，避免出现无意义的覆盖选项。
     */
    private fun offerImport(
        courses: List<Course>,
        settings: AppSettings.TableSettings?,
        baseName: String,
        sourceLabel: String
    ) {
        if (courses.isEmpty()) {
            showError("没有解析到可导入的课程")
            return
        }
        val current = CourseStore.activeTable(this)
        if (current == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("保存导入课程")
                .setMessage(
                    "当前还没有课表。\n\n" +
                        "将新建「$baseName」并导入 ${courses.size} 个课程时段。"
                )
                .setPositiveButton("新建课表") { _, _ ->
                    saveAsNewTable(courses, settings, baseName)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val currentCount = CourseStore.load(this).size
        MaterialAlertDialogBuilder(this)
            .setTitle("导入 $sourceLabel")
            .setMessage(
                "已解析 ${courses.size} 个课程时段。\n\n" +
                    "覆盖当前课表「${current.name}」会删除现有 $currentCount 个课程时段；\n" +
                    "新建课表不会影响现有课表。"
            )
            .setPositiveButton("覆盖当前课表") { _, _ ->
                confirmOverwrite(courses, settings, baseName, current, currentCount)
            }
            .setNegativeButton("新建课表") { _, _ ->
                saveAsNewTable(courses, settings, baseName)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun confirmOverwrite(
        courses: List<Course>,
        settings: AppSettings.TableSettings?,
        baseName: String,
        current: CourseStore.TableInfo,
        currentCount: Int
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle("确认覆盖？")
            .setMessage(
                "即将删除「${current.name}」现有的 $currentCount 个课程时段，\n" +
                    "并写入 ${courses.size} 个新课程时段。\n\n" +
                    "此操作不可撤销。"
            )
            .setPositiveButton("确认覆盖") { _, _ ->
                overwriteCurrentTable(courses, settings, current)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveAsNewTable(
        courses: List<Course>,
        settings: AppSettings.TableSettings?,
        baseName: String
    ) {
        runCatching {
            val table = createUniqueTable(baseName)
            CourseStore.save(this, table.id, courses)
            applySettings(table.id, settings, courses)
            AppSettings.reload(this)
            showDone(table.name, courses.size)
        }.onFailure { e ->
            showError("导入失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun overwriteCurrentTable(
        courses: List<Course>,
        settings: AppSettings.TableSettings?,
        current: CourseStore.TableInfo
    ) {
        runCatching {
            // save() 会整体替换该表的课程文本，不需要先 clear；先写入成功再刷新页面。
            CourseStore.save(this, current.id, courses)
            applySettings(current.id, settings, courses)
            AppSettings.reload(this)
            showDone(current.name, courses.size)
        }.onFailure { e ->
            showError("覆盖失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 应用导入设置，并保证学期周数覆盖所有导入课程。
     * 解决「课程默认周数超过 20 周」时被 maxWeek 截断、课程不显示的问题。
     */
    private fun applySettings(
        tableId: String,
        importedSettings: AppSettings.TableSettings?,
        courses: List<Course>
    ) {
        val base = importedSettings ?: AppSettings.readTableSettings(this, tableId)
        val maxCourseWeek = courses.maxOfOrNull { it.endWeek } ?: 1
        val maxWeek = maxOf(base.maxWeek, maxCourseWeek).coerceIn(1, 60)
        AppSettings.saveTableSettings(this, tableId, base.copy(maxWeek = maxWeek))
    }

    private fun normalizeCourses(courses: List<Course>): List<Course> {
        if (courses.isEmpty()) return courses
        return courses.map { c ->
            val startWeek = c.startWeek.coerceIn(1, 60)
            val endWeek = c.endWeek.coerceIn(startWeek, 60)
            val startNode = c.startNode.coerceIn(1, 20)
            val endNode = c.endNode.coerceIn(startNode, 20)
            c.copy(
                startWeek = startWeek,
                endWeek = endWeek,
                startNode = startNode,
                endNode = endNode
            )
        }
    }

    private fun createUniqueTable(baseName: String): CourseStore.TableInfo {
        val names = CourseStore.tables(this).map { it.name }.toSet()
        val normalized = baseName.ifBlank { "导入课表" }
        var name = normalized
        if (name in names) {
            var i = 2
            while ("$normalized ($i)" in names) i++
            name = "$normalized ($i)"
        }
        return CourseStore.createTable(this, name)
    }

    private fun readBytes(uri: Uri, max: Int): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取文件")
        input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw IllegalArgumentException("文件过大")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun displayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        }.getOrNull()
    }

    private fun showStatus(text: String) {
        tvResult.visibility = TextView.VISIBLE
        tvResult.text = text
    }

    private fun showError(text: String) {
        showStatus(text)
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun showDone(tableName: String, count: Int) {
        showStatus("✅ 已导入「$tableName」：$count 个课程时段")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入完成")
            .setMessage(
                "课程已保存到「$tableName」。\n" +
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
}
