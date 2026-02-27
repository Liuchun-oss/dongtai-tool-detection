package com.android.dongtai.utils

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.android.dongtai.service.OnnxToolDetectionService
import java.io.File

/**
 * 识别辅助工具类
 * 抽取 WorkInfoScreen 和 AfterWorkScreen 中的公共逻辑
 */
object DetectionHelper {

    /**
     * 识别结果数据类
     */
    data class DetectionResult(
        val toolsJson: String?,
        val detectedCount: Int,
        val toolsList: List<String>,
        val toolsWithConfidence: Map<String, Float>
    )

    /**
     * 执行工具识别（统一入口）
     * @param context 上下文
     * @param imagePath 图片路径
     * @param useTTA 是否使用TTA模式（重新识别时为true）
     * @return DetectionResult
     */
    suspend fun detectTools(
        context: Context,
        imagePath: String,
        useTTA: Boolean = false
    ): DetectionResult {
        // 删除旧的标注图片
        val annotatedPath = imagePath.replace(".jpg", "_annotated.jpg")
        val annotatedFile = File(annotatedPath)
        if (annotatedFile.exists()) {
            annotatedFile.delete()
            DetectionLogger.log(DetectionLogger.Level.INFO, "删除旧的标注图片")
        }

        val detectionService = OnnxToolDetectionService.getInstance(context)

        if (useTTA) {
            DetectionLogger.log(
                DetectionLogger.Level.INFO,
                "重新识别：使用TTA模式（原图+翻转+旋转多次推理合并）"
            )
        }

        val toolsJson = detectionService.detectTools(imagePath, null, useTTA = useTTA)

        DetectionLogger.log(DetectionLogger.Level.INFO, "识别完成 - toolsJson: $toolsJson")

        // 解析结果
        val comparisonMode = SettingsManager.getComparisonMode(context)
        return parseDetectionResult(toolsJson, comparisonMode)
    }

    /**
     * 解析识别结果
     */
    private fun parseDetectionResult(
        toolsJson: String?,
        comparisonMode: String
    ): DetectionResult {
        if (comparisonMode == "specific_tools" && toolsJson != null) {
            try {
                val toolsList = OnnxToolDetectionService.parseToolsJson(toolsJson)
                val count = toolsList.sumOf { tool ->
                    val parts = tool.split("*")
                    if (parts.size == 2) parts[1].toIntOrNull() ?: 0 else 0
                }
                val confidence = toolsList.associate { tool ->
                    tool.split("*")[0] to 0.9f
                }
                DetectionLogger.log(
                    DetectionLogger.Level.INFO,
                    "具体工具列表解析 - toolsList: $toolsList, count: $count"
                )
                return DetectionResult(toolsJson, count, toolsList, confidence)
            } catch (e: Exception) {
                DetectionLogger.log(
                    DetectionLogger.Level.WARN,
                    "解析工具列表失败: ${e.message}"
                )
                val count = OnnxToolDetectionService.parseToolCount(toolsJson ?: "")
                return DetectionResult(toolsJson, count, emptyList(), emptyMap())
            }
        } else {
            val count = OnnxToolDetectionService.parseToolCount(toolsJson ?: "")
            DetectionLogger.log(DetectionLogger.Level.INFO, "总数量对比模式 - count: $count")
            return DetectionResult(toolsJson, count, emptyList(), emptyMap())
        }
    }

    /**
     * 触发识别完成反馈（震动 + 声音）
     */
    fun triggerFeedback(context: Context) {
        val vibrationEnabled = SettingsManager.getVibrationFeedback(context)
        val soundEnabled = SettingsManager.getSoundFeedback(context)

        if (vibrationEnabled) {
            triggerVibration(context)
        }
        if (soundEnabled) {
            triggerSound(context)
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (e: Exception) {
            DetectionLogger.log(DetectionLogger.Level.WARN, "震动反馈失败: ${e.message}")
        }
    }

    /**
     * 播放系统通知音（之前的实现是假的，这次真的播放）
     */
    private fun triggerSound(context: Context) {
        try {
            val uri = Settings.System.DEFAULT_NOTIFICATION_URI
            val mediaPlayer = MediaPlayer.create(context, uri)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            DetectionLogger.log(DetectionLogger.Level.WARN, "声音反馈失败: ${e.message}")
        }
    }
}
