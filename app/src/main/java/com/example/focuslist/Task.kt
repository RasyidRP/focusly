package com.example.focuslist

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)