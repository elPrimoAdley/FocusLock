package com.example.focuslock.domain.detector

import kotlinx.coroutines.flow.Flow

interface ForegroundAppDetector {
    /**
     * Émet chaque nom de package passant au premier plan (hors système/clavier).
     */
    val foregroundPackages: Flow<String>
}