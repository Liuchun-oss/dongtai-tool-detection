package com.android.dongtai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * 蓝色区域检测器
 * 用于检测图片中的蓝色背景区域
 */
object BlueRegionDetector {
    
    /**
     * 检测图片中的蓝色区域
     * @param bitmap 输入图片
     * @param expandPixels 向外扩展的像素数（基于原图尺寸）
     * @param optimize 是否启用优化（缩放检测），默认true
     * @return 蓝色区域的边界框，如果没有检测到返回null（使用整张图片）
     */
    fun detectBlueRegion(bitmap: Bitmap, expandPixels: Int = 20, optimize: Boolean = true): Rect? {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        
        val detectionWidth: Int
        val detectionHeight: Int
        val scaledBitmap: Bitmap
        val scaleBackX: Float
        val scaleBackY: Float
        
        if (optimize) {
            // 性能优化：缩小图片后再检测，大幅减少处理像素数
            // 目标尺寸：最大800x800，保持宽高比
            val maxDetectionSize = 800
            val scale = if (originalWidth > originalHeight) {
                maxDetectionSize.toFloat() / originalWidth
            } else {
                maxDetectionSize.toFloat() / originalHeight
            }.coerceAtMost(1f) // 不放大，只缩小
            
            detectionWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
            detectionHeight = (originalHeight * scale).toInt().coerceAtLeast(1)
            
            // 缩小图片用于检测
            scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, detectionWidth, detectionHeight, true)
            } else {
                bitmap
            }
            
            scaleBackX = originalWidth.toFloat() / detectionWidth
            scaleBackY = originalHeight.toFloat() / detectionHeight
            
            DetectionLogger.log(
                DetectionLogger.Level.DEBUG,
                "蓝色区域检测（优化模式）：原图${originalWidth}x${originalHeight} -> 检测图${detectionWidth}x${detectionHeight} (缩放${String.format("%.2f", scale)})"
            )
        } else {
            // 不使用优化，直接使用原图
            detectionWidth = originalWidth
            detectionHeight = originalHeight
            scaledBitmap = bitmap
            scaleBackX = 1f
            scaleBackY = 1f
            
            DetectionLogger.log(
                DetectionLogger.Level.DEBUG,
                "蓝色区域检测（标准模式）：原图${originalWidth}x${originalHeight}"
            )
        }
        
        // 使用getPixels批量获取像素，比逐个getPixel快得多
        val pixels = IntArray(detectionWidth * detectionHeight)
        scaledBitmap.getPixels(pixels, 0, detectionWidth, 0, 0, detectionWidth, detectionHeight)
        
        // 创建蓝色掩码
        val blueMask = BooleanArray(detectionWidth * detectionHeight)
        var hasBlue = false
        
        // 扫描图片，标记蓝色像素（使用批量像素数据）
        for (i in pixels.indices) {
            if (isBluePixel(pixels[i])) {
                blueMask[i] = true
                hasBlue = true
            }
        }
        
        // 如果检测图是缩小的，需要释放
        if (scaledBitmap != bitmap && optimize) {
            scaledBitmap.recycle()
        }
        
        if (!hasBlue) {
            DetectionLogger.log(DetectionLogger.Level.WARN, "未检测到蓝色区域，使用整张图片")
            return null
        }
        
        // 查找蓝色区域的边界（在检测图上的坐标）
        var minX = detectionWidth
        var minY = detectionHeight
        var maxX = 0
        var maxY = 0
        
        for (y in 0 until detectionHeight) {
            for (x in 0 until detectionWidth) {
                if (blueMask[y * detectionWidth + x]) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }
        
        // 将检测图坐标转换回原图坐标
        var rectMinX = (minX * scaleBackX).toInt()
        var rectMinY = (minY * scaleBackY).toInt()
        var rectMaxX = ((maxX + 1) * scaleBackX).toInt().coerceAtMost(originalWidth - 1)
        var rectMaxY = ((maxY + 1) * scaleBackY).toInt().coerceAtMost(originalHeight - 1)
        
        // 向外扩展（基于原图尺寸）
        rectMinX = max(0, rectMinX - expandPixels)
        rectMinY = max(0, rectMinY - expandPixels)
        rectMaxX = min(originalWidth - 1, rectMaxX + expandPixels)
        rectMaxY = min(originalHeight - 1, rectMaxY + expandPixels)
        
        val rect = Rect(rectMinX, rectMinY, rectMaxX, rectMaxY)
        
        DetectionLogger.log(
            DetectionLogger.Level.INFO,
            "检测到蓝色区域: ($rectMinX, $rectMinY) - ($rectMaxX, $rectMaxY), 大小: ${rectMaxX - rectMinX}x${rectMaxY - rectMinY}"
        )
        
        return rect
    }
    
    /**
     * 判断像素是否为蓝色
     * 蓝色判定标准：B通道明显大于R和G通道
     */
    private fun isBluePixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        // 蓝色判定条件：
        // 1. 蓝色通道要足够强 (b > 100)
        // 2. 蓝色通道要明显大于红色和绿色 (b > r + 30 && b > g + 30)
        // 3. 或者蓝色占主导且其他颜色较弱 (b > 150 && b > r * 1.5 && b > g * 1.5)
        
        return (b > 100 && b > r + 30 && b > g + 30) ||
               (b > 150 && b > r * 1.5 && b > g * 1.5)
    }
    
    /**
     * 检查检测框是否在蓝色区域内
     * @param detectionX 检测框中心X（归一化坐标 0-1）
     * @param detectionY 检测框中心Y（归一化坐标 0-1）
     * @param imageWidth 图片宽度
     * @param imageHeight 图片高度
     * @param blueRegion 蓝色区域边界
     * @return 是否在区域内
     */
    fun isInBlueRegion(
        detectionX: Float,
        detectionY: Float,
        imageWidth: Int,
        imageHeight: Int,
        blueRegion: Rect?
    ): Boolean {
        // 如果没有检测到蓝色区域，接受所有检测
        if (blueRegion == null) return true
        
        // 转换为像素坐标
        val pixelX = (detectionX * imageWidth).toInt()
        val pixelY = (detectionY * imageHeight).toInt()
        
        // 检查是否在蓝色区域内
        return blueRegion.contains(pixelX, pixelY)
    }
    
    /**
     * 在图片上绘制蓝色区域边界（用于调试）
     */
    fun drawBlueRegionBorder(bitmap: Bitmap, blueRegion: Rect?): Bitmap {
        if (blueRegion == null) return bitmap
        
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        
        val paint = android.graphics.Paint().apply {
            color = Color.YELLOW
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        
        canvas.drawRect(blueRegion, paint)
        
        return mutableBitmap
    }
}
