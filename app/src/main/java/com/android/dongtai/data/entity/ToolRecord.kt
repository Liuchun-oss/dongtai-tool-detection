package com.android.dongtai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 工具清点记录实体
 */
@Entity(tableName = "tool_records")
data class ToolRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordType: RecordType, // 作业前或作业后
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String, // 照片路径
    val toolsJson: String = "[]", // 工具列表JSON（暂时为空，等模型集成后再填充）
    val beforeRecordId: Long? = null, // 如果是作业后记录，关联的作业前记录ID
    val workGroup: String = "", // 作业组
    val workLocation: String = "" // 作业地点
)
