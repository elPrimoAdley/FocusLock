package com.example.focuslock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.focuslock.ui.navigation.FocusAppScaffold
import com.example.focuslock.ui.theme.FocusLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activation Edge-to-Edge sombre (1.2)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            FocusLockTheme {
                FocusAppScaffold()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 3.9 & 3.22 : Déclencheur automatique au retour de l'application au premier plan
        val container = (applicationContext as FocusLockApplication).container
        lifecycleScope.launch(Dispatchers.IO) {
            val now = container.clock.instant()
            container.focusSessionRepository.completeExpiredSessions(now)
            container.gamificationCoordinator.applyOverdue(now)
        }
    }
}