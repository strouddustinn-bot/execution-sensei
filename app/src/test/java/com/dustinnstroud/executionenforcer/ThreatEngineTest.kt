package com.dustinnstroud.executionenforcer

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThreatEngineTest {
    @Test
    fun recommends_orange_when_multiple_checkins_missed_and_no_progress() {
        val nowMillis = LocalDate.now().atTime(22, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snapshot = AppSnapshot(
            goals = emptyList(),
            tasks = listOf(
                TaskEntity(
                    id = 1,
                    goalId = 1,
                    title = "Task",
                    urgency = 10,
                    impact = 10,
                    threatReduction = 10,
                    dueEpochMillis = 0L,
                    completed = false,
                )
            ),
            checkIns = emptyList(),
            settings = AppSettings(manualThreat = ThreatLevel.GREEN),
        )

        val assessment = ThreatEngine.assess(snapshot, nowMillis = nowMillis)
        assertEquals(ThreatLevel.RED, assessment.recommended)
    }

    @Test
    fun manual_threat_floor_wins() {
        val snapshot = AppSnapshot(
            goals = emptyList(),
            tasks = emptyList(),
            checkIns = emptyList(),
            settings = AppSettings(manualThreat = ThreatLevel.RED),
        )

        val assessment = ThreatEngine.assess(snapshot, nowMillis = 1_720_000_000_000L)
        assertEquals(ThreatLevel.RED, assessment.effective)
    }

    @Test
    fun next_task_ranking_returns_best_candidate() {
        val snapshot = AppSnapshot(
            goals = listOf(GoalEntity(id = 1, title = "Goal", category = "Business", deadlineEpochDay = 30_000, priority = 10)),
            tasks = listOf(
                TaskEntity(id = 1, goalId = 1, title = "Low", urgency = 2, impact = 3, threatReduction = 1, dueEpochMillis = 9_999_999_999_999L),
                TaskEntity(id = 2, goalId = 1, title = "High", urgency = 10, impact = 9, threatReduction = 8, dueEpochMillis = 0L),
            ),
            checkIns = emptyList(),
            settings = AppSettings(),
        )

        val ranked = ThreatEngine.rankNextTask(snapshot, nowMillis = 1_720_000_000_000L)
        assertNotNull(ranked)
        assertEquals("High", ranked?.task?.title)
    }
}
