package com.example.focuslock.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.domain.store.GamificationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferencesGamificationStoreTest {

    private lateinit var testFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testFile = File(context.filesDir, "test_gamification_${System.nanoTime()}.preferences_pb")
        if (testFile.exists()) testFile.delete()
    }

    @After
    fun tearDown() {
        if (testFile.exists()) testFile.delete()
    }

    @Test
    fun roundTripAndSurvivesProcessRestartOnDisk() = runBlocking {
        val expected = GamificationState(
            totalXp = 750,
            currentStreakDays = 9,
            lastStreakEvalEpochDay = 20767L,
            quotaMalusReductionPercent = 50,
            quotaMalusEpochDay = 20767L,
            p3LateCounter = 2,
            dailyFocusGoalMinutes = 150,
            consecutiveP2Days = 3,
            lastP2CompletionEpochDay = 20767L
        )

        // 1. Première session d'application : écriture sur disque puis fermeture du scope
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds1 = PreferenceDataStoreFactory.create(scope = scope1) { testFile }
        val store1 = PreferencesGamificationStore(ds1)
        store1.update { expected }
        assertEquals(expected, store1.getSnapshot())
        scope1.cancel()

        // 2. Deuxième session d'application : réouverture depuis le même fichier physique
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds2 = PreferenceDataStoreFactory.create(scope = scope2) { testFile }
        val store2 = PreferencesGamificationStore(ds2)
        val reloaded = store2.getSnapshot()
        assertEquals(expected, reloaded)
        scope2.cancel()
    }
}