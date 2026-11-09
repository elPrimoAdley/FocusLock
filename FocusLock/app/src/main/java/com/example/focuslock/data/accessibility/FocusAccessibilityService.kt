package com.example.focuslock.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.focuslock.ui.overlay.BlockOverlayContent
import com.example.focuslock.ui.overlay.OverlayLifecycleOwner
import com.example.focuslock.ui.theme.FocusLockTheme

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusAccessService"

        // Mettre à false pour tester l'étape 2.2 (Vue Android classique),
        // ou true pour tester l'étape 2.3 (ComposeView + OverlayLifecycleOwner)
        var useComposeOverlay: Boolean = true

        // Packages cibles codés en dur pour la preuve de faisabilité (2.2 / 2.3)
        private val HARDCODED_BLOCKED_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.android.chrome"
        )

        // Packages système toujours ignorés (2.2)
        private val STATIC_IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var blockCounter = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            Log.i(TAG, "FocusAccessibilityService connecté et prêt.")
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur dans onServiceConnected", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 2.1 Try/catch global obligatoire pour éviter que le système ne désactive le service
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return
            }

            val pkgName = event.packageName?.toString() ?: return

            // Ignorer FocusLock lui-même, SystemUI, le launcher Samsung et les claviers (IME)
            if (shouldIgnorePackage(pkgName)) {
                return
            }

            // 2.1 Journalisation Logcat du package au premier plan
            Log.d(TAG, "Premier plan détecté : $pkgName")

            // 2.2 & 2.3 Interception d'un package cible codé en dur
            if (pkgName in HARDCODED_BLOCKED_PACKAGES) {
                blockCounter++
                Log.w(TAG, "Cible bloquée détectée ($pkgName) - Tentative #$blockCounter")

                // 1. Retour immédiat à l'écran d'accueil pour couper le flux audio/vidéo
                performGlobalAction(GLOBAL_ACTION_HOME)

                // 2. Affichage de la fenêtre TYPE_ACCESSIBILITY_OVERLAY sur le thread UI
                mainHandler.post {
                    showBlockOverlay(pkgName)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception interceptée dans onAccessibilityEvent", t)
        }
    }

    private fun shouldIgnorePackage(pkgName: String): Boolean {
        if (pkgName == packageName) return true
        if (pkgName in STATIC_IGNORED_PACKAGES) return true

        // Vérifie dynamiquement si le package est un clavier (IME) actif sur le téléphone
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val imePackages = imm?.enabledInputMethodList?.map { it.packageName } ?: emptyList()
        return pkgName in imePackages
    }

    private fun showBlockOverlay(blockedPackage: String) {
        try {
            if (overlayView != null) {
                // Déjà affiché à l'écran
                return
            }

            val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
                windowManager = it
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            val viewToAttach: View = if (useComposeOverlay) {
                createComposeOverlayView(blockedPackage)
            } else {
                createClassicOverlayView(blockedPackage)
            }

            overlayView = viewToAttach
            wm.addView(viewToAttach, params)
            Log.i(TAG, "Overlay attaché (Compose=$useComposeOverlay) pour $blockedPackage")
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur lors de l'affichage de l'overlay", t)
            cleanupOverlayResources()
        }
    }

    /**
     * 2.2 Vue Android classique (« Bloqué » + bouton « Fermer »).
     */
    private fun createClassicOverlayView(blockedPackage: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(72, 72, 72, 72)
            setBackgroundColor(Color.parseColor("#070B14")) // Night Abyss

            val titleView = TextView(context).apply {
                text = "Bloqué"
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 32f
                gravity = Gravity.CENTER
            }

            val subtitleView = TextView(context).apply {
                text = "Application interceptée :\n$blockedPackage"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 64)
            }

            val closeButton = Button(context).apply {
                text = "Fermer"
                setTextColor(Color.parseColor("#06281E"))
                textSize = 16f
                isAllCaps = false
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#6FE6C4"), Color.parseColor("#34D399"))
                ).apply {
                    cornerRadius = 100f
                }
                setOnClickListener {
                    dismissOverlay()
                }
            }

            addView(titleView)
            addView(subtitleView)
            addView(closeButton)
        }
    }

    /**
     * 2.3 ComposeView avec OverlayLifecycleOwner personnalisé.
     */
    private fun createComposeOverlayView(blockedPackage: String): View {
        val lifecycleOwner = OverlayLifecycleOwner().also {
            it.onCreate()
            overlayLifecycleOwner = it
        }

        return ComposeView(this).apply {
            lifecycleOwner.attachToView(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FocusLockTheme {
                    BlockOverlayContent(
                        blockedPackage = blockedPackage,
                        openCount = blockCounter,
                        onDismiss = { dismissOverlay() }
                    )
                }
            }
        }
    }

    private fun dismissOverlay() {
        try {
            val view = overlayView ?: return
            (view as? ComposeView)?.disposeComposition()
            windowManager?.removeViewImmediate(view)
            Log.i(TAG, "Overlay fermé proprement.")
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur lors de la suppression de la vue overlay", t)
        } finally {
            cleanupOverlayResources()
        }
    }

    private fun cleanupOverlayResources() {
        try {
            overlayLifecycleOwner?.onDestroy()
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur lors de la destruction de OverlayLifecycleOwner", t)
        }
        overlayLifecycleOwner = null
        overlayView = null
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService interrompu.")
    }

    override fun onDestroy() {
        dismissOverlay()
        super.onDestroy()
    }
}