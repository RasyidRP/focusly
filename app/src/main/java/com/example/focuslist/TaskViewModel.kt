package com.example.focuslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    val tasks: StateFlow<List<Task>> = dao.getAllTasks()
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
        val command = if(!task.isCompleted) TimerService.ACTION_STOP else null
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted, isRunning = false)
            dao.update(updatedTask)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.delete(task)
        }
    }

    fun addTaskWithSeconds(name: String, totalDurationSeconds: Long) {
        viewModelScope.launch {
            if (totalDurationSeconds > 0) {
                val newTask = Task(
                    name = name,
                    totalSeconds = totalDurationSeconds,
                    remainingSeconds = totalDurationSeconds
                )
                dao.insert(newTask)
            }
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
        viewModelScope.launch {
            dao.update(task)
        }
    }
}