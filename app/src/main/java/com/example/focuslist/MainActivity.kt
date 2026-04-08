package com.example.focuslist

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focuslist.ui.theme.DarkGrey
import com.example.focuslist.ui.theme.FocusListTheme
import com.example.focuslist.ui.theme.LightGrey
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Main entry point for the focusly application.
 * Handles lock-screen bypassing, Picture-in-Picture mode transitions,
 * and deep-linking intents from the TimerService.
 */
class MainActivity : ComponentActivity() {
    private var isTaskRunning = false
    private var isInPiPModeState by mutableStateOf(false)
    private val incomingTaskId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        scheduleMorningReminder(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val overlayIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(overlayIntent)
            }
        }

        incomingTaskId.value = intent.getStringExtra("SHOW_FULLSCREEN_TASK_ID")

        setContent {
            FocusListTheme {
                val context = LocalContext.current
                val database = AppDatabase.getDatabase(context)
                val factory = TaskViewModelFactory(database.taskDao())
                val viewModel: TaskViewModel = viewModel(factory = factory)

                val tasks by viewModel.tasks.collectAsState(initial = emptyList())
                val runningTask = tasks.find { it.isRunning }
                isTaskRunning = runningTask != null

                val extraTaskId by incomingTaskId.collectAsState()
                var fullScreenTaskId by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(extraTaskId) {
                    if (extraTaskId != null) {
                        fullScreenTaskId = extraTaskId
                        incomingTaskId.value = null
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FocusListApp(
                        viewModel = viewModel,
                        tasksFlow = tasks,
                        runningTask = runningTask,
                        isInPiPMode = isInPiPModeState,
                        fullScreenTaskId = fullScreenTaskId,
                        onSetFullScreenTask = { fullScreenTaskId = it }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingTaskId.value = intent.getStringExtra("SHOW_FULLSCREEN_TASK_ID")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isTaskRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiPModeState = isInPictureInPictureMode
    }
}

sealed interface AppScreen {
    data object MainList : AppScreen
    data class FullScreen(val taskId: String) : AppScreen
    data object PiP : AppScreen
    data object Analytics : AppScreen
    data object Settings : AppScreen
    data object Info : AppScreen
}

@Composable
fun FocusListApp(
    viewModel: TaskViewModel,
    tasksFlow: List<Task>,
    runningTask: Task?,
    isInPiPMode: Boolean,
    fullScreenTaskId: String?,
    onSetFullScreenTask: (String?) -> Unit
) {
    KeepScreenOn()
    val context = LocalContext.current
    val taskInFullScreen = tasksFlow.find { it.id == fullScreenTaskId }
    var taskBeingEdited by remember { mutableStateOf<Task?>(null) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val silencedTaskIds = remember { mutableStateListOf<String>() }
    val allTaskHistory by viewModel.allTaskHistory.collectAsState()
    LaunchedEffect(tasksFlow) {
        silencedTaskIds.removeAll { id ->
            val task = tasksFlow.find { it.id == id }
            task != null && task.remainingSeconds > 0
        }
    }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(60000)
            currentTime = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = taskInFullScreen != null || showAnalytics || showSettings || showInfo) {
        if (showSettings) showSettings = false
        else if (showAnalytics) showAnalytics = false
        else if (showInfo) showInfo = false
        else onSetFullScreenTask(null)
    }

    val currentScreen = when {
        isInPiPMode -> AppScreen.PiP
        taskInFullScreen != null -> AppScreen.FullScreen(taskInFullScreen.id)
        showSettings -> AppScreen.Settings
        showAnalytics -> AppScreen.Analytics
        showInfo -> AppScreen.Info
        else -> AppScreen.MainList
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "Screen Transition"
    ) { screen ->
        when (screen) {
            is AppScreen.PiP -> PiPTaskUI(runningTask = runningTask)
            is AppScreen.Settings -> SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false }
            )
            is AppScreen.Info -> InfoScreen(onBack = { showInfo = false })
            is AppScreen.FullScreen -> {
                val task = tasksFlow.find { it.id == screen.taskId }
                if (task != null) {
                    FullScreenTaskUI(
                        task = task,
                        currentTimeMillis = currentTime,
                        isSilenced = silencedTaskIds.contains(task.id),
                        onSilence = {
                            silencedTaskIds.add(task.id)
                            val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_SILENCE }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                        },
                        onTogglePlayPause = {
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = if (task.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
                                putExtra(TimerService.EXTRA_TASK_ID, task.id)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                            viewModel.toggleTaskRunning(task)
                        },
                        onEditClick = { taskBeingEdited = task },
                        onMinimize = { onSetFullScreenTask(null) }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
            is AppScreen.Analytics -> AnalyticsScreen(
                taskHistory = allTaskHistory,
                onBack = { showAnalytics = false },
                onWipeData = { viewModel.wipeAnalyticsData() }
            )
            is AppScreen.MainList -> FullAppUI(
                viewModel = viewModel,
                tasksFlow = tasksFlow,
                runningTask = runningTask,
                taskHistory = allTaskHistory,
                currentTimeMillis = currentTime,
                onEnterFullScreen = { taskId -> onSetFullScreenTask(taskId) },
                onEditClick = { taskToEdit -> taskBeingEdited = taskToEdit },
                onOpenAnalytics = { showAnalytics = true },
                onOpenSettings = { showSettings = true },
                onOpenInfo = { showInfo = true }
            )
        }
    }

    taskBeingEdited?.let { task ->
        val (h, m, s) = secondsToHms(max(0L, task.remainingSeconds))

        TaskEditorDialog(
            initialName = task.name,
            initialHour = h,
            initialMinute = m,
            initialSecond = s,
            initialCategory = task.category,
            pastTasks = allTaskHistory,
            onDismiss = { taskBeingEdited = null },
            onConfirm = { newName, newHours, newMins, newSecs, newCategory ->
                val newTotalSeconds = (newHours * 3600L) + (newMins * 60L) + newSecs
                val updatedTask = task.copy(
                    name = newName,
                    totalSeconds = newTotalSeconds,
                    remainingSeconds = newTotalSeconds,
                    category = newCategory
                )
                viewModel.updateTask(updatedTask)
                taskBeingEdited = null
            }
        )
    }
}

@Composable
fun FullScreenTaskUI(
    task: Task,
    currentTimeMillis: Long,
    isSilenced: Boolean,
    onSilence: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onEditClick: () -> Unit,
    onMinimize: () -> Unit
) {
    val isOvertime = task.remainingSeconds <= 0 && task.totalSeconds > 0
    val timerColor = if (isOvertime) Color(0xFFE2231E) else MaterialTheme.colorScheme.primary
    val finishTimeColor = if (isOvertime) Color(0xFFE2231E) else Color.Gray

    val currentMathProgress = if (task.totalSeconds > 0 && !isOvertime) {
        task.remainingSeconds.toFloat() / task.totalSeconds.toFloat()
    } else 0f

    val animatedProgress = remember { Animatable(currentMathProgress) }

    LaunchedEffect(task.isRunning, task.remainingSeconds, isOvertime) {
        if (isOvertime) {
            animatedProgress.snapTo(0f)
        } else if (task.isRunning) {
            val nextSecondProgress = if (task.totalSeconds > 0) {
                kotlin.math.max(0f, (task.remainingSeconds - 1).toFloat() / task.totalSeconds.toFloat())
            } else 0f

            animatedProgress.animateTo(
                targetValue = nextSecondProgress,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            )
        } else {
            animatedProgress.stop()
        }
    }

    val haloAlpha by animateFloatAsState(
        targetValue = if (task.isRunning) 0.15f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "Halo Alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing Scale"
    )

    val predictedFinishTime = formatPredictedTime(currentTimeMillis, task.remainingSeconds)
    val elapsedSeconds = max(0L, task.totalSeconds - task.remainingSeconds)

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(task.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = finishTimeColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Finish by: $predictedFinishTime", style = MaterialTheme.typography.titleMedium, color = finishTimeColor)
            }
            if (elapsedSeconds > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Time Elapsed: ${formatSeconds(elapsedSeconds)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).aspectRatio(1f)) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(breathingScale)
                    .background(color = timerColor.copy(alpha = haloAlpha), shape = CircleShape)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape)
            )

            CircularProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.fillMaxSize(),
                color = timerColor,
                strokeWidth = 16.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round
            )

            Text(
                formatSeconds(task.remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = timerColor
            )
        }

        Row(modifier = Modifier.padding(bottom = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isOvertime && task.isRunning) {
                IconButton(onClick = onSilence, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = "Silence Alarm",
                        tint = if (isSilenced) Color.Gray else Color(0xFFE2231E).copy(alpha = 0.8f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                IconButton(onClick = onEditClick, enabled = !task.isRunning, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Task", tint = if (task.isRunning) Color.Gray else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(onClick = onTogglePlayPause, modifier = Modifier.size(120.dp), shape = CircleShape, color = Color.Transparent) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (task.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onMinimize, modifier = Modifier.size(64.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Minimize", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun PiPTaskUI(runningTask: Task?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (runningTask != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                val isOvertime = runningTask.remainingSeconds < 0
                Text(
                    text = formatSeconds(runningTask.remainingSeconds),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isOvertime) Color(0xFFE2231E) else Color.LightGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = runningTask.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text("No Running Task", color = Color.Gray)
        }
    }
}

/**
 * The primary interactive dashboard.
 * Manages the draggable LazyColumn, Kaizen progress bars, and state hoisting
 * for the various dialogue menus. Reorder state is handled locally before
 * pushing to the Room DB to guarantee 60fps animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullAppUI(
    viewModel: TaskViewModel,
    tasksFlow: List<Task>,
    runningTask: Task?,
    taskHistory: List<Task>,
    currentTimeMillis: Long,
    onEnterFullScreen: (String) -> Unit,
    onEditClick: (Task) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val highlightIds = remember { mutableStateListOf<String>() }
    val microIds = remember { mutableStateListOf<String>() }
    val standardIds = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    var taskDataMap by remember { mutableStateOf(emptyMap<String, Task>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    val isAnyTaskRunning = runningTask != null

    val totalSeconds = tasksFlow.filter { !it.isCompleted }.sumOf { max(0L, it.remainingSeconds) }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(60000)
            currentTime = System.currentTimeMillis()
        }
    }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState

            fun moveInList(list: androidx.compose.runtime.snapshots.SnapshotStateList<String>) {
                val fromIndex = list.indexOf(fromKey)
                val toIndex = list.indexOf(toKey)
                if (fromIndex != -1 && toIndex != -1) list.add(toIndex, list.removeAt(fromIndex))
            }

            if (fromKey in highlightIds && toKey in highlightIds) moveInList(highlightIds)
            else if (fromKey in microIds && toKey in microIds) moveInList(microIds)
            else if (fromKey in standardIds && toKey in standardIds) moveInList(standardIds)
        },
        canDragOver = { draggedOver, dragging ->
            val dragKey = dragging.key as? String ?: return@rememberReorderableLazyListState false
            val overKey = draggedOver.key as? String ?: return@rememberReorderableLazyListState false

            (dragKey in highlightIds && overKey in highlightIds) ||
                    (dragKey in microIds && overKey in microIds) ||
                    (dragKey in standardIds && overKey in standardIds)
        },
        onDragEnd = { _, _ ->
            val newOrderTasks = (highlightIds + microIds + standardIds).mapNotNull { taskDataMap[it] }
            viewModel.updateTaskOrder(newOrderTasks)
        }
    )

    LaunchedEffect(tasksFlow) {
        taskDataMap = tasksFlow.associateBy { it.id }
        val sortedFlow = tasksFlow.sortedBy { it.sortOrder }

        fun safeSync(list: androidx.compose.runtime.snapshots.SnapshotStateList<String>, newIds: List<String>) {
            val currentSet = list.toSet()
            val newSet = newIds.toSet()
            list.removeAll { it !in newSet }
            newIds.filter { it !in currentSet }.forEach { list.add(it) }
        }

        safeSync(highlightIds, sortedFlow.filter { it.category == TaskCategory.HIGHLIGHT }.map { it.id })
        safeSync(microIds, sortedFlow.filter { it.category == TaskCategory.MICRO_COMMITMENT }.map { it.id })
        safeSync(standardIds, sortedFlow.filter { it.category == TaskCategory.STANDARD || it.category == TaskCategory.BRAIN_DUMP }.map { it.id })
    }

    val getPredictedFinishTime: (String) -> String? = { taskId ->
        val task = taskDataMap[taskId]
        if (task == null || task.isCompleted || task.category == TaskCategory.BRAIN_DUMP) {
            null
        } else {
            var runningTotal = 0L
            var found = false
            for (id in (highlightIds + microIds + standardIds)) {
                val t = taskDataMap[id] ?: continue
                if (!t.isCompleted) runningTotal += max(0L, t.remainingSeconds)
                if (id == taskId) { found = true; break }
            }
            if (found) formatPredictedTime(currentTimeMillis, runningTotal) else null
        }
    }

    val actionableTasks = tasksFlow.filter { it.category != TaskCategory.BRAIN_DUMP }
    val completedCount = actionableTasks.count { it.isCompleted }
    val totalActionableCount = actionableTasks.size
    val dayProgress = if (totalActionableCount > 0) completedCount.toFloat() / totalActionableCount.toFloat() else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = dayProgress,
        animationSpec = tween(durationMillis = 800),
        label = "Progress Animation"
    )

    val isHighlightDone = tasksFlow.any { it.category == TaskCategory.HIGHLIGHT && it.isCompleted }

    val quotes = remember {
        listOf(
            "Let's make 1% progress today.",
            "Focus on the signal, ignore the noise.",
            "Action precedes motivation. Just start.",
            "Momentum is built in the micro-commitments.",
            "One step at a time.",
            "Small inputs lead to massive outputs.",
            "Embrace the process. Ignore the overwhelm.",
            "Clear the fog. Execute the next step.",
            "Clarity comes from action, not overthinking.",
            "Done is absolutely better than perfect.",
            "You don't need to feel like it to do it.",
            "Discipline equals freedom. Do the work.",
            "Protect your focus at all costs.",
            "The hardest part is just sitting down.",
            "Don't break the chain. Keep moving.",
            "Start small, but start right now.",
            "Trust the framework.",
            "Kaizen: Continuous, incremental improvement.",
            "Win the micro-battles.",
            "Lower the stakes. Just take the next step."
        )
    }

    val activeQuote = remember(completedCount) { quotes.random() }

    val greeting = when {
        totalActionableCount == 0 -> "A fresh canvas. Add a Highlight to begin."
        totalActionableCount > 0 && completedCount == totalActionableCount -> "Day complete. Time to recharge."
        isHighlightDone && completedCount == 1 -> "Highlight secured. Great momentum."
        else -> activeQuote
    }

    var showReviewDialog by remember { mutableStateOf(false) }
    val staleThresholdMillis = 3L * 24 * 60 * 60 * 1000
    val staleTasks = actionableTasks.filter { !it.isCompleted && (currentTimeMillis - it.createdAtMillis) > staleThresholdMillis }

    val currentStreak = calculateStreak(taskHistory)

    var showConfetti by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.header_logo), contentDescription = null, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "focusly.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    AnimatedVisibility(visible = currentStreak > 0) {
                        Row {
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF5B735).copy(alpha = 0.15f)) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Whatshot, contentDescription = "Streak", tint = Color(0xFFF5B735), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "$currentStreak", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF5B735), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onOpenInfo,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Tips", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = onOpenAnalytics,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = "Analytics", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(greeting, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Daily Progress", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Text("$completedCount / $totalActionableCount Tasks", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Remaining", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(formatSeconds(totalSeconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Predicted Finish", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(formatPredictedTime(currentTimeMillis, totalSeconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = staleTasks.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    onClick = { showReviewDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Weekly Review", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("${staleTasks.size} tasks are stagnating. Time to clean up.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = "Review", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            val configuration = LocalConfiguration.current
            val paddedScreenWidth = configuration.screenWidthDp.dp - 40.dp
            val rowWidth = paddedScreenWidth * 0.9f
            val halfButtonWidth = (rowWidth - 12.dp) / 2f

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(50.dp).weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Task", fontSize = 18.sp)
                }

                AnimatedVisibility(
                    visible = completedCount > 0,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                showConfetti = true
                                viewModel.clearCompletedTasks()
                            },
                            modifier = Modifier
                                .height(50.dp)
                                .width(halfButtonWidth),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Clear Done", fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                state = state.listState,
                modifier = Modifier.fillMaxWidth().weight(1f).reorderable(state),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (highlightIds.isNotEmpty()) {
                    item(key = "header_highlights") { Text("Daily Highlight", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)) }
                    items(highlightIds, key = { it }) { id ->
                        taskDataMap[id]?.let { task ->
                            ReorderableItem(reorderableState = state, key = task.id) { isDragging ->
                                TaskItem(
                                    task = task, isDragging = isDragging, canToggleTimer = task.isRunning || !isAnyTaskRunning,
                                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                    onTogglePlayPause = {
                                        val intent = Intent(context, TimerService::class.java).apply {
                                            action = if (task.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
                                            putExtra(TimerService.EXTRA_TASK_ID, task.id)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                                        viewModel.toggleTaskRunning(task)
                                    },
                                    onDelete = { viewModel.deleteTask(task) },
                                    dragModifier = Modifier.detectReorder(state), onEditClick = { onEditClick(task) },
                                    predictedFinishTime = getPredictedFinishTime(task.id), onEnterFullScreen = { onEnterFullScreen(task.id) }
                                )
                            }
                        }
                    }
                    item(key = "spacer_highlights") { Spacer(modifier = Modifier.height(16.dp)) }
                }

                if (microIds.isNotEmpty()) {
                    item(key = "header_micros") { Text("Micro-Commitments", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)) }
                    items(microIds, key = { it }) { id ->
                        taskDataMap[id]?.let { task ->
                            ReorderableItem(reorderableState = state, key = task.id) { isDragging ->
                                TaskItem(
                                    task = task, isDragging = isDragging, canToggleTimer = task.isRunning || !isAnyTaskRunning,
                                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                    onTogglePlayPause = {
                                        val intent = Intent(context, TimerService::class.java).apply {
                                            action = if (task.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
                                            putExtra(TimerService.EXTRA_TASK_ID, task.id)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                                        viewModel.toggleTaskRunning(task)
                                    },
                                    onDelete = { viewModel.deleteTask(task) },
                                    dragModifier = Modifier.detectReorder(state), onEditClick = { onEditClick(task) },
                                    predictedFinishTime = getPredictedFinishTime(task.id), onEnterFullScreen = { onEnterFullScreen(task.id) }
                                )
                            }
                        }
                    }
                    item(key = "spacer_micros") { Spacer(modifier = Modifier.height(16.dp)) }
                }

                if (standardIds.isNotEmpty() && (highlightIds.isNotEmpty() || microIds.isNotEmpty())) {
                    item(key = "header_standards") { Text("Tasks & Brain Dumps", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)) }
                }
                items(standardIds, key = { it }) { id ->
                    taskDataMap[id]?.let { task ->
                        ReorderableItem(reorderableState = state, key = task.id) { isDragging ->
                            TaskItem(
                                task = task, isDragging = isDragging, canToggleTimer = task.isRunning || !isAnyTaskRunning,
                                onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                onTogglePlayPause = {
                                    val intent = Intent(context, TimerService::class.java).apply {
                                        action = if (task.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
                                        putExtra(TimerService.EXTRA_TASK_ID, task.id)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                                    viewModel.toggleTaskRunning(task)
                                },
                                onDelete = { viewModel.deleteTask(task) },
                                dragModifier = Modifier.detectReorder(state), onEditClick = { onEditClick(task) },
                                predictedFinishTime = getPredictedFinishTime(task.id), onEnterFullScreen = { onEnterFullScreen(task.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showConfetti) {
            ConfettiBurst(modifier = Modifier.fillMaxSize()) {
                showConfetti = false
            }
        }
    }

    if (showAddDialog) {
        TaskEditorDialog(
            pastTasks = taskHistory,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, hours, mins, secs, category ->
                viewModel.addTaskWithSeconds(name, (hours * 3600L) + (mins * 60L) + secs, category)
                showAddDialog = false
            }
        )
    }

    if (showReviewDialog) {
        WeeklyReviewDialog(
            staleTasks = staleTasks,
            onDismiss = { showReviewDialog = false },
            onRecommit = { task ->
                viewModel.updateTask(task.copy(createdAtMillis = System.currentTimeMillis()))
            },
            onLetGo = { task ->
                viewModel.deleteTask(task)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FocuslyPrefs", android.content.Context.MODE_PRIVATE)

    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var vibrateEnabled by remember { mutableStateOf(prefs.getBoolean("vibrate_enabled", true)) }
    var customAlarmUri by remember { mutableStateOf(prefs.getString("custom_alarm_uri", null)) }

    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            customAlarmUri = it.toString()
            prefs.edit().putString("custom_alarm_uri", customAlarmUri).apply()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportData(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importData(context, it) }
    }

    var savedHour by remember { mutableStateOf(prefs.getInt("reminder_hour", 9)) }
    var savedMinute by remember { mutableStateOf(prefs.getInt("reminder_minute", 0)) }

    val reminderTimeDisplay = remember(savedHour, savedMinute) {
        val amPm = if (savedHour >= 12) "PM" else "AM"
        val displayHour = if (savedHour % 12 == 0) 12 else savedHour % 12
        String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, savedMinute, amPm)
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showDevNoteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 32.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            Text("Alarm Preferences", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Sound", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it; prefs.edit().putBoolean("sound_enabled", it).apply() }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Custom Sound", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (customAlarmUri == null) "Default Android Alarm" else "Custom Audio Set", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                        Row {
                            if (customAlarmUri != null) {
                                TextButton(onClick = { customAlarmUri = null; prefs.edit().remove("custom_alarm_uri").apply() }) { Text("Clear", color = Color(0xFFCF6679)) }
                            }
                            TextButton(onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) }) { Text(if (customAlarmUri == null) "Select" else "Change", color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Vibration", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Switch(checked = vibrateEnabled, onCheckedChange = { vibrateEnabled = it; prefs.edit().putBoolean("vibrate_enabled", it).apply() }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                    }
                }
            }

            Text("Daily Primer", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Highlight Reminder", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(reminderTimeDisplay, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                        TextButton(onClick = { showTimePicker = true }) {
                            Text("Change", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Test Reminder Now", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        TextButton(onClick = {
                            val intent = Intent(context, MorningReminderReceiver::class.java)
                            context.sendBroadcast(intent)
                        }) {
                            Text("Test", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Text("Data Vault", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Export Backup", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        TextButton(onClick = { exportLauncher.launch("focusly_backup.json") }) {
                            Text("Export", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Import Backup", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                            Text("Import", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Text("About", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column {
                    SettingsRowItem("Support the Developer ☕") {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://buymeacoffee.com/rasyidrp")
                        }
                        context.startActivity(intent)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    SettingsRowItem("Send Feedback") {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:thenameis.rasyid@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Focusly App Feedback")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Feedback"))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    SettingsRowItem("Terms & Conditions") { showTermsDialog = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    SettingsRowItem("Developer's Note") { showDevNoteDialog = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version History", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Text("v2.1.3", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = savedHour, initialMinute = savedMinute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { TimePicker(state = timePickerState) } },
            confirmButton = {
                TextButton(onClick = {
                    savedHour = timePickerState.hour
                    savedMinute = timePickerState.minute
                    prefs.edit().putInt("reminder_hour", savedHour).putInt("reminder_minute", savedMinute).apply()
                    scheduleMorningReminder(context)
                    showTimePicker = false
                }) { Text("Save", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Terms & Conditions", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Last Updated: April 6, 2026\n\n" +
                                "1. Privacy & Data Storage\n" +
                                "focusly. operates on a strictly offline-first architecture. All task data, analytics, and preferences are stored locally on your device via an encrypted database. We do not track, collect, or transmit any personal telemetry to external servers. If you uninstall the app without exporting your Data Vault, your history is permanently deleted.\n\n" +
                                "2. System Permissions & Overrides\n" +
                                "To function as a strict productivity enforcer, this application utilizes high-level Android APIs, including WakeLocks, Foreground Services, and System Alert Windows (Draw Over Other Apps). By using focusly., you consent to the app bypassing standard battery optimization (Doze Mode) to wake your device and interrupt your screen usage when deep-work timers conclude.\n\n" +
                                "3. Limitation of Liability\n" +
                                "This software is provided 'as is', without warranty of any kind. The developer is not liable for missed deadlines, failed alarms resulting from OS-level throttling, or accidental data loss.\n\n" +
                                "4. The Kaizen Agreement\n" +
                                "By tapping 'I Agree', you commit to prioritizing the signal over the noise, mastering your workflow, and taking it 1% at a time.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("I Agree", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showDevNoteDialog) {
        AlertDialog(
            onDismissRequest = { showDevNoteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Developer's Note", color = MaterialTheme.colorScheme.primary) },
            text = {
                Text(
                    "I built focusly. because I was exhausted by the constant cycle of ADHD paralysis and productivity guilt. Traditional to-do lists didn't work for my brain, and staring at a massive wall of pending tasks just added to the noise and made the 'activation energy' required to start feel impossible.\n\nI didn't need another app to make me feel bad about what I hadn't done. I needed a system that cuts through the fog, forces me to prioritize the signal over the noise, and works with my brain instead of against it.\n\nThis app is my personal engine to stay disciplined, embrace Kaizen, and take it 1% at a time. Trust the process.\n\n— Rasyid",
                    color = Color.White
                )
            },
            confirmButton = { TextButton(onClick = { showDevNoteDialog = false }) { Text("Close", color = MaterialTheme.colorScheme.primary) } }
        )
    }
}

@Composable
fun SettingsRowItem(title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    isDragging: Boolean,
    canToggleTimer: Boolean,
    onToggleComplete: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
    onEditClick: () -> Unit,
    predictedFinishTime: String?,
    onEnterFullScreen: () -> Unit
) {
    val context = LocalContext.current

    val animatedBorderColor by animateColorAsState(
        targetValue = if (task.isRunning) Color.Gray else Color.Transparent,
        animationSpec = tween(
            durationMillis = 250,
            easing = FastOutSlowInEasing
        ),
        label = "Card Border Color Animation"
    )

    val animatedContainerColor = MaterialTheme.colorScheme.surface
    val animatedContentColor = MaterialTheme.colorScheme.onSurface

    val elevation = if (isDragging) 8.dp else 2.dp
    val isOvertime = task.remainingSeconds < 0
    val elapsedSeconds = max(0L, task.totalSeconds - task.remainingSeconds)

    val secondLineStyle = MaterialTheme.typography.bodySmall
    val secondLineColor = if(isOvertime) Color(0xFFE2231E).copy(alpha=0.7f) else animatedContentColor.copy(alpha = 0.6f)

    val categoryIcon = when (task.category) {
        TaskCategory.HIGHLIGHT -> Icons.Default.Star
        TaskCategory.MICRO_COMMITMENT -> Icons.Default.FlashOn
        TaskCategory.BRAIN_DUMP -> Icons.Default.Cloud
        TaskCategory.STANDARD -> null
    }

    val iconTint = when (task.category) {
        TaskCategory.HIGHLIGHT -> Color(0xFFF5B735)
        TaskCategory.MICRO_COMMITMENT -> Color(0xFFF5B735)
        TaskCategory.BRAIN_DUMP -> Color.White
        TaskCategory.STANDARD -> animatedContentColor
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor, contentColor = animatedContentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(2.dp, animatedBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = {
                    if (task.isRunning) {
                        val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_STOP }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    }
                    onToggleComplete()
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = animatedContentColor
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (categoryIcon != null) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = task.category.name,
                            tint = if (task.isCompleted) Color.Gray else iconTint,
                            modifier = Modifier.size(20.dp).padding(end = 4.dp)
                        )
                    }

                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        color = if (task.isCompleted) Color.Gray else animatedContentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (task.category != TaskCategory.BRAIN_DUMP && !task.isCompleted) {
                        Text(
                            text = formatSeconds(task.remainingSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOvertime) Color(0xFFE2231E) else animatedContentColor
                        )
                    }
                }

                if (!task.isCompleted && task.category != TaskCategory.BRAIN_DUMP) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (predictedFinishTime != null) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = secondLineColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Finish by: $predictedFinishTime", style = secondLineStyle, color = secondLineColor)
                        }
                        if (predictedFinishTime != null && elapsedSeconds > 0) {
                            Text(text = "  |  ", style = secondLineStyle, color = secondLineColor)
                        }
                        if (elapsedSeconds > 0) {
                            Text(text = "Elapsed: ${formatSeconds(elapsedSeconds)}", style = secondLineStyle, color = secondLineColor)
                        }
                    }
                }
                else if (task.isCompleted && task.category != TaskCategory.BRAIN_DUMP) {
                    Spacer(modifier = Modifier.height(6.dp))

                    val actualSeconds = max(0L, task.totalSeconds - task.remainingSeconds)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (actualSeconds == 0L) {
                            Text(
                                text = "Completed without timer",
                                style = secondLineStyle,
                                color = Color.Gray
                            )
                        } else if (task.remainingSeconds > 0) {
                            Text(
                                text = "Finished early!",
                                style = secondLineStyle,
                                color = Color(0xFFF5B735),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Est: ${formatSeconds(task.totalSeconds)}  |  Actual: ${formatSeconds(actualSeconds)}",
                                style = secondLineStyle,
                                color = Color.Gray
                            )
                        } else {
                            Text(
                                text = "Est: ${formatSeconds(task.totalSeconds)}  |  Actual: ${formatSeconds(actualSeconds)}",
                                style = secondLineStyle,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (!task.isCompleted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.category != TaskCategory.BRAIN_DUMP) {
                            IconButton(
                                onClick = onTogglePlayPause,
                                enabled = canToggleTimer
                            ) {
                                Icon(
                                    imageVector = if (task.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (task.isRunning) "Pause" else "Start",
                                    tint = if (!canToggleTimer) Color.Gray else animatedContentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(onClick = onEditClick, enabled = !task.isRunning) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if (task.isRunning) Color.Gray else animatedContentColor, modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (task.category != TaskCategory.BRAIN_DUMP) {
                            IconButton(onClick = onEnterFullScreen, enabled = canToggleTimer) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen", tint = if (canToggleTimer) animatedContentColor else Color.Gray, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(onClick = { onDelete() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = animatedContentColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            if (!task.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Drag to reorder",
                    tint = if(task.isRunning) animatedContentColor else Color.LightGray,
                    modifier = dragModifier.padding(start = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
        }
    }
}

fun secondsToHms(totalSeconds: Long): Triple<Int, Int, Int> {
    val absSeconds = abs(totalSeconds)
    val hours = (absSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (absSeconds % 60).toInt()
    return Triple(hours, minutes, seconds)
}

fun formatSeconds(seconds: Long): String {
    val isNegative = seconds < 0
    val absSeconds = abs(seconds)

    val h = absSeconds / 3600
    val m = (absSeconds % 3600) / 60
    val s = absSeconds % 60
    val formatted = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    return if (isNegative) "-$formatted" else formatted
}

fun formatPredictedTime(currentTimeMillis: Long, addedSeconds: Long): String {
    if (addedSeconds <= 0L) return "Now"
    val targetTime = Instant.ofEpochMilli(currentTimeMillis).plusSeconds(addedSeconds)
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    return formatter.format(targetTime)
}

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun AnalyticsScreen(
    taskHistory: List<Task>,
    onBack: () -> Unit,
    onWipeData: () -> Unit
) {
    val completedTasks = taskHistory.filter { it.isCompleted }
    var showClearDialog by remember { mutableStateOf(false) }

    val actionableCompleted = completedTasks.filter { it.category != TaskCategory.BRAIN_DUMP }
    val mostCommonTaskName = if (actionableCompleted.isNotEmpty()) {
        actionableCompleted
            .groupBy { it.name.trim().lowercase() }
            .maxByOrNull { it.value.size }
            ?.value?.first()?.name ?: "None"
    } else {
        "None"
    }

    val highlightsDone = completedTasks.count { it.category == TaskCategory.HIGHLIGHT }
    val microsDone = completedTasks.count { it.category == TaskCategory.MICRO_COMMITMENT }
    val standardsDone = completedTasks.count { it.category == TaskCategory.STANDARD }
    val dumpsDone = completedTasks.count { it.category == TaskCategory.BRAIN_DUMP }

    val tasksWithTimers = completedTasks.filter { it.category != TaskCategory.BRAIN_DUMP && it.totalSeconds > 0 }
    val totalEstimatedSeconds = tasksWithTimers.sumOf { it.totalSeconds }
    val totalActualSeconds = tasksWithTimers.sumOf { max(0L, it.totalSeconds - it.remainingSeconds) }

    val timeSaved = totalEstimatedSeconds - totalActualSeconds
    val estimationFeedback = when {
        tasksWithTimers.isEmpty() -> "No timed tasks completed yet."
        timeSaved > 0 -> "You finished tasks faster than expected by ${formatSeconds(timeSaved)}!"
        timeSaved < 0 -> "Tasks took ${formatSeconds(abs(timeSaved))} longer than expected."
        else -> "Your time estimations were perfectly accurate!"
    }

    val firstTaskTime = completedTasks.mapNotNull { it.completedAtMillis }.minOrNull() ?: System.currentTimeMillis()
    val daysActive = max(1L, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstTaskTime) + 1L)
    val avgPerDay = (completedTasks.size.toFloat() / daysActive.toFloat()).let { Math.round(it * 10f) / 10f }

    val zoneId = ZoneId.systemDefault()
    val tasksByDate = completedTasks.groupBy { task ->
        task.completedAtMillis?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
        } ?: LocalDate.now()
    }

    val availableDates = tasksByDate.keys.sortedDescending()
    var selectedDate by remember(availableDates) { mutableStateOf(availableDates.firstOrNull()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 32.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kaizen Feedback", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f).height(100.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Most Common", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = mostCommonTaskName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f).height(100.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Avg Per Day", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$avgPerDay",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Time Estimation Accuracy", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(estimationFeedback, style = MaterialTheme.typography.bodyMedium, color = Color.White)

                    if (tasksWithTimers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Estimated Time", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text(formatSeconds(totalEstimatedSeconds), style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Actual Time", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text(formatSeconds(totalActualSeconds), style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Task Distribution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    DistributionRow(label = "Daily Highlights", count = highlightsDone, total = completedTasks.size, color = Color(0xFFF5B735))
                    DistributionRow(label = "Micro-Commitments", count = microsDone, total = completedTasks.size, color = Color(0xFFF5B735))
                    DistributionRow(label = "Standard Tasks", count = standardsDone, total = completedTasks.size, color = Color.LightGray)
                    DistributionRow(label = "Brain Dumps Processed", count = dumpsDone, total = completedTasks.size, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (availableDates.isNotEmpty() && selectedDate != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 20.dp)) {
                        Text("Daily Time Log", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp))

                        DailyLogSelector(
                            loggedDates = availableDates,
                            selectedDate = selectedDate,
                            onDateSelected = { selectedDate = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val tasksForSelectedDate = tasksByDate[selectedDate] ?: emptyList()

                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            tasksForSelectedDate.forEach { task ->
                                val actualSecs = max(0L, task.totalSeconds - task.remainingSeconds)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(task.category.name.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (task.category != TaskCategory.BRAIN_DUMP) {
                                        Text(formatSeconds(actualSecs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("--", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear Analytics Data", color = Color(0xFFCF6679))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Analytics Data?") },
            text = { Text("This will permanently delete all archived tasks and reset your stats. Your active dashboard tasks will remain.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onWipeData()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear Data", color = Color(0xFFCF6679))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun DistributionRow(label: String, count: Int, total: Int, color: Color) {
    val progress = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text("$count", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun WeeklyReviewDialog(
    staleTasks: List<Task>,
    onDismiss: () -> Unit,
    onRecommit: (Task) -> Unit,
    onLetGo: (Task) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Weekly Review", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("These tasks have been sitting for days. Either recommit to them, or let them go to clear your mind.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(staleTasks) { task ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(task.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRecommit(task) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Recommit")
                                }
                                Button(
                                    onClick = { onLetGo(task) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679).copy(alpha = 0.2f), contentColor = Color(0xFFCF6679))
                                ) {
                                    Text("Let Go")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    }
}

fun calculateStreak(taskHistory: List<Task>): Int {
    val completedTasks = taskHistory.filter { it.isCompleted && it.completedAtMillis != null }
    if (completedTasks.isEmpty()) return 0

    val zoneId = ZoneId.systemDefault()
    val distinctDates = completedTasks
        .mapNotNull { it.completedAtMillis }
        .map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        .distinct()
        .sortedDescending()

    if (distinctDates.isEmpty()) return 0

    val today = LocalDate.now(zoneId)
    var currentStreak = 0

    if (distinctDates.first().isBefore(today.minusDays(1))) {
        return 0
    }

    var expectedDate = distinctDates.first()
    for (date in distinctDates) {
        if (date == expectedDate) {
            currentStreak++
            expectedDate = expectedDate.minusDays(1)
        } else {
            break
        }
    }
    return currentStreak
}

fun scheduleMorningReminder(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, MorningReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val prefs = context.getSharedPreferences("FocuslyPrefs", Context.MODE_PRIVATE)
    val savedHour = prefs.getInt("reminder_hour", 9)
    val savedMinute = prefs.getInt("reminder_minute", 0)

    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(java.util.Calendar.HOUR_OF_DAY, savedHour)
        set(java.util.Calendar.MINUTE, savedMinute)
        set(java.util.Calendar.SECOND, 0)
    }

    if (calendar.timeInMillis <= System.currentTimeMillis()) {
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    } else {
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}

@Composable
fun InfoScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 32.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("The Framework", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {

            Text(
                "The Activation Energy Problem",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Traditional productivity advice fails because it doesn't address the hardest part: getting started. You can have the perfect plan, but if you lack the activation energy, it doesn't matter. focusly is built to solve this.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            FrameworkCard(
                icon = Icons.Default.FlashOn,
                title = "Part 1: Activating the Engine",
                description = "How to get into the right headspace and mood to start working.",
                points = listOf(
                    "Morning Light Therapy" to "Sit in front of a 10,000-lux lamp (or sunlight) for 20-30 mins every morning. It resets your circadian rhythm and clears brain fog better than coffee.",
                    "The Kitchen Table Setup" to "Reduce friction by putting your laptop on the kitchen table the night before. Casually check emails while eating to trick your brain into flow, then move to your real desk.",
                    "Plan the Day Before" to "Waking up without a plan makes you assume you have all the time in the world. Spend 5-10 mins the night before structuring exactly what needs to get done."
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            FrameworkCard(
                icon = Icons.Default.Star,
                title = "Part 2: Signal vs. Noise",
                description = "High achievers don't do more things; they focus on the right things.",
                points = listOf(
                    "The Brain Dump" to "Your morning brain is chaotic. Dump every thought, task, and anxiety into a Brain Dump task to silence your mind and prevent distractions later.",
                    "Identify the Signal" to "Pick ONE Daily Highlight that would make the day a win. It can be work, gym, or self-care. Focus on fulfillment to create a positive reinforcement loop.",
                    "Everything Else is Noise" to "Once your Highlight is set, everything else is just 'noise.' Protect your signal at all costs and learn to say no."
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            FrameworkCard(
                icon = Icons.Default.PlayArrow,
                title = "Part 3: Keeping the Momentum",
                description = "Task switching kills momentum. Protect your focus.",
                points = listOf(
                    "Follow Your Energy" to "Don't 'eat the frog' if it drains you. Do interesting tasks until 4:00 PM, then strictly focus on your Highlight until 9:00 PM. Stop working completely at 9:00 PM.",
                    "The Sprinting Method" to "Categorize tasks by brain power (Urgent, Deadlines, Admin, Creative). Treat the sprint as ONE task and do not take breaks until that specific category is cleared.",
                    "Use Visual Boards" to "Staring at a massive list of 50 tasks is paralyzing. focusly breaks things down into manageable cards so you don't get overwhelmed."
                )
            )
        }
    }
}

@Composable
fun FrameworkCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    points: List<Pair<String, String>>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            points.forEachIndexed { index, point ->
                Column(modifier = Modifier.padding(bottom = if (index == points.size - 1) 0.dp else 16.dp)) {
                    Text(point.first, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(point.second, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                }
            }
        }
    }
}

/**
 * Lightweight physics engine for a celebratory confetti burst.
 */
data class Particle(
    val xVel: Float,
    val yVel: Float,
    val color: Color,
    val size: Float,
    val isCircle: Boolean,
    val rotSpeed: Float
)

@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val animatable = remember { Animatable(0f) }

    val particles = remember {
        List(150) {
            val angle = Math.toRadians(Random.nextDouble(0.0, 360.0))
            val speed = Random.nextDouble(1500.0, 6000.0)

            Particle(
                xVel = (cos(angle) * speed).toFloat(),
                yVel = (sin(angle) * speed).toFloat(),
                color = listOf(Color(0xFFF5B735), Color.White, Color(0xFFFFD700)).random(),
                size = Random.nextFloat() * 24f + 12f,
                isCircle = Random.nextBoolean(),
                rotSpeed = Random.nextFloat() * 1080f - 540f
            )
        }
    }

    LaunchedEffect(Unit) {
        animatable.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
        onFinished()
    }

    Canvas(modifier = modifier) {
        val progress = animatable.value
        val startX = size.width / 2f
        val startY = size.height / 3f

        particles.forEach { p ->
            val px = startX + (p.xVel * progress)
            val py = startY + (p.yVel * progress) + (3500f * progress * progress)

            val currentRotation = p.rotSpeed * progress

            val alpha = if (progress < 0.7f) 1f else (1f - progress) / 0.3f

            rotate(degrees = currentRotation, pivot = Offset(px, py)) {
                if (p.isCircle) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = p.size / 2f,
                        center = Offset(px, py)
                    )
                } else {
                    drawRect(
                        color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                        topLeft = Offset(px - p.size / 2f, py - p.size / 2f),
                        size = Size(p.size, p.size)
                    )
                }
            }
        }
    }
}