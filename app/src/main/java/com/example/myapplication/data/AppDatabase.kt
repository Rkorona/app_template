package com.example.myapplication.data

import android.content.Context
import androidx.room.*
import com.example.myapplication.ui.screens.DepStatus
import com.example.myapplication.ui.screens.DepType

// 转换器：把枚举转为 String 存入数据库，取出时转回枚举
class Converters {
    @TypeConverter
    fun fromDepType(value: DepType): String = value.name
    @TypeConverter
    fun toDepType(value: String): DepType = enumValueOf(value)

    @TypeConverter
    fun fromDepStatus(value: DepStatus): String = value.name
    @TypeConverter
    fun toDepStatus(value: String): DepStatus = enumValueOf(value)
}

@Database(entities = [EnvVarEntity::class, DependencyEntity::class, ScriptEntity::class, ScheduledTaskEntity::class, RunLogEntity::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun envVarDao(): EnvVarDao
    abstract fun dependencyDao(): DependencyDao
    abstract fun scriptDao(): ScriptDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun runLogDao(): RunLogDao

    // 单例模式，防止多次打开数据库
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kls_database.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}