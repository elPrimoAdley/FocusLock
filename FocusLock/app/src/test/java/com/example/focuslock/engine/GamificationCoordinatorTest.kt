package com.example.focuslock.engine

import com.example.focuslock.domain.FocusSession
import com.example.focuslock.domain.QuestPriority
import com.example.focuslock.domain.QuestTask
import com.example.focuslock.domain.SessionStatus
import com.example.focuslock.domain.SessionType
import com.example.focuslock.domain.repository.FocusSessionRepository
import com.example.focuslock.domain.repository.QuestTaskRepository
import com.example.focuslock.domain.repository.TransactionRunner
import com.example.focuslock.domain.store.GamificationState
import com.example.focuslock.domain.store.GamificationStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class GamificationCoordinatorTest {

    private val zone = ZoneId.of("America/Toronto")

    private fun instantOf(iso: String): Instant =
        LocalDateTime.parse(iso).atZone(zone).toInstant()

    @Test
    fun `3_8 two concurrent applyOverdue calls apply malus and create debt session only once`() = runTest {
        val taskRepo = FakeQuestTaskRepository()
        val sessionRepo = FakeFocusSessionRepository()
        val store = FakeGamificationStore(GamificationState(totalXp = 200))
        val txRunner = object : TransactionRunner {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R {
                return block()
            }
        }

        val overdueP1 = QuestTask(
            id = "p1-overdue",
            title = "1h Espagnol",
            priority = QuestPriority.P1,
            dueAt = instantOf("2026-11-09T16:00:00"),
            completedAt = null,
            penaltyApplied = false
        )
        taskRepo.upsertTask(overdueP1)

        val coordinator = GamificationCoordinator(
            zoneId = zone,
            questTaskRepository = taskRepo,
            focusSessionRepository = sessionRepo,
            gamificationStore = store,
            transactionRunner = txRunner
        )

        val now = instantOf("2026-11-09T16:05:00")

        // Lancement de deux appels simultanés
        val call1 = async { coordinator.applyOverdue(now) }
        val call2 = async { coordinator.applyOverdue(now) }
        call1.await()
        call2.await()

        // 1. La tâche est marquée penaltyApplied = true
        assertTrue(taskRepo.getTaskById("p1-overdue")!!.penaltyApplied)

        // 2. Une seule session FOCUS_DEBT_MALUS de 30 minutes a été créée (et non 60 min !)
        val runningSessions = sessionRepo.getRunningSessions()
        assertEquals(1, runningSessions.size)
        val debtSession = runningSessions.first()
        assertEquals(SessionType.FOCUS_DEBT_MALUS, debtSession.sessionType)
        assertEquals(30L, Duration.between(debtSession.startedAt, debtSession.plannedEndAt).toMinutes())

        // 3. L'XP n'a été déduite qu'une seule fois (200 - 50 = 150 XP) et le quota réduit de 50 %
        val finalState = store.getSnapshot()
        assertEquals(150, finalState.totalXp)
        assertEquals(50, finalState.quotaMalusReductionPercent)

        // 4. Rejouer applyOverdue une 3e fois n'a strictement aucun effet
        val replayPlan = coordinator.applyOverdue(now.plusSeconds(60))
        assertEquals(0, replayPlan.addedDebtMinutes)
        assertEquals(150, store.getSnapshot().totalXp)
    }

    @Test
    fun `3_8 crash between Room commit and DataStore update never causes double debt session on retry`() = runTest {
        val taskRepo = FakeQuestTaskRepository()
        val sessionRepo = FakeFocusSessionRepository()
        val store = FakeGamificationStore(
            GamificationState(totalXp = 200),
            failNextUpdate = true // Simule un crash juste après la transaction Room !
        )
        val txRunner = object : TransactionRunner {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R {
                return block()
            }
        }

        taskRepo.upsertTask(
            QuestTask(
                id = "p1-crash",
                title = "Livrable critique",
                priority = QuestPriority.P1,
                dueAt = instantOf("2026-11-09T12:00:00")
            )
        )

        val coordinator = GamificationCoordinator(zone, taskRepo, sessionRepo, store, txRunner)
        val now = instantOf("2026-11-09T12:10:00")

        // Premier appel : la transaction Room réussit, mais le DataStore plante
        runCatching { coordinator.applyOverdue(now) }

        // La dette de 30 min est bien dans Room et penaltyApplied est déjà à true
        assertTrue(taskRepo.getTaskById("p1-crash")!!.penaltyApplied)
        assertEquals(1, sessionRepo.getRunningSessions().size)

        // Relance au redémarrage de l'app : aucun double malus de dette n'est appliqué
        coordinator.applyOverdue(now.plusSeconds(10))
        val debt = sessionRepo.getActiveDebtSession(now.plusSeconds(10))
        assertNotNull(debt)
        assertEquals(30L, Duration.between(debt!!.startedAt, debt.plannedEndAt).toMinutes())
    }

    // ==================== FAKES DE TEST EN MÉMOIRE ====================

    private class FakeQuestTaskRepository : QuestTaskRepository {
        private val tasksFlow = MutableStateFlow<Map<String, QuestTask>>(emptyMap())

        override fun observeAllTasks(): Flow<List<QuestTask>> =
            tasksFlow.map { it.values.toList() }

        override fun observeChecklistForRoutine(routineId: String): Flow<List<QuestTask>> =
            tasksFlow.map { map ->
                map.values.filter { it.isRoutineChecklistItem && it.parentRoutineId == routineId }
            }

        override suspend fun getAllTasks(): List<QuestTask> = tasksFlow.value.values.toList()

        override suspend fun getTaskById(id: String): QuestTask? = tasksFlow.value[id]

        override suspend fun getTasksDueBetween(startOfDay: Instant, endOfDay: Instant): List<QuestTask> =
            tasksFlow.value.values.filter {
                !it.isRoutineChecklistItem && it.dueAt != null &&
                    !it.dueAt.isBefore(startOfDay) && it.dueAt.isBefore(endOfDay)
            }

        override suspend fun getOverdueUnpenalizedTasks(now: Instant): List<QuestTask> {
            delay(10) // Favorise l'entrelacement des coroutines lors du test concurrent
            return tasksFlow.value.values.filter { task ->
                !task.isRoutineChecklistItem && !task.penaltyApplied && task.dueAt != null &&
                    ((task.completedAt == null && now.isAfter(task.dueAt)) ||
                        (task.completedAt != null && task.completedAt.isAfter(task.dueAt)))
            }
        }

        override suspend fun upsertTask(task: QuestTask) {
            tasksFlow.value = tasksFlow.value + (task.id to task)
        }

        override suspend fun updateTaskCompletion(id: String, completedAt: Instant?) {
            val current = tasksFlow.value[id] ?: return
            tasksFlow.value = tasksFlow.value + (id to current.copy(completedAt = completedAt))
        }

        override suspend fun markTasksPenalized(ids: Set<String>) {
            val updated = tasksFlow.value.toMutableMap()
            ids.forEach { id ->
                updated[id]?.let { updated[id] = it.copy(penaltyApplied = true) }
            }
            tasksFlow.value = updated
        }

        override suspend fun deleteTaskById(id: String) {
            tasksFlow.value = tasksFlow.value - id
        }
    }

    private class FakeFocusSessionRepository : FocusSessionRepository {
        private val sessionsFlow = MutableStateFlow<Map<String, FocusSession>>(emptyMap())

        override fun observeAllSessions(): Flow<List<FocusSession>> =
            sessionsFlow.map { it.values.toList() }

        override fun observeRunningSessions(): Flow<List<FocusSession>> =
            sessionsFlow.map { map -> map.values.filter { it.status == SessionStatus.RUNNING } }

        override suspend fun getRunningSessions(): List<FocusSession> =
            sessionsFlow.value.values.filter { it.status == SessionStatus.RUNNING }

        override suspend fun getActiveDebtSession(now: Instant): FocusSession? =
            sessionsFlow.value.values.firstOrNull {
                it.status == SessionStatus.RUNNING &&
                    it.sessionType == SessionType.FOCUS_DEBT_MALUS &&
                    it.plannedEndAt.isAfter(now)
            }

        override suspend fun getTodayCompletedFocusMinutes(startOfDay: Instant, endOfDay: Instant): Int = 0

        override suspend fun upsertSession(session: FocusSession) {
            sessionsFlow.value = sessionsFlow.value + (session.id to session)
        }

        override suspend fun updateSessionStatus(
            id: String,
            status: SessionStatus,
            actualEndedAt: Instant?,
            summaryNote: String?
        ) {
            val current = sessionsFlow.value[id] ?: return
            sessionsFlow.value = sessionsFlow.value + (
                id to current.copy(
                    status = status,
                    actualEndedAt = actualEndedAt,
                    summaryValidationNote = summaryNote
                )
            )
        }

        override suspend fun completeExpiredSessions(now: Instant): Int {
            var count = 0
            val updated = sessionsFlow.value.toMutableMap()
            updated.forEach { (id, session) ->
                if (session.status == SessionStatus.RUNNING && !session.plannedEndAt.isAfter(now)) {
                    updated[id] = session.copy(status = SessionStatus.COMPLETED, actualEndedAt = session.plannedEndAt)
                    count++
                }
            }
            sessionsFlow.value = updated
            return count
        }
    }

    private class FakeGamificationStore(
        initial: GamificationState,
        private var failNextUpdate: Boolean = false
    ) : GamificationStore {
        private val flow = MutableStateFlow(initial)
        override val state: Flow<GamificationState> = flow
        override suspend fun getSnapshot(): GamificationState = flow.value
        override suspend fun update(
            transform: (GamificationState) -> GamificationState
        ): GamificationState {
            if (failNextUpdate) {
                failNextUpdate = false
                throw RuntimeException("Simulated crash before DataStore write")
            }
            val next = transform(flow.value)
            flow.value = next
            return next
        }
    }
}