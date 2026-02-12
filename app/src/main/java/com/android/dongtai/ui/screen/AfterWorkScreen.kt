package com.android.dongtai.ui.screen

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.android.dongtai.R
import com.android.dongtai.data.entity.ToolRecord
import com.android.dongtai.data.entity.WorkRecord
import com.android.dongtai.service.OnnxToolDetectionService
import com.android.dongtai.utils.DetectionLogger
import com.android.dongtai.utils.SettingsManager
import com.android.dongtai.ui.viewmodel.ToolRecordViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 作业后清点界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AfterWorkScreen(
    workRecord: WorkRecord,
    viewModel: ToolRecordViewModel,
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit,
    onImageClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val beforeRecord = workRecord.beforeRecord
    
    // 解析作业前的工具数量和工具列表
    val comparisonMode = SettingsManager.getComparisonMode(context)
    val beforeToolsList = remember(beforeRecord.toolsJson, comparisonMode) {
        if (comparisonMode == "specific_tools") {
            try {
                val list = OnnxToolDetectionService.parseToolsJson(beforeRecord.toolsJson)
                DetectionLogger.log(DetectionLogger.Level.INFO, "AfterWorkScreen - 解析作业前工具列表成功 - toolsJson: ${beforeRecord.toolsJson}, list: $list")
                list
            } catch (e: Exception) {
                DetectionLogger.log(DetectionLogger.Level.WARN, "AfterWorkScreen - 解析作业前工具列表失败: ${e.message}, toolsJson: ${beforeRecord.toolsJson}")
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    val beforeCount = remember(beforeRecord.toolsJson, comparisonMode) { 
        val count = if (comparisonMode == "specific_tools") {
            // 具体工具数量对比模式：从工具列表计算数量
            if (beforeToolsList.isNotEmpty()) {
                beforeToolsList.sumOf { tool ->
                    val parts = tool.split("*")
                    if (parts.size == 2) parts[1].toIntOrNull() ?: 0 else 0
                }
            } else {
                // 如果工具列表为空，尝试使用parseToolCount
                OnnxToolDetectionService.parseToolCount(beforeRecord.toolsJson)
            }
        } else {
            // 总数量对比模式：使用parseToolCount
            OnnxToolDetectionService.parseToolCount(beforeRecord.toolsJson)
        }
        DetectionLogger.log(DetectionLogger.Level.INFO, "AfterWorkScreen - 解析作业前工具数量 - comparisonMode: $comparisonMode, toolsJson: ${beforeRecord.toolsJson}, beforeToolsList: $beforeToolsList, count: $count")
        count
    }
    
    var afterImagePath by remember { mutableStateOf<String?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectedCount by remember { mutableIntStateOf(0) }
    var afterToolsJson by remember { mutableStateOf<String?>(null) } // 保存作业后的工具JSON
    var missingTools by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var currentPhotoFile by remember { mutableStateOf<File?>(null) }
    var triggerRecognition by remember { mutableIntStateOf(0) }
    var isReRecognizing by remember { mutableStateOf(false) } // 标记是否为重新识别
    var showFullScreenImage by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()
    
    // 图片列表（作业前和作业后）
    val imagePaths = remember(afterImagePath) {
        if (afterImagePath != null) {
            listOf(beforeRecord.imagePath, afterImagePath!!)
        } else {
            listOf(beforeRecord.imagePath)
        }
    }
    
    val pagerState = rememberPagerState(initialPage = 0) { imagePaths.size }
    
    // 识别工具（检查自动识别设置）
    LaunchedEffect(afterImagePath, triggerRecognition) {
        if (afterImagePath != null && !isDetecting) {
            // 检查自动识别设置
            val autoRecognition = SettingsManager.getAutoRecognition(context)
            if (!autoRecognition) {
                DetectionLogger.log(DetectionLogger.Level.INFO, "自动识别已关闭（设置中），跳过识别")
                return@LaunchedEffect
            }
            
            isDetecting = true
            detectedCount = 0
            missingTools = emptyList()
            
            // 删除旧的标注图片（如果存在）
            val annotatedPath = afterImagePath!!.replace(".jpg", "_annotated.jpg")
            val annotatedFile = File(annotatedPath)
            if (annotatedFile.exists()) {
                annotatedFile.delete()
                DetectionLogger.log(DetectionLogger.Level.INFO, "删除旧的标注图片")
            }
            
            // 传入作业前的工具列表JSON，确保作业后的结果不超过作业前
            val detectionService = OnnxToolDetectionService.getInstance(context)
            
            // 如果是重新识别，更大幅度地降低置信度阈值，尝试检测到更多工具
            val toolsJson = if (isReRecognizing) {
                val baseConfidence = SettingsManager.getConfidenceThreshold(context)
                val baseIou = SettingsManager.getIouThreshold(context)
                
                // 重新识别时：更大幅度地降低置信度阈值（降低0.1-0.15），尝试检测到更多工具
                // 这样可以检测到置信度较低但可能正确的工具（比如螺丝刀被误识别为扳手的情况）
                val confidenceReduction = kotlin.random.Random.nextFloat() * 0.05f + 0.1f // 降低0.1-0.15
                val adjustedConfidence = (baseConfidence - confidenceReduction).coerceAtLeast(0.1f)
                
                // IOU阈值稍微降低，避免过度过滤
                val iouReduction = kotlin.random.Random.nextFloat() * 0.05f + 0.05f // 降低0.05-0.1
                val adjustedIou = (baseIou - iouReduction).coerceAtLeast(0.2f)
                
                DetectionLogger.log(
                    DetectionLogger.Level.INFO,
                    "重新识别：大幅降低阈值以检测更多工具 - 置信度 ${String.format("%.3f", baseConfidence)} -> ${String.format("%.3f", adjustedConfidence)} (降低${String.format("%.3f", confidenceReduction)}), IOU ${String.format("%.3f", baseIou)} -> ${String.format("%.3f", adjustedIou)}"
                )
                
                detectionService.detectTools(
                    afterImagePath!!,
                    beforeRecord.toolsJson,
                    overrideConfidenceThreshold = adjustedConfidence,
                    overrideIouThreshold = adjustedIou
                )
            } else {
                detectionService.detectTools(
                    afterImagePath!!, 
                    beforeRecord.toolsJson
                )
            }
            
            afterToolsJson = toolsJson // 保存工具JSON
            
            // 根据对比模式解析工具数量
            val comparisonMode = SettingsManager.getComparisonMode(context)
            detectedCount = if (comparisonMode == "specific_tools" && toolsJson != null) {
                // 具体工具数量对比模式：从工具列表解析
                try {
                    val toolsList = OnnxToolDetectionService.parseToolsJson(toolsJson)
                    toolsList.sumOf { tool ->
                        val parts = tool.split("*")
                        if (parts.size == 2) {
                            // 移除状态信息（"已存在"或"未找到"）
                            val countPart = parts[1].trim().split(" ")[0]
                            countPart.toIntOrNull() ?: 0
                        } else {
                            0
                        }
                    }
                } catch (e: Exception) {
                    // 解析失败，使用parseToolCount
                    DetectionLogger.log(DetectionLogger.Level.WARN, "解析工具列表失败，使用parseToolCount: ${e.message}")
                    OnnxToolDetectionService.parseToolCount(toolsJson)
                }
            } else {
                // 总数量对比模式：使用parseToolCount
                OnnxToolDetectionService.parseToolCount(toolsJson)
            }
            
            DetectionLogger.log(DetectionLogger.Level.INFO, "AfterWorkScreen - 解析作业后工具数量 - comparisonMode: $comparisonMode, toolsJson: $toolsJson, detectedCount: $detectedCount")
            
            // 计算对比结果（缺失的工具）- 根据设置选择对比方式
            missingTools = OnnxToolDetectionService.compareToolsByMode(context, beforeRecord.toolsJson, toolsJson)
            isDetecting = false
            isReRecognizing = false // 重置标记
            
            // 识别完成后，自动切换到作业后照片页面（如果还在作业前页面）
            scope.launch {
                kotlinx.coroutines.delay(100)
                try {
                    if (pagerState.currentPage == 0 && imagePaths.size > 1) {
                        pagerState.animateScrollToPage(1)
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            
            // 反馈设置：震动和声音
            val vibrationFeedback = SettingsManager.getVibrationFeedback(context)
            val soundFeedback = SettingsManager.getSoundFeedback(context)
            
            if (vibrationFeedback) {
                // 震动反馈
                try {
                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(100)
                    }
                } catch (e: Exception) {
                    DetectionLogger.log(DetectionLogger.Level.WARN, "震动反馈失败: ${e.message}")
                }
            }
            
            if (soundFeedback) {
                // 声音反馈（使用系统通知音）
                try {
                    val notification = android.app.Notification.Builder(context, "detection_channel")
                        .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                        .build()
                    // 这里只是示例，实际可以使用 MediaPlayer 播放声音
                } catch (e: Exception) {
                    DetectionLogger.log(DetectionLogger.Level.WARN, "声音反馈失败: ${e.message}")
                }
            }
            // 识别完成后，自动切换到作业后照片页面
            if (pagerState.currentPage == 0) {
                pagerState.animateScrollToPage(1)
            }
        }
    }
    
    // 调用系统相机
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoFile != null) {
            afterImagePath = currentPhotoFile!!.absolutePath
            detectedCount = 0
            triggerRecognition++ // Trigger recognition after photo taken
            // 清除焦点，避免弹出输入法
            focusManager.clearFocus()
            // 自动切换到作业后图片页面（向左滑动）
            scope.launch {
                kotlinx.coroutines.delay(300) // 等待页面更新和图片加载
                try {
                    pagerState.animateScrollToPage(1)
                } catch (e: Exception) {
                    // 如果页面还没有准备好，忽略错误
                }
            }
        } else {
            currentPhotoFile?.delete() // Delete temp file if not successful
            currentPhotoFile = null
        }
    }
    
    // 启动拍照的函数
    fun launchCamera() {
        val photoFile = File(
            context.getExternalFilesDir(null),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )
        currentPhotoFile = photoFile
        
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        
        takePictureLauncher.launch(photoUri)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("作业后清点") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // 已移除分享功能
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 可滚动的内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 作业信息（不可编辑）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (beforeRecord.workGroup.isNotBlank()) {
                            Text(
                                text = "作业组：${beforeRecord.workGroup}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (beforeRecord.workLocation.isNotBlank()) {
                            Text(
                                text = "作业地点：${beforeRecord.workLocation}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // 图片滑动区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val imagePath = imagePaths[page]
                            // 优先显示带标注的图片
                            val annotatedPath = imagePath.replace(".jpg", "_annotated.jpg")
                            val annotatedFile = File(annotatedPath)
                            val imageFile = if (annotatedFile.exists()) annotatedFile else File(imagePath)
                            
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (imageFile.exists()) {
                                    // 图片区域（可点击）
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            ImageRequest.Builder(context)
                                                .data(imageFile)
                                                .build()
                                        ),
                                        contentDescription = if (page == 0) "作业前照片" else "作业后照片",
                                        modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .clickable {
                                            // 点击图片打开全屏查看（使用Dialog）
                                            fullScreenImagePath = imageFile.absolutePath
                                            showFullScreenImage = true
                                        },
                                        contentScale = ContentScale.Fit
                                    )
                                    
                                    // 点击提示（右上角）
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(16.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "点击查看大图",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 页面指示器
                        if (imagePaths.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                imagePaths.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (pagerState.currentPage == index) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                            }
                        }
                        
                        // 操作按钮（仅在作业后图片时显示）
                        if (afterImagePath != null && pagerState.currentPage == 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 重新拍照按钮
                                FloatingActionButton(
                                    onClick = {
                                        afterImagePath = null
                                        detectedCount = 0
                                        isDetecting = false
                                        currentPhotoFile?.delete()
                                        currentPhotoFile = null
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "重新拍照",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                // 重新识别按钮
                                FloatingActionButton(
                                    onClick = {
                                        if (!isDetecting) {
                                            // 触发重新识别（使用随机调整的阈值）
                                            detectedCount = 0
                                            afterToolsJson = null
                                            missingTools = emptyList()
                                            isReRecognizing = true // 标记为重新识别
                                            triggerRecognition++
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = if (isDetecting) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                ) {
                                    if (isDetecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "重新识别",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 拍摄作业后照片按钮（在预览图下方）
                if (afterImagePath == null) {
                    Button(
                        onClick = {
                            if (cameraPermissionState.status is PermissionStatus.Granted) {
                                launchCamera()
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("拍摄作业后照片")
                    }
                }
                
                // 识别状态（仅在作业后页面且正在识别时显示）
                if (pagerState.currentPage == 1 && isDetecting) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "AI识别中...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                
                // 识别结果（根据当前页面切换显示）
                val currentCount = if (pagerState.currentPage == 0) {
                    beforeCount
                } else {
                    detectedCount
                }
                
                // 检查对比方式，决定是否显示具体工具列表
                val currentToolsList = if (comparisonMode == "specific_tools") {
                    if (pagerState.currentPage == 0) {
                        // 作业前页面：显示作业前的工具列表
                        beforeToolsList
                    } else {
                        // 作业后页面：显示作业后的工具列表
                        if (afterToolsJson != null) {
                            try {
                                OnnxToolDetectionService.parseToolsJson(afterToolsJson!!)
                            } catch (e: Exception) {
                                DetectionLogger.log(DetectionLogger.Level.WARN, "解析作业后工具列表失败: ${e.message}")
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    }
                } else {
                    emptyList()
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // 始终显示总数，即使为0也要显示
                            if (!(pagerState.currentPage == 1 && isDetecting)) {
                                // 检查是否显示工具数量
                                val showToolCount = SettingsManager.getShowToolCount(context)
                                if (showToolCount) {
                                    Text(
                                        text = if (pagerState.currentPage == 0) "作业前：识别到共 $currentCount 件工具" else "作业后：识别到共 $currentCount 件工具",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = if (pagerState.currentPage == 0) "作业前：已识别工具" else "作业后：已识别工具",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        // 如果是具体工具数量对比模式，显示具体工具列表
                        if (currentToolsList.isNotEmpty() && currentCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (pagerState.currentPage == 0) "作业前识别到的工具：" else "与作业前识别的工具的对比结果：",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            currentToolsList.forEach { tool ->
                                // 检查是否包含状态信息（"已存在"、"未找到"或"缺失X件"）
                                val hasStatus = tool.contains("已存在") || tool.contains("未找到") || tool.contains("缺失")
                                val textColor = if (hasStatus) {
                                    if (tool.contains("已存在")) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        // "未找到"或"缺失X件"都显示为错误颜色
                                        MaterialTheme.colorScheme.error
                                    }
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                
                                Text(
                                    text = "  • $tool",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor,
                                    fontWeight = if (hasStatus) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                
                // 对比结果（仅在作业后页面且识别完成时显示，即使识别结果为空也要显示对比）
                if (pagerState.currentPage == 1 && !isDetecting && afterImagePath != null && afterToolsJson != null) {
                    // 重新计算对比结果（根据设置选择对比方式）
                    val currentMissingTools = OnnxToolDetectionService.compareToolsByMode(context, beforeRecord.toolsJson, afterToolsJson!!)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentMissingTools.isEmpty()) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentMissingTools.isEmpty()) {
                                Text(
                                    text = "✓ 工具清点完整",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "所有工具已清点完毕，无缺失",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                // 显示AI判定结果（只显示总数对比）
                                currentMissingTools.forEach { tool ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = tool,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                
                                // 纠错按钮
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showCorrectionDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("纠错")
                                }
                            }
                        }
                    }
                }
            }
            
            // 提交按钮（固定在底部）
            Button(
                onClick = {
                    if (afterImagePath != null && !isDetecting && afterToolsJson != null) {
                        viewModel.insertAfterWorkRecord(
                            imagePath = afterImagePath!!,
                            toolsJson = afterToolsJson!!,
                            workGroup = beforeRecord.workGroup,
                            workLocation = beforeRecord.workLocation
                        ) {
                            showSuccessDialog = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                enabled = afterImagePath != null &&
                         !isDetecting &&
                         detectedCount > 0
            ) {
                Text("提交保存", style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        // 成功提示对话框
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("提交成功") },
                text = { Text("工具清点记录已保存") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSuccessDialog = false
                            onSaveComplete()
                        }
                    ) {
                        Text("确定")
                    }
                }
            )
        }
        
        // 纠错确认对话框
        if (showCorrectionDialog) {
            AlertDialog(
                onDismissRequest = { showCorrectionDialog = false },
                title = {
                    Text(
                        text = "纠错确认",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "是否为AI识别错误？实际未发生工具丢失的情况！",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // 纠错：将所有工具标记为"已存在"
                            if (beforeCount > 0) {
                                if (comparisonMode == "specific_tools") {
                                    // 具体工具数量对比：将所有作业前的工具标记为"已存在"
                                    val correctedToolsList = beforeToolsList.map { tool ->
                                        val parts = tool.split("*")
                                        if (parts.size == 2) {
                                            val toolName = parts[0]
                                            val count = parts[1].toIntOrNull() ?: 1
                                            "$toolName*$count 已存在"
                                        } else {
                                            "$tool 已存在"
                                        }
                                    }
                                    afterToolsJson = OnnxToolDetectionService.toolsToJson(correctedToolsList)
                                    detectedCount = beforeCount
                                    DetectionLogger.log(DetectionLogger.Level.INFO, "纠错：将所有工具标记为已存在 - $correctedToolsList")
                                } else {
                                    // 总数量对比：直接设置数量
                                    detectedCount = beforeCount
                                    afterToolsJson = OnnxToolDetectionService.toolCountToJson(detectedCount)
                                    DetectionLogger.log(DetectionLogger.Level.INFO, "纠错：设置作业后数量为作业前数量 - $detectedCount")
                                }
                                // 重新计算对比结果（应该为空）
                                missingTools = OnnxToolDetectionService.compareToolsByMode(context, beforeRecord.toolsJson, afterToolsJson!!)
                                DetectionLogger.log(DetectionLogger.Level.INFO, "纠错后对比结果: $missingTools")
                            }
                            
                            showCorrectionDialog = false
                        }
                    ) {
                        Text("是")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCorrectionDialog = false }) {
                        Text("否")
                    }
                }
            )
        }
    }
    
    // 全屏图片查看Dialog
    if (showFullScreenImage && fullScreenImagePath != null) {
        FullScreenImageDialog(
            imagePath = fullScreenImagePath!!,
            onDismiss = {
                showFullScreenImage = false
                fullScreenImagePath = null
            }
        )
    }
}
