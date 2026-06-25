package com.scripthub.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EnvVarDao {
    // Flow 会自动监听数据库变化，只要数据库有增删改，UI 瞬间自动刷新！
    @Query("SELECT * FROM env_vars")
    fun getAll(): Flow<List<EnvVarEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(envVar: EnvVarEntity)

    @Update
    suspend fun update(envVar: EnvVarEntity)

    @Delete
    suspend fun delete(envVar: EnvVarEntity)
}

@Dao
interface DependencyDao {
    @Query("SELECT * FROM dependencies")
    fun getAll(): Flow<List<DependencyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dependency: DependencyEntity)

    @Update
    suspend fun update(dependency: DependencyEntity)
    
    @Delete
    suspend fun delete(dependency: DependencyEntity)
}

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY name ASC")
    fun getAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY name ASC")
    suspend fun getAllOnce(): List<ScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTaskEntity)

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Delete
    suspend fun delete(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface RunLogDao {
    @Query("SELECT * FROM run_logs WHERE scriptName = :scriptName ORDER BY startTime DESC LIMIT 20")
    fun getLogsForScript(scriptName: String): Flow<List<RunLogEntity>>

    @Query("SELECT * FROM run_logs ORDER BY startTime DESC LIMIT 50")
    fun getAllRecent(): Flow<List<RunLogEntity>>

    @Insert
    suspend fun insert(log: RunLogEntity)

    @Query("DELETE FROM run_logs WHERE scriptName = :scriptName AND id NOT IN (SELECT id FROM run_logs WHERE scriptName = :scriptName ORDER BY startTime DESC LIMIT 20)")
    suspend fun pruneOldLogs(scriptName: String)

    @Query("DELETE FROM run_logs WHERE scriptName = :scriptName")
    suspend fun deleteForScript(scriptName: String)

    @Query("DELETE FROM run_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM run_logs WHERE startTime >= :startOfDayMs")
    fun countTodayFlow(startOfDayMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM run_logs WHERE startTime >= :startOfDayMs")
    suspend fun countToday(startOfDayMs: Long): Int
}

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts")
    fun getAll(): Flow<List<ScriptEntity>>
    
    @Query("SELECT * FROM scripts")
    suspend fun getAllOnce(): List<ScriptEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: ScriptEntity)

    @Query("SELECT * FROM scripts WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ScriptEntity?

    @Delete
    suspend fun delete(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("UPDATE scripts SET lastRun = :lastRun WHERE name = :name")
    suspend fun updateLastRun(name: String, lastRun: String)

    @Query("UPDATE scripts SET trigger = :trigger WHERE name = :name")
    suspend fun updateTrigger(name: String, trigger: String)

    @Query("UPDATE scripts SET entryPoint = :entryPoint WHERE name = :name")
    suspend fun updateEntryPoint(name: String, entryPoint: String)

    @Query("UPDATE scripts SET name = :newName WHERE name = :oldName")
    suspend fun updateName(oldName: String, newName: String)
}