package com.android.dongtai.service

import android.content.Context
import android.graphics.*
import android.graphics.BitmapFactory
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.android.dongtai.data.entity.WorkRecord
import com.android.dongtai.utils.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 分享图片生成器
 * 将作业前后对比图片和对比结果合成为一张图片
 */
object ShareImageGenerator {
    
    // 图片配置
    private const val OUTPUT_WIDTH = 1080 // 输出图片宽度
    private const val PADDING = 40 // 边距
    private const val IMAGE_SPACING = 30 // 图片间距
    private const val TEXT_SPACING = 30 // 文字间距（增加）
    private const val LINE_HEIGHT = 55 // 每行文本高度
    private const val TOOL_LINE_HEIGHT = 50 // 工具列表每行高度
    
    /**
     * 生成分享图片
     * @param context 上下文
     * @param workRecord 工作记录
     * @param beforeCount 作业前工具总数
     * @param afterCount 作业后工具总数
     * @param missingTools 缺失工具列表
     * @return 生成的图片文件路径
     */
    suspend fun generateShareImage(
        context: Context,
        workRecord: WorkRecord,
        beforeCount: Int,
        afterCount: Int,
        missingTools: List<String>
    ): String = withContext(Dispatchers.IO) {
        // 加载作业前后图片（使用采样优化，减少内存占用）
        val beforeImagePath = workRecord.beforeRecord.imagePath
        val afterImagePath = workRecord.afterRecord?.imagePath
        
        // 使用采样加载图片，减少内存占用
        val beforeBitmap = loadBitmapWithSampling(beforeImagePath)
        val afterBitmap = afterImagePath?.let { loadBitmapWithSampling(it) }
        
        // 计算缩放后的图片尺寸
        val imageWidth = (OUTPUT_WIDTH - PADDING * 2 - IMAGE_SPACING) / 2
        val beforeScaled = scaleBitmap(beforeBitmap, imageWidth)
        val afterScaled = afterBitmap?.let { scaleBitmap(it, imageWidth) }
        
        // 计算总高度
        val imageHeight = beforeScaled.height
        val comparisonMode = SettingsManager.getComparisonMode(context)
        val textAreaHeight = calculateTextAreaHeight(
            workRecord, missingTools, comparisonMode
        )
        // 标题区域高度
        val titleHeight = 120
        // 总高度 = 上边距 + 标题 + 标签区域 + 图片 + 文本区域
        val labelHeight = 60 // "作业前" "作业后" 标签
        val totalHeight = PADDING + titleHeight + labelHeight + imageHeight + textAreaHeight
        
        // 创建画布
        val outputBitmap = Bitmap.createBitmap(OUTPUT_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        
        // 绘制白色背景
        canvas.drawColor(Color.WHITE)
        
        // 绘制标题
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#1976D2")
            textSize = 60f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("工具清点对比报告", PADDING.toFloat(), PADDING.toFloat() + 70f, titlePaint)
        
        // 绘制作业前图片
        var currentY = PADDING + 120
        val labelPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        canvas.drawText("作业前", PADDING.toFloat(), currentY.toFloat() + 40f, labelPaint)
        currentY += 60
        canvas.drawBitmap(beforeScaled, PADDING.toFloat(), currentY.toFloat(), null)
        
        // 绘制作业后图片
        if (afterScaled != null) {
            val afterX = PADDING + imageWidth + IMAGE_SPACING
            canvas.drawText("作业后", afterX.toFloat(), (currentY - 60).toFloat() + 40f, labelPaint)
            canvas.drawBitmap(afterScaled, afterX.toFloat(), currentY.toFloat(), null)
        }
        
        currentY += imageHeight + TEXT_SPACING
        
        // 绘制分隔线
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 2f
        }
        canvas.drawLine(
            PADDING.toFloat(),
            currentY.toFloat(),
            (OUTPUT_WIDTH - PADDING).toFloat(),
            currentY.toFloat(),
            linePaint
        )
        
        currentY += PADDING
        
        // 绘制对比结果文本
        currentY = drawComparisonText(
            canvas, currentY, workRecord, beforeCount, afterCount, missingTools, comparisonMode, context
        )
        
        // 保存图片
        val outputFile = File(
            context.getExternalFilesDir(null),
            "share_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )
        
        FileOutputStream(outputFile).use { out ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // 回收所有bitmap
        beforeBitmap.recycle()
        afterBitmap?.recycle()
        beforeScaled.recycle()
        afterScaled?.recycle()
        outputBitmap.recycle()
        
        outputFile.absolutePath
    }
    
    /**
     * 使用采样加载图片，减少内存占用
     */
    private fun loadBitmapWithSampling(imagePath: String): Bitmap {
        // 先读取图片尺寸
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)
        
        // 计算采样率（目标最大2048x2048）
        val maxSize = 2048
        val sampleSize = if (options.outWidth > maxSize || options.outHeight > maxSize) {
            val widthRatio = options.outWidth / maxSize
            val heightRatio = options.outHeight / maxSize
            maxOf(widthRatio, heightRatio)
        } else {
            1
        }
        
        // 使用采样加载图片
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        return BitmapFactory.decodeFile(imagePath, options)
            ?: throw IllegalStateException("无法加载图片: $imagePath")
    }
    
    /**
     * 缩放图片
     */
    private fun scaleBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val scale = targetWidth.toFloat() / bitmap.width
        val targetHeight = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        // 如果创建了新bitmap，回收原bitmap
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
    
    /**
     * 计算文本区域高度
     */
    private fun calculateTextAreaHeight(
        workRecord: WorkRecord,
        missingTools: List<String>,
        comparisonMode: String
    ): Int {
        var height = 0
        
        // 作业信息区域
        if (workRecord.beforeRecord.workGroup.isNotBlank()) height += LINE_HEIGHT
        if (workRecord.beforeRecord.workLocation.isNotBlank()) height += LINE_HEIGHT
        if (workRecord.beforeRecord.workGroup.isNotBlank() || 
            workRecord.beforeRecord.workLocation.isNotBlank()) {
            height += TEXT_SPACING
        }
        
        // 时间信息（并排显示，只需要一行）
        height += LINE_HEIGHT + TEXT_SPACING
        
        if (workRecord.afterRecord != null) {
            if (comparisonMode == "specific_tools") {
                // 具体工具数量对比模式：需要显示工具列表（并排显示）
                val beforeToolsList = parseToolsJson(workRecord.beforeRecord.toolsJson)
                val afterToolsList = parseToolsJson(workRecord.afterRecord.toolsJson)
                // 标题行
                height += LINE_HEIGHT // "作业前工具：" 和 "作业后工具："
                // 工具列表（取最大行数，因为并排显示）
                height += maxOf(beforeToolsList.size, afterToolsList.size) * TOOL_LINE_HEIGHT
                height += TEXT_SPACING
            } else {
                // 总数量对比模式：只显示总数
                height += LINE_HEIGHT + TEXT_SPACING
            }
            
            // 对比结果区域
            height += 120 + TEXT_SPACING
        } else {
            // 如果没有作业后记录
            if (comparisonMode == "specific_tools") {
                // 显示作业前工具列表
                val beforeToolsList = parseToolsJson(workRecord.beforeRecord.toolsJson)
                height += LINE_HEIGHT // "作业前工具："
                height += beforeToolsList.size * TOOL_LINE_HEIGHT
            } else {
                height += LINE_HEIGHT
            }
            height += TEXT_SPACING
        }
        
        // 额外的底部边距
        height += PADDING * 2
        
        return height
    }
    
    /**
     * 解析工具JSON为列表
     */
    private fun parseToolsJson(toolsJson: String?): List<String> {
        if (toolsJson.isNullOrBlank()) return emptyList()
        return try {
            val gson = Gson()
            val jsonArray = gson.fromJson(toolsJson, Array<String>::class.java)
            jsonArray?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 绘制对比文本
     */
    private fun drawComparisonText(
        canvas: Canvas,
        startY: Int,
        workRecord: WorkRecord,
        beforeCount: Int,
        afterCount: Int,
        missingTools: List<String>,
        comparisonMode: String,
        context: Context
    ): Int {
        var currentY = startY
        
        val normalPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 34f
            isAntiAlias = true
        }
        
        val boldPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val smallPaint = TextPaint().apply {
            color = Color.GRAY
            textSize = 32f
            isAntiAlias = true
        }
        
        // 作业信息
        if (workRecord.beforeRecord.workGroup.isNotBlank()) {
            canvas.drawText(
                "作业组：${workRecord.beforeRecord.workGroup}",
                PADDING.toFloat(),
                currentY.toFloat() + 40f,
                boldPaint
            )
            currentY += LINE_HEIGHT
        }
        
        if (workRecord.beforeRecord.workLocation.isNotBlank()) {
            canvas.drawText(
                "作业地点：${workRecord.beforeRecord.workLocation}",
                PADDING.toFloat(),
                currentY.toFloat() + 40f,
                boldPaint
            )
            currentY += LINE_HEIGHT
        }
        
        if (workRecord.beforeRecord.workGroup.isNotBlank() || 
            workRecord.beforeRecord.workLocation.isNotBlank()) {
            currentY += TEXT_SPACING
        }
        
        // 时间信息（并排显示）
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val halfWidth = (OUTPUT_WIDTH - PADDING * 2) / 2
        
        canvas.drawText(
            "作业前：${dateFormat.format(Date(workRecord.beforeRecord.timestamp))}",
            PADDING.toFloat(),
            currentY.toFloat() + 38f,
            normalPaint
        )
        
        if (workRecord.afterRecord != null) {
            canvas.drawText(
                "作业后：${dateFormat.format(Date(workRecord.afterRecord.timestamp))}",
                (PADDING + halfWidth).toFloat(),
                currentY.toFloat() + 38f,
                normalPaint
            )
        }
        
        currentY += LINE_HEIGHT + TEXT_SPACING
        
        if (workRecord.afterRecord != null) {
            if (comparisonMode == "specific_tools") {
                // 具体工具数量对比模式：显示具体工具列表（并排显示）
                val beforeToolsList = parseToolsJson(workRecord.beforeRecord.toolsJson)
                val afterToolsList = parseToolsJson(workRecord.afterRecord.toolsJson)
                
                // 计算每列的宽度（减去间距）
                val columnWidth = (OUTPUT_WIDTH - PADDING * 2 - 16) / 2
                val leftColumnX = PADDING.toFloat()
                val rightColumnX = (PADDING + columnWidth + 16).toFloat()
                
                // 作业前工具列表标题
                canvas.drawText(
                    "作业前工具：",
                    leftColumnX,
                    currentY.toFloat() + 40f,
                    boldPaint
                )
                
                // 作业后工具列表标题
                canvas.drawText(
                    "作业后工具：",
                    rightColumnX,
                    currentY.toFloat() + 40f,
                    boldPaint
                )
                currentY += LINE_HEIGHT
                
                // 计算最大行数（用于对齐）
                val maxRows = maxOf(beforeToolsList.size, afterToolsList.size)
                
                // 并排绘制工具列表
                repeat(maxRows) { index ->
                    var lineY = currentY.toFloat() + 40f
                    
                    // 左侧：作业前工具
                    if (index < beforeToolsList.size) {
                        val tool = beforeToolsList[index]
                        // 解析工具名称和数量（格式：扳手*1）
                        val parts = tool.split("*")
                        val displayText = if (parts.size == 2) {
                            val toolName = parts[0]
                            val count = parts[1].trim().split(" ")[0] // 移除状态信息（如果有）
                            "  • $toolName × $count"
                        } else {
                            "  • $tool"
                        }
                        canvas.drawText(
                            displayText,
                            leftColumnX,
                            lineY,
                            normalPaint
                        )
                    }
                    
                    // 右侧：作业后工具
                    if (index < afterToolsList.size) {
                        val tool = afterToolsList[index]
                        // 解析工具名称、数量和状态（格式：扳手*1 已存在）
                        val parts = tool.split("*")
                        val displayText = if (parts.size == 2) {
                            val toolName = parts[0]
                            val countAndStatus = parts[1].trim()
                            val countPart = countAndStatus.split(" ")[0]
                            val statusPart = if (countAndStatus.contains(" ")) {
                                " ${countAndStatus.substringAfter(" ")}"
                            } else {
                                ""
                            }
                            "  • $toolName × $countPart$statusPart"
                        } else {
                            "  • $tool"
                        }
                        
                        // 检查是否包含状态信息
                        val hasStatus = tool.contains("已存在") || tool.contains("缺失") || tool.contains("未找到")
                        val toolPaint = if (hasStatus) {
                            if (tool.contains("已存在")) {
                                TextPaint().apply {
                                    color = Color.parseColor("#4CAF50")
                                    textSize = 34f
                                    isAntiAlias = true
                                }
                            } else {
                                TextPaint().apply {
                                    color = Color.parseColor("#F44336")
                                    textSize = 34f
                                    isAntiAlias = true
                                }
                            }
                        } else {
                            normalPaint
                        }
                        canvas.drawText(
                            displayText,
                            rightColumnX,
                            lineY,
                            toolPaint
                        )
                    }
                    
                    currentY += TOOL_LINE_HEIGHT
                }
                
                currentY += TEXT_SPACING
            } else {
                // 总数量对比模式：显示总数（并排显示）
                canvas.drawText(
                    "作业前：识别到共 $beforeCount 件工具",
                    PADDING.toFloat(),
                    currentY.toFloat() + 40f,
                    boldPaint
                )
                
                canvas.drawText(
                    "作业后：识别到共 $afterCount 件工具",
                    (PADDING + halfWidth).toFloat(),
                    currentY.toFloat() + 40f,
                    boldPaint
                )
                
                currentY += LINE_HEIGHT + TEXT_SPACING
            }
            
            // 对比结果
            val resultPaint = TextPaint().apply {
                color = if (missingTools.isEmpty()) {
                    Color.parseColor("#4CAF50")
                } else {
                    Color.parseColor("#F44336")
                }
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            
            // 从缺失工具列表中提取数量（格式：发现缺失X个工具）
            val missingCount = missingTools.firstOrNull()?.let { tool ->
                val match = Regex("发现缺失(\\d+)个工具").find(tool)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } ?: 0
            
            val resultText = if (missingTools.isEmpty() || missingCount == 0) {
                "✓ 工具清点完整，无缺失工具"
            } else {
                "⚠ 发现缺失 $missingCount 个工具"
            }
            
            // 绘制背景框
            val bgPaint = Paint().apply {
                color = if (missingTools.isEmpty()) {
                    Color.parseColor("#E8F5E9")
                } else {
                    Color.parseColor("#FFEBEE")
                }
                style = Paint.Style.FILL
            }
            
            val bgRect = RectF(
                PADDING.toFloat(),
                currentY.toFloat(),
                (OUTPUT_WIDTH - PADDING).toFloat(),
                currentY.toFloat() + 110f
            )
            canvas.drawRoundRect(bgRect, 10f, 10f, bgPaint)
            
            // 绘制结果文本
            canvas.drawText(
                resultText,
                PADDING.toFloat() + 20f,
                currentY.toFloat() + 66f,
                resultPaint
            )
            currentY += 120
        } else {
            // 只有作业前记录
            if (comparisonMode == "specific_tools") {
                // 具体工具数量对比模式：显示作业前工具列表
                val beforeToolsList = parseToolsJson(workRecord.beforeRecord.toolsJson)
                canvas.drawText(
                    "作业前工具：",
                    PADDING.toFloat(),
                    currentY.toFloat() + 40f,
                    boldPaint
                )
                currentY += LINE_HEIGHT
                
                beforeToolsList.forEach { tool ->
                    // 解析工具名称和数量（格式：扳手*1）
                    val parts = tool.split("*")
                    val displayText = if (parts.size == 2) {
                        val toolName = parts[0]
                        val count = parts[1].trim().split(" ")[0] // 移除状态信息（如果有）
                        "  • $toolName × $count"
                    } else {
                        "  • $tool"
                    }
                    canvas.drawText(
                        displayText,
                        PADDING.toFloat(),
                        currentY.toFloat() + 40f,
                        normalPaint
                    )
                    currentY += TOOL_LINE_HEIGHT
                }
            } else {
                // 总数量对比模式：显示总数
                canvas.drawText(
                    "作业前：识别到共 $beforeCount 件工具",
                    PADDING.toFloat(),
                    currentY.toFloat() + 40f,
                    boldPaint
                )
                currentY += LINE_HEIGHT
            }
        }
        
        return currentY
    }
}
