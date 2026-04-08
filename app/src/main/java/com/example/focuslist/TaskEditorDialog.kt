package com.example.focuslist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    initialName: String = "",
    initialHour: Int = 0,
    initialMinute: Int = 0,
    initialSecond: Int = 0,
    initialCategory: TaskCategory = TaskCategory.STANDARD,
    pastTasks: List<Task> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int, TaskCategory) -> Unit
) {
    var taskName by remember(initialName) { mutableStateOf(initialName) }
    var selectedHour by remember(initialHour) { mutableIntStateOf(initialHour) }
    var selectedMinute by remember(initialMinute) { mutableIntStateOf(initialMinute) }
    var selectedSecond by remember(initialSecond) { mutableIntStateOf(initialSecond) }
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var expanded by remember { mutableStateOf(false) }

    val dialogTitle = if (initialName.isEmpty()) "New Task" else "Edit Task"
    val buttonText = if (initialName.isEmpty()) "Add Task" else "Save Changes"

    val formatCategoryName: (String) -> String = { name ->
        name.split("_").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("Task Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialName.isEmpty() && pastTasks.isNotEmpty()) {
                    val uniquePastTasks = pastTasks
                        .sortedByDescending { it.id }
                        .distinctBy { it.name.trim().lowercase() }
                        .take(15)

                    if (uniquePastTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uniquePastTasks) { pastTask ->
                                SuggestionChip(
                                    onClick = {
                                        taskName = pastTask.name
                                        selectedCategory = pastTask.category

                                        val h = (pastTask.totalSeconds / 3600).toInt()
                                        val m = ((pastTask.totalSeconds % 3600) / 60).toInt()
                                        val s = (pastTask.totalSeconds % 60).toInt()

                                        selectedHour = h
                                        selectedMinute = m
                                        selectedSecond = s
                                    },
                                    label = { Text(pastTask.name) },
                                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(formatCategoryName(selectedCategory.name))
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            TaskCategory.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(formatCategoryName(category.name)) },
                                    onClick = {
                                        selectedCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = selectedCategory != TaskCategory.BRAIN_DUMP,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LabeledWheel(label = "hours", count = 24, initialValue = selectedHour, onScroll = { selectedHour = it })
                            LabeledWheel(label = "minutes", count = 60, initialValue = selectedMinute, onScroll = { selectedMinute = it })
                            LabeledWheel(label = "seconds", count = 60, initialValue = selectedSecond, onScroll = { selectedSecond = it })
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val isTimedCategory = selectedCategory != TaskCategory.BRAIN_DUMP
                val currentTotalSecs = (selectedHour * 3600) + (selectedMinute * 60) + selectedSecond

                val isSaveEnabled = taskName.isNotBlank() && (!isTimedCategory || currentTotalSecs > 0)

                val animatedButtonColor by animateColorAsState(
                    targetValue = if (isSaveEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "Save Button Color"
                )
                val animatedTextColor by animateColorAsState(
                    targetValue = if (isSaveEnabled) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "Save Button Text Color"
                )

                Button(
                    onClick = {
                        val finalH = if (selectedCategory == TaskCategory.BRAIN_DUMP) 0 else selectedHour
                        val finalM = if (selectedCategory == TaskCategory.BRAIN_DUMP) 0 else selectedMinute
                        val finalS = if (selectedCategory == TaskCategory.BRAIN_DUMP) 0 else selectedSecond

                        onConfirm(taskName, finalH, finalM, finalS, selectedCategory)
                    },
                    enabled = isSaveEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animatedButtonColor,
                        contentColor = animatedTextColor,
                        disabledContainerColor = animatedButtonColor,
                        disabledContentColor = animatedTextColor
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(buttonText, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun LabeledWheel(label: String, count: Int, initialValue: Int, onScroll: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WheelPicker(count = count, startIndex = initialValue, rowCount = 3, onScrollFinished = { onScroll(it) }) { index ->
            Text(text = index.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (index == initialValue) MaterialTheme.colorScheme.primary else Color.Gray)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(count: Int, startIndex: Int, rowCount: Int, onScrollFinished: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeight = 50.dp

    LaunchedEffect(startIndex) {
        if (!listState.isScrollInProgress) {
            listState.scrollToItem(startIndex)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val containerCenter = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                var closestIndex = -1; var minDiff = Float.MAX_VALUE
                layoutInfo.visibleItemsInfo.forEach { item ->
                    val itemCenter = item.offset + item.size / 2f
                    val diff = abs(containerCenter - itemCenter)
                    if (diff < minDiff) { minDiff = diff; closestIndex = item.index }
                }
                if (closestIndex != -1) onScrollFinished(closestIndex)
            }
        }
    }

    Box(modifier = Modifier.height(itemHeight * rowCount).width(80.dp), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.fillMaxWidth().height(itemHeight), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {}
        LazyColumn(state = listState, flingBehavior = flingBehavior, horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(vertical = itemHeight * (rowCount / 2)), modifier = Modifier.fillMaxSize()) {
            items(count) { index -> Box(modifier = Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) { content(index) } }
        }
    }
}