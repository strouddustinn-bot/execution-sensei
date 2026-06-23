package com.dustinnstroud.executionenforcer

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class AppSnapshot(
    val goals: List<GoalEntity>,
    val tasks: List<TaskEntity>,
    val checkIns: List<CheckInEntity>,
    val settings: AppSettings,
)

class ExecutionRepository(private val context: Context) {
    private val db = ExecutionDatabase.get(context)
    private val settingsStore = SettingsStore(context)

    val goals: Flow<List<GoalEntity>> = db.goalDao().observeActiveGoals()
    val tasks: Flow<List<TaskEntity>> = db.taskDao().observeTasks()
    val checkIns: Flow<List<CheckInEntity>> = db.checkInDao().observeCheckIns()

    fun settings(): AppSettings = settingsStore.load()

    fun snapshot(): Flow<AppSnapshot> = combine(goals, tasks, checkIns) { goals, tasks, checkIns ->
        AppSnapshot(goals, tasks, checkIns, settings())
    }

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        val hasGoal = goals.firstValue().isNotEmpty()
        if (hasGoal) return@withContext

        val today = LocalDate.now()
        val independenceId = db.goalDao().insert(
            GoalEntity(
                title = "Regain independence",
                category = "Stability",
                deadlineEpochDay = today.plusMonths(3).toEpochDay(),
                priority = 10,
                notes = "License, bills, transportation, own place."
            )
        )
        val businessId = db.goalDao().insert(
            GoalEntity(
                title = "Make AgentPulse reliably profitable",
                category = "Business",
                deadlineEpochDay = today.plusMonths(2).toEpochDay(),
                priority = 9,
                notes = "Focus on revenue-producing tasks, not side quests."
            )
        )
        val recoveryId = db.goalDao().insert(
            GoalEntity(
                title = "Reduce relapse risk through structure",
                category = "Recovery",
                deadlineEpochDay = today.plusMonths(1).toEpochDay(),
                priority = 10,
                notes = "Check-ins, visible progress, anti-isolation."
            )
        )

        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        db.taskDao().insert(
            TaskEntity(
                goalId = independenceId,
                title = "List next 3 independence blockers",
                notes = "Make the invisible visible.",
                urgency = 8,
                impact = 9,
                threatReduction = 7,
                dueEpochMillis = now + day
            )
        )
        db.taskDao().insert(
            TaskEntity(
                goalId = businessId,
                title = "Ship one revenue-moving AgentPulse task",
                notes = "Pick the task that most directly improves signups or sales.",
                urgency = 7,
                impact = 10,
                threatReduction = 6,
                dueEpochMillis = now + day
            )
        )
        db.taskDao().insert(
            TaskEntity(
                goalId = recoveryId,
                title = "Complete your morning check-in before drift starts",
                notes = "Use structure before you need discipline.",
                urgency = 9,
                impact = 8,
                threatReduction = 10,
                dueEpochMillis = now + (6 * 60 * 60 * 1000L)
            )
        )
    }

    suspend fun addGoal(title: String, category: String, deadline: LocalDate, priority: Int, notes: String) {
        db.goalDao().insert(
            GoalEntity(
                title = title,
                category = category,
                deadlineEpochDay = deadline.toEpochDay(),
                priority = priority,
                notes = notes
            )
        )
    }

    suspend fun addTask(
        goalId: Long,
        title: String,
        notes: String,
        urgency: Int,
        impact: Int,
        threatReduction: Int,
        dueAt: Instant,
    ) {
        db.taskDao().insert(
            TaskEntity(
                goalId = goalId,
                title = title,
                notes = notes,
                urgency = urgency,
                impact = impact,
                threatReduction = threatReduction,
                dueEpochMillis = dueAt.toEpochMilli(),
            )
        )
    }

    suspend fun completeTask(taskId: Long) {
        db.taskDao().complete(taskId, System.currentTimeMillis())
    }

    suspend fun addCheckIn(segment: String, reflection: String, alignmentScore: Int) {
        db.checkInDao().insert(
            CheckInEntity(
                segment = segment,
                createdAtEpochMillis = System.currentTimeMillis(),
                reflection = reflection,
                alignmentScore = alignmentScore,
            )
        )
    }

    fun saveManualThreat(level: ThreatLevel) = settingsStore.saveManualThreat(level)
    fun saveBlockedPackages(packages: List<String>) = settingsStore.saveBlockedPackages(packages)
    fun saveHarshMode(enabled: Boolean) = settingsStore.saveHarshMode(enabled)
    fun saveRemindersEnabled(enabled: Boolean) = settingsStore.saveRemindersEnabled(enabled)
}

private suspend fun <T> Flow<List<T>>.firstValue(): List<T> = withContext(Dispatchers.IO) {
    first()
}

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
