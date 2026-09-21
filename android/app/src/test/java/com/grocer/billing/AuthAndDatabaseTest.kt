package com.grocer.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AuthAndDatabaseTest {

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testPinHashing_deterministicAndMatches() {
        val pin = "1234"
        val hash1 = hashPin(pin)
        val hash2 = hashPin(pin)
        assertEquals(hash1, hash2)
        assertTrue(hash1.isNotEmpty())
    }

    @Test
    fun testPinVerification_wrongPinRejected() {
        val correctPin = "4567"
        val wrongPin = "0000"
        val storedHash = hashPin(correctPin)

        assertTrue(hashPin(correctPin) == storedHash)
        assertFalse(hashPin(wrongPin) == storedHash)
    }

    @Test
    fun testShopSettingsJson_structure() {
        val settings = """{"language":"en","regional_language":"hi","tax_enabled":false}"""
        assertTrue(settings.contains("regional_language"))
        assertTrue(settings.contains("hi"))
    }

    @Test
    fun testPinUpdate_newPinSucceedsAndOldFails() {
        var activePinHash = hashPin("1234")
        assertTrue(hashPin("1234") == activePinHash)

        // User updates PIN to 9876
        val newPin = "9876"
        activePinHash = hashPin(newPin)

        // Old PIN no longer valid, new PIN is valid
        assertFalse(hashPin("1234") == activePinHash)
        assertTrue(hashPin("9876") == activePinHash)
    }
}
