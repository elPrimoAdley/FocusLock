package com.example.focuslock.domain.vault

import com.example.focuslock.data.local.dao.SavedContentDao
import com.example.focuslock.data.local.entity.SavedContentEntity
import com.example.focuslock.data.repository.RoomSavedContentRepository
import com.example.focuslock.domain.SavedPost
import com.example.focuslock.domain.repository.VaultSaveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VaultQueryAndRepositoryTest {

    private val samplePosts = listOf(
        SavedPost(
            id = "1",
            sourceUrl = "https://www.instagram.com/reel/Esp1/",
            extractedCaption = "Vocabulaire espagnol avancé",
            userTitle = "Études des subjonctifs",
            personalNote = "À réviser avant vendredi",
            tag = "À étudier",
            savedAt = Instant.ofEpochMilli(1_000L),
            isReviewed = false,
            isFavorite = true
        ),
        SavedPost(
            id = "2",
            sourceUrl = "https://www.instagram.com/reel/Spo2/",
            extractedCaption = "Routine mobilité épaules",
            userTitle = "Échauffement calisthenics",
            personalNote = null,
            tag = "Sport",
            savedAt = Instant.ofEpochMilli(2_000L),
            isReviewed = true,
            isFavorite = false
        ),
        SavedPost(
            id = "3",
            sourceUrl = "https://www.instagram.com/p/Biz3/",
            extractedCaption = "Stratégie SaaS B2B",
            userTitle = "Études de marché SaaS",
            personalNote = "Idée de tarification",
            tag = "Business",
            savedAt = Instant.ofEpochMilli(3_000L),
            isReviewed = false,
            isFavorite = false
        )
    )

    @Test
    fun `4_4 VaultQuery finds Etudes with unaccented etudes and combines tag, status, and search`() {
        // 1. "Études" trouvé par "etudes" sans accent ni majuscule
        val bySearch = VaultQuery.filter(samplePosts, searchQuery = "etudes")
        assertEquals(2, bySearch.size)
        assertEquals(setOf("1", "3"), bySearch.map { it.id }.toSet())

        // 2. Combinaison tag ("Business") + recherche ("etudes")
        val combined = VaultQuery.filter(
            posts = samplePosts,
            selectedTag = "Business",
            searchQuery = "etudes"
        )
        assertEquals(1, combined.size)
        assertEquals("3", combined.first().id)

        // 3. Recherche sur tag accentué ("a etudier" trouve "À étudier") + filtre Favoris
        val favToStudy = VaultQuery.filter(
            posts = samplePosts,
            selectedTag = "a etudier",
            statusFilter = VaultStatusFilter.FAVORITES
        )
        assertEquals(1, favToStudy.size)
        assertEquals("1", favToStudy.first().id)

        // 4. Filtre Consultés (isReviewed = true) vs À voir (isReviewed = false)
        assertEquals(listOf("2"), VaultQuery.filter(samplePosts, statusFilter = VaultStatusFilter.REVIEWED).map { it.id })
        assertEquals(setOf("1", "3"), VaultQuery.filter(samplePosts, statusFilter = VaultStatusFilter.TO_WATCH).map { it.id }.toSet())
    }

    @Test
    fun `4_4 RoomSavedContentRepository rejects duplicate sourceUrl and increments openCount while marking reviewed`() = runTest {
        val fakeDao = InMemorySavedContentDao()
        val repository = RoomSavedContentRepository(fakeDao)

        val firstPost = samplePosts[0]
        val duplicateUrlPost = firstPost.copy(
            id = "duplicate-id",
            userTitle = "Titre différent mais même URL normalisée"
        )

        // 1. Première insertion -> Saved
        val firstResult = repository.insertIfAbsent(firstPost)
        assertTrue(firstResult is VaultSaveResult.Saved)

        // 2. Deuxième insertion avec la même sourceUrl -> Duplicate rejeté !
        val secondResult = repository.insertIfAbsent(duplicateUrlPost)
        assertTrue(secondResult is VaultSaveResult.Duplicate)
        assertEquals("1", (secondResult as VaultSaveResult.Duplicate).existingPost.id)

        // 3. Ouverture du post -> incrémente openCount à 1 et passe automatiquement isReviewed à true (Décision #12 & #13)
        val afterOpen1 = repository.incrementOpenCountAndMarkReviewed("1")
        assertEquals(1, afterOpen1?.openCount)
        assertEquals(true, afterOpen1?.isReviewed)

        val afterOpen2 = repository.incrementOpenCountAndMarkReviewed("1")
        assertEquals(2, afterOpen2?.openCount)
    }

    private class InMemorySavedContentDao : SavedContentDao {
        private val items = LinkedHashMap<String, SavedContentEntity>()
        private val flow = MutableStateFlow<List<SavedContentEntity>>(emptyList())

        override fun observeAllSavedContents(): Flow<List<SavedContentEntity>> = flow

        override suspend fun getSavedContentById(id: String): SavedContentEntity? = items[id]

        override suspend fun getSavedContentBySourceUrl(sourceUrl: String): SavedContentEntity? =
            items.values.firstOrNull { it.sourceUrl == sourceUrl }

        override suspend fun insertSavedContent(content: SavedContentEntity): Long {
            if (items.values.any { it.sourceUrl == content.sourceUrl }) {
                throw IllegalStateException("UNIQUE constraint failed: saved_contents.sourceUrl")
            }
            items[content.id] = content
            flow.value = items.values.toList()
            return 1L
        }

        override suspend fun upsertSavedContent(content: SavedContentEntity) {
            items[content.id] = content
            flow.value = items.values.toList()
        }

        override suspend fun updateTitleTagAndNote(id: String, userTitle: String, tag: String, personalNote: String?) {
            val cur = items[id] ?: return
            items[id] = cur.copy(userTitle = userTitle, tag = tag, personalNote = personalNote)
            flow.value = items.values.toList()
        }

        override suspend fun updateReviewedStatus(id: String, isReviewed: Boolean) {
            val cur = items[id] ?: return
            items[id] = cur.copy(isReviewed = isReviewed)
            flow.value = items.values.toList()
        }

        override suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) {
            val cur = items[id] ?: return
            items[id] = cur.copy(isFavorite = isFavorite)
            flow.value = items.values.toList()
        }

        override suspend fun incrementOpenCountAndMarkReviewed(id: String) {
            val cur = items[id] ?: return
            items[id] = cur.copy(openCount = cur.openCount + 1, isReviewed = true)
            flow.value = items.values.toList()
        }

        override suspend fun deleteSavedContentById(id: String) {
            items.remove(id)
            flow.value = items.values.toList()
        }
    }
}
