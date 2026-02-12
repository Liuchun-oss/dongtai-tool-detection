package com.android.dongtai.data.entity

import java.text.SimpleDateFormat
import java.util.*

/**
 * 工作记录组合（包含作业前和作业后）
 */
data class WorkRecord(
    val beforeRecord: ToolRecord,
    val afterRecord: ToolRecord? = null
) {
    /**
     * 获取记录显示名称
     * 格式：作业前时间-作业后时间 或 作业前时间-作业中，未结束
     */
    fun getDisplayName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val beforeTime = dateFormat.format(Date(beforeRecord.timestamp))
        
        return if (afterRecord != null) {
            val afterTime = dateFormat.format(Date(afterRecord.timestamp))
            "$beforeTime - $afterTime"
        } else {
            "$beforeTime - 作业中，未结束"
        }
    }
    
    /**
     * 获取记录ID（使用作业前记录的ID作为唯一标识）
     */
    fun getId(): Long = beforeRecord.id
    
    /**
     * 是否已完成（有作业后记录）
     */
    fun isCompleted(): Boolean = afterRecord != null
}
