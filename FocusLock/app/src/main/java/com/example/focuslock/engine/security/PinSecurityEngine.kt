package com.example.focuslock.engine.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPinCredentials(
    val hashBase64: String,
    val saltBase64: String
)

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult

    data class InvalidPin(
        val failedAttempts: Int,
        val remainingFreeAttempts: Int,
        val lockedOutUntil: Instant?
    ) : PinVerificationResult

    data class CurrentlyLockedOut(
        val lockedOutUntil: Instant,
        val remainingSeconds: Long
    ) : PinVerificationResult
}

object PinSecurityEngine {

    const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val PBKDF2_ITERATIONS = 120_000
    const val SALT_BYTES_LENGTH = 16
    const val KEY_BITS_LENGTH = 256
    const val FREE_ATTEMPTS_BEFORE_LOCKOUT = 3

    // Section 3.2 : Délais d'attente exponentiels après le 3e échec (30 s, 2 min, 10 min, 30 min)
    private val LOCKOUT_DURATIONS_SECONDS = longArrayOf(
        30L,       // 3e échec consécutif -> 30 secondes
        120L,      // 4e échec consécutif -> 2 minutes
        600L,      // 5e échec consécutif -> 10 minutes
        1_800L     // 6e échec et suivants -> 30 minutes
    )

    private val secureRandom = SecureRandom()

    fun isValidPinFormat(rawPin: String): Boolean {
        return rawPin.length in 4..8 && rawPin.all { it.isDigit() }
    }

    /**
     * Génère un sel cryptographique aléatoire et hache le code PIN avec PBKDF2-HMAC-SHA256.
     */
    fun createPinHash(rawPin: String): HashedPinCredentials {
        require(isValidPinFormat(rawPin)) { "Le code PIN doit contenir entre 4 et 8 chiffres." }
        val salt = ByteArray(SALT_BYTES_LENGTH).also { secureRandom.nextBytes(it) }
        val hash = pbkdf2(rawPin, salt)
        return HashedPinCredentials(
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            saltBase64 = Base64.getEncoder().encodeToString(salt)
        )
    }

    /**
     * Vérifie un code PIN en temps constant et gère le compteur d'échecs + délai exponentiel.
     */
    fun verifyPin(
        candidatePin: String,
        storedHashBase64: String,
        storedSaltBase64: String,
        currentFailedAttempts: Int,
        currentLockoutUntil: Instant?,
        now: Instant
    ): PinVerificationResult {
        // 1. Vérifie si un verrouillage anti-brute-force est actuellement en cours
        if (currentLockoutUntil != null && now.isBefore(currentLockoutUntil)) {
            val remainingSec = (currentLockoutUntil.epochSecond - now.epochSecond).coerceAtLeast(1L)
            return PinVerificationResult.CurrentlyLockedOut(
                lockedOutUntil = currentLockoutUntil,
                remainingSeconds = remainingSec
            )
        }

        val salt = runCatching { Base64.getDecoder().decode(storedSaltBase64) }.getOrNull()
        val expectedHash = runCatching { Base64.getDecoder().decode(storedHashBase64) }.getOrNull()

        val isMatch = if (salt != null && expectedHash != null && candidatePin.isNotEmpty()) {
            val candidateHash = pbkdf2(candidatePin, salt)
            MessageDigest.isEqual(candidateHash, expectedHash)
        } else {
            false
        }

        if (isMatch) {
            return PinVerificationResult.Success
        }

        // 2. Échec : incrémente le compteur et calcule le délai exponentiel à partir du 3e essai
        val newFailedAttempts = currentFailedAttempts.coerceAtLeast(0) + 1
        val lockedOutUntil = computeLockoutUntil(newFailedAttempts, now)
        val remainingFree = (FREE_ATTEMPTS_BEFORE_LOCKOUT - newFailedAttempts).coerceAtLeast(0)

        return PinVerificationResult.InvalidPin(
            failedAttempts = newFailedAttempts,
            remainingFreeAttempts = remainingFree,
            lockedOutUntil = lockedOutUntil
        )
    }

    /**
     * Calcule l'horodatage de fin de blocage selon le nombre d'échecs consécutifs.
     * - 1 ou 2 échecs : null (pas encore de blocage temporel)
     * - 3 échecs : now + 30 s
     * - 4 échecs : now + 2 min (120 s)
     * - 5 échecs : now + 10 min (600 s)
     * - >= 6 échecs : now + 30 min (1800 s)
     */
    fun computeLockoutUntil(failedAttempts: Int, now: Instant): Instant? {
        if (failedAttempts < FREE_ATTEMPTS_BEFORE_LOCKOUT) return null
        val tierIndex = (failedAttempts - FREE_ATTEMPTS_BEFORE_LOCKOUT)
            .coerceIn(0, LOCKOUT_DURATIONS_SECONDS.lastIndex)
        return now.plusSeconds(LOCKOUT_DURATIONS_SECONDS[tierIndex])
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
