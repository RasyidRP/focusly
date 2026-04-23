package com.rasyid.focuslist

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Defines the priority level and operational framework of a task.
 */
enum class TaskCategory {
    HIGHLIGHT,
    MICRO_COMMITMENT,
    STANDARD,
    BRAIN_DUMP
}

/**
 * Core entity representing a Kaizen productivity item.
 * * @property totalSeconds The original estimated time budget.
 * @property remainingSeconds The live-updating countdown state. Can go negative to track overtime.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val category: TaskCategory = TaskCategory.STANDARD,
    val isArchived: Boolean = false,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)