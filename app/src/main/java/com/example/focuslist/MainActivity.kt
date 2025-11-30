package com.example.focuslist

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private var isTaskRunning = false
    private var isInPiPModeState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            FocusListTheme {
                val context = LocalContext.current
                val database = AppDatabase.getDatabase(context)
                val factory = TaskViewModelFactory(database.taskDao())
                val viewModel: TaskViewModel = viewModel(factory = factory)

                val tasks by viewModel.tasks.collectAsState(initial = emptyList())
                val runningTask = tasks.find { it.isRunning }
                isTaskRunning = runningTask != null

                var fullScreenTaskId by remember { mutableStateOf<String?>(null) }

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

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(60000)
            currentTime = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = taskInFullScreen != null) {
        onSetFullScreenTask(null)
    }

    val currentScreen = when {
        isInPiPMode -> AppScreen.PiP
        taskInFullScreen != null -> AppScreen.FullScreen(taskInFullScreen.id)
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
            is AppScreen.PiP -> {
                PiPTaskUI(runningTask = runningTask)
            }
            is AppScreen.FullScreen -> {
                val task = tasksFlow.find { it.id == screen.taskId }
                if (task != null) {
                    FullScreenTaskUI(
                        task = task,
                        currentTimeMillis = currentTime,
                        onTogglePlayPause = {
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = if (task.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
                                putExtra(TimerService.EXTRA_TASK_ID, task.id)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            viewModel.toggleTaskRunning(task)
                        },
                        onEditClick = { taskBeingEdited = task },
                        onMinimize = {
                            onSetFullScreenTask(null)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
            is AppScreen.MainList -> {
                FullAppUI(
                    viewModel = viewModel,
                    tasksFlow = tasksFlow,
                    runningTask = runningTask,
                    currentTimeMillis = currentTime,
                    onEnterFullScreen = { taskId -> onSetFullScreenTask(taskId) },
                    onEditClick = { taskToEdit -> taskBeingEdited = taskToEdit }
                )
            }
        }
    }

    taskBeingEdited?.let { task ->
        val (h, m, s) = secondsToHms(max(0L, task.remainingSeconds))

        TaskEditorDialog(
            initialName = task.name,
            initialHour = h,
            initialMinute = m,
            initialSecond = s,
            onDismiss = { taskBeingEdited = null },
            onConfirm = { newName, newHours, newMins, newSecs ->
                val newTotalSeconds = (newHours * 3600L) + (newMins * 60L) + newSecs
                val updatedTask = task.copy(name = newName, totalSeconds = newTotalSeconds, remainingSeconds = newTotalSeconds)
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
    onTogglePlayPause: () -> Unit,
    onEditClick: () -> Unit,
    onMinimize: () -> Unit
) {
    val isOvertime = task.remainingSeconds < 0
    val progress = if (task.totalSeconds > 0 && !isOvertime) {
        task.remainingSeconds.toFloat() / task.totalSeconds.toFloat()
    } else 0f

    val timerColor = if (isOvertime) Color.Red else MaterialTheme.colorScheme.primary

    val animatedCircleBackgroundColor by animateColorAsState(
        targetValue = if (task.isRunning) LightGrey else DarkGrey,
        animationSpec = tween(durationMillis = 150),
        label = "Timer Background Color Animation"
    )

    val predictedFinishTime = formatPredictedTime(currentTimeMillis, task.remainingSeconds)
    val finishTimeColor = if (isOvertime) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    val elapsedSeconds = max(0L, task.totalSeconds - task.remainingSeconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = finishTimeColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Finish by: $predictedFinishTime",
                    style = MaterialTheme.typography.titleMedium,
                    color = finishTimeColor
                )
            }

            if (elapsedSeconds > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Time Elapsed: ${formatSeconds(elapsedSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(color = animatedCircleBackgroundColor, shape = CircleShape)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = timerColor,
                strokeWidth = 16.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round
            )

            Text(
                text = formatSeconds(task.remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = timerColor
            )
        }

        Row(
            modifier = Modifier.padding(bottom = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onEditClick,
                enabled = !task.isRunning,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Task",
                    tint = if (task.isRunning) Color.Gray else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (task.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (task.isRunning) "Pause" else "Start",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Minimize",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
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
                    color = if (isOvertime) Color.Red else Color.LightGray
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

@Composable
fun FullAppUI(
    viewModel: TaskViewModel,
    tasksFlow: List<Task>,
    runningTask: Task?,
    currentTimeMillis: Long,
    onEnterFullScreen: (String) -> Unit,
    onEditClick: (Task) -> Unit
) {
    val orderedTaskIds = remember { mutableStateListOf<String>() }
    var taskDataMap by remember { mutableStateOf(emptyMap<String, Task>()) }

    val totalSeconds = tasksFlow.filter { !it.isCompleted }.sumOf { max(0L, it.remainingSeconds) }
    var showAddDialog by remember { mutableStateOf(false) }
    val isAnyTaskRunning = runningTask != null

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(60000)
            currentTime = System.currentTimeMillis()
        }
    }

    val state = rememberReorderableLazyListState(
        onMove = { from, to -> orderedTaskIds.apply { add(to.index, removeAt(from.index)) } },
        onDragEnd = { _, _ ->
            val newOrderTasks = orderedTaskIds.mapNotNull { id -> taskDataMap[id] }
            viewModel.updateTaskOrder(newOrderTasks)
        }
    )

    LaunchedEffect(tasksFlow) {
        if (orderedTaskIds.isEmpty() && tasksFlow.isNotEmpty()) {
            orderedTaskIds.addAll(tasksFlow.map { it.id })
        }
    }

    LaunchedEffect(tasksFlow) {
        val currentIdsSet = orderedTaskIds.toSet()
        val newIdsSet = tasksFlow.map { it.id }.toSet()
        orderedTaskIds.removeAll { id -> id !in newIdsSet }
        val newTasks = tasksFlow.filter { it.id !in currentIdsSet }
        newTasks.forEach { task -> orderedTaskIds.add(task.id) }
    }

    LaunchedEffect(tasksFlow) {
        taskDataMap = tasksFlow.associateBy { it.id }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.header_logo),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "focusly.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Time Remaining Today", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = formatSeconds(totalSeconds), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Final predicted finish: ${formatPredictedTime(currentTimeMillis, totalSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.height(50.dp).fillMaxWidth(0.8f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Task", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            state = state.listState,
            modifier = Modifier.fillMaxWidth().weight(1f).reorderable(state),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(orderedTaskIds, key = { _, id -> id }) { index, id ->
                val task = taskDataMap[id]

                if (task != null) {
                    val runningTotalAtIndex = if (!task.isCompleted) {
                        orderedTaskIds.take(index + 1)
                            .mapNotNull { taskDataMap[it] }
                            .filter { !it.isCompleted }
                            .sumOf { max(0L, it.remainingSeconds) }
                    } else 0L

                    val predictedTimeForThisTask = if (!task.isCompleted) {
                        formatPredictedTime(currentTimeMillis, runningTotalAtIndex)
                    } else {
                        null
                    }

                    ReorderableItem(reorderableState = state, key = task.id) { isDragging ->
                        TaskItem(
                            task = task,
                            isDragging = isDragging,
                            canToggleTimer = task.isRunning || !isAnyTaskRunning,
                            onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            dragModifier = Modifier.detectReorder(state),
                            onEditClick = { onEditClick(task) },
                            predictedFinishTime = predictedTimeForThisTask,
                            onEnterFullScreen = { onEnterFullScreen(task.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        TaskEditorDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, hours, mins, secs ->
                val totalDurationSeconds = (hours * 3600L) + (mins * 60L) + secs
                viewModel.addTaskWithSeconds(name, totalDurationSeconds)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TaskItem(
    task: Task,
    isDragging: Boolean,
    canToggleTimer: Boolean,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
    onEditClick: () -> Unit,
    predictedFinishTime: String?,
    onEnterFullScreen: () -> Unit
) {
    val context = LocalContext.current

    val animatedContainerColor by animateColorAsState(
        targetValue = if (task.isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 150),
        label = "Card Container Color Animation"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (task.isRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 150),
        label = "Card Content Color Animation"
    )

    val elevation = if (isDragging) 8.dp else 2.dp
    val isOvertime = task.remainingSeconds < 0
    val elapsedSeconds = max(0L, task.totalSeconds - task.remainingSeconds)

    val secondLineStyle = MaterialTheme.typography.bodySmall
    val secondLineColor = if(isOvertime) Color.Red.copy(alpha=0.7f) else animatedContentColor.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor, contentColor = animatedContentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = {
                    if (task.isRunning) {
                        val intent = Intent(context, TimerService::class.java).apply {
                            action = TimerService.ACTION_STOP
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
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

                    Text(
                        text = formatSeconds(task.remainingSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (task.isCompleted) Color.Gray else if (isOvertime) Color.Red else animatedContentColor
                    )
                }

                if (!task.isCompleted) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (predictedFinishTime != null) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = secondLineColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Finish by: $predictedFinishTime",
                                style = secondLineStyle,
                                color = secondLineColor
                            )
                        }

                        if (predictedFinishTime != null && elapsedSeconds > 0) {
                            Text(
                                text = "  |  ",
                                style = secondLineStyle,
                                color = secondLineColor
                            )
                        }

                        if (elapsedSeconds > 0) {
                            Text(
                                text = "Elapsed: ${formatSeconds(elapsedSeconds)}",
                                style = secondLineStyle,
                                color = secondLineColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = if (task.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
                                putExtra(TimerService.EXTRA_TASK_ID, task.id)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                        },
                        enabled = !task.isCompleted && canToggleTimer
                    ) {
                        Icon(
                            imageVector = if (task.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (task.isRunning) "Pause" else "Start",
                            tint = if (task.isCompleted || !canToggleTimer) Color.Gray else animatedContentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEditClick,
                        enabled = !task.isCompleted && !task.isRunning,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Task",
                            tint = if (task.isCompleted || task.isRunning) Color.Gray else animatedContentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onEnterFullScreen,
                        enabled = !task.isCompleted && canToggleTimer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Enter Full Screen",
                            tint = if (!task.isCompleted && canToggleTimer) animatedContentColor else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { onDelete() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (task.isCompleted) Color.Gray else animatedContentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                tint = if(task.isRunning) animatedContentColor else Color.LightGray,
                modifier = dragModifier.padding(start = 8.dp)
            )
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