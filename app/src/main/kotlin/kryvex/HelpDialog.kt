package kryvex.loader

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.View

class HelpDialog(private val appContext: Context) {
    fun show() {
        val dialog = Dialog(appContext)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_kryvex_help)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.55f)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            (appContext.resources.displayMetrics.widthPixels * 0.89f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        val content = dialog.findViewById<View>(android.R.id.content)
        content.alpha = 0f
        content.scaleX = 0.94f
        content.scaleY = 0.94f
        dialog.findViewById<View>(R.id.help_close).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.help_telegram).setOnClickListener {
            runCatching {
                appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+eK6YlqQPkdQyZmQ1")))
            }
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setLayout(
            (appContext.resources.displayMetrics.widthPixels * 0.89f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(210L).start()
    }
}
