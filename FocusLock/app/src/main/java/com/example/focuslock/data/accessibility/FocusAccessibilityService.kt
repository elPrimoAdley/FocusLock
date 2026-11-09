package com.example.focuslock.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.focuslock.FocusLockApplication
import com.example.focuslock.ui.overlay.BlockOverlayContent
import com.example.focuslock.ui.overlay.OverlayLifecycleOwner
import com.example.focuslock.ui.theme.FocusLockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusAccessService"

        private val STATIC_IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceScope: CoroutineScope? = null
    private var coordinator: BlockEnforcementCoordinator? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val app = applicationContext as FocusLockApplication
            val container = app.container

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            serviceScope = scope

            coordinator = BlockEnforcementCoordinator(
                scope = scope,
                clock = container.clock,
                packageManager = packageManager,
                detector = container.foregroundAppDetector,
                usageStatsTracker = container.usageStatsTracker,
                ruleRepository = container.ruleRepository,
                questTaskRepository = container.questTaskRepository,
                focusSessionRepository = container.focusSessionRepository,
                blockAuditRepository = container.blockAuditRepository,
                onBlockTriggered = {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    mainHandler.post { showOverlayIfNeeded() }
                },
                onDismissOverlay = {
                    mainHandler.post { dismissOverlay() }
                }
            ).also { it.start() }

            Log.i(TAG, "FocusAccessibilityService connecté et abonné au BlockEnforcementCoordinator.")
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur dans onServiceConnected", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return
            }
            val pkgName = event.packageName?.toString() ?: return
            if (shouldIgnorePackage(pkgName)) return

            Log.d(TAG, "Package détecté : $pkgName")
            val app = applicationContext as FocusLockApplication
            app.container.foregroundAppDetector.emitForegroundPackage(pkgName)
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur dans onAccessibilityEvent", t)
        }
    }

    private fun shouldIgnorePackage(pkgName: String): Boolean {
        if (pkgName == packageName) return true
        if (pkgName in STATIC_IGNORED_PACKAGES) return true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val imePackages = imm?.enabledInputMethodList?.map { it.packageName } ?: emptyList()
        return pkgName in imePackages
    }

    private fun showOverlayIfNeeded() {
        try {
            if (overlayView != null) return
            val coord = coordinator ?: return
            val wm = windowManager ?: return

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            val lifecycleOwner = OverlayLifecycleOwner().also {
                it.onCreate()
                overlayLifecycleOwner = it
            }

            val composeView = ComposeView(this).apply {
                lifecycleOwner.attachToView(this)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val uiModel by coord.overlayState.collectAsState()
                    val currentModel = uiModel
                    if (currentModel != null) {
                        FocusLockTheme {
                            BlockOverlayContent(
                                model = currentModel,
                                onToggleChecklistItem = { task ->
                                    coord.toggleRoutineChecklistItem(task)
                                },
                                onDismiss = {
                                    coord.clearOverlayState()
                                    dismissOverlay()
                                }
                            )
                        }
                    }
                }
            }

            overlayView = composeView
            wm.addView(composeView, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur lors de l'affichage de l'overlay", t)
            cleanupOverlayResources()
        }
    }

    private fun dismissOverlay() {
        try {
            val view = overlayView ?: return
            (view as? ComposeView)?.disposeComposition()
            windowManager?.removeViewImmediate(view)
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur lors de la fermeture de l'overlay", t)
        } finally {
            cleanupOverlayResources()
        }
    }

    private fun cleanupOverlayResources() {
        try {
            overlayLifecycleOwner?.onDestroy()
        } catch (_: Throwable) {}
        overlayLifecycleOwner = null
        overlayView = null
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService interrompu.")
    }

    override fun onDestroy() {
        dismissOverlay()
        serviceScope?.cancel()
        serviceScope = null
        coordinator = null
        super.onDestroy()
    }
}