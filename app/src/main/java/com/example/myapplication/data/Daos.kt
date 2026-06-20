package com.example.myapplication.data

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
}