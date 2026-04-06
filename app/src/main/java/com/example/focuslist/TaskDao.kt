package com.example.focuslist

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY sortOrder ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC")
    fun getEveryTaskHistory(): Flow<List<Task>>

    @Query("UPDATE tasks SET isArchived = 1 WHERE isCompleted = 1")
    suspend fun archiveCompletedTasks()

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Update
    suspend fun update(task: Task)

    @Update
    suspend fun updateAll(tasks: List<Task>)

    @Query("DELETE FROM tasks WHERE isArchived = 1")
    suspend fun wipeArchivedTasks()

    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND isCompleted = 0")
    suspend fun getActiveTasksSnapshot(): List<Task>

    @Query("SELECT * FROM tasks")
    suspend fun getEverySingleTaskSnapshot(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)
}