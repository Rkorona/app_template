package com.scripthub.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scripthub.app.data.AppDatabase
import com.scripthub.app.data.DependencyEntity
import com.scripthub.app.data.EnvVarEntity
import com.scripthub.app.ui.screens.DepStatus
import com.scripthub.app.ui.screens.DepType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val envDao = db.envVarDao()
    private val depDao = db.dependencyDao()

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

    fun addOrUpdateDependency(dep: DependencyEntity) {
        viewModelScope.launch { depDao.insert(dep) }
    }

    fun deleteDependency(dep: DependencyEntity) {
        viewModelScope.launch { depDao.delete(dep) }
    }

    /** 先将依赖写入数据库并标记为「安装中」，实际安装由 DepInstallConsoleBottomSheet 执行 */
    fun installDependency(dep: DependencyEntity) {
        viewModelScope.launch {
            depDao.insert(dep.copy(status = DepStatus.Installing))
        }
    }

    /** 安装结束后由 Sheet 回调更新最终状态 */
    fun updateDepStatus(dep: DependencyEntity, status: DepStatus) {
        viewModelScope.launch {
            depDao.update(dep.copy(status = status))
        }
    }

    /** 根据依赖类型构造对应包管理器的安装命令 */
    fun buildInstallCommand(dep: DependencyEntity): String {
        val ver = dep.version.trim()
        val hasVersion = ver.isNotEmpty() && ver != "latest"
        return when (dep.type) {
            DepType.NodeJS -> {
                val pkg = if (hasVersion) "${dep.name}@$ver" else dep.name
                "npm install -g $pkg"
            }
            DepType.Python3 -> {
                val pkg = if (hasVersion) "${dep.name}==$ver" else dep.name
                "pip3 install --break-system-packages $pkg"
            }
            DepType.Linux -> {
                "DEBIAN_FRONTEND=noninteractive apt-get install -y ${dep.name}"
            }
        }
    }
}
