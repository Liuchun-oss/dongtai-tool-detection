package com.android.dongtai.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 检测日志工具类
 * 用于记录模型加载、推理过程的详细信息，方便调试
 */
object DetectionLogger {
    
    private const val TAG = "OnnxDetection"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    // 日志级别
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 初始化日志文件
     */
    fun init(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logDir, "detection_log_$timestamp.txt")
            
            log(Level.INFO, "=== 检测日志开始 ===")
            log(Level.INFO, "设备信息: ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
            log(Level.INFO, "应用版本: 1.0")
        } catch (e: Exception) {
            Log.e(TAG, "初始化日志文件失败", e)
        }
    }
    
    /**
     * 记录日志
     */
    fun log(level: Level, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] [${level.name}] $message"
        
        // 输出到Logcat
        when (level) {
            Level.DEBUG -> Log.d(TAG, message, throwable)
            Level.INFO -> Log.i(TAG, message, throwable)
            Level.WARN -> Log.w(TAG, message, throwable)
            Level.ERROR -> Log.e(TAG, message, throwable)
        }
        
        // 写入文件
        try {
            logFile?.appendText("$logMessage\n")
            if (throwable != null) {
                logFile?.appendText("异常: ${throwable.message}\n")
                logFile?.appendText("堆栈: ${throwable.stackTraceToString()}\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败", e)
        }
    }
    
    /**
     * 记录模型初始化
     */
    fun logModelInit(success: Boolean, duration: Long, error: String? = null) {
        if (success) {
            log(Level.INFO, "✅ 模型初始化成功，耗时: ${duration}ms")
        } else {
            log(Level.ERROR, "❌ 模型初始化失败: $error")
        }
    }
    
    /**
     * 记录图片预处理
     */
    fun logPreprocess(imagePath: String, width: Int, height: Int, success: Boolean) {
        if (success) {
            log(Level.INFO, "✅ 图片预处理成功: $imagePath (${width}x${height})")
        } else {
            log(Level.ERROR, "❌ 图片预处理失败: $imagePath")
        }
    }
    
    /**
     * 记录推理过程
     */
    fun logInference(duration: Long, detectionCount: Int, success: Boolean) {
        if (success) {
            log(Level.INFO, "✅ 推理完成，耗时: ${duration}ms，检测到 $detectionCount 个目标")
        } else {
            log(Level.ERROR, "❌ 推理失败")
        }
    }
    
    /**
     * 记录检测结果
     */
    fun logDetectionResults(results: List<String>) {
        if (results.isEmpty()) {
            log(Level.WARN, "⚠️ 未检测到任何工具")
        } else {
            log(Level.INFO, "📊 检测结果: ${results.joinToString(", ")}")
        }
    }
    
    /**
     * 记录NMS后处理
     */
    fun logNMS(beforeCount: Int, afterCount: Int) {
        log(Level.INFO, "🔄 NMS处理: $beforeCount -> $afterCount 个检测框")
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * 清理旧日志（保留最近7天）
     */
    fun cleanOldLogs(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (logDir.exists()) {
                val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
                logDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete()
                        Log.d(TAG, "删除旧日志: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧日志失败", e)
        }
    }
}
