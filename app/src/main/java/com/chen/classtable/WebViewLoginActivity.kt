package com.chen.classtable

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chen.classtable.capture.CaptureResult
import com.chen.classtable.capture.PagePrecheck
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/**
 * 校园网/教务系统 WebView 登录 + 可靠采集。
 *
 * 采集状态：IDLE → WAITING（等待 DOM/课程数据，带超时）→ CAPTURING →
 *           DELIVERED（成功，最多交付一次）或 FAILED（保留 WebView，可重新采集）。
 *
 * 结果按“主页面 + 每层 iframe”分离保存（URL/标题/HTML/层级/状态），
 * 通过 EXTRA_CAPTURE_JSON 回传；EXTRA_HTML 仅作为主文档兼容字段。
 * 不再回传 Cookie。
 */
class WebViewLoginActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_HTML = "html"
        const val EXTRA_FINAL_URL = "final_url"
        const val EXTRA_CAPTURE_JSON = "capture_json"
        const val EXTRA_CAPTURE_PATH = "capture_path"
        const val DEMO_ASSET = "file:///android_asset/demo_school.html"

        private const val TAG = "WakeUpCapture"
        private const val MAX_WAIT_MS = 15_000L
        private const val WAIT_INTERVAL_MS = 500L
    }

    private enum class State { IDLE, WAITING, CAPTURING, FAILED, DELIVERED }

    private lateinit var webView: WebView
    private lateinit var tvUrl: TextView
    private lateinit var tvCaptureStatus: TextView
    private lateinit var btnCapture: Button

    private var state = State.IDLE
    private var captureId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        UiUtils.applySystemBarAppearance(this)
        webView = findViewById(R.id.webView)
        tvUrl = findViewById(R.id.tvUrl)
        tvCaptureStatus = findViewById(R.id.tvCaptureStatus)
        btnCapture = findViewById(R.id.btnCapture)

        val requestedUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: DEMO_ASSET
        val isLocalAsset = requestedUrl.startsWith("file:///android_asset/")

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // 最小权限：只有本地演示页才允许文件访问；禁用 content 访问与文件 URL 跨域
        settings.allowFileAccess = isLocalAsset
        settings.allowContentAccess = false
        settings.databaseEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // 仅本地演示资源允许 file URL 读取其同源 iframe；远程教务页面保持禁止
            settings.allowFileAccessFromFileURLs = isLocalAsset
            settings.allowUniversalAccessFromFileURLs = false
        }
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setGeolocationEnabled(false)
        @Suppress("DEPRECATION")
        settings.setSavePassword(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 兼容模式：不无条件放行所有混合内容
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        // 电脑 UA（桌面版教务页面）
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(Bridge(), "WakeUpBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvUrl.text = url ?: ""
                if (state == State.IDLE || state == State.FAILED) {
                    showStatus("页面加载完成，点击「采集课表」", visible = true)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }
        webView.webChromeClient = WebChromeClient()
        btnCapture.setOnClickListener { beginCapture() }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
        showStatus("页面加载完成后点击「采集课表」", visible = true)
        webView.loadUrl(requestedUrl)
    }

    // ---------------- 采集状态机 ----------------

    private fun beginCapture() {
        if (state == State.WAITING || state == State.CAPTURING) {
            Toast.makeText(this, "正在采集中，请稍候…", Toast.LENGTH_SHORT).show()
            return
        }
        val id = UUID.randomUUID().toString()
        captureId = id
        state = State.WAITING
        setBusy(true)
        showStatus("正在等待页面加载课程数据…", visible = true)
        Log.i(TAG, "capture_begin id=$id state=$state")

        handler.postDelayed({
            if (captureId == id && state == State.WAITING) {
                failCapture(id, "等待页面超时：未检测到课程数据", listOf("wait_timeout"))
            }
        }, MAX_WAIT_MS + 3_000)
        waitForReady(id, 0L)
    }

    private fun waitForReady(id: String, elapsed: Long) {
        if (captureId != id || state != State.WAITING) return
        webView.evaluateJavascript(READY_JS) { value ->
            if (captureId != id || state != State.WAITING) return@evaluateJavascript
            val obj = parseJsJson(value)
            if (obj == null) {
                failCapture(id, "页面脚本执行异常，无法读取页面状态", listOf("JS_READY_ERROR"))
                return@evaluateJavascript
            }
            val ready = obj.optString("readyState") == "complete"
            val hasData = obj.optBoolean("hasCourseData", false)
            val isLogin = obj.optBoolean("isLogin", false)
            val isError = obj.optBoolean("isError", false)
            val isBlank = obj.optBoolean("isBlank", false)
            if (ready && hasData) {
                runCapture(id)
                return@evaluateJavascript
            }
            if (ready && isLogin) {
                failCapture(id, "当前仍停留在登录页，请先完成登录后再采集", listOf("page=login"))
                return@evaluateJavascript
            }
            if (ready && isError) {
                failCapture(id, "页面为错误页或无权限页，请检查登录状态或地址", listOf("page=error"))
                return@evaluateJavascript
            }
            if (ready && isBlank) {
                failCapture(id, "页面内容为空，未加载完成或地址无效", listOf("page=blank"))
                return@evaluateJavascript
            }
            if (elapsed >= MAX_WAIT_MS) {
                // 超时：仍然采集一次，交由预检给出明确诊断（而不是伪装成功）
                runCapture(id)
                return@evaluateJavascript
            }
            handler.postDelayed({ waitForReady(id, elapsed + WAIT_INTERVAL_MS) }, WAIT_INTERVAL_MS)
        }
    }

    private fun runCapture(id: String) {
        if (captureId != id || state != State.WAITING) return
        state = State.CAPTURING
        showStatus("正在采集主页面与 iframe…", visible = true)
        webView.evaluateJavascript(captureJs(id)) { value ->
            if (captureId != id || state != State.CAPTURING) return@evaluateJavascript
            val raw = extractJsonString(value)
            if (raw.isNullOrBlank()) {
                failCapture(id, "页面脚本执行异常，未能获取采集结果", listOf("JS_CAPTURE_EXCEPTION"))
            } else {
                finalizeCapture(id, raw, "evaluateJavascript")
            }
        }
    }

    /** Bridge 与 evaluateJavascript 兜底统一入口；同一请求最多成功一次。 */
    private fun finalizeCapture(id: String, json: String, source: String) {
        if (captureId != id || state != State.CAPTURING) return
        val result = CaptureResult.fromJson(json)
        if (result == null) {
            failCapture(id, "采集结果解析失败", listOf("capture_json_invalid"))
            return
        }
        if (result.requestId.isNotBlank() && result.requestId != id) {
            // 旧请求的迟到回调，忽略
            Log.i(TAG, "stale_callback id=$id resultId=${result.requestId}")
            return
        }
        val primary = result.primaryDocument()
        val verdict = primary?.let { PagePrecheck.check(it.html, it.url) }
            ?: PagePrecheck.Verdict(false, "none", "未采集到任何可用文档")
        val okDocs = result.documents.count { it.status == "OK" && it.html.isNotBlank() }
        if (!verdict.ok || okDocs == 0) {
            val diag = result.diagnostics.toMutableList()
            result.documents.filter { it.status != "OK" }.forEach {
                diag.add("doc level=${it.level} status=${it.status} ${it.message}")
            }
            failCapture(id, verdict.message, diag)
            return
        }
        state = State.DELIVERED
        Log.i(
            TAG,
            "capture_delivered id=$id source=$source docs=${result.documents.size} ok=$okDocs " +
                "diag=${result.diagnostics.size} ${result.summary()}"
        )
        // 大页面 HTML 通过文件传递，避免 Intent/Binder 事务超限导致崩溃
        val captureFile = java.io.File(cacheDir, "capture_${id}.json")
        val saved = runCatching {
            captureFile.writeText(json)
            true
        }.getOrDefault(false)
        val html = primary?.html ?: ""
        val intent = Intent().apply {
            putExtra(EXTRA_FINAL_URL, result.mainUrl.ifBlank { webView.url ?: "" })
            if (saved) {
                putExtra(EXTRA_CAPTURE_PATH, captureFile.absolutePath)
            } else {
                // 文件写入异常时回退；限制体积，避免再次触发事务超限
                putExtra(EXTRA_CAPTURE_JSON, json)
                if (html.length < 200_000) putExtra(EXTRA_HTML, html)
            }
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun failCapture(id: String, message: String, diagnostics: List<String>) {
        if (captureId != null && captureId != id) return
        state = State.FAILED
        captureId = null
        handler.removeCallbacksAndMessages(null)
        setBusy(false, retry = true)
        val diagText = diagnostics.take(4).joinToString("\n") { "· $it" }
        showStatus(
            "❌ $message" + if (diagText.isNotBlank()) "\n诊断：\n$diagText" else "",
            visible = true
        )
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.i(TAG, "capture_failed reason=${message.take(80)} diagnostics=${diagnostics.size}")
    }

    // ---------------- Bridge ----------------

    inner class Bridge {
        @JavascriptInterface
        fun deliver(json: String?, requestId: String?) {
            if (json.isNullOrBlank() || requestId.isNullOrBlank()) return
            runOnUiThread {
                if (requestId == captureId && state == State.CAPTURING) {
                    finalizeCapture(requestId, json, "bridge")
                }
            }
        }
    }

    // ---------------- 工具 ----------------

    private fun setBusy(busy: Boolean, retry: Boolean = false) {
        btnCapture.isEnabled = !busy
        btnCapture.text = when {
            busy -> "采集中…"
            retry -> "重新采集"
            else -> "采集课表"
        }
    }

    private fun showStatus(text: String, visible: Boolean) {
        tvCaptureStatus.text = text
        tvCaptureStatus.visibility = if (visible) TextView.VISIBLE else TextView.GONE
    }

    private fun parseJsJson(value: String?): JSONObject? {
        if (value == null || value == "null" || value == "\"null\"") return null
        return runCatching {
            when (val inner = JSONTokener(value).nextValue()) {
                is String -> JSONObject(inner)
                is JSONObject -> inner
                else -> null
            }
        }.getOrNull()
    }

    private fun extractJsonString(value: String?): String? {
        if (value == null || value == "null" || value == "\"null\"") return null
        return runCatching {
            when (val inner = JSONTokener(value).nextValue()) {
                is String -> inner
                is JSONObject -> inner.toString()
                else -> null
            }
        }.getOrNull()
    }

    @Deprecated("Deprecated in API 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.removeJavascriptInterface("WakeUpBridge")
        webView.destroy()
        super.onDestroy()
    }

    // ---------------- JS ----------------

    private val READY_JS = """
        (function(){
          try{
            var words=['星期','周一','周二','周三','周四','周五','周六','周日','节次','课程','课表','教学班','上课时间','任课教师'];
            function scan(win){
              var r={hits:0,structure:false,evidence:false,content:0,tables:0};
              var doc;
              try{ doc=win.document; }catch(e){ return r; }
              if(!doc||!doc.documentElement) return r;
              var text=((doc.body&&(doc.body.innerText||doc.body.textContent))||'').replace(/\s+/g,' ');
              r.content=text.length;
              for(var i=0;i<words.length;i++){ if(text.indexOf(words[i])>=0) r.hits++; }
              r.tables=doc.querySelectorAll('table').length;
              r.structure=!!(doc.querySelector('table,tr,td,[class*="course"],[class*="timetable"],[class*="schedule"],[id*="course"],[id*="kcb"]'));
              r.evidence=/(\d+\s*[-~至]\s*\d+\s*周|单周|双周|教师|老师|教\d+|楼|\d{1,2}:\d{2})/.test(text);
              var frames=doc.querySelectorAll('iframe');
              for(var j=0;j<frames.length;j++){
                try{
                  if(frames[j].contentDocument){
                    var c=scan(frames[j].contentWindow);
                    r.hits+=c.hits; r.content+=c.content; r.tables+=c.tables;
                    r.structure=r.structure||c.structure; r.evidence=r.evidence||c.evidence;
                  }
                }catch(e){ /* 跨域子帧不参与判定 */ }
              }
              return r;
            }
            var agg=scan(window);
            var topText=((document.body&&(document.body.innerText||document.body.textContent))||'').replace(/\s+/g,' ');
            var hasCourse=agg.structure && agg.hits>=2 && agg.evidence;
            var login=(/登录|登陆|login|统一身份认证/i.test(topText))&&(/密码|password|用户名|username|验证码|captcha/i.test(topText))&&!hasCourse;
            var error=(/404|500|系统错误|访问出错|无权限|页面不存在|服务器错误|error/i.test(topText))&&!hasCourse;
            var blank=(agg.content<20)&&!document.querySelector('table,form,input')&&!hasCourse;
            return JSON.stringify({readyState:document.readyState,hasCourseData:hasCourse,isLogin:login,isError:error,isBlank:blank,textLength:agg.content,tableCount:agg.tables});
          }catch(e){ return JSON.stringify({readyState:'error',hasCourseData:false,isLogin:false,isError:false,isBlank:false,error:String(e)}); }
        })()
    """.trimIndent()

    private fun captureJs(reqId: String): String = """
        (function(req){
          try{
            var out={requestId:req,mainUrl:location.href,capturedAt:Date.now(),documents:[],diagnostics:[]};
            function add(level,url,title,html,status,message){
              out.documents.push({level:level,url:url||'',title:title||'',html:html||'',status:status,message:message||''});
            }
            function walk(win, level){
              var doc;
              try{ doc=win.document; }
              catch(e){
                out.diagnostics.push('LEVEL '+level+' CROSS_ORIGIN: '+String(e));
                add(level,'','','','CROSS_ORIGIN',String(e));
                return;
              }
              if(!doc || !doc.documentElement){
                add(level,'','','','EMPTY','no documentElement');
                return;
              }
              var html=doc.documentElement.outerHTML||'';
              var text=doc.body?(doc.body.innerText||''):'';
              var blank=html.replace(/\s/g,'').length<300 && text.replace(/\s/g,'').length<20;
              add(level, doc.URL||'', doc.title||'', html, blank?'EMPTY':'OK', blank?'blank document':'');
              var frames=doc.querySelectorAll('iframe');
              for(var i=0;i<frames.length;i++){
                var f=frames[i];
                try{
                  if(f.contentDocument){ walk(f.contentWindow, level+1); }
                  else{
                    add(level+1, f.src||'', '', '', 'INACCESSIBLE', 'contentDocument unavailable');
                    out.diagnostics.push('LEVEL '+(level+1)+' IFRAME_INACCESSIBLE: '+(f.src||''));
                  }
                }catch(e){
                  add(level+1, f.src||'', '', '', 'CROSS_ORIGIN', String(e));
                  out.diagnostics.push('LEVEL '+(level+1)+' CROSS_ORIGIN: '+(f.src||''));
                }
              }
            }
            walk(window,0);
            var s=JSON.stringify(out);
            try{
              if(window.WakeUpBridge && window.WakeUpBridge.deliver){ window.WakeUpBridge.deliver(s, req); }
            }catch(e){ out.diagnostics.push('BRIDGE_ERROR: '+String(e)); s=JSON.stringify(out); }
            return s;
          }catch(e){
            return JSON.stringify({requestId:req,mainUrl:location.href,capturedAt:Date.now(),documents:[],diagnostics:['FATAL: '+String(e)]});
          }
        })('$reqId')
    """.trimIndent()
}
