package com.example.focuslock.data.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.focuslock.FocusLockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FocusBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as? FocusLockApplication
        if (app == null) {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = app.container
                val now = container.clock.instant()
                val state = container.gamificationStore.getSnapshot()
                if (state.secureAfterRestart) {
                    // Révoque tout déverrouillage d'urgence résiduel au redémarrage
                    container.gamificationStore.update {
                        it.copy(emergencyUnlockUntilTimestamp = null)
                    }
                    container.gamificationCoordinator.applyOverdue(now)
                }
                Log.i(TAG, "Sécurité après redémarrage initialisée ($action).")
            } catch (t: Throwable) {
                Log.e(TAG, "Erreur dans BootReceiver", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
