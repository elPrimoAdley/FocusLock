package com.example.focuslock.domain

// Modèles immuables du domaine : BlockRule, QuestTask, FocusSession, SavedPost, AppCategory
// Aucun import android.* ne doit être présent ici.

// TODO: Ajouter les data classes du domaine.
enum class QuestPriority(val label: String, val xpReward: Int) {
    P1("P1", 100),
    P2("P2", 50),
    P3("P3", 20)
}

enum class FocusMode(val label: String) {
    FOCUS("Focus"),
    LANGUAGE("Étude Langue"),
    READING("Lecture")
}

data class QuestPreviewItem(
    val id: String,
    val title: String,
    val priority: QuestPriority,
    val isCompleted: Boolean
)
