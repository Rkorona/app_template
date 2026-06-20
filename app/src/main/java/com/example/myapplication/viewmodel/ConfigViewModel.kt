package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.DependencyEntity
import com.example.myapplication.data.EnvVarEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val envDao = db.envVarDao()
    private val depDao = db.dependencyDao()

    // 将 Flow 转换为 StateFlow 给 Compose 观察使用，自带生命周期感知
    val envVarsList: StateFlow<List<EnvVarEntity>> = envDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val depsList: StateFlow<List<DependencyEntity>> = depDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- 环境变量操作 ---
    fun addOrUpdateEnv(env: EnvVarEntity) {
        viewModelScope.launch { envDao.insert(env) }
    }
    
    fun toggleEnvState(env: EnvVarEntity, isEnabled: Boolean) {
        viewModelScope.launch { envDao.update(env.copy(isEnabled = isEnabled)) }
    }

    fun deleteEnv(env: EnvVarEntity) {
        viewModelScope.launch { envDao.delete(env) }
    }

    // --- 依赖操作 ---
    fun addDependency(dep: DependencyEntity) {
        viewModelScope.launch { depDao.insert(dep) }
    }
    
    fun deleteDependency(dep: DependencyEntity) {
        viewModelScope.launch { depDao.delete(dep) }
    }
    
    // 初始化一些测试数据（仅用于第一次安装）
    fun insertMockDataIfNeeded() {
        viewModelScope.launch {
            if (envVarsList.value.isEmpty()) {
                envDao.insert(EnvVarEntity(name = "TG_BOT_TOKEN", value = "12345:ABCDE", remarks = "机器人Token"))
                envDao.insert(EnvVarEntity(name = "DEBUG_MODE", value = "true", isEnabled = false))
            }
        }
    }
}