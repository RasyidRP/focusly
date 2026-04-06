package com.example.focuslist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Core ViewModel managing the Kaizen productivity state.
 * * Handles Room database transactions, state hoisting for the UI, and zero-dependency
 * JSON serialization (using org.json) for the offline-first Data Vault export/import functionality.
 */
class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    val tasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTaskHistory: StateFlow<List<Task>> = dao.getEveryTaskHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(name: String, durationMinutes: Int) {
        viewModelScope.launch {
            val durationSeconds = durationMinutes * 60L
            val newTask = Task(
                name = name,
                totalSeconds = durationSeconds,
                remainingSeconds = durationSeconds
            )
            dao.insert(newTask)
        }
    }

    fun toggleTaskRunning(task: Task) {
        viewModelScope.launch {
            dao.update(task.copy(isRunning = !task.isRunning))
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val isNowCompleted = !task.isCompleted
            val updatedTask = task.copy(
                isCompleted = isNowCompleted,
                isRunning = false,
                completedAtMillis = if (isNowCompleted) System.currentTimeMillis() else null
            )
            dao.update(updatedTask)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { dao.delete(task) }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch { dao.archiveCompletedTasks() }
    }

    fun addTaskWithSeconds(name: String, totalDurationSeconds: Long, category: TaskCategory) {
        viewModelScope.launch {
            val newTask = Task(
                name = name,
                totalSeconds = totalDurationSeconds,
                remainingSeconds = totalDurationSeconds,
                category = category
            )
            dao.insert(newTask)
        }
    }

    fun updateTaskOrder(reorderedTasks: List<Task>) {
        viewModelScope.launch {
            val updatedTasks = reorderedTasks.mapIndexed { index, task ->
                task.copy(sortOrder = index)
            }
            dao.updateAll(updatedTasks)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch { dao.update(task) }
    }

    fun wipeAnalyticsData() {
        viewModelScope.launch { dao.wipeArchivedTasks() }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val tasks = dao.getEverySingleTaskSnapshot()
                val jsonArray = JSONArray()

                for (task in tasks) {
                    val obj = JSONObject()
                    obj.put("id", task.id)
                    obj.put("name", task.name)
                    obj.put("totalSeconds", task.totalSeconds)
                    obj.put("remainingSeconds", task.remainingSeconds)
                    obj.put("category", task.category.name)
                    obj.put("isRunning", false)
                    obj.put("isCompleted", task.isCompleted)
                    obj.put("sortOrder", task.sortOrder)
                    obj.put("createdAtMillis", task.createdAtMillis)
                    if (task.completedAtMillis != null) {
                        obj.put("completedAtMillis", task.completedAtMillis)
                    }
                    obj.put("isArchived", task.isArchived)
                    jsonArray.put(obj)
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonArray.toString(4))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val stringBuilder = java.lang.StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }

                val jsonArray = JSONArray(stringBuilder.toString())
                val importedTasks = mutableListOf<Task>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val task = Task(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        totalSeconds = obj.getLong("totalSeconds"),
                        remainingSeconds = obj.getLong("remainingSeconds"),
                        category = TaskCategory.valueOf(obj.getString("category")),
                        isRunning = false,
                        isCompleted = obj.getBoolean("isCompleted"),
                        sortOrder = if (obj.has("sortOrder")) obj.getInt("sortOrder") else 0,
                        createdAtMillis = if (obj.has("createdAtMillis")) obj.getLong("createdAtMillis") else System.currentTimeMillis(),
                        completedAtMillis = if (obj.has("completedAtMillis") && !obj.isNull("completedAtMillis")) obj.getLong("completedAtMillis") else null,
                        isArchived = if (obj.has("isArchived")) obj.getBoolean("isArchived") else false
                    )
                    importedTasks.add(task)
                }
                dao.insertAll(importedTasks)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}