package com.android.dongtai.utils

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream

/**
 * 检测结果可视化工具
 * 在图片上绘制检测框和标签
 */
object DetectionVisualizer {
    
    // 预定义的颜色列表（24种不同颜色）
    private val colors = listOf(
        Color.rgb(255, 0, 0),      // 红色
        Color.rgb(0, 255, 0),      // 绿色
        Color.rgb(0, 0, 255),      // 蓝色
        Color.rgb(255, 255, 0),    // 黄色
        Color.rgb(255, 0, 255),    // 品红
        Color.rgb(0, 255, 255),    // 青色
        Color.rgb(255, 128, 0),    // 橙色
        Color.rgb(128, 0, 255),    // 紫色
        Color.rgb(0, 255, 128),    // 青绿
        Color.rgb(255, 0, 128),    // 玫红
        Color.rgb(128, 255, 0),    // 黄绿
        Color.rgb(0, 128, 255),    // 天蓝
        Color.rgb(255, 128, 128),  // 浅红
        Color.rgb(128, 255, 128),  // 浅绿
        Color.rgb(128, 128, 255),  // 浅蓝
        Color.rgb(255, 255, 128),  // 浅黄
        Color.rgb(255, 128, 255),  // 浅品红
        Color.rgb(128, 255, 255),  // 浅青
        Color.rgb(192, 64, 0),     // 棕色
        Color.rgb(64, 192, 0),     // 草绿
        Color.rgb(0, 64, 192),     // 深蓝
        Color.rgb(192, 0, 64),     // 深红
        Color.rgb(64, 0, 192),     // 深紫
        Color.rgb(0, 192, 64)      // 深绿
    )
    
    /**
     * 检测框数据类
     */
    data class DetectionBox(
        val x: Float,           // 中心点x (0-1)
        val y: Float,           // 中心点y (0-1)
        val width: Float,       // 宽度 (0-1)
        val height: Float,      // 高度 (0-1)
        val confidence: Float,  // 置信度
        val classId: Int,       // 类别ID
        val className: String   // 类别名称
    )
    
    /**
     * 在图片上绘制检测框
     * @param imagePath 原始图片路径
     * @param detections 检测结果列表
     * @param outputPath 输出图片路径（如果为null，则覆盖原图）
     * @param blueRegion 蓝色区域边界（可选，用于绘制区域边界）
     * @param quality 图片质量（0-100）
     * @param compress 是否压缩图片
     * @return 绘制后的图片路径
     */
    fun drawDetections(
        imagePath: String,
        detections: List<DetectionBox>,
        outputPath: String? = null,
        blueRegion: android.graphics.Rect? = null,
        quality: Int = 95,
        compress: Boolean = false
    ): String {
        // 加载原始图片
        val originalBitmap = BitmapFactory.decodeFile(imagePath)
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val imageWidth = mutableBitmap.width.toFloat()
        val imageHeight = mutableBitmap.height.toFloat()
        
        // 创建画笔
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            textSize = 32f
            color = Color.WHITE
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val textBgPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // 不绘制蓝色区域边界（用户反馈：不需要显示大框）
        // if (blueRegion != null) {
        //     val regionPaint = Paint().apply {
        //         color = Color.YELLOW
        //         style = Paint.Style.STROKE
        //         strokeWidth = 6f
        //         isAntiAlias = true
        //     }
        //     canvas.drawRect(blueRegion, regionPaint)
        // }
        
        // 绘制每个检测框
        detections.forEach { detection ->
            // 获取颜色
            val color = colors[detection.classId % colors.size]
            boxPaint.color = color
            textBgPaint.color = color
            
            // 转换为像素坐标
            val centerX = detection.x * imageWidth
            val centerY = detection.y * imageHeight
            val boxWidth = detection.width * imageWidth
            val boxHeight = detection.height * imageHeight
            
            val left = centerX - boxWidth / 2
            val top = centerY - boxHeight / 2
            val right = centerX + boxWidth / 2
            val bottom = centerY + boxHeight / 2
            
            // 绘制边界框
            canvas.drawRect(left, top, right, bottom, boxPaint)
            
            // 准备标签文本
            val label = "${detection.className} ${String.format("%.2f", detection.confidence)}"
            
            // 测量文本大小
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            
            // 绘制文本背景
            val textBgLeft = left
            val textBgTop = top - textBounds.height() - 16f
            val textBgRight = left + textBounds.width() + 16f
            val textBgBottom = top
            
            canvas.drawRect(textBgLeft, textBgTop, textBgRight, textBgBottom, textBgPaint)
            
            // 绘制文本
            canvas.drawText(label, left + 8f, top - 8f, textPaint)
        }
        
        // 保存图片
        val finalOutputPath = outputPath ?: imagePath.replace(".jpg", "_annotated.jpg")
        
        // 如果需要压缩，先缩小图片
        val bitmapToSave = if (compress && mutableBitmap.width > 1920) {
            val scale = 1920f / mutableBitmap.width
            val newWidth = 1920
            val newHeight = (mutableBitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(mutableBitmap, newWidth, newHeight, true)
        } else {
            mutableBitmap
        }
        
        FileOutputStream(finalOutputPath).use { out ->
            bitmapToSave.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        
        if (bitmapToSave != mutableBitmap) {
            bitmapToSave.recycle()
        }
        
        mutableBitmap.recycle()
        originalBitmap.recycle()
        
        return finalOutputPath
    }
    
    /**
     * 创建带检测框的缩略图
     * @param imagePath 原始图片路径
     * @param detections 检测结果列表
     * @param maxSize 最大尺寸
     * @return 缩略图Bitmap
     */
    fun createThumbnailWithDetections(
        imagePath: String,
        detections: List<DetectionBox>,
        maxSize: Int = 800
    ): Bitmap {
        // 加载原始图片
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)
        
        // 计算缩放比例
        val scale = maxSize.toFloat() / maxOf(options.outWidth, options.outHeight)
        
        options.inJustDecodeBounds = false
        options.inSampleSize = (1 / scale).toInt().coerceAtLeast(1)
        
        val bitmap = BitmapFactory.decodeFile(imagePath, options)
        val scaledBitmap = if (scale < 1) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        val mutableBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val imageWidth = mutableBitmap.width.toFloat()
        val imageHeight = mutableBitmap.height.toFloat()
        
        // 创建画笔（较细的线条适合缩略图）
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            textSize = 20f
            color = Color.WHITE
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val textBgPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // 绘制每个检测框
        detections.forEach { detection ->
            val color = colors[detection.classId % colors.size]
            boxPaint.color = color
            textBgPaint.color = color
            
            val centerX = detection.x * imageWidth
            val centerY = detection.y * imageHeight
            val boxWidth = detection.width * imageWidth
            val boxHeight = detection.height * imageHeight
            
            val left = centerX - boxWidth / 2
            val top = centerY - boxHeight / 2
            val right = centerX + boxWidth / 2
            val bottom = centerY + boxHeight / 2
            
            canvas.drawRect(left, top, right, bottom, boxPaint)
            
            val label = detection.className
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            
            val textBgLeft = left
            val textBgTop = top - textBounds.height() - 8f
            val textBgRight = left + textBounds.width() + 8f
            val textBgBottom = top
            
            canvas.drawRect(textBgLeft, textBgTop, textBgRight, textBgBottom, textBgPaint)
            canvas.drawText(label, left + 4f, top - 4f, textPaint)
        }
        
        if (bitmap != scaledBitmap) {
            bitmap.recycle()
        }
        if (scaledBitmap != mutableBitmap) {
            scaledBitmap.recycle()
        }
        
        return mutableBitmap
    }
}
