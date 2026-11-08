package com.example.focuslock.ui.navigation

sealed class Destination(
    val route: String,
    val title: String,
    val subtitle: String,
    val symbol: String
) {
    data object Hub : Destination("hub", "Le Hub Central", "Tableau de bord & Focus Ring", "◎")
    data object Quests : Destination("quests", "Quêtes & Tâches", "Priorités P1–P3, XP & Malus", "⚔")
    data object Rules : Destination("rules", "Règles & Détox", "Plannings, Quotas & 1j/2", "🛡")
    data object Vault : Destination("vault", "Coffre de Contenu", "Instagram hors-ligne", "📥")
    data object Insights : Destination("insights", "Statistiques & Audit", "Insights 24h & Journal local", "📊")
    data object ProfileSecurity : Destination(
        "profile_security",
        "Profil, Sécurité & Santé",
        "Mode strict, PIN & Batterie Samsung",
        "🔒"
    )
    data object GalleryDebug : Destination(
        "gallery_debug",
        "Galerie Design System",
        "Validation Phase 1.2 à 1.4",
        "🎨"
    )

    companion object {
        val mainDrawerDestinations get() = listOf(Hub, Quests, Rules, Vault, Insights)
    }
}