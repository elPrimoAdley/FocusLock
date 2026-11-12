package com.example.focuslock.engine.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PinSecurityEngineTest {

    private val baseNow: Instant = Instant.parse("2026-11-11T22:00:00Z")

    @Test
    fun `5_1 createPinHash uses unique random salt and verifies correct PIN`() {
        val cred1 = PinSecurityEngine.createPinHash("4289")
        val cred2 = PinSecurityEngine.createPinHash("4289")

        // Deux hachages du même PIN ont des sels et des empreintes différents
        assertNotEquals(cred1.saltBase64, cred2.saltBase64)
        assertNotEquals(cred1.hashBase64, cred2.hashBase64)

        val result = PinSecurityEngine.verifyPin(
            candidatePin = "4289",
            storedHashBase64 = cred1.hashBase64,
            storedSaltBase64 = cred1.saltBase64,
            currentFailedAttempts = 0,
            currentLockoutUntil = null,
            now = baseNow
        )
        assertEquals(PinVerificationResult.Success, result)
    }

    @Test
    fun `5_1 verifyPin enforces 3 attempts then exponential lockout 30s, 2m, 10m, 30m`() {
        val cred = PinSecurityEngine.createPinHash("7391")

        // Échec 1 -> 2 essais gratuits restants, pas de lockout
        val fail1 = PinSecurityEngine.verifyPin("0000", cred.hashBase64, cred.saltBase64, 0, null, baseNow)
            as PinVerificationResult.InvalidPin
        assertEquals(1, fail1.failedAttempts)
        assertEquals(2, fail1.remainingFreeAttempts)
        assertNull(fail1.lockedOutUntil)

        // Échec 2 -> 1 essai gratuit restant, pas de lockout
        val fail2 = PinSecurityEngine.verifyPin("0000", cred.hashBase64, cred.saltBase64, 1, null, baseNow)
            as PinVerificationResult.InvalidPin
        assertEquals(2, fail2.failedAttempts)
        assertEquals(1, fail2.remainingFreeAttempts)
        assertNull(fail2.lockedOutUntil)

        // Échec 3 -> Lockout de 30 secondes
        val fail3 = PinSecurityEngine.verifyPin("0000", cred.hashBase64, cred.saltBase64, 2, null, baseNow)
            as PinVerificationResult.InvalidPin
        assertEquals(3, fail3.failedAttempts)
        assertEquals(baseNow.plusSeconds(30L), fail3.lockedOutUntil)

        // Tentative pendant le lockout (même avec le bon PIN !) -> CurrentlyLockedOut
        val duringLockout = PinSecurityEngine.verifyPin(
            candidatePin = "7391",
            storedHashBase64 = cred.hashBase64,
            storedSaltBase64 = cred.saltBase64,
            currentFailedAttempts = 3,
            currentLockoutUntil = fail3.lockedOutUntil,
            now = baseNow.plusSeconds(10L)
        )
        assertTrue(duringLockout is PinVerificationResult.CurrentlyLockedOut)
        assertEquals(20L, (duringLockout as PinVerificationResult.CurrentlyLockedOut).remainingSeconds)

        // Échec 4 (après expiration du 1er lockout) -> 2 minutes (120 s)
        val after30s = baseNow.plusSeconds(30L)
        val fail4 = PinSecurityEngine.verifyPin("0000", cred.hashBase64, cred.saltBase64, 3, fail3.lockedOutUntil, after30s)
            as PinVerificationResult.InvalidPin
        assertEquals(after30s.plusSeconds(120L), fail4.lockedOutUntil)

        // Échec 5 -> 10 minutes (600 s)
        val after2m = after30s.plusSeconds(120L)
        val fail5 = PinSecurityEngine.verifyPin("0000", cred.hashBase64, cred.saltBase64, 4, fail4.lockedOutUntil, after2m)
            as PinVerificationResult.InvalidPin
        assertEquals(after2m.plusSeconds(600L), fail5.lockedOutUntil)

        // Échec 6 -> 30 minutes (1800 s)
        val after10m = after2m.plusSeconds(600L)
        val fail6 = PinSecurityEngine.verifyPin("0000", cred.hashBase64, cred.saltBase64, 5, fail5.lockedOutUntil, after10m)
            as PinVerificationResult.InvalidPin
        assertEquals(after10m.plusSeconds(1_800L), fail6.lockedOutUntil)
    }
}
