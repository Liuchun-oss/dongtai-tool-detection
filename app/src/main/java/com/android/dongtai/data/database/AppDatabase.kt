package com.android.dongtai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.android.dongtai.data.converter.Converters
import com.android.dongtai.data.dao.ToolRecordDao
import com.android.dongtai.data.entity.ToolRecord

/**
 * 应用数据库
 */
@Database(
    entities = [ToolRecord::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun toolRecordDao(): ToolRecordDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tool_database"
                )
                .fallbackToDestructiveMigration() // 开发阶段使用，生产环境应使用Migration
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
