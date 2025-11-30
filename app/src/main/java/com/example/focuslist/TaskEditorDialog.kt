package com.example.focuslist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs

@Composable
fun TaskEditorDialog(
    initialName: String = "",
    initialHour: Int = 0,
    initialMinute: Int = 0,
    initialSecond: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int) -> Unit
) {
    var taskName by remember(initialName) { mutableStateOf(initialName) }
    var selectedHour by remember(initialHour) { mutableIntStateOf(initialHour) }
    var selectedMinute by remember(initialMinute) { mutableIntStateOf(initialMinute) }
    var selectedSecond by remember(initialSecond) { mutableIntStateOf(initialSecond) }

    val dialogTitle = if (initialName.isEmpty()) "New Task" else "Edit Task"
    val buttonText = if (initialName.isEmpty()) "Add Task" else "Save Changes"

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

                Spacer(modifier = Modifier.height(24.dp))

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

                Button(
                    onClick = {
                        if (taskName.isNotBlank()) {
                            if (selectedHour + selectedMinute + selectedSecond > 0) {
                                onConfirm(taskName, selectedHour, selectedMinute, selectedSecond)
                            }
                        }
                    },
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