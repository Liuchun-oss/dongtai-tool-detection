package com.android.dongtai.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置管理器
 * 使用 SharedPreferences 保存和读取设置
 */
object SettingsManager {
    private const val PREFS_NAME = "dongtai_settings"
    
    // 设置键名
    private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
    private const val KEY_IOU_THRESHOLD = "iou_threshold"
    private const val KEY_BLUE_REGION_EXPAND = "blue_region_expand"
    private const val KEY_BLUE_REGION_OPTIMIZE = "blue_region_optimize"
    private const val KEY_MAX_BOX_AREA_RATIO = "max_box_area_ratio"
    private const val KEY_EDGE_TOLERANCE = "edge_tolerance"
    private const val KEY_AUTO_RECOGNITION = "auto_recognition"
    private const val KEY_SHOW_DETECTION_BOX = "show_detection_box"
    private const val KEY_IMAGE_QUALITY = "image_quality"
    private const val KEY_AUTO_CLEAN_LOGS = "auto_clean_logs"
    private const val KEY_LOG_RETENTION_DAYS = "log_retention_days"
    private const val KEY_AUTO_CLEAN_IMAGES = "auto_clean_images"
    private const val KEY_IMAGE_RETENTION_DAYS = "image_retention_days"
    private const val KEY_VIBRATION_FEEDBACK = "vibration_feedback"
    private const val KEY_SOUND_FEEDBACK = "sound_feedback"
    private const val KEY_SHOW_TOOL_COUNT = "show_tool_count"
    private const val KEY_ENABLE_NNAPI = "enable_nnapi"
    private const val KEY_DETECTION_TIMEOUT = "detection_timeout"
    private const val KEY_SAVE_ANNOTATED_IMAGE = "save_annotated_image"
    private const val KEY_COMPRESS_IMAGES = "compress_images"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SHOW_TUTORIAL = "show_tutorial"
    private const val KEY_ENABLE_STATISTICS = "enable_statistics"
    private const val KEY_COMPARISON_MODE = "comparison_mode" // "total_count" 或 "specific_tools"
    private const val KEY_ALTERNATIVE_MATCH_THRESHOLD = "alternative_match_threshold" // 备选匹配阈值
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // 检测相关设置
    fun getConfidenceThreshold(context: Context): Float {
        return getPrefs(context).getFloat(KEY_CONFIDENCE_THRESHOLD, 0.35f)
    }
    
    fun setConfidenceThreshold(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_CONFIDENCE_THRESHOLD, value).apply()
    }
    
    fun getIouThreshold(context: Context): Float {
        return getPrefs(context).getFloat(KEY_IOU_THRESHOLD, 0.50f)
    }
    
    fun setIouThreshold(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_IOU_THRESHOLD, value).apply()
    }
    
    fun getBlueRegionExpand(context: Context): Int {
        return getPrefs(context).getInt(KEY_BLUE_REGION_EXPAND, 30)
    }
    
    fun setBlueRegionExpand(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_BLUE_REGION_EXPAND, value).apply()
    }
    
    fun getBlueRegionOptimize(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BLUE_REGION_OPTIMIZE, true)
    }
    
    fun setBlueRegionOptimize(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BLUE_REGION_OPTIMIZE, value).apply()
    }
    
    fun getMaxBoxAreaRatio(context: Context): Float {
        return getPrefs(context).getFloat(KEY_MAX_BOX_AREA_RATIO, 0.35f)
    }
    
    fun setMaxBoxAreaRatio(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_MAX_BOX_AREA_RATIO, value).apply()
    }
    
    fun getEdgeTolerance(context: Context): Int {
        return getPrefs(context).getInt(KEY_EDGE_TOLERANCE, 10)
    }
    
    fun setEdgeTolerance(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_EDGE_TOLERANCE, value).apply()
    }
    
    // 功能开关
    fun getAutoRecognition(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_RECOGNITION, true)
    }
    
    fun setAutoRecognition(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_RECOGNITION, value).apply()
    }
    
    fun getShowDetectionBox(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_DETECTION_BOX, true)
    }
    
    fun setShowDetectionBox(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_DETECTION_BOX, value).apply()
    }
    
    fun getSaveAnnotatedImage(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SAVE_ANNOTATED_IMAGE, true)
    }
    
    fun setSaveAnnotatedImage(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SAVE_ANNOTATED_IMAGE, value).apply()
    }
    
    fun getCompressImages(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_COMPRESS_IMAGES, false)
    }
    
    fun setCompressImages(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COMPRESS_IMAGES, value).apply()
    }
    
    fun getEnableNnapi(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLE_NNAPI, true)
    }
    
    fun setEnableNnapi(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_NNAPI, value).apply()
    }
    
    fun getShowToolCount(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_TOOL_COUNT, true)
    }
    
    fun setShowToolCount(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_TOOL_COUNT, value).apply()
    }
    
    // 图片设置
    fun getImageQuality(context: Context): Int {
        return getPrefs(context).getInt(KEY_IMAGE_QUALITY, 85)
    }
    
    fun setImageQuality(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_IMAGE_QUALITY, value).apply()
    }
    
    // 日志设置
    fun getAutoCleanLogs(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CLEAN_LOGS, true)
    }
    
    fun setAutoCleanLogs(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CLEAN_LOGS, value).apply()
    }
    
    fun getLogRetentionDays(context: Context): Int {
        return getPrefs(context).getInt(KEY_LOG_RETENTION_DAYS, 7)
    }
    
    fun setLogRetentionDays(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_LOG_RETENTION_DAYS, value).apply()
    }
    
    // 图片清理设置
    fun getAutoCleanImages(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CLEAN_IMAGES, false)
    }
    
    fun setAutoCleanImages(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CLEAN_IMAGES, value).apply()
    }
    
    fun getImageRetentionDays(context: Context): Int {
        return getPrefs(context).getInt(KEY_IMAGE_RETENTION_DAYS, 30)
    }
    
    fun setImageRetentionDays(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_IMAGE_RETENTION_DAYS, value).apply()
    }
    
    // 反馈设置
    fun getVibrationFeedback(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATION_FEEDBACK, true)
    }
    
    fun setVibrationFeedback(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATION_FEEDBACK, value).apply()
    }
    
    fun getSoundFeedback(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_FEEDBACK, false)
    }
    
    fun setSoundFeedback(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_FEEDBACK, value).apply()
    }
    
    // 检测超时设置
    fun getDetectionTimeout(context: Context): Int {
        return getPrefs(context).getInt(KEY_DETECTION_TIMEOUT, 30)
    }
    
    fun setDetectionTimeout(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_DETECTION_TIMEOUT, value).apply()
    }
    
    // 界面设置
    fun getThemeMode(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_MODE, "system") ?: "system"
    }
    
    fun setThemeMode(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, value).apply()
    }
    
    fun getFontSize(context: Context): String {
        return getPrefs(context).getString(KEY_FONT_SIZE, "normal") ?: "normal"
    }
    
    fun setFontSize(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_FONT_SIZE, value).apply()
    }
    
    fun getLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "zh") ?: "zh"
    }
    
    fun setLanguage(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, value).apply()
    }
    
    // 其他设置
    fun getShowTutorial(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_TUTORIAL, true)
    }
    
    fun setShowTutorial(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_TUTORIAL, value).apply()
    }
    
    fun getEnableStatistics(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLE_STATISTICS, true)
    }
    
    fun setEnableStatistics(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_STATISTICS, value).apply()
    }
    
    // 对比方式设置
    fun getComparisonMode(context: Context): String {
        return getPrefs(context).getString(KEY_COMPARISON_MODE, "specific_tools") ?: "specific_tools"
    }
    
    fun setComparisonMode(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_COMPARISON_MODE, value).apply()
    }
    
    // 备选匹配阈值设置（用于作业后检测时，在Top-3类别中查找匹配）
    fun getAlternativeMatchThreshold(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ALTERNATIVE_MATCH_THRESHOLD, 0.1f)
    }
    
    fun setAlternativeMatchThreshold(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_ALTERNATIVE_MATCH_THRESHOLD, value).apply()
    }
    
    // 重置所有设置
    fun resetAllSettings(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
