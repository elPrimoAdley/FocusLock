package com.example.focuslock.domain.vault

import java.net.URI

data class ParsedInstagramShare(
    val normalizedUrl: String,
    val postCode: String,
    val postType: String, // "p", "reel", "tv"
    val extractedCaption: String?
)

object InstagramUrlParser {

    const val MAX_CAPTION_LENGTH: Int = 2_000

    private val ALLOWED_HOSTS = setOf(
        "instagram.com",
        "www.instagram.com",
        "instagr.am"
    )

    private val ALLOWED_PATH_TYPES = setOf("p", "reel", "reels", "tv")

    private val POST_CODE_REGEX = Regex("^[A-Za-z0-9_-]{3,64}$")

    // Repère les candidats URL dans un texte brut partagé
    private val URL_TOKEN_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /**
     * Extrait, valide et normalise le premier lien de publication Instagram valide présent dans [rawSharedText].
     * Retourne null si aucun lien Instagram valide (/p/, /reel/, /reels/, /tv/) n'est trouvé.
     */
    fun parseSharedText(rawSharedText: String): ParsedInstagramShare? {
        if (rawSharedText.isBlank()) return null

        val matches = URL_TOKEN_REGEX.findAll(rawSharedText).toList()
        if (matches.isEmpty()) return null

        var chosenMatch: MatchResult? = null
        var parsedResult: Triple<String, String, String>? = null

        for (match in matches) {
            val cleanCandidate = match.value.trimEnd('.', ',', ';', ')', ']', '!', '?')
            val validated = validateAndNormalizeUrl(cleanCandidate)
            if (validated != null) {
                chosenMatch = match
                parsedResult = validated
                break
            }
        }

        val (normalizedUrl, canonicalType, postCode) = parsedResult ?: return null
        val matchToRemove = chosenMatch ?: return null

        // Supprime uniquement l'URL retenue du texte partagé pour produire la légende (extractedCaption)
        val rawBefore = rawSharedText.substring(0, matchToRemove.range.first)
        val rawAfter = rawSharedText.substring(matchToRemove.range.last + 1)
        val combinedCaption = (rawBefore + " " + rawAfter)
            .replace(Regex("""\s+"""), " ")
            .trim()

        val truncatedCaption = combinedCaption
            .takeIf { it.isNotEmpty() }
            ?.take(MAX_CAPTION_LENGTH)

        return ParsedInstagramShare(
            normalizedUrl = normalizedUrl,
            postCode = postCode,
            postType = canonicalType,
            extractedCaption = truncatedCaption
        )
    }

    /**
     * Valide une URL brute et retourne Triple(normalizedUrl, canonicalType, postCode) ou null.
     */
    private fun validateAndNormalizeUrl(candidateUrl: String): Triple<String, String, String>? {
        val uri = runCatching { URI(candidateUrl) }.getOrNull() ?: return null

        // 1. Schéma strictly https
        if (!uri.scheme.equals("https", ignoreCase = true)) return null

        // 2. Interdiction de tout userInfo (contre https://instagram.com@evil.com/p/x)
        if (uri.rawUserInfo != null || uri.userInfo != null) return null

        // 3. Validation stricte de l'hôte (contre instagram.com.evil.net et evilinstagram.com)
        val host = uri.host?.lowercase() ?: return null
        if (host !in ALLOWED_HOSTS) return null

        // 4. Analyse des segments du chemin : /{type}/{code}
        val pathSegments = (uri.path ?: "")
            .split('/')
            .filter { it.isNotBlank() }

        if (pathSegments.size < 2) return null

        val rawType = pathSegments[0].lowercase()
        if (rawType !in ALLOWED_PATH_TYPES) return null

        val postCode = pathSegments[1]
        if (!POST_CODE_REGEX.matches(postCode)) return null

        // Normalise "reels" en "reel" pour que /reel/XYZ et /reels/XYZ produisent la même URL canonique
        val canonicalType = if (rawType == "reels") "reel" else rawType
        val normalizedUrl = "https://www.instagram.com/$canonicalType/$postCode"

        return Triple(normalizedUrl, canonicalType, postCode)
    }
}
