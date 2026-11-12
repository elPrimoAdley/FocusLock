package com.example.focuslock.domain.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramUrlParserTest {

    @Test
    fun `4_1 parses p, reel, reels, tv and normalizes URLs without tracking params`() {
        val post = InstagramUrlParser.parseSharedText("https://www.instagram.com/p/C9abc_12-3/?igsh=MzRlODBiNWFlZA==")
        assertNotNull(post)
        assertEquals("https://www.instagram.com/p/C9abc_12-3", post!!.normalizedUrl)
        assertEquals("C9abc_12-3", post.postCode)
        assertEquals("p", post.postType)
        assertNull(post.extractedCaption)

        val reel = InstagramUrlParser.parseSharedText("https://instagram.com/reel/D1xyz99/?utm_source=ig_web_copy_link&igsh=123")
        assertNotNull(reel)
        assertEquals("https://www.instagram.com/reel/D1xyz99", reel!!.normalizedUrl)
        assertEquals("D1xyz99", reel.postCode)
        assertEquals("reel", reel.postType)

        // /reels/ et /reel/ produisent exactement la même URL normalisée pour la détection de doublon
        val reels = InstagramUrlParser.parseSharedText("https://instagr.am/reels/D1xyz99/")
        assertNotNull(reels)
        assertEquals("https://www.instagram.com/reel/D1xyz99", reels!!.normalizedUrl)
        assertEquals("D1xyz99", reels.postCode)

        val tv = InstagramUrlParser.parseSharedText("https://www.instagram.com/tv/TV_code_01?utm_medium=copy_link")
        assertNotNull(tv)
        assertEquals("https://www.instagram.com/tv/TV_code_01", tv!!.normalizedUrl)
        assertEquals("tv", tv.postType)
    }

    @Test
    fun `4_1 extracts surrounding text as caption and handles multiple URLs`() {
        val shared = "Super exercice de mobilité épaules : https://www.instagram.com/reel/Mob123/?igsh=abc à essayer demain ! https://example.com/other"
        val parsed = InstagramUrlParser.parseSharedText(shared)
        assertNotNull(parsed)
        assertEquals("https://www.instagram.com/reel/Mob123", parsed!!.normalizedUrl)
        assertEquals("Mob123", parsed.postCode)
        assertEquals(
            "Super exercice de mobilité épaules : à essayer demain ! https://example.com/other",
            parsed.extractedCaption
        )
    }

    @Test
    fun `4_1 rejects http scheme, fake hosts, userInfo spoofing, and text without valid post URL`() {
        // http refusé
        assertNull(InstagramUrlParser.parseSharedText("http://www.instagram.com/p/Abc1234/"))

        // Faux hôtes refusés
        assertNull(InstagramUrlParser.parseSharedText("https://instagram.com.evil.net/p/Abc1234/"))
        assertNull(InstagramUrlParser.parseSharedText("https://evilinstagram.com/p/Abc1234/"))

        // Usurpation via userInfo (@evil.com) refusée
        assertNull(InstagramUrlParser.parseSharedText("https://instagram.com@evil.com/p/Abc1234/"))

        // Profil Instagram sans /p/, /reel/, /reels/, /tv/ refusé
        assertNull(InstagramUrlParser.parseSharedText("https://www.instagram.com/username/"))

        // Texte sans URL
        assertNull(InstagramUrlParser.parseSharedText("Juste une note sans lien"))
        assertNull(InstagramUrlParser.parseSharedText("   "))
    }

    @Test
    fun `4_1 truncates very long caption to 2000 characters`() {
        val longDescription = "A".repeat(3_500)
        val shared = "$longDescription https://www.instagram.com/p/LongPost99/"
        val parsed = InstagramUrlParser.parseSharedText(shared)
        assertNotNull(parsed)
        assertEquals("https://www.instagram.com/p/LongPost99", parsed!!.normalizedUrl)
        assertEquals(2_000, parsed.extractedCaption?.length)
    }
}
