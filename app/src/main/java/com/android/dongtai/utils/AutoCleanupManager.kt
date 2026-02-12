package com.android.dongtai.utils

import android.content.Context
import java.io.File
import java.util.*

/**
 * 自动清理管理器
 * 根据设置自动清理日志和图片
 */
object AutoCleanupManager {
    
    /**
     * 清理过期日志
     */
    fun cleanOldLogs(context: Context) {
        val autoCleanLogs = SettingsManager.getAutoCleanLogs(context)
        if (!autoCleanLogs) {
            return
        }
        
        val retentionDays = SettingsManager.getLogRetentionDays(context)
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            return
        }
        
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        logDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        
        if (deletedCount > 0) {
            DetectionLogger.log(DetectionLogger.Level.INFO, "自动清理了 $deletedCount 个过期日志文件（保留 $retentionDays 天）")
        }
    }
    
    /**
     * 清理过期图片
     */
    fun cleanOldImages(context: Context) {
        val autoCleanImages = SettingsManager.getAutoCleanImages(context)
        if (!autoCleanImages) {
            return
        }
        
        val retentionDays = SettingsManager.getImageRetentionDays(context)
        val imageDir = context.getExternalFilesDir(null)
        if (imageDir == null || !imageDir.exists()) {
            return
        }
        
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        imageDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith("_annotated.jpg"))) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
        }
        
        if (deletedCount > 0) {
            DetectionLogger.log(DetectionLogger.Level.INFO, "自动清理了 $deletedCount 张过期图片（保留 $retentionDays 天）")
        }
    }
    
    /**
     * 执行所有清理任务
     */
    fun performCleanup(context: Context) {
        cleanOldLogs(context)
        cleanOldImages(context)
    }
}
