@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dustinnstroud.executionenforcer

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AppTab { COMMAND, GOALS, ENFORCE, REVIEW }

data class AppUiState(
    val snapshot: AppSnapshot? = null,
    val threat: ThreatAssessment? = null,
    val nextTask: RankedTask? = null,
    val selectedTab: AppTab = AppTab.COMMAND,
    val interventionPackage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExecutionRepository(application)
    private val selectedTab = MutableStateFlow(AppTab.COMMAND)
    private val interventionPackage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUiState> = combine(
        repository.snapshot(),
        selectedTab,
        interventionPackage,
    ) { snapshot, tab, blockedPackage ->
        val threat = ThreatEngine.assess(snapshot)
        AppUiState(
            snapshot = snapshot,
            threat = threat,
            nextTask = ThreatEngine.rankNextTask(snapshot),
            selectedTab = tab,
            interventionPackage = blockedPackage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }
    }

    fun setSelectedTab(tab: AppTab) {
        selectedTab.value = tab
    }

    fun setInterventionPackage(pkg: String?) {
        interventionPackage.value = pkg
    }

    fun completeTask(taskId: Long) = viewModelScope.launch {
        repository.completeTask(taskId)
    }

    fun addCheckIn(segment: String, reflection: String, alignmentScore: Int) = viewModelScope.launch {
        repository.addCheckIn(segment, reflection, alignmentScore)
    }

    fun addGoal(title: String, category: String, deadline: LocalDate, priority: Int, notes: String) = viewModelScope.launch {
        repository.addGoal(title, category, deadline, priority, notes)
    }

    fun addTask(goalId: Long, title: String, notes: String, urgency: Int, impact: Int, threatReduction: Int, dueAt: Instant) = viewModelScope.launch {
        repository.addTask(goalId, title, notes, urgency, impact, threatReduction, dueAt)
    }

    fun saveManualThreat(level: ThreatLevel) {
        repository.saveManualThreat(level)
    }

    fun saveBlockedPackages(packages: List<String>) {
        repository.saveBlockedPackages(packages)
    }

    fun saveHarshMode(enabled: Boolean) {
        repository.saveHarshMode(enabled)
    }

    fun saveRemindersEnabled(enabled: Boolean) {
        repository.saveRemindersEnabled(enabled)
        ReminderScheduler.scheduleAll(getApplication())
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        object : ViewModelProvider.AndroidViewModelFactory(application) {}
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.scheduleAll(this)
        intent?.getStringExtra(EXTRA_INTERVENTION_PACKAGE)?.let(viewModel::setInterventionPackage)
        maybeRequestNotificationPermission()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    ExecutionEnforcerApp(
                        uiState = uiState,
                        onTabSelected = viewModel::setSelectedTab,
                        onCompleteTask = viewModel::completeTask,
                        onAddCheckIn = viewModel::addCheckIn,
                        onAddGoal = viewModel::addGoal,
                        onAddTask = viewModel::addTask,
                        onSaveManualThreat = viewModel::saveManualThreat,
                        onSaveBlockedPackages = viewModel::saveBlockedPackages,
                        onSaveHarshMode = viewModel::saveHarshMode,
                        onSaveRemindersEnabled = viewModel::saveRemindersEnabled,
                        onDismissIntervention = { viewModel.setInterventionPackage(null) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_INTERVENTION_PACKAGE)?.let(viewModel::setInterventionPackage)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_INTERVENTION_PACKAGE = "intervention_package"
    }
}

@Composable
fun ExecutionEnforcerApp(
    uiState: AppUiState,
    onTabSelected: (AppTab) -> Unit,
    onCompleteTask: (Long) -> Unit,
    onAddCheckIn: (String, String, Int) -> Unit,
    onAddGoal: (String, String, LocalDate, Int, String) -> Unit,
    onAddTask: (Long, String, String, Int, Int, Int, Instant) -> Unit,
    onSaveManualThreat: (ThreatLevel) -> Unit,
    onSaveBlockedPackages: (List<String>) -> Unit,
    onSaveHarshMode: (Boolean) -> Unit,
    onSaveRemindersEnabled: (Boolean) -> Unit,
    onDismissIntervention: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snapshot = uiState.snapshot
    val threat = uiState.threat

    if (uiState.interventionPackage != null && threat != null) {
        InterventionDialog(
            blockedPackage = uiState.interventionPackage,
            threat = threat.effective,
            harshMode = snapshot?.settings?.harshMode == true,
            onDismiss = onDismissIntervention,
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Execution Sensei") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            TabBar(current = uiState.selectedTab, onTabSelected = onTabSelected)
        }
    ) { padding ->
        if (snapshot == null || threat == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading your focus frame…")
            }
        } else {
            when (uiState.selectedTab) {
                AppTab.COMMAND -> CommandScreen(
                    modifier = Modifier.padding(padding),
                    snapshot = snapshot,
                    threat = threat,
                    nextTask = uiState.nextTask,
                    onCompleteTask = { taskId ->
                        onCompleteTask(taskId)
                        scope.launch { snackbarHostState.showSnackbar("Progress logged.") }
                    },
                    onAddCheckIn = onAddCheckIn,
                )
                AppTab.GOALS -> GoalsScreen(
                    modifier = Modifier.padding(padding),
                    snapshot = snapshot,
                    onAddGoal = onAddGoal,
                    onAddTask = onAddTask,
                )
                AppTab.ENFORCE -> EnforceScreen(
                    modifier = Modifier.padding(padding),
                    settings = snapshot.settings,
                    threat = threat,
                    onSaveManualThreat = onSaveManualThreat,
                    onSaveBlockedPackages = onSaveBlockedPackages,
                    onSaveHarshMode = onSaveHarshMode,
                    onSaveRemindersEnabled = onSaveRemindersEnabled,
                )
                AppTab.REVIEW -> ReviewScreen(
                    modifier = Modifier.padding(padding),
                    snapshot = snapshot,
                    threat = threat,
                )
            }
        }
    }
}

@Composable
fun TabBar(current: AppTab, onTabSelected: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AppTab.entries.forEach { tab ->
            val selected = tab == current
            val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            TextButton(onClick = { onTabSelected(tab) }, modifier = Modifier.background(container, RoundedCornerShape(16.dp))) {
                Text(tab.name.lowercase().replaceFirstChar { it.titlecase() }, color = content)
            }
        }
    }
}

@Composable
fun CommandScreen(
    modifier: Modifier = Modifier,
    snapshot: AppSnapshot,
    threat: ThreatAssessment,
    nextTask: RankedTask?,
    onCompleteTask: (Long) -> Unit,
    onAddCheckIn: (String, String, Int) -> Unit,
) {
    var checkInSegment by rememberSaveable { mutableStateOf("morning") }
    var reflection by rememberSaveable { mutableStateOf("") }
    var scoreText by rememberSaveable { mutableStateOf("7") }
    val zone = ZoneId.systemDefault()

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatusCard(snapshot, threat, nextTask)
        }
        item {
            CheckInCard(
                segment = checkInSegment,
                onSegmentChange = { checkInSegment = it },
                reflection = reflection,
                onReflectionChange = { reflection = it },
                scoreText = scoreText,
                onScoreChange = { scoreText = it.filter(Char::isDigit).take(2) },
                onSubmit = {
                    onAddCheckIn(checkInSegment, reflection.ifBlank { "Stayed in the fight." }, scoreText.toIntOrNull() ?: 7)
                    reflection = ""
                }
            )
        }
        item {
            Text("Open tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(snapshot.tasks.filterNot { it.completed }.sortedBy { it.dueEpochMillis }) { task ->
            val due = Instant.ofEpochMilli(task.dueEpochMillis).atZone(zone).toLocalDate()
            TaskCard(task = task, dueDate = due, onCompleteTask = onCompleteTask)
        }
    }
}

@Composable
fun StatusCard(snapshot: AppSnapshot, threat: ThreatAssessment, nextTask: RankedTask?) {
    Card(colors = CardDefaults.cardColors(containerColor = threatColor(threat.effective).copy(alpha = 0.18f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Threat: ${threat.effective.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(threat.reasons.joinToString(" • ").ifBlank { "You are in bounds. Keep pressure on the plan." })
            HorizontalDivider()
            nextTask?.let {
                Text("Mandatory next action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(it.task.title)
                Text("Goal: ${it.goalTitle}")
                Text("Weighted score: ${"%.1f".format(it.score)}")
            } ?: Text("No active tasks. Add a goal and its next action now.")
            HorizontalDivider()
            Text("Countdowns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            snapshot.goals.take(3).forEach { goal ->
                val days = ThreatEngine.daysRemaining(goal.deadlineEpochDay)
                Text("${goal.title}: ${if (days >= 0) "$days day(s) left" else "${-days} day(s) overdue"}")
            }
        }
    }
}

@Composable
fun CheckInCard(
    segment: String,
    onSegmentChange: (String) -> Unit,
    reflection: String,
    onReflectionChange: (String) -> Unit,
    scoreText: String,
    onScoreChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sensei check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("morning", "midday", "evening").forEach { phase ->
                    val selected = phase == segment
                    OutlinedButton(onClick = { onSegmentChange(phase) }) {
                        Text(if (selected) "• ${phase.replaceFirstChar { it.titlecase() }}" else phase.replaceFirstChar { it.titlecase() })
                    }
                }
            }
            OutlinedTextField(
                value = reflection,
                onValueChange = onReflectionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What are you avoiding or committing to right now?") },
                minLines = 3,
            )
            OutlinedTextField(
                value = scoreText,
                onValueChange = onScoreChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Alignment score (1-10)") },
            )
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                Text("Log check-in")
            }
        }
    }
}

@Composable
fun TaskCard(task: TaskEntity, dueDate: LocalDate, onCompleteTask: (Long) -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold)
            if (task.notes.isNotBlank()) Text(task.notes)
            Text("Due ${dueDate.format(DateTimeFormatter.ISO_DATE)} • U${task.urgency} I${task.impact} T${task.threatReduction}")
            Button(onClick = { onCompleteTask(task.id) }) {
                Text("Mark complete")
            }
        }
    }
}

@Composable
fun GoalsScreen(
    modifier: Modifier = Modifier,
    snapshot: AppSnapshot,
    onAddGoal: (String, String, LocalDate, Int, String) -> Unit,
    onAddTask: (Long, String, String, Int, Int, Int, Instant) -> Unit,
) {
    var goalDialogOpen by rememberSaveable { mutableStateOf(false) }
    var taskDialogOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { goalDialogOpen = true }) { Text("Add goal") }
                OutlinedButton(onClick = { taskDialogOpen = true }) { Text("Add task") }
            }
            snapshot.goals.forEach { goal ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${goal.category} • priority ${goal.priority} • due ${LocalDate.ofEpochDay(goal.deadlineEpochDay)}")
                        if (goal.notes.isNotBlank()) Text(goal.notes)
                        snapshot.tasks.filter { it.goalId == goal.id }.forEach { task ->
                            Text("- ${task.title}${if (task.completed) " ✓" else ""}")
                        }
                    }
                }
            }
        }
    }

    if (goalDialogOpen) {
        AddGoalDialog(onDismiss = { goalDialogOpen = false }, onSave = { title, category, deadline, priority, notes ->
            onAddGoal(title, category, deadline, priority, notes)
            goalDialogOpen = false
        })
    }

    if (taskDialogOpen) {
        AddTaskDialog(goals = snapshot.goals, onDismiss = { taskDialogOpen = false }, onSave = { goalId, title, notes, urgency, impact, threatReduction, dueAt ->
            onAddTask(goalId, title, notes, urgency, impact, threatReduction, dueAt)
            taskDialogOpen = false
        })
    }
}

@Composable
fun EnforceScreen(
    modifier: Modifier = Modifier,
    settings: AppSettings,
    threat: ThreatAssessment,
    onSaveManualThreat: (ThreatLevel) -> Unit,
    onSaveBlockedPackages: (List<String>) -> Unit,
    onSaveHarshMode: (Boolean) -> Unit,
    onSaveRemindersEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var blockedText by rememberSaveable(settings.blockedPackages) { mutableStateOf(settings.blockedPackages.joinToString("\n")) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enforcement status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Recommended threat: ${threat.recommended.name}")
                Text("Effective threat: ${threat.effective.name}")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = settings.manualThreat.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Manual threat floor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ThreatLevel.entries.forEach { level ->
                            DropdownMenuItem(text = { Text(level.name) }, onClick = {
                                onSaveManualThreat(level)
                                expanded = false
                            })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Harsh drift prompts")
                    Switch(checked = settings.harshMode, onCheckedChange = onSaveHarshMode)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reminder notifications")
                    Switch(checked = settings.remindersEnabled, onCheckedChange = onSaveRemindersEnabled)
                }
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open Accessibility settings") }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Blocked packages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("One package name per line. The accessibility shield will bounce you back into this app when these launch.")
                OutlinedTextField(
                    value = blockedText,
                    onValueChange = { blockedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("Example: com.android.chrome") },
                )
                Button(onClick = {
                    onSaveBlockedPackages(blockedText.lines().map { it.trim() }.filter { it.isNotBlank() })
                }) { Text("Save blocklist") }
            }
        }
    }
}

@Composable
fun ReviewScreen(modifier: Modifier = Modifier, snapshot: AppSnapshot, threat: ThreatAssessment) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pattern review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Completed tasks: ${snapshot.tasks.count { it.completed }} / ${snapshot.tasks.size}")
                Text("Check-ins logged: ${snapshot.checkIns.size}")
                Text("Missed required check-ins today: ${threat.missedCheckInsToday}")
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recent check-ins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                snapshot.checkIns.take(8).forEach { checkIn ->
                    Text("${checkIn.segment.replaceFirstChar { it.titlecase() }} • score ${checkIn.alignmentScore} • ${Instant.ofEpochMilli(checkIn.createdAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()}")
                    Text(checkIn.reflection)
                    Divider()
                }
            }
        }
    }
}

@Composable
fun InterventionDialog(blockedPackage: String, threat: ThreatLevel, harshMode: Boolean, onDismiss: () -> Unit) {
    val message = when {
        harshMode && threat.score >= ThreatLevel.ORANGE.score ->
            "You opened $blockedPackage while drifting. This is avoidance, not strategy. Go back to your next action and stop feeding the spiral."
        else ->
            "You opened $blockedPackage. Recommit before you drift."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sensei correction") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onDismiss) { Text("Return to target") } },
    )
}

@Composable
fun AddGoalDialog(onDismiss: () -> Unit, onSave: (String, String, LocalDate, Int, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Business") }
    var deadline by rememberSaveable { mutableStateOf(LocalDate.now().plusWeeks(2).toString()) }
    var priority by rememberSaveable { mutableStateOf("8") }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("Deadline YYYY-MM-DD") })
                OutlinedTextField(value = priority, onValueChange = { priority = it.filter(Char::isDigit).take(2) }, label = { Text("Priority 1-10") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(title, category, LocalDate.parse(deadline), priority.toIntOrNull()?.coerceIn(1, 10) ?: 5, notes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddTaskDialog(
    goals: List<GoalEntity>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Int, Int, Int, Instant) -> Unit,
) {
    var goalId by rememberSaveable { mutableStateOf(goals.firstOrNull()?.id ?: 0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var urgency by rememberSaveable { mutableStateOf("8") }
    var impact by rememberSaveable { mutableStateOf("8") }
    var threatReduction by rememberSaveable { mutableStateOf("8") }
    var dueDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Goal")
                goals.forEach { goal ->
                    OutlinedButton(onClick = { goalId = goal.id }) {
                        Text(if (goalId == goal.id) "• ${goal.title}" else goal.title)
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task title") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                OutlinedTextField(value = urgency, onValueChange = { urgency = it.filter(Char::isDigit).take(2) }, label = { Text("Urgency 1-10") })
                OutlinedTextField(value = impact, onValueChange = { impact = it.filter(Char::isDigit).take(2) }, label = { Text("Impact 1-10") })
                OutlinedTextField(value = threatReduction, onValueChange = { threatReduction = it.filter(Char::isDigit).take(2) }, label = { Text("Threat reduction 1-10") })
                OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due YYYY-MM-DD") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    goalId,
                    title,
                    notes,
                    urgency.toIntOrNull()?.coerceIn(1, 10) ?: 5,
                    impact.toIntOrNull()?.coerceIn(1, 10) ?: 5,
                    threatReduction.toIntOrNull()?.coerceIn(1, 10) ?: 5,
                    LocalDate.parse(dueDate).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun threatColor(level: ThreatLevel): Color = when (level) {
    ThreatLevel.GREEN -> Color(0xFF16A34A)
    ThreatLevel.YELLOW -> Color(0xFFEAB308)
    ThreatLevel.ORANGE -> Color(0xFFF97316)
    ThreatLevel.RED -> Color(0xFFDC2626)
}
