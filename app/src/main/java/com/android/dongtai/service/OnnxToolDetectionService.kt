package com.android.dongtai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.*
import com.android.dongtai.utils.BlueRegionDetector
import com.android.dongtai.utils.DetectionLogger
import com.android.dongtai.utils.DetectionVisualizer
import com.android.dongtai.utils.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * ONNX Tool Detection Service
 * 使用YOLOv8 ONNX模型进行工具检测
 */
class OnnxToolDetectionService(private val context: Context) {
    
    private var ortSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null
    
    // YOLOv8 输入尺寸
    private val inputWidth = 640
    private val inputHeight = 640
    
    // Letterbox预处理参数（用于坐标转换）
    private var letterboxScale: Float = 1.0f
    private var letterboxPadX: Float = 0f
    private var letterboxPadY: Float = 0f
    
    // 检测阈值（从设置中读取）
    private fun getConfidenceThreshold(overrideThreshold: Float? = null): Float = 
        overrideThreshold ?: SettingsManager.getConfidenceThreshold(context)
    private fun getIouThreshold(overrideThreshold: Float? = null): Float = 
        overrideThreshold ?: SettingsManager.getIouThreshold(context)
    
    // 蓝色区域扩展像素数（从设置中读取）
    private fun getBlueRegionExpandPixels(): Int = SettingsManager.getBlueRegionExpand(context)
    
    // 最大检测框面积比例（从设置中读取）
    private fun getMaxBoxAreaRatio(): Float = SettingsManager.getMaxBoxAreaRatio(context)
    
    // 边缘容差（从设置中读取）
    private fun getEdgeTolerance(): Int = SettingsManager.getEdgeTolerance(context)
    
    // 类别名称列表（从classes.txt文件读取，直接使用类别名）
    private var classLabels: List<String> = emptyList()
    
    /**
     * 从assets读取类别文件
     */
    private fun loadClassLabels(): List<String> {
        return try {
            val classes = mutableListOf<String>()
            context.assets.open("classes.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        classes.add(trimmed)
                    }
                }
            }
            DetectionLogger.log(DetectionLogger.Level.INFO, "成功加载 ${classes.size} 个类别: ${classes.joinToString(", ")}")
            classes
        } catch (e: Exception) {
            DetectionLogger.log(DetectionLogger.Level.ERROR, "读取类别文件失败: ${e.message}")
            // 如果读取失败，返回空列表（会导致后续错误，但至少不会崩溃）
            emptyList()
        }
    }
    
    /**
     * 根据类别ID获取类别名称
     */
    private fun getClassName(classId: Int): String {
        return if (classId >= 0 && classId < classLabels.size) {
            classLabels[classId]
        } else {
            "Unknown"
        }
    }
    
    /**
     * 将检测框坐标从letterbox空间（640x640）转换到原图空间
     * @param detectionX 检测框中心X坐标（在640x640空间中）
     * @param detectionY 检测框中心Y坐标（在640x640空间中）
     * @param detectionWidth 检测框宽度（在640x640空间中）
     * @param detectionHeight 检测框高度（在640x640空间中）
     * @param originalWidth 原图宽度
     * @param originalHeight 原图高度
     * @return Pair<原图中心X, 原图中心Y>, Pair<原图宽度, 原图高度>
     */
    private fun convertLetterboxToOriginal(
        detectionX: Float,
        detectionY: Float,
        detectionWidth: Float,
        detectionHeight: Float,
        originalWidth: Int,
        originalHeight: Int
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        // 步骤1：从letterbox空间转换到缩放后的图片空间（减去padding）
        val scaledX = detectionX - letterboxPadX
        val scaledY = detectionY - letterboxPadY
        
        // 步骤2：从缩放后的图片空间转换到原图空间（除以scale）
        val originalX = scaledX / letterboxScale
        val originalY = scaledY / letterboxScale
        val originalWidth_scaled = detectionWidth / letterboxScale
        val originalHeight_scaled = detectionHeight / letterboxScale
        
        return Pair(Pair(originalX, originalY), Pair(originalWidth_scaled, originalHeight_scaled))
    }
    
    /**
     * 初始化ONNX模型
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        DetectionLogger.log(DetectionLogger.Level.INFO, "开始初始化ONNX模型...")
        
        try {
            // 首先加载类别文件
            DetectionLogger.log(DetectionLogger.Level.INFO, "加载类别文件...")
            classLabels = loadClassLabels()
            if (classLabels.isEmpty()) {
                throw RuntimeException("类别文件 classes.txt 为空或读取失败")
            }
            DetectionLogger.log(DetectionLogger.Level.INFO, "已加载 ${classLabels.size} 个类别")
            
            // 检查模型文件是否存在
            DetectionLogger.log(DetectionLogger.Level.INFO, "检查模型文件...")
            val assetFiles = context.assets.list("") ?: emptyArray()
            DetectionLogger.log(DetectionLogger.Level.INFO, "Assets文件列表: ${assetFiles.joinToString()}")
            
            if (!assetFiles.contains("best.onnx")) {
                throw RuntimeException("模型文件 best.onnx 不存在于assets文件夹中")
            }
            
            // 创建ONNX Runtime环境
            DetectionLogger.log(DetectionLogger.Level.INFO, "创建ONNX Runtime环境...")
            ortEnvironment = OrtEnvironment.getEnvironment()
            DetectionLogger.log(DetectionLogger.Level.INFO, "ONNX Runtime环境已创建")
            
            // 从assets加载模型
            DetectionLogger.log(DetectionLogger.Level.INFO, "从assets加载模型文件...")
            val modelBytes = context.assets.open("best.onnx").use { it.readBytes() }
            DetectionLogger.log(DetectionLogger.Level.INFO, "模型文件大小: ${modelBytes.size / 1024 / 1024} MB")
            
            // 创建会话选项
            DetectionLogger.log(DetectionLogger.Level.INFO, "配置会话选项...")
            val sessionOptions = OrtSession.SessionOptions().apply {
                // 使用所有可用的优化
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // 根据设置决定是否启用NNAPI加速
                if (SettingsManager.getEnableNnapi(context)) {
                    try {
                        // 使用NNAPI加速（Android神经网络API）
                        addNnapi()
                        DetectionLogger.log(DetectionLogger.Level.INFO, "NNAPI加速已启用")
                    } catch (e: Exception) {
                        DetectionLogger.log(DetectionLogger.Level.WARN, "NNAPI加速不可用: ${e.message}")
                    }
                } else {
                    DetectionLogger.log(DetectionLogger.Level.INFO, "NNAPI加速已禁用（设置中关闭）")
                }
            }
            
            // 创建推理会话
            DetectionLogger.log(DetectionLogger.Level.INFO, "创建推理会话...")
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
            
            val duration = System.currentTimeMillis() - startTime
            DetectionLogger.logModelInit(true, duration)
            DetectionLogger.log(DetectionLogger.Level.INFO, "模型输入名称: ${ortSession?.inputNames?.joinToString()}")
            DetectionLogger.log(DetectionLogger.Level.INFO, "模型输出名称: ${ortSession?.outputNames?.joinToString()}")
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            DetectionLogger.logModelInit(false, duration, e.message)
            DetectionLogger.log(DetectionLogger.Level.ERROR, "模型初始化失败", e)
            throw RuntimeException("Failed to initialize ONNX model: ${e.message}", e)
        }
    }
    
    /**
     * 检测工具
     * @param imagePath 图片路径
     * @param beforeToolsJson 作业前的工具列表JSON（可选）
     * @param overrideConfidenceThreshold 临时覆盖置信度阈值（可选，用于重新识别时随机调整）
     * @param overrideIouThreshold 临时覆盖IOU阈值（可选，用于重新识别时随机调整）
     * @return 工具列表的JSON字符串（格式：工具名*数量）
     */
    suspend fun detectTools(
        imagePath: String, 
        beforeToolsJson: String? = null,
        overrideConfidenceThreshold: Float? = null,
        overrideIouThreshold: Float? = null
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val detectionTimeout = SettingsManager.getDetectionTimeout(context) * 1000L // 转换为毫秒
        
        DetectionLogger.log(DetectionLogger.Level.INFO, "========== 开始检测 ==========")
        DetectionLogger.log(DetectionLogger.Level.INFO, "检测时间戳: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(startTime))}")
        DetectionLogger.log(DetectionLogger.Level.INFO, "图片路径: $imagePath")
        DetectionLogger.log(DetectionLogger.Level.INFO, "检测超时设置: ${detectionTimeout / 1000}秒")
        DetectionLogger.log(DetectionLogger.Level.INFO, "检测ID: ${System.nanoTime()}") // 每次检测的唯一ID
        
        // 使用 withTimeout 实现超时控制
        try {
            kotlinx.coroutines.withTimeout(detectionTimeout) {
                try {
                    // 检查图片文件是否存在
                    val imageFile = File(imagePath)
                    if (!imageFile.exists()) {
                        DetectionLogger.log(DetectionLogger.Level.ERROR, "图片文件不存在: $imagePath")
                        val gson = Gson()
                        return@withTimeout gson.toJson(emptyList<String>())
                    }
                    DetectionLogger.log(DetectionLogger.Level.INFO, "图片文件大小: ${imageFile.length() / 1024} KB")
                    
                    // 确保模型已初始化
                    if (ortSession == null) {
                        DetectionLogger.log(DetectionLogger.Level.INFO, "模型未初始化，开始初始化...")
                        initialize()
                    } else {
                        DetectionLogger.log(DetectionLogger.Level.INFO, "模型已初始化，直接使用")
                    }
                    
                    // 加载并预处理图片
                    DetectionLogger.log(DetectionLogger.Level.INFO, "开始加载和预处理图片...")
                    DetectionLogger.log(DetectionLogger.Level.INFO, "强制重新加载图片（无缓存）")
                    var bitmap: Bitmap? = null
                    try {
                        bitmap = loadAndRotateImage(imagePath)
                        DetectionLogger.logPreprocess(imagePath, bitmap.width, bitmap.height, true)
                        DetectionLogger.log(DetectionLogger.Level.INFO, "图片加载完成，尺寸: ${bitmap.width}x${bitmap.height}")
                        
                        val preprocessedImage = preprocessImage(bitmap)
                        DetectionLogger.log(DetectionLogger.Level.INFO, "图片预处理完成，buffer大小: ${preprocessedImage.capacity()}")
                        
                        // 记录使用的阈值（如果有临时覆盖）
                        if (overrideConfidenceThreshold != null || overrideIouThreshold != null) {
                            DetectionLogger.log(
                                DetectionLogger.Level.INFO,
                                "使用临时阈值: 置信度=${overrideConfidenceThreshold ?: getConfidenceThreshold()}, IOU=${overrideIouThreshold ?: getIouThreshold()}"
                            )
                        }
                        
                        // 运行推理
                        DetectionLogger.log(DetectionLogger.Level.INFO, "开始运行推理...")
                        val inferenceStart = System.currentTimeMillis()
                        val detections = runInference(preprocessedImage, overrideConfidenceThreshold)
                        val inferenceDuration = System.currentTimeMillis() - inferenceStart
                        DetectionLogger.logInference(inferenceDuration, detections.size, true)
                        DetectionLogger.log(DetectionLogger.Level.INFO, "推理完成，检测到 ${detections.size} 个候选框")
                        
                        // 检测蓝色区域
                        DetectionLogger.log(DetectionLogger.Level.INFO, "开始检测蓝色区域...")
                        val blueRegionExpandPixels = getBlueRegionExpandPixels()
                        val blueRegionOptimize = SettingsManager.getBlueRegionOptimize(context)
                        val blueRegion = BlueRegionDetector.detectBlueRegion(bitmap, blueRegionExpandPixels, blueRegionOptimize)
                        
                        // 后处理：NMS（非极大值抑制）
                    DetectionLogger.log(DetectionLogger.Level.INFO, "开始NMS后处理...")
                    val filteredDetections = applyNMS(detections, overrideIouThreshold)
                    DetectionLogger.logNMS(detections.size, filteredDetections.size)
                    
                    // 记录每个检测框的Top-3类别置信度（用于排查识别错误）
                    if (filteredDetections.isNotEmpty()) {
                        DetectionLogger.log(DetectionLogger.Level.INFO, "记录检测框的Top-3类别置信度（用于排查识别错误）...")
                        // 注意：这里需要重新运行推理来获取所有类别的置信度，但为了性能，我们只在日志中记录
                        // 实际应用中，可以考虑在runInference中保存所有类别的置信度
                    }
                    
                    // 过滤1：只保留蓝色区域内的检测结果
                    val blueRegionDetections = if (blueRegion != null) {
                        filteredDetections.filter { detection ->
                            // 将检测框坐标从letterbox空间转换到原图空间
                            val (center, _) = convertLetterboxToOriginal(
                                detection.x, detection.y, detection.width, detection.height,
                                bitmap.width, bitmap.height
                            )
                            BlueRegionDetector.isInBlueRegion(
                                detectionX = center.first / bitmap.width,
                                detectionY = center.second / bitmap.height,
                                imageWidth = bitmap.width,
                                imageHeight = bitmap.height,
                                blueRegion = blueRegion
                            )
                        }
                    } else {
                        filteredDetections
                    }
                    
                    DetectionLogger.log(
                        DetectionLogger.Level.INFO,
                        "蓝色区域过滤: ${filteredDetections.size} -> ${blueRegionDetections.size} 个检测框"
                    )
                    
                    // 过滤2：移除面积过大的检测框
                    val imageArea = (bitmap.width * bitmap.height).toFloat()
                    val maxBoxAreaRatio = getMaxBoxAreaRatio()
                    val areaFilteredDetections = blueRegionDetections.filter { detection ->
                        // 将检测框坐标从letterbox空间转换到原图空间
                        val (_, size) = convertLetterboxToOriginal(
                            detection.x, detection.y, detection.width, detection.height,
                            bitmap.width, bitmap.height
                        )
                        val boxWidth = size.first
                        val boxHeight = size.second
                        val boxArea = boxWidth * boxHeight
                        val areaRatio = boxArea / imageArea
                        
                        if (areaRatio > maxBoxAreaRatio) {
                            DetectionLogger.log(
                                DetectionLogger.Level.WARN,
                                "过滤掉过大的检测框: 类别${detection.classId}, 面积比例: ${String.format("%.2f%%", areaRatio * 100)}"
                            )
                            false
                        } else {
                            true
                        }
                    }
                    
                    DetectionLogger.log(
                        DetectionLogger.Level.INFO,
                        "面积过滤: ${blueRegionDetections.size} -> ${areaFilteredDetections.size} 个检测框"
                    )
                    
                    // 过滤3：移除紧贴图片边缘的检测框（任意一条边紧贴边缘即过滤）
                    val edgeTolerance = getEdgeTolerance()
                    val validDetections = areaFilteredDetections.filter { detection ->
                        // 将检测框坐标从letterbox空间转换到原图空间
                        val (center, size) = convertLetterboxToOriginal(
                            detection.x, detection.y, detection.width, detection.height,
                            bitmap.width, bitmap.height
                        )
                        val boxCenterX = center.first
                        val boxCenterY = center.second
                        val boxWidth = size.first
                        val boxHeight = size.second
                        
                        val left = boxCenterX - boxWidth / 2
                        val top = boxCenterY - boxHeight / 2
                        val right = boxCenterX + boxWidth / 2
                        val bottom = boxCenterY + boxHeight / 2
                        
                        // 检查是否紧贴边缘（任意一条边紧贴即判定为识别错误）
                        val touchLeft = left < edgeTolerance
                        val touchTop = top < edgeTolerance
                        val touchRight = right > (bitmap.width - edgeTolerance)
                        val touchBottom = bottom > (bitmap.height - edgeTolerance)
                        
                        // 如果任意一条边紧贴边缘，过滤掉（判定为识别错误）
                        if (touchLeft || touchTop || touchRight || touchBottom) {
                            val touchEdges = mutableListOf<String>()
                            if (touchLeft) touchEdges.add("左")
                            if (touchTop) touchEdges.add("上")
                            if (touchRight) touchEdges.add("右")
                            if (touchBottom) touchEdges.add("下")
                            
                            DetectionLogger.log(
                                DetectionLogger.Level.WARN,
                                "过滤掉紧贴边缘的检测框（识别错误）: 类别${detection.classId}, 紧贴边缘: ${touchEdges.joinToString("、")}"
                            )
                            false
                        } else {
                            true
                        }
                    }
                    
                    DetectionLogger.log(
                        DetectionLogger.Level.INFO,
                        "边缘过滤: ${areaFilteredDetections.size} -> ${validDetections.size} 个检测框"
                    )
                    
                    // 统计每个类别的数量
                    DetectionLogger.log(DetectionLogger.Level.INFO, "统计检测结果...")
                    val toolCounts = mutableMapOf<String, Int>()
                    val toolConfidences = mutableMapOf<String, MutableList<Float>>() // 保存每个工具的所有置信度
                    validDetections.forEachIndexed { index, detection ->
                        val toolName = getClassName(detection.classId)
                        toolCounts[toolName] = (toolCounts[toolName] ?: 0) + 1
                        
                        // 保存置信度
                        if (!toolConfidences.containsKey(toolName)) {
                            toolConfidences[toolName] = mutableListOf()
                        }
                        toolConfidences[toolName]!!.add(detection.confidence)
                        
                        // 记录识别结果（包括类别和置信度）
                        DetectionLogger.log(
                            DetectionLogger.Level.INFO,
                            "检测框${index + 1}: 识别为 $toolName (类别${detection.classId}), 置信度: ${String.format("%.2f", detection.confidence)}"
                        )
                        
                        // 如果置信度较低（<0.8），记录警告，可能是识别错误
                        if (detection.confidence < 0.8f) {
                            DetectionLogger.log(
                                DetectionLogger.Level.WARN,
                                "⚠️ 检测框${index + 1}置信度较低(${String.format("%.2f", detection.confidence)})，可能存在识别错误！请检查标注图片确认识别结果是否正确。"
                            )
                        }
                    }
                    
                    // 计算每个工具的平均置信度
                    val toolAvgConfidences = toolConfidences.mapValues { (_, confidences) ->
                        confidences.average().toFloat()
                    }
                    
                    // 计算总数量
                    val totalCount = toolCounts.values.sum()
                    
                    // 如果是作业后检测，限制数量不能超过作业前
                    val finalCount = if (beforeToolsJson != null && beforeToolsJson.isNotBlank()) {
                        DetectionLogger.log(DetectionLogger.Level.INFO, "作业后检测，应用数量限制...")
                        
                        // 解析作业前的工具总数（考虑对比模式）
                        val comparisonMode = SettingsManager.getComparisonMode(context)
                        val beforeCount = if (comparisonMode == "specific_tools") {
                            // 具体工具数量对比模式：尝试从工具列表解析
                            try {
                                val beforeToolsList = MockToolDetectionService.parseToolsJson(beforeToolsJson)
                                if (beforeToolsList.isNotEmpty()) {
                                    beforeToolsList.sumOf { tool ->
                                        val parts = tool.split("*")
                                        if (parts.size == 2) parts[1].toIntOrNull() ?: 0 else 0
                                    }
                                } else {
                                    // 如果工具列表为空，使用parseToolCount
                                    MockToolDetectionService.parseToolCount(beforeToolsJson)
                                }
                            } catch (e: Exception) {
                                // 解析失败，使用parseToolCount
                                DetectionLogger.log(DetectionLogger.Level.WARN, "解析工具列表失败，使用parseToolCount: ${e.message}")
                                MockToolDetectionService.parseToolCount(beforeToolsJson)
                            }
                        } else {
                            // 总数量对比模式：使用parseToolCount
                            MockToolDetectionService.parseToolCount(beforeToolsJson)
                        }
                        
                        DetectionLogger.log(DetectionLogger.Level.INFO, "作业前工具数量解析 - comparisonMode: $comparisonMode, beforeToolsJson: $beforeToolsJson, beforeCount: $beforeCount")
                        
                        // 限制作业后的数量不超过作业前
                        val limitedCount = if (totalCount > beforeCount) {
                            DetectionLogger.log(
                                DetectionLogger.Level.WARN,
                                "限制工具数量: 检测到${totalCount}件，但作业前只有${beforeCount}件，限制为${beforeCount}件"
                            )
                            beforeCount
                        } else {
                            totalCount
                        }
                        
                        DetectionLogger.log(
                            DetectionLogger.Level.INFO,
                            "数量限制: ${totalCount}件 -> ${limitedCount}件"
                        )
                        
                        limitedCount
                    } else {
                        totalCount
                    }
                    
                    DetectionLogger.logDetectionResults(listOf("总数*$finalCount"))
                    DetectionLogger.log(DetectionLogger.Level.INFO, "最终检测结果: 总数=$finalCount 件工具")
                    
                    // 根据对比方式设置决定返回格式
                    val comparisonMode = SettingsManager.getComparisonMode(context)
                    val resultJson = if (comparisonMode == "specific_tools") {
                        // 具体工具数量对比模式
                        if (beforeToolsJson != null && beforeToolsJson.isNotBlank()) {
                            // 作业后检测：基于作业前的工具列表查找匹配
                            try {
                                val beforeToolsList = MockToolDetectionService.parseToolsJson(beforeToolsJson)
                                if (beforeToolsList.isNotEmpty()) {
                                    DetectionLogger.log(DetectionLogger.Level.INFO, "作业后检测：基于作业前工具列表查找匹配 - 作业前工具: $beforeToolsList")
                                    
                                    // 解析作业前的工具
                                    fun parseTool(toolStr: String): Pair<String, Int> {
                                        val parts = toolStr.split("*")
                                        return if (parts.size == 2) {
                                            Pair(parts[0], parts[1].toIntOrNull() ?: 1)
                                        } else {
                                            Pair(toolStr, 1)
                                        }
                                    }
                                    
                                    val beforeMap = beforeToolsList.map { parseTool(it) }
                                        .groupBy { it.first }
                                        .mapValues { it.value.sumOf { pair -> pair.second } }
                                    
                                    // 创建工具名称到类别ID的反向映射（用于备选匹配）
                                    // 现在类别名就是工具名，所以直接通过类别名查找类别ID
                                    val toolNameToClassIds = mutableMapOf<String, MutableList<Int>>()
                                    classLabels.forEachIndexed { classId, className ->
                                        if (!toolNameToClassIds.containsKey(className)) {
                                            toolNameToClassIds[className] = mutableListOf()
                                        }
                                        toolNameToClassIds[className]!!.add(classId)
                                    }
                                    
                                    // 备选匹配阈值（如果备选类别中有目标工具，且置信度>=此阈值，就判定为匹配）
                                    val alternativeMatchThreshold = SettingsManager.getAlternativeMatchThreshold(context)
                                    
                                    // 对于每个作业前的工具，在检测结果中查找匹配
                                    val resultList = mutableListOf<String>()
                                    val usedDetections = mutableSetOf<Int>() // 记录已使用的检测框索引
                                    
                                    beforeMap.forEach { (toolName, beforeCount) ->
                                        var exactMatchCount = 0
                                        var alternativeMatchCount = 0
                                        
                                        // 获取目标工具的类别ID列表
                                        val targetClassIds = toolNameToClassIds[toolName] ?: emptyList()
                                        
                                        if (targetClassIds.isEmpty()) {
                                            DetectionLogger.log(DetectionLogger.Level.WARN, "工具 $toolName 没有对应的类别ID，无法匹配")
                                            resultList.add("$toolName*$beforeCount 未找到")
                                            return@forEach
                                        }
                                        
                                        // 第一步：查找完全匹配的检测结果
                                        validDetections.forEachIndexed { index, detection ->
                                            if (!usedDetections.contains(index) && exactMatchCount < beforeCount) {
                                                if (detection.classId in targetClassIds) {
                                                    exactMatchCount++
                                                    usedDetections.add(index)
                                                    DetectionLogger.log(
                                                        DetectionLogger.Level.INFO,
                                                        "工具 $toolName: 完全匹配（检测框$index，类别${detection.classId}，置信度${String.format("%.2f", detection.confidence)}）"
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // 第二步：如果完全匹配数量不足，查找备选类别
                                        if (exactMatchCount < beforeCount) {
                                            val remainingNeeded = beforeCount - exactMatchCount
                                            validDetections.forEachIndexed { index, detection ->
                                                if (!usedDetections.contains(index) && alternativeMatchCount < remainingNeeded) {
                                                    // 检查Top-3类别中是否包含目标工具
                                                    val matchedClass = detection.top3Classes.find { (classId, conf) ->
                                                        classId in targetClassIds && conf >= alternativeMatchThreshold
                                                    }
                                                    
                                                    if (matchedClass != null) {
                                                        alternativeMatchCount++
                                                        usedDetections.add(index)
                                                        DetectionLogger.log(
                                                            DetectionLogger.Level.INFO,
                                                            "工具 $toolName: 备选匹配（检测框$index，原识别类别${detection.classId}，备选类别${matchedClass.first}，置信度${String.format("%.2f", matchedClass.second)}）"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        val totalMatchedCount = exactMatchCount + alternativeMatchCount
                                        
                                        // 判断状态和缺失数量
                                        val statusText = if (totalMatchedCount >= beforeCount) {
                                            // 完全匹配或超出，标记为"已存在"
                                            resultList.add("$toolName*$beforeCount 已存在")
                                            "已存在"
                                        } else {
                                            // 部分匹配，计算缺失数量
                                            val missingCount = beforeCount - totalMatchedCount
                                            resultList.add("$toolName*$beforeCount 缺失${missingCount}件")
                                            "缺失${missingCount}件"
                                        }
                                        DetectionLogger.log(
                                            DetectionLogger.Level.INFO,
                                            "工具 $toolName: 作业前${beforeCount}件，匹配到${totalMatchedCount}件（完全匹配${exactMatchCount}件，备选匹配${alternativeMatchCount}件） -> $statusText"
                                        )
                                    }
                                    
                                    DetectionLogger.log(DetectionLogger.Level.INFO, "返回作业后匹配结果: $resultList")
                                    MockToolDetectionService.toolsToJson(resultList)
                                } else {
                                    // 如果作业前工具列表为空，使用原来的逻辑
                                    val toolList = toolCounts.map { (toolName, count) -> "$toolName*$count" }
                                    DetectionLogger.log(DetectionLogger.Level.INFO, "作业前工具列表为空，返回检测结果: $toolList")
                                    MockToolDetectionService.toolsToJson(toolList)
                                }
                            } catch (e: Exception) {
                                DetectionLogger.log(DetectionLogger.Level.WARN, "解析作业前工具列表失败，使用原逻辑: ${e.message}")
                                val toolList = toolCounts.map { (toolName, count) -> "$toolName*$count" }
                                MockToolDetectionService.toolsToJson(toolList)
                            }
                        } else {
                            // 作业前检测：返回工具列表（格式：["扳手*2", "螺丝刀*1"]）
                            val toolList = toolCounts.map { (toolName, count) -> "$toolName*$count" }
                            DetectionLogger.log(DetectionLogger.Level.INFO, "返回具体工具列表: $toolList")
                            MockToolDetectionService.toolsToJson(toolList)
                        }
                    } else {
                        // 总数量对比：返回总数量（格式：{"count": 5}）
                        DetectionLogger.log(DetectionLogger.Level.INFO, "返回总数量: $finalCount")
                        MockToolDetectionService.toolCountToJson(finalCount)
                    }
                    
                    // 绘制检测框并保存标注图片（即使没有检测结果，也绘制蓝色区域边界）
                    try {
                        DetectionLogger.log(DetectionLogger.Level.INFO, "开始绘制标注图片...")
                        
                        // 获取原图尺寸用于坐标缩放
                        val originalWidth = bitmap.width.toFloat()
                        val originalHeight = bitmap.height.toFloat()
                        
                        DetectionLogger.log(DetectionLogger.Level.DEBUG, "原图尺寸: ${originalWidth}x${originalHeight}, letterbox缩放: ${letterboxScale}, padding: (${letterboxPadX}, ${letterboxPadY})")
                        
                        // 转换检测结果为可视化格式（只绘制有效的检测）
                        val visualDetections = validDetections.map { detection ->
                            val toolName = getClassName(detection.classId)
                            
                            // 将检测框坐标从letterbox空间转换到原图空间，然后归一化
                            val (center, size) = convertLetterboxToOriginal(
                                detection.x, detection.y, detection.width, detection.height,
                                bitmap.width, bitmap.height
                            )
                            
                            DetectionVisualizer.DetectionBox(
                                x = center.first / originalWidth,
                                y = center.second / originalHeight,
                                width = size.first / originalWidth,
                                height = size.second / originalHeight,
                                confidence = detection.confidence,
                                classId = detection.classId,
                                className = toolName
                            )
                        }
                        
                        DetectionLogger.log(DetectionLogger.Level.INFO, "准备绘制 ${visualDetections.size} 个有效检测框")
                        
                        // 检查设置：是否保存标注图片
                        val saveAnnotatedImage = SettingsManager.getSaveAnnotatedImage(context)
                        val showDetectionBox = SettingsManager.getShowDetectionBox(context)
                        val imageQuality = SettingsManager.getImageQuality(context)
                        val compressImages = SettingsManager.getCompressImages(context)
                        
                        if (saveAnnotatedImage) {
                            // 绘制检测框到原图（根据设置决定是否绘制检测框）
                            val detectionsToDraw = if (showDetectionBox) visualDetections else emptyList()
                            val annotatedPath = DetectionVisualizer.drawDetections(
                                imagePath = imagePath,
                                detections = detectionsToDraw,
                                outputPath = imagePath.replace(".jpg", "_annotated.jpg"),
                                blueRegion = blueRegion,
                                quality = imageQuality,
                                compress = compressImages
                            )
                            
                            DetectionLogger.log(DetectionLogger.Level.INFO, "✅ 标注图片已保存: $annotatedPath (共${detectionsToDraw.size}个框)")
                        } else {
                            DetectionLogger.log(DetectionLogger.Level.INFO, "跳过保存标注图片（设置中已关闭）")
                        }
                    } catch (e: Exception) {
                        DetectionLogger.log(DetectionLogger.Level.WARN, "绘制标注图片失败: ${e.message}", e)
                    }
                    
                        val totalDuration = System.currentTimeMillis() - startTime
                        DetectionLogger.log(DetectionLogger.Level.INFO, "检测完成，总耗时: ${totalDuration}ms")
                        DetectionLogger.log(DetectionLogger.Level.INFO, "识别到工具总数: $finalCount 件")
                        DetectionLogger.log(DetectionLogger.Level.INFO, "========== 检测结束 ==========\n")
                        
                        // 返回结果（根据对比方式设置决定格式）
                        resultJson
                    } catch (e: Exception) {
                        val totalDuration = System.currentTimeMillis() - startTime
                        DetectionLogger.log(DetectionLogger.Level.ERROR, "检测过程发生异常，耗时: ${totalDuration}ms", e)
                        DetectionLogger.log(DetectionLogger.Level.ERROR, "========== 检测异常结束 ==========\n")
                        
                        // 返回空结果（数量为0）
                        MockToolDetectionService.toolCountToJson(0)
                    } finally {
                        // 确保bitmap被回收，防止内存泄漏
                        bitmap?.let {
                            if (!it.isRecycled) {
                                it.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {
                    val totalDuration = System.currentTimeMillis() - startTime
                    DetectionLogger.log(DetectionLogger.Level.ERROR, "检测过程发生异常，耗时: ${totalDuration}ms", e)
                    DetectionLogger.log(DetectionLogger.Level.ERROR, "========== 检测异常结束 ==========\n")
                    
                    // 返回空结果（数量为0）
                    MockToolDetectionService.toolCountToJson(0)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val totalDuration = System.currentTimeMillis() - startTime
            DetectionLogger.log(DetectionLogger.Level.ERROR, "检测超时（${detectionTimeout / 1000}秒），耗时: ${totalDuration}ms")
            DetectionLogger.log(DetectionLogger.Level.ERROR, "========== 检测超时结束 ==========\n")
            MockToolDetectionService.toolCountToJson(0)
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            DetectionLogger.log(DetectionLogger.Level.ERROR, "检测过程发生异常，耗时: ${totalDuration}ms", e)
            DetectionLogger.log(DetectionLogger.Level.ERROR, "========== 检测异常结束 ==========\n")
            
            // 如果检测失败，返回数量0
            MockToolDetectionService.toolCountToJson(0)
        }
    }
    
    /**
     * 加载图片并根据EXIF信息旋转
     * 使用采样优化，减少内存占用
     */
    private fun loadAndRotateImage(imagePath: String): Bitmap {
        // 先读取图片尺寸，计算采样率
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)
        
        // 如果图片太大，使用采样缩小（最大2048x2048）
        val maxSize = 2048
        val sampleSize = if (options.outWidth > maxSize || options.outHeight > maxSize) {
            val widthRatio = options.outWidth / maxSize
            val heightRatio = options.outHeight / maxSize
            maxOf(widthRatio, heightRatio)
        } else {
            1
        }
        
        // 解码图片（使用采样）
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        val bitmap = BitmapFactory.decodeFile(imagePath, options)
            ?: throw IllegalStateException("无法加载图片: $imagePath")
        
        // 读取EXIF信息以获取正确的旋转角度
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        
        return if (matrix.isIdentity) {
            bitmap
        } else {
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            // 回收原始bitmap
            bitmap.recycle()
            rotatedBitmap
        }
    }
    
    /**
     * 预处理图片（使用Letterbox方式，保持宽高比）
     * 将图片调整为模型输入尺寸，并归一化
     * 与YOLOv8训练时的处理方式一致
     */
    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()
        
        // 计算缩放比例（保持宽高比）
        val scale = minOf(inputWidth / originalWidth, inputHeight / originalHeight)
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        // 保存letterbox参数（用于后续坐标转换）
        letterboxScale = scale
        letterboxPadX = (inputWidth - scaledWidth) / 2f
        letterboxPadY = (inputHeight - scaledHeight) / 2f
        
        // 缩放图片（保持宽高比）
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // 创建640x640的黑色画布
        val letterboxBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(letterboxBitmap)
        canvas.drawColor(android.graphics.Color.BLACK) // 填充黑色
        
        // 将缩放后的图片居中放置
        val left = letterboxPadX.toInt()
        val top = letterboxPadY.toInt()
        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
        
        try {
            // 创建FloatBuffer用于存储预处理后的图片数据
            val buffer = FloatBuffer.allocate(1 * 3 * inputHeight * inputWidth)
            
            // 转换为CHW格式并归一化到[0, 1]
            val pixels = IntArray(inputWidth * inputHeight)
            letterboxBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            
            // 按CHW顺序填充buffer: [R通道, G通道, B通道]
            for (c in 0 until 3) {
                for (h in 0 until inputHeight) {
                    for (w in 0 until inputWidth) {
                        val pixelIndex = h * inputWidth + w
                        val pixel = pixels[pixelIndex]
                        
                        val value = when (c) {
                            0 -> ((pixel shr 16) and 0xFF) / 255.0f  // R
                            1 -> ((pixel shr 8) and 0xFF) / 255.0f   // G
                            2 -> (pixel and 0xFF) / 255.0f           // B
                            else -> 0f
                        }
                        
                        buffer.put(value)
                    }
                }
            }
            
            buffer.rewind()
            return buffer
        } finally {
            // 回收临时创建的bitmap
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            letterboxBitmap.recycle()
        }
    }
    
    /**
     * 运行ONNX推理
     * @param inputBuffer 输入缓冲区
     * @param overrideConfidenceThreshold 临时覆盖置信度阈值（可选）
     */
    private fun runInference(inputBuffer: FloatBuffer, overrideConfidenceThreshold: Float? = null): List<Detection> {
        val session = ortSession ?: throw RuntimeException("ONNX session not initialized")
        
        // 创建输入张量
        val inputShape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, inputShape)
        
        // 运行推理
        val results = session.run(mapOf(session.inputNames.first() to inputTensor))
        
        // 获取输出
        // YOLOv8输出格式: [1, 28, 8400] -> [batch, num_classes+4, num_predictions]
        val outputTensor = results[0].value as Array<*>
        val output = outputTensor[0] as Array<FloatArray>
        
        val detections = mutableListOf<Detection>()
        val numClasses = classLabels.size
        
        // output[0] = [x, x, x, ...] 8400个x坐标
        // output[1] = [y, y, y, ...] 8400个y坐标
        // output[2] = [w, w, w, ...] 8400个宽度
        // output[3] = [h, h, h, ...] 8400个高度
        // output[4-27] = 24个类别的置信度
        
        val numPredictions = output[0].size
        
        for (i in 0 until numPredictions) {
            // 获取边界框坐标（已经是中心点格式）
            val x = output[0][i]
            val y = output[1][i]
            val w = output[2][i]
            val h = output[3][i]
            
            // 收集所有类别的置信度
            val classConfidences = mutableListOf<Pair<Int, Float>>()
            for (c in 0 until numClasses) {
                val conf = output[4 + c][i]
                if (conf > 0.05f) { // 只记录置信度>0.05的类别
                    classConfidences.add(Pair(c, conf))
                }
            }
            
            // 按置信度降序排序
            classConfidences.sortByDescending { it.second }
            
            // 找到最大置信度的类别
            val maxConf = classConfidences.firstOrNull()?.second ?: 0f
            val maxClassId = classConfidences.firstOrNull()?.first ?: -1
            
            // 过滤低置信度检测
            val confidenceThreshold = getConfidenceThreshold(overrideConfidenceThreshold)
            if (maxConf > confidenceThreshold && maxClassId >= 0) {
                // 保存Top-3类别置信度（用于备选匹配）
                val top3 = classConfidences.take(3)
                
                // 记录Top-3类别置信度（用于排查识别错误）
                if (classConfidences.size > 1) {
                    val top3Info = top3.joinToString(", ") { (classId, conf) ->
                        val toolName = getClassName(classId)
                        "$toolName(${String.format("%.2f", conf)})"
                    }
                    DetectionLogger.log(
                        DetectionLogger.Level.DEBUG,
                        "检测框候选类别Top-3: $top3Info"
                    )
                }
                
                detections.add(
                    Detection(
                        x = x,
                        y = y,
                        width = w,
                        height = h,
                        confidence = maxConf,
                        classId = maxClassId,
                        top3Classes = top3
                    )
                )
            }
        }
        
        inputTensor.close()
        results.close()
        
        return detections
    }
    
    /**
     * 应用非极大值抑制(NMS)
     * @param detections 检测结果列表
     * @param overrideIouThreshold 临时覆盖IOU阈值（可选）
     */
    private fun applyNMS(detections: List<Detection>, overrideIouThreshold: Float? = null): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        val iouThreshold = getIouThreshold(overrideIouThreshold)
        
        // 按置信度降序排序
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selectedDetections = mutableListOf<Detection>()
        
        val suppressed = BooleanArray(sortedDetections.size)
        
        for (i in sortedDetections.indices) {
            if (suppressed[i]) continue
            
            selectedDetections.add(sortedDetections[i])
            
            // 抑制与当前检测框重叠度高的其他检测框
            for (j in i + 1 until sortedDetections.size) {
                if (suppressed[j]) continue
                
                val iou = calculateIoU(sortedDetections[i], sortedDetections[j])
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        
        return selectedDetections
    }
    
    /**
     * 计算两个检测框的IoU（交并比）
     */
    private fun calculateIoU(det1: Detection, det2: Detection): Float {
        val x1Min = det1.x - det1.width / 2
        val y1Min = det1.y - det1.height / 2
        val x1Max = det1.x + det1.width / 2
        val y1Max = det1.y + det1.height / 2
        
        val x2Min = det2.x - det2.width / 2
        val y2Min = det2.y - det2.height / 2
        val x2Max = det2.x + det2.width / 2
        val y2Max = det2.y + det2.height / 2
        
        val intersectXMin = max(x1Min, x2Min)
        val intersectYMin = max(y1Min, y2Min)
        val intersectXMax = min(x1Max, x2Max)
        val intersectYMax = min(y1Max, y2Max)
        
        val intersectWidth = max(0f, intersectXMax - intersectXMin)
        val intersectHeight = max(0f, intersectYMax - intersectYMin)
        val intersectArea = intersectWidth * intersectHeight
        
        val area1 = det1.width * det1.height
        val area2 = det2.width * det2.height
        val unionArea = area1 + area2 - intersectArea
        
        return if (unionArea > 0) intersectArea / unionArea else 0f
    }
    
    /**
     * 释放资源
     */
    fun release() {
        ortSession?.close()
        ortEnvironment?.close()
        ortSession = null
        ortEnvironment = null
    }
    
    /**
     * 检测结果数据类
     */
    private data class Detection(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
        val classId: Int,
        val top3Classes: List<Pair<Int, Float>> = emptyList() // Top-3类别置信度列表（类别ID, 置信度）
    )
    
    companion object {
        @Volatile
        private var instance: OnnxToolDetectionService? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): OnnxToolDetectionService {
            return instance ?: synchronized(this) {
                instance ?: OnnxToolDetectionService(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        /**
         * 解析工具数量JSON
         */
        fun parseToolCount(toolsJson: String): Int {
            return MockToolDetectionService.parseToolCount(toolsJson)
        }
        
        /**
         * 对比两个工具数量
         */
        fun compareToolCounts(beforeCount: Int, afterCount: Int): List<String> {
            return MockToolDetectionService.compareToolCounts(beforeCount, afterCount)
        }
        
        /**
         * 根据设置对比工具（自动选择对比方式）
         * @param context Context
         * @param beforeToolsJson 作业前的工具JSON
         * @param afterToolsJson 作业后的工具JSON
         * @return 缺失的工具信息列表
         */
        fun compareToolsByMode(context: Context, beforeToolsJson: String, afterToolsJson: String): List<String> {
            val comparisonMode = SettingsManager.getComparisonMode(context)
            DetectionLogger.log(DetectionLogger.Level.INFO, "compareToolsByMode - comparisonMode: $comparisonMode, beforeToolsJson: $beforeToolsJson, afterToolsJson: $afterToolsJson")
            
            // 检查工具JSON格式：如果是列表格式（以[开头），说明是具体工具数量对比模式的数据
            val isListFormat = beforeToolsJson.trim().startsWith("[") || afterToolsJson.trim().startsWith("[")
            
            return if (comparisonMode == "specific_tools" || isListFormat) {
                // 具体工具数量对比（或者数据是列表格式）
                val beforeTools = MockToolDetectionService.parseToolsJson(beforeToolsJson)
                val afterTools = MockToolDetectionService.parseToolsJson(afterToolsJson)
                DetectionLogger.log(DetectionLogger.Level.INFO, "compareToolsByMode - 具体工具数量对比模式 - beforeTools: $beforeTools, afterTools: $afterTools")
                MockToolDetectionService.compareSpecificTools(beforeTools, afterTools)
            } else {
                // 总数量对比（默认）
                val beforeCount = MockToolDetectionService.parseToolCount(beforeToolsJson)
                val afterCount = MockToolDetectionService.parseToolCount(afterToolsJson)
                DetectionLogger.log(DetectionLogger.Level.INFO, "compareToolsByMode - 总数量对比模式 - beforeCount: $beforeCount, afterCount: $afterCount")
                MockToolDetectionService.compareToolCounts(beforeCount, afterCount)
            }
        }
        
        /**
         * 将工具数量转换为JSON
         */
        fun toolCountToJson(count: Int): String {
            return MockToolDetectionService.toolCountToJson(count)
        }
        
        /**
         * 解析工具列表JSON（已废弃，保留用于向后兼容）
         */
        @Deprecated("使用 parseToolCount 代替")
        fun parseToolsJson(toolsJson: String): List<String> {
            return MockToolDetectionService.parseToolsJson(toolsJson)
        }
        
        /**
         * 对比两个工具列表（已废弃，保留用于向后兼容）
         */
        @Deprecated("使用 compareToolCounts 代替")
        fun compareTools(beforeTools: List<String>, afterTools: List<String>): List<String> {
            return MockToolDetectionService.compareTools(beforeTools, afterTools)
        }
        
        /**
         * 将工具列表转换为JSON（已废弃，保留用于向后兼容）
         */
        @Deprecated("使用 toolCountToJson 代替")
        fun toolsToJson(tools: List<String>): String {
            return MockToolDetectionService.toolsToJson(tools)
        }
    }
}
