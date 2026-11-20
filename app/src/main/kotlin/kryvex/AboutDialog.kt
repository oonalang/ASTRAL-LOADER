package kryvex.loader

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class AboutDialog(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<View>(R.id.about_root).background = KryvexGradientDrawable()
        findViewById<View>(R.id.about_back).setOnClickListener { dismiss() }
        findViewById<View>(R.id.about_close_action).setOnClickListener { dismiss() }
        findViewById<View>(R.id.about_channel_action).setOnClickListener {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+eK6YlqQPkdQyZmQ1")))
            }
        }
        bindDetails()
    }

    override fun onStart() {
        super.onStart()
        window?.let { dialogWindow ->
            dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            dialogWindow.statusBarColor = ContextCompat.getColor(context, R.color.loader_background)
            dialogWindow.navigationBarColor = ContextCompat.getColor(context, R.color.loader_background)
            dialogWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        findViewById<View>(R.id.about_scroll).apply {
            alpha = 0f
            translationY = 18f * resources.displayMetrics.density
            animate().alpha(1f).translationY(0f).setDuration(260L).start()
        }
    }

    private fun bindDetails() {
        val details = listOf(
            Detail(R.id.detail_developer, R.drawable.ic_user, "Owner", "@Kryvex_official09"),
            Detail(R.id.detail_username, R.drawable.ic_about, "Platform", "Android"),
            Detail(R.id.detail_channel, R.drawable.ic_secure, "Developer", "@Revenant_Shai"),
            Detail(R.id.detail_version, R.drawable.ic_package, "Version", BuildConfig.VERSION_NAME)
        )

        details.forEach { detail ->
            val row = findViewById<View>(detail.id)
            row.findViewById<ImageView>(R.id.about_detail_icon).setImageResource(detail.icon)
            row.findViewById<TextView>(R.id.about_detail_label).text = detail.label
            row.findViewById<TextView>(R.id.about_detail_value).text = detail.value
        }
    }

    private data class Detail(
        val id: Int,
        val icon: Int,
        val label: String,
        val value: String
    )
}
