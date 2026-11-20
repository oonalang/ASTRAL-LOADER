package kryvex.loader

import android.content.Intent
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import java.net.NetworkInterface

class SplashActivity : AppCompatActivity() {

    init {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var progressAnimator: ValueAnimator? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var notificationPermissionRequester: NotificationPermissionRequester
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private val totalDuration = 1850L

    companion object {
        var detectedVpn: String? = null

        fun getDetectedVPN(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()?.toList()?.firstOrNull { net ->
                    net.isUp &&
                    net.interfaceAddresses.isNotEmpty() &&
                    (
                        (net.name.contains("tun", true) ||
                        net.name.contains("ppp", true) ||
                        net.name.contains("wg", true) ||
                        net.name.contains("tap", true)) &&
                        !net.name.contains("vowifi", true) &&
                        !net.name.contains("rmnet", true)
                    )
                }?.name
            } catch (e: Exception) {
                null
            }
        }

        fun showDialog(context: AppCompatActivity, title: String, message: String) {
            AlertDialog.Builder(context, R.style.DarkAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    context.finish()
                }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        setContentView(R.layout.activity_splash)
        findViewById<android.view.View>(R.id.splash_root).background = KryvexGradientDrawable()

        progressBar = findViewById(R.id.progressBarSplash)
        progressBar.progress = 0
        val splashLogo = findViewById<ImageView>(R.id.splash_logo)
        val splashTitle = findViewById<TextView>(R.id.splash_title)
        splashLogo.alpha = 0f
        splashLogo.scaleX = 0.90f
        splashLogo.scaleY = 0.90f
        splashTitle.alpha = 0f
        splashTitle.translationY = 16f * resources.displayMetrics.density
        splashLogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420L).setInterpolator(DecelerateInterpolator()).start()
        splashTitle.animate().alpha(1f).translationY(0f)
            .setStartDelay(100L).setDuration(420L).start()

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        notificationPermissionRequester = NotificationPermissionRequester(this, permissionLauncher)
        notificationPermissionRequester.request()

        animateLoading()

        handler.postDelayed({

            detectedVpn = getDetectedVPN()

            if (detectedVpn != null) {
                showDialog(
                    this,
                    "VPN DETECTED",
                    "Detected VPN Interface:\n$detectedVpn\n\nPlease disable your VPN and try again."
                )
                return@postDelayed
            }

            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )

            startActivity(
                Intent(this, MainActivity::class.java),
                options.toBundle()
            )
            finish()

        }, totalDuration)
    }

    private fun animateLoading() {
        val status = findViewById<TextView>(R.id.splash_status)
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = totalDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { value ->
                val progress = value.animatedValue as Int
                progressBar.progress = progress
                status.text = if (progress < 85) "Preparing workspace" else "Opening KRYVEX"
            }
            start()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        progressAnimator?.cancel()
        super.onDestroy()
    }
}
