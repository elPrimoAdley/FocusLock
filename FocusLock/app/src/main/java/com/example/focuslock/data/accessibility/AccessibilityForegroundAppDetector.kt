package com.example.focuslock.data.accessibility

import com.example.focuslock.domain.detector.ForegroundAppDetector
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AccessibilityForegroundAppDetector : ForegroundAppDetector {

    private val _foregroundPackages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val foregroundPackages: Flow<String> = _foregroundPackages.asSharedFlow()

    fun emitForegroundPackage(packageName: String) {
        _foregroundPackages.tryEmit(packageName)
    }
}