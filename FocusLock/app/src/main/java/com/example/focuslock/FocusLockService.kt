package com.example.focuslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.math.max

class FocusLockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var reasonTextView: TextView? = null
    private var isOverlayShowing = false

    companion object {
        const val ACTION_START_FOCUS = "ACTION_START_FOCUS"
        const val ACTION_STOP_FOCUS = "ACTION_STOP_FOCUS"
        const val ACTION_TEST_OVERLAY = "ACTION_TEST_OVERLAY"
        const val EXTRA_DURATION_MINUTES = "EXTRA_DURATION_MINUTES"

        var isFocusActive = false
        var remainingSeconds = 0L
        var currentBlockReason = "Session Focus en cours"
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isFocusActive && remainingSeconds > 0) {
                remainingSeconds--
                updateOverlayTimer()
                if (remainingSeconds <= 0) {
                    isFocusActive = false
                    removeOverlay()
                }
            }
            checkForegroundAppAndBlock()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val mins = intent.getIntExtra(EXTRA_DURATION_MINUTES, 25)
                remainingSeconds = mins * 60L
                isFocusActive = true
                currentBlockReason = "Contrat d'apprentissage actif ($mins min)"
                showOverlayIfPossible(currentBlockReason)
            }
            ACTION_TEST_OVERLAY -> {
                if (remainingSeconds <= 0) remainingSeconds = 60L
                isFocusActive = true
                currentBlockReason = "Test en direct de l'écran de blocage"
                showOverlayIfPossible(currentBlockReason)
            }
            ACTION_STOP_FOCUS -> {
                isFocusActive = false
                remainingSeconds = 0L
                removeOverlay()
            }
        }
        return START_STICKY
    }

    private fun checkForegroundAppAndBlock() {
        if (!Settings.canDrawOverlays(this)) return

        val fgPackage = getForegroundPackage() ?: return
        if (fgPackage == packageName || fgPackage.contains("launcher") || fgPackage.contains("systemui")) {
            return
        }

        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val ruleMode = prefs.getString("rule_$fgPackage", "NONE") ?: "NONE"

        // 1. Si une session Focus stricte tourne, tout hors liste blanche est bloqué
        if (isFocusActive) {
            currentBlockReason = "Focus actif • Reste concentré sur ta tâche"
            showOverlayIfPossible(currentBlockReason)
            return
        }

        // 2. Vérification des règles spécifiques par application (Détox 1j/2, Pause Sabbatique, Quota)
        when (ruleMode) {
            "ALTERNATING_1_2" -> {
                val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                // Bloque les jours impairs (1 jour sur 2)
                if (dayOfYear % 2 != 0) {
                    currentBlockReason = "Détox 1j/2 • Journée de pause pour cette application"
                    showOverlayIfPossible(currentBlockReason)
                }
            }
            "SABBATICAL_7D" -> {
                val until = prefs.getLong("sabbatical_until_$fgPackage", 0L)
                if (System.currentTimeMillis() < until) {
                    val daysLeft = max(1L, (until - System.currentTimeMillis()) / (1000 * 60 * 60 * 24))
                    currentBlockReason = "Pause Sabbatique active • Encore $daysLeft j de détox"
                    showOverlayIfPossible(currentBlockReason)
                }
            }
            "ALWAYS_BLOCKED" -> {
                currentBlockReason = "Application verrouillée par tes règles de discipline"
                showOverlayIfPossible(currentBlockReason)
            }
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - 10000, time)
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    private fun showOverlayIfPossible(reason: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (isOverlayShowing) {
            reasonTextView?.text = reason
            updateOverlayTimer()
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(72, 96, 72, 96)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#050505"),
                    Color.parseColor("#141A28"),
                    Color.parseColor("#2A1810")
                )
            )
        }

        val badge = TextView(this).apply {
            text = "🔒 MODE DISCIPLINE ACTIF"
            setTextColor(Color.parseColor("#FFC773"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(36, 16, 36, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFC773"))
                cornerRadius = 50f
            }
        }

        val title = TextView(this).apply {
            text = "C'est ton temps.\nReste concentré."
            setTextColor(Color.parseColor("#F5F1E8"))
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }

        timerTextView = TextView(this).apply {
            text = formatSeconds(remainingSeconds)
            setTextColor(Color.parseColor("#FF8B5E"))
            textSize = 54f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        reasonTextView = TextView(this).apply {
            text = reason
            setTextColor(Color.parseColor("#9DA7B8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(24, 0, 24, 64)
        }

        val homeBtn = Button(this).apply {
            text = "Revenir à l'écran d'accueil"
            setTextColor(Color.parseColor("#2E1607"))
            textSize = 15f
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FFC773"), Color.parseColor("#FF8B5E"))
            ).apply { cornerRadius = 40f }
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                if (!isFocusActive) removeOverlay()
            }
        }

        val pinInput = EditText(this).apply {
            hint = "Code PIN d'urgence (défaut: 4289)"
            setHintTextColor(Color.parseColor("#6E7787"))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val unlockBtn = Button(this).apply {
            text = "Déverrouiller (Urgence)"
            setTextColor(Color.parseColor("#9DA7B8"))
            setBackgroundColor(Color.TRANSPARENT)
            isAllCaps = false
            setOnClickListener {
                val savedPin = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                    .getString("strict_pin", "4289") ?: "4289"
                if (pinInput.text.toString() == savedPin) {
                    isFocusActive = false
                    remainingSeconds = 0L
                    removeOverlay()
                } else {
                    Toast.makeText(context, "Code PIN incorrect", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(badge)
        root.addView(title)
        root.addView(timerTextView)
        root.addView(reasonTextView)
        root.addView(homeBtn)
        root.addView(pinInput)
        root.addView(unlockBtn)

        overlayView = root
        windowManager?.addView(overlayView, params)
        isOverlayShowing = true
    }

    private fun updateOverlayTimer() {
        timerTextView?.text = if (remainingSeconds > 0) {
            formatSeconds(remainingSeconds)
        } else {
            "PAUSE"
        }
    }

    private fun removeOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
            isOverlayShowing = false
        }
    }

    private fun formatSeconds(totalSeconds: Long): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "focus_lock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Protection FocusLock Active",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("FocusLock • Bouclier actif")
                .setContentText("Surveillance des distractions et contrats de temps")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("FocusLock • Bouclier actif")
                .setContentText("Surveillance des distractions")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .build()
        }

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}