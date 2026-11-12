package com.example.focuslock.domain.vault

import com.example.focuslock.domain.SavedPost
import java.text.Normalizer
import java.util.Locale

enum class VaultStatusFilter(val label: String) {
    ALL("Tous"),
    TO_WATCH("À voir"),      // isReviewed == false (Décision #12)
    REVIEWED("Consultés"),   // isReviewed == true  (Décision #12)
    FAVORITES("Favoris")     // isFavorite == true  (Décision #12)
}

object VaultTags {
    // Décision #12 : Union des tags cités dans le cahier
    val PRESET_TAGS: List<String> = listOf(
        "Langues",
        "Sport",
        "Business",
        "Inspiration",
        "À étudier"
    )
}

object VaultQuery {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    /**
     * Normalise une chaîne pour la recherche : décomposition NFD, suppression des accents,
     * passage en minuscules et réduction des espaces multiples.
     */
    fun normalizeForSearch(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /**
     * 4.4 : Filtre pur par tag, par statut (Tous / À voir / Consultés / Favoris)
     * et par recherche textuelle insensible aux accents et aux majuscules.
     */
    fun filter(
        posts: List<SavedPost>,
        selectedTag: String? = null,
        statusFilter: VaultStatusFilter = VaultStatusFilter.ALL,
        searchQuery: String = ""
    ): List<SavedPost> {
        val normalizedSelectedTag = selectedTag?.takeIf { it.isNotBlank() }?.let(::normalizeForSearch)
        val normalizedQuery = normalizeForSearch(searchQuery)

        return posts.filter { post ->
            // 1. Filtre par tag
            val matchesTag = normalizedSelectedTag == null ||
                normalizeForSearch(post.tag) == normalizedSelectedTag

            // 2. Filtre par statut (Décision #12)
            val matchesStatus = when (statusFilter) {
                VaultStatusFilter.ALL -> true
                VaultStatusFilter.TO_WATCH -> !post.isReviewed
                VaultStatusFilter.REVIEWED -> post.isReviewed
                VaultStatusFilter.FAVORITES -> post.isFavorite
            }

            // 3. Filtre par recherche textuelle (titre, légende extraite, note personnelle, tag)
            val matchesSearch = if (normalizedQuery.isEmpty()) {
                true
            } else {
                normalizeForSearch(post.userTitle).contains(normalizedQuery) ||
                    (post.extractedCaption != null && normalizeForSearch(post.extractedCaption).contains(normalizedQuery)) ||
                    (post.personalNote != null && normalizeForSearch(post.personalNote).contains(normalizedQuery)) ||
                    normalizeForSearch(post.tag).contains(normalizedQuery)
            }

            matchesTag && matchesStatus && matchesSearch
        }
    }
}
