package com.chen.classtable

import android.os.Bundle
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 关于小陈课表。 */
class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        setContentView(R.layout.activity_about)
        UiUtils.applySystemBarAppearance(this)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationIconTint(
                MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface)
            )
            setNavigationOnClickListener { finish() }
        }

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.1"
        findViewById<TextView>(R.id.tvVersion).text = version
        findViewById<TextView>(R.id.tvVersionChip).text = "v$version"

        findViewById<android.view.View>(R.id.btnCheckUpdate).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("检查更新")
                .setMessage("当前版本：v$version\n本应用为本地版本，暂无在线更新服务。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }
}
