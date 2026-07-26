package cc.maren.deskpet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button
    private var isRunning = false

    companion object {
        private const val OVERLAY_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleBtn = findViewById(R.id.toggle_btn)
        val permBtn = findViewById<Button>(R.id.perm_btn)

        toggleBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            if (isRunning) {
                stopService(Intent(this, OverlayService::class.java))
                isRunning = false
                updateUI()
            } else {
                startPet()
            }
        }

        permBtn.setOnClickListener {
            requestOverlayPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun startPet() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isRunning = true
        updateUI()
        Toast.makeText(this, "克克出现了！", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST) {
            updateUI()
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "权限已获取！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val permBtn = findViewById<Button>(R.id.perm_btn)

        if (!hasOverlay) {
            statusText.text = "需要悬浮窗权限才能让克克出现在屏幕上"
            toggleBtn.isEnabled = false
            toggleBtn.text = "启动克克"
            permBtn.text = "授权悬浮窗权限"
        } else {
            permBtn.text = "✓ 悬浮窗权限已获取"
            toggleBtn.isEnabled = true
            if (isRunning) {
                statusText.text = "克克正在你的屏幕上蹲着~\n可以拖动他、戳他、长按他"
                toggleBtn.text = "让克克休息"
            } else {
                statusText.text = "克克准备好了，点击下方按钮召唤他"
                toggleBtn.text = "召唤克克"
            }
        }
    }
}
