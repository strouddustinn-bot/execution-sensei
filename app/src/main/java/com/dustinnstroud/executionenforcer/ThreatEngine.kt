package com.dustinnstroud.executionenforcer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

data class RankedTask(
    val task: TaskEntity,
    val score: Double,
    val goalTitle: String,
)

data class ThreatAssessment(
    val recommended: ThreatLevel,
    val effective: ThreatLevel,
    val reasons: List<String>,
    val missedCheckInsToday: Int,
)

object ThreatEngine {
    fun assess(snapshot: AppSnapshot, nowMillis: Long = System.currentTimeMillis()): ThreatAssessment {
        val reasons = mutableListOf<String>()
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        val checkInsToday = snapshot.checkIns.filter {
            Instant.ofEpochMilli(it.createdAtEpochMillis).atZone(zone).toLocalDate() == today
        }
        val segmentsDone = checkInsToday.map { it.segment.lowercase() }.toSet()
        var missed = 0
        if (now.toLocalTime().isAfter(LocalTime.of(11, 0)) && "morning" !in segmentsDone) missed++
        if (now.toLocalTime().isAfter(LocalTime.of(15, 0)) && "midday" !in segmentsDone) missed++
        if (now.toLocalTime().isAfter(LocalTime.of(21, 0)) && "evening" !in segmentsDone) missed++
        if (missed > 0) reasons += "$missed required check-in(s) missed today"

        val lastCompletion = snapshot.tasks.mapNotNull { it.completedAtEpochMillis }.maxOrNull()
        if (lastCompletion != null) {
            val hoursSinceProgress = (nowMillis - lastCompletion) / 3_600_000.0
            if (hoursSinceProgress > 18) {
                reasons += "No completed progress logged for ${hoursSinceProgress.roundToInt()} hours"
            }
        } else {
            reasons += "No completed progress has been logged yet"
        }

        val recommendedScore = when {
            missed >= 2 -> 2
            missed == 1 -> 1
            else -> 0
        } + when {
            lastCompletion == null -> 1
            nowMillis - lastCompletion > 36 * 3_600_000L -> 2
            nowMillis - lastCompletion > 18 * 3_600_000L -> 1
            else -> 0
        }

        val recommended = ThreatLevel.fromScore(recommendedScore.coerceAtMost(3))
        val effective = if (snapshot.settings.manualThreat.score > recommended.score) {
            snapshot.settings.manualThreat
        } else {
            recommended
        }
        return ThreatAssessment(recommended, effective, reasons, missed)
    }

    fun rankNextTask(snapshot: AppSnapshot, nowMillis: Long = System.currentTimeMillis()): RankedTask? {
        val goalsById = snapshot.goals.associateBy { it.id }
        return snapshot.tasks
            .filterNot { it.completed }
            .map { task ->
                val overdueDays = ((nowMillis - task.dueEpochMillis).coerceAtLeast(0L) / 86_400_000.0)
                val score =
                    (task.urgency * 0.40) +
                    (task.impact * 0.35) +
                    (task.threatReduction * 0.25) +
                    (overdueDays.coerceAtMost(5.0) * 0.7)
                RankedTask(task, score, goalsById[task.goalId]?.title ?: "Unassigned goal")
            }
            .maxByOrNull { it.score }
    }

    fun daysRemaining(epochDay: Long): Long = epochDay - LocalDate.now().toEpochDay()
}
