package com.example.focuslock.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.example.focuslock.engine.usage.RawEventType
import com.example.focuslock.engine.usage.RawUsageEvent
import java.time.Instant

class UsageEventsReader(private val context: Context) {

    /**
     * Lit les événements bruts d'Android entre [start] et [end] et les convertit en [RawUsageEvent].
     */
    fun readEvents(start: Instant, end: Instant): List<RawUsageEvent> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val rawEvents = mutableListOf<RawUsageEvent>()
        val usageEvents = usm.queryEvents(start.toEpochMilli(), end.toEpochMilli())
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val mappedType = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> RawEventType.ACTIVITY_RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> RawEventType.ACTIVITY_PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> RawEventType.ACTIVITY_STOPPED
                UsageEvents.Event.SCREEN_INTERACTIVE -> RawEventType.SCREEN_INTERACTIVE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawEventType.SCREEN_NON_INTERACTIVE
                UsageEvents.Event.DEVICE_SHUTDOWN -> RawEventType.DEVICE_SHUTDOWN
                else -> null
            } ?: continue

            rawEvents.add(
                RawUsageEvent(
                    timestamp = Instant.ofEpochMilli(event.timeStamp),
                    packageName = event.packageName,
                    activityClass = event.className,
                    type = mappedType
                )
            )
        }
        return rawEvents
    }

    /**
     * Retourne l'ensemble des packages exclus du temps d'écran (Décision #16) :
     * SystemUI, Launcher Samsung/Google, Claviers (IME) et FocusLock lui-même.
     */
    fun getExcludedScreenTimePackages(): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val dynamicImes = imm?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        return setOf(
            context.packageName,
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin"
        ) + dynamicImes
    }
}
