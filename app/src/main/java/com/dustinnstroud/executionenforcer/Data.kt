package com.dustinnstroud.executionenforcer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val deadlineEpochDay: Long,
    val priority: Int,
    val notes: String = "",
    val active: Boolean = true,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val notes: String = "",
    val urgency: Int,
    val impact: Int,
    val threatReduction: Int,
    val dueEpochMillis: Long,
    val completed: Boolean = false,
    val completedAtEpochMillis: Long? = null,
)

@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segment: String,
    val createdAtEpochMillis: Long,
    val reflection: String,
    val alignmentScore: Int,
)

enum class ThreatLevel(val score: Int) {
    GREEN(0),
    YELLOW(1),
    ORANGE(2),
    RED(3);

    companion object {
        fun fromScore(score: Int): ThreatLevel = entries[minOf(maxOf(score, 0), entries.lastIndex)]
        fun fromName(name: String?): ThreatLevel = entries.firstOrNull { it.name == name } ?: GREEN
    }
}

data class AppSettings(
    val manualThreat: ThreatLevel = ThreatLevel.GREEN,
    val blockedPackages: List<String> = emptyList(),
    val harshMode: Boolean = true,
    val remindersEnabled: Boolean = true,
)

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE active = 1 ORDER BY priority DESC, deadlineEpochDay ASC")
    fun observeActiveGoals(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity): Long
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, dueEpochMillis ASC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET completed = 1, completedAtEpochMillis = :completedAt WHERE id = :taskId")
    suspend fun complete(taskId: Long, completedAt: Long)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM checkins ORDER BY createdAtEpochMillis DESC")
    fun observeCheckIns(): Flow<List<CheckInEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: CheckInEntity): Long
}

@Database(entities = [GoalEntity::class, TaskEntity::class, CheckInEntity::class], version = 1)
abstract class ExecutionDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao
    abstract fun checkInDao(): CheckInDao

    companion object {
        @Volatile private var instance: ExecutionDatabase? = null

        fun get(context: Context): ExecutionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ExecutionDatabase::class.java,
                "execution-enforcer.db"
            ).build().also { instance = it }
        }
    }
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("execution-enforcer", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        manualThreat = ThreatLevel.fromName(prefs.getString(KEY_MANUAL_THREAT, ThreatLevel.GREEN.name)),
        blockedPackages = prefs.getString(KEY_BLOCKED_PACKAGES, "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() },
        harshMode = prefs.getBoolean(KEY_HARSH_MODE, true),
        remindersEnabled = prefs.getBoolean(KEY_REMINDERS_ENABLED, true),
    )

    fun saveManualThreat(level: ThreatLevel) {
        prefs.edit { putString(KEY_MANUAL_THREAT, level.name) }
    }

    fun saveBlockedPackages(packages: List<String>) {
        prefs.edit { putString(KEY_BLOCKED_PACKAGES, packages.joinToString(",")) }
    }

    fun saveHarshMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HARSH_MODE, enabled) }
    }

    fun saveRemindersEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDERS_ENABLED, enabled) }
    }

    companion object {
        private const val KEY_MANUAL_THREAT = "manual_threat"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_HARSH_MODE = "harsh_mode"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    }
}
