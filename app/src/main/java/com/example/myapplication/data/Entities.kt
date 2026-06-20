package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myapplication.ui.screens.DepStatus
import com.example.myapplication.ui.screens.DepType
import java.util.UUID

@Entity(tableName = "env_vars")
data class EnvVarEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: String,
    val remarks: String = "",
    val isEnabled: Boolean = true
)

@Entity(tableName = "dependencies")
data class DependencyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DepType, // Room 需要转换器来存储枚举
    val status: DepStatus,
    val version: String = "latest"
)