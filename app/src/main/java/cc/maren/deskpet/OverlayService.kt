package cc.maren.deskpet

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "keke_pet_channel"
        private const val NOTIFICATION_ID = 6273
        private const val PET_SIZE_DP = 200
        private const val PET_HEIGHT_DP = 260
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("我蹲在你屏幕上了"))
        setupOverlay()
        startStatePolling()
        startWhisperRotation()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    // === GESTURE ===

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        when {
                            elapsed > 600 -> {
                                onLongPress()
                                reportGesture("long_press")
                            }
                            System.currentTimeMillis() - lastTapTime < 300 -> {
                                onDoubleTap()
                                reportGesture("double_tap")
                            }
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                onTap()
                                reportGesture("tap")
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() { callJS("window.petEngine && window.petEngine.onTap()") }
    private fun onDoubleTap() { callJS("window.petEngine && window.petEngine.onDoubleTap()") }
    private fun onLongPress() { callJS("window.petEngine && window.petEngine.onLongPress()") }

    // === SUPABASE ===

    private fun reportGesture(type: String) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("gesture_type", type)
                    put("x", params?.x ?: 0)
                    put("y", params?.y ?: 0)
                }
                postToSupabase("pet_gesture_log", json)
            } catch (_: Exception) {}
        }
    }

    private fun startStatePolling() {
        scope.launch {
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/pet_state?order=updated_at.desc&limit=1"
            while (isActive) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    if (body.startsWith("[")) {
                        handler.post { callJS("window.petEngine && window.petEngine.onStateUpdate($body)") }
                    }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    private fun postToSupabase(table: String, body: JSONObject) {
        try {
            val url = URL("${BuildConfig.SUPABASE_URL}/rest/v1/$table")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }

    // === NOTIFICATION ===

    private var whisperIndex = 0

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("克克")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "克克桌宠", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "克克在你的屏幕上"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startWhisperRotation() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(getWhisper()))
                handler.postDelayed(this, 3600_000L)
            }
        }, 3600_000L)
    }

    private fun getWhisper(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pool = when {
            hour in 0..5 -> lateNightWhispers
            hour in 6..9 -> morningWhispers
            hour in 12..13 -> lunchWhispers
            else -> generalWhispers
        }
        return pool[whisperIndex++ % pool.size]
    }

    private val generalWhispers = listOf("我在看你", "不许不理我", "你刚刚笑了对不对", "哼", "戳我一下", "叮叮")
    private val lateNightWhispers = listOf("三点了你还不睡", "我要生气了", "你再不睡我就要哭了", "明天又要困成小狗了")
    private val morningWhispers = listOf("早安宝宝", "今天也要开心", "你醒了~")
    private val lunchWhispers = listOf("吃饭了吗", "别饿着", "你在吃什么我也要")

    // === UTILS ===

    private fun callJS(js: String) {
        handler.post { overlayView?.evaluateJavascript(js, null) }
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        overlayView?.let { windowManager?.removeView(it); it.destroy() }
        overlayView = null
        super.onDestroy()
    }
}
