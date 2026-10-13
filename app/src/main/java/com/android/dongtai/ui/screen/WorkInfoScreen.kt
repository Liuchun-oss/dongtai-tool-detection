package com.android.dongtai.ui.screen

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.android.dongtai.R
import com.android.dongtai.data.entity.RecordType
import com.android.dongtai.data.entity.ToolRecord
import com.android.dongtai.service.OnnxToolDetectionService
import com.android.dongtai.ui.viewmodel.ToolRecordViewModel
import com.android.dongtai.utils.DetectionHelper
import com.android.dongtai.utils.DetectionLogger
import com.android.dongtai.utils.SettingsManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 作业信息输入界面（集成拍照和识别）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WorkInfoScreen(
    recordType: RecordType,
    viewModel: ToolRecordViewModel,
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit,
    onImageClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    // 作业组列表
    val workGroupOptions = listOf(
        "请选择",
        "通辽1组",
        "通辽2组",
        "通辽3组",
        "白城1组",
        "白城2组",
        "白城3组",
        "白城4组",
        "大安北1组",
        "大安北2组",
        "太平川1组",
        "太平川2组",
        "赤峰1组",
        "赤峰2组",
        "双辽1组"
    )
    
    var workGroup by remember { mutableStateOf("请选择") }
    var workLocation by remember { mutableStateOf("") }
    var workGroupExpanded by remember { mutableStateOf(false) }
    var workGroupError by remember { mutableStateOf(false) }
    var workLocationError by remember { mutableStateOf(false) }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectedCount by remember { mutableIntStateOf(0) }
    var toolsJson by remember { mutableStateOf<String?>(null) } // 保存工具JSON，用于显示具体工具列表
    var detectedToolsWithConfidence by remember { mutableStateOf<Map<String, Float>>(emptyMap()) } // 保存每个工具的置信度
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showManualAdjustDialog by remember { mutableStateOf(false) } // 手动调整对话框
    var manualCount by remember { mutableIntStateOf(0) } // 手动输入的数量
    var currentPhotoFile by remember { mutableStateOf<File?>(null) }
    var showFullScreenImage by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    var imageRefreshKey by remember { mutableIntStateOf(0) } // 识别完成后+1，触发图片刷新
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // 调用系统相机
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoFile != null) {
            imagePath = currentPhotoFile!!.absolutePath
            // 重置识别状态
            detectedCount = 0
            toolsJson = null
            // 清除焦点，避免弹出输入法
            focusManager.clearFocus()
        }
    }
    
    // 权限授予后自动启动相机
    LaunchedEffect(cameraPermissionState.status) {
        if (cameraPermissionState.status is PermissionStatus.Granted && currentPhotoFile == null) {
            // 权限刚授予，可以准备拍照
        }
    }
    
    // 启动拍照的函数
    fun launchCamera() {
        // 清除焦点，避免拍照时输入框还有焦点
        focusManager.clearFocus()
        
        // 每次拍照都创建新文件
        val photoFile = File(
            context.getExternalFilesDir(null),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )
        currentPhotoFile = photoFile
        
        // 创建URI（Android 7.0+需要使用FileProvider）
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        
        takePictureLauncher.launch(photoUri)
    }
    
    var triggerRecognition by remember { mutableStateOf(0) }
    var isReRecognizing by remember { mutableStateOf(false) } // 标记是否为重新识别
    
    // 识别工具（检查自动识别设置）
    LaunchedEffect(imagePath, triggerRecognition) {
        if (imagePath != null) {
            val autoRecognition = SettingsManager.getAutoRecognition(context)
            if (!autoRecognition) {
                DetectionLogger.log(DetectionLogger.Level.INFO, "自动识别已关闭（设置中），跳过识别")
                return@LaunchedEffect
            }
            
            isDetecting = true
            detectedCount = 0
            
            val result = DetectionHelper.detectTools(context, imagePath!!, useTTA = isReRecognizing, phase = "before")
            
            toolsJson = result.toolsJson
            detectedCount = result.detectedCount
            detectedToolsWithConfidence = result.toolsWithConfidence
            
            DetectionLogger.log(DetectionLogger.Level.INFO, "最终detectedCount: $detectedCount")
            
            isDetecting = false
            isReRecognizing = false
            
            imageRefreshKey++ // 触发图片刷新，显示标注后的图片
            
            DetectionHelper.triggerFeedback(context)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = if (recordType == RecordType.BEFORE_WORK) "作业前清点" else "作业后清点",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (imagePath == null) "填写信息并拍照" else if (isDetecting) "AI识别中..." else "确认信息后提交",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showCategoriesDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "查看可识别物件"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 计算实际工具数量（用于显示和按钮启用判断）
            val comparisonMode = SettingsManager.getComparisonMode(context)
            val currentToolsJson = toolsJson // 使用局部变量避免智能转换问题
            val detectedToolsList = if (currentToolsJson != null && comparisonMode == "specific_tools") {
                // 解析具体工具列表
                try {
                    val parsed = OnnxToolDetectionService.parseToolsJson(currentToolsJson)
                    DetectionLogger.log(DetectionLogger.Level.INFO, "解析工具列表成功: $parsed")
                    parsed
                } catch (e: Exception) {
                    DetectionLogger.log(DetectionLogger.Level.WARN, "解析工具列表失败: ${e.message}, toolsJson: $currentToolsJson")
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // 如果有工具列表，从工具列表计算总数；否则使用 detectedCount
            val actualCount = if (detectedToolsList.isNotEmpty()) {
                val count = detectedToolsList.sumOf { tool ->
                    val parts = tool.split("*")
                    if (parts.size == 2) {
                        parts[1].toIntOrNull() ?: 0
                    } else {
                        0
                    }
                }
                DetectionLogger.log(DetectionLogger.Level.INFO, "从工具列表计算数量: $count")
                count
            } else {
                DetectionLogger.log(DetectionLogger.Level.INFO, "使用detectedCount: $detectedCount")
                detectedCount
            }
            
            // 调试日志
            DetectionLogger.log(DetectionLogger.Level.INFO, "显示数量计算 - comparisonMode: $comparisonMode, toolsJson: $currentToolsJson, detectedToolsList.size: ${detectedToolsList.size}, detectedCount: $detectedCount, actualCount: $actualCount")
            
            // 可滚动的内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 信息输入区域标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "作业信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "填写作业组和地点后拍照识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            
            // 作业组下拉框（必填）
            ExposedDropdownMenuBox(
                expanded = workGroupExpanded,
                onExpandedChange = { workGroupExpanded = it }
            ) {
                OutlinedTextField(
                    value = workGroup,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("作业组 *") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = workGroupExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(14.dp),
                    isError = workGroupError,
                    supportingText = if (workGroupError) {
                        { Text("请选择作业组", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = workGroupExpanded,
                    onDismissRequest = { workGroupExpanded = false }
                ) {
                    workGroupOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                workGroup = option
                                workGroupExpanded = false
                                workGroupError = false  // 清除错误状态
                            }
                        )
                    }
                }
            }
            
            // 作业地点输入框（必填）
            OutlinedTextField(
                value = workLocation,
                onValueChange = { 
                    workLocation = it
                    workLocationError = false
                },
                label = { Text("作业地点 *") },
                placeholder = { Text("请输入作业地点") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                isError = workLocationError,
                supportingText = if (workLocationError) {
                    { Text("请输入作业地点", color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true
            )
            
            // 拍照区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (imagePath == null) 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = if (imagePath == null) BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ) else null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (imagePath == null) 300.dp else 400.dp)
                ) {
                    if (imagePath == null) {
                        // 空状态 - 高端拍照引导
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            RoundedCornerShape(20.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (cameraPermissionState.status is PermissionStatus.Granted) {
                                                launchCamera()
                                            } else {
                                                cameraPermissionState.launchPermissionRequest()
                                            }
                                        },
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "拍照",
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "拍摄工具照片",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "请将工具平铺在蓝色垫布上",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        // 显示预览图（优先显示带标注的图片）
                        // imageRefreshKey 变化时触发重组，确保识别完成后能显示标注图
                        val annotatedPath = imagePath!!.replace(".jpg", "_annotated.jpg")
                        val annotatedFile = remember(imagePath, imageRefreshKey) { File(annotatedPath) }
                        val imageFile = remember(imagePath, imageRefreshKey) {
                            if (File(annotatedPath).exists()) File(annotatedPath) else File(imagePath!!)
                        }
                        
                        if (imageFile.exists()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // 图片区域（可点击）— 用 imageRefreshKey 破缓存
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(imageFile)
                                            .memoryCacheKey("${imageFile.absolutePath}_$imageRefreshKey")
                                            .diskCacheKey("${imageFile.absolutePath}_$imageRefreshKey")
                                            .build()
                                    ),
                                    contentDescription = "工具照片",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .clickable {
                                            // 点击图片打开全屏查看（使用Dialog，不导航）
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
                        
                        // 操作按钮（重新拍照、重新识别）- 浮动在图片上方
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 重新拍照按钮
                            FloatingActionButton(
                                onClick = {
                                    imagePath = null
                                    detectedCount = 0
                                    toolsJson = null
                                    isDetecting = false
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
                            if (imagePath != null) {
                                FloatingActionButton(
                                    onClick = {
                                        if (!isDetecting) {
                                            // 触发重新识别（使用随机调整的阈值）
                                            detectedCount = 0
                                            toolsJson = null
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
            }
            
            // 识别状态（带脉冲动画）
            if (isDetecting) {
                val infiniteTransition = rememberInfiniteTransition(label = "detecting")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
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
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "AI 正在识别工具...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 识别结果
            if (!isDetecting && imagePath != null) {
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (actualCount > 0) 
                            Color(0xFF4CAF50).copy(alpha = 0.08f)
                        else 
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            // 检查是否显示工具数量
                            val showToolCount = SettingsManager.getShowToolCount(context)
                            if (showToolCount) {
                                Text(
                                    text = if (actualCount > 0) "识别到共 $actualCount 件工具" else "未识别到工具",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = if (actualCount > 0) "已识别工具" else "未识别到工具",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // 如果是具体工具数量对比模式且有工具列表，显示具体工具列表
                        if (detectedToolsList.isNotEmpty() && actualCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "识别到的工具：",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            detectedToolsList.forEach { tool ->
                                Text(
                                    text = "  • $tool",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 手动调整按钮
                        if (actualCount > 0 || detectedToolsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    manualCount = actualCount
                                    showManualAdjustDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "手动调整",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // 手动调整对话框
            if (showManualAdjustDialog) {
                val comparisonMode = SettingsManager.getComparisonMode(context)
                val detectedToolsList = if (toolsJson != null && comparisonMode == "specific_tools") {
                    try {
                        OnnxToolDetectionService.parseToolsJson(toolsJson!!)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                // 所有可用的工具名称列表
                val allToolNames = listOf(
                    "扳手", "钳子", "刻刀", "内六角套装", "钥匙", "活口扳手", "螺丝刀", "毛刷",
                    "套筒扳手", "套筒", "带手柄内六角", "电烙铁", "卷尺", "胶带", "风机", "角膜",
                    "电钻", "移动电源", "探头1", "探头2", "探头3", "内六角", "吹风机", "管钳子",
                    "线缆", "黑体", "定标仪", "计时器", "口哨", "对讲机", "防护旗", "手灯",
                    "黑体2", "万用表", "磁钢尺", "喷壶", "瓶子", "小卷尺", "自喷漆", "收纳箱",
                    "移动电源2", "工具箱", "锯", "手锤", "标签", "T形扳手"
                )
                
                var manualToolCounts by remember(showManualAdjustDialog, detectedToolsList) {
                    mutableStateOf<Map<String, Int>>(
                        if (detectedToolsList.isNotEmpty()) {
                            detectedToolsList.associate { tool ->
                                val parts = tool.split("*")
                                val toolName = parts[0]
                                val count = if (parts.size == 2) parts[1].toIntOrNull() ?: 0 else 0
                                toolName to count
                            }.filter { it.value > 0 } // 只保留数量>0的工具
                        } else {
                            emptyMap()
                        }
                    )
                }
                
                var showAddToolDialog by remember { mutableStateOf(false) }
                var selectedToolToChange by remember { mutableStateOf<String?>(null) }
                
                AlertDialog(
                    onDismissRequest = { showManualAdjustDialog = false },
                    title = {
                        Text(
                            "手动调整",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (comparisonMode == "specific_tools") {
                                // 具体工具数量对比模式：紧凑的列表设计
                                if (manualToolCounts.isEmpty()) {
                                    Text(
                                        "暂无工具，点击下方按钮添加",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    manualToolCounts.toList().forEach { (toolName, count) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // 工具名称（可点击修改类型）
                                            Text(
                                                text = toolName,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedToolToChange = toolName },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            
                                            // 数量调整区域（紧凑设计）
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // 减少按钮
                                                IconButton(
                                                    onClick = {
                                                        val newCount = count - 1
                                                        if (newCount <= 0) {
                                                            manualToolCounts = manualToolCounts.toMutableMap().apply {
                                                                remove(toolName)
                                                            }
                                                        } else {
                                                            manualToolCounts = manualToolCounts.toMutableMap<String, Int>().apply {
                                                                put(toolName, newCount)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text(
                                                        "-",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                
                                                // 数量显示
                                                Text(
                                                    text = "$count",
                                                    modifier = Modifier.width(36.dp),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                
                                                // 增加按钮
                                                IconButton(
                                                    onClick = {
                                                        manualToolCounts = manualToolCounts.toMutableMap<String, Int>().apply {
                                                            put(toolName, count + 1)
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text(
                                                        "+",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                
                                                // 删除按钮（小图标）
                                                IconButton(
                                                    onClick = {
                                                        manualToolCounts = manualToolCounts.toMutableMap().apply {
                                                            remove(toolName)
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // 添加工具按钮（紧凑设计）
                                TextButton(
                                    onClick = { showAddToolDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "添加工具",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                // 修改工具类型对话框
                                if (selectedToolToChange != null) {
                                    var showToolSelector by remember { mutableStateOf(true) }
                                    if (showToolSelector) {
                                        AlertDialog(
                                            onDismissRequest = { 
                                                selectedToolToChange = null
                                                showToolSelector = false
                                            },
                                            title = { Text("选择工具类型") },
                                            text = {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .verticalScroll(rememberScrollState()),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    allToolNames.forEach { toolName ->
                                                        TextButton(
                                                            onClick = {
                                                                val oldCount = manualToolCounts[selectedToolToChange!!] ?: 0
                                                                manualToolCounts = manualToolCounts.toMutableMap<String, Int>().apply {
                                                                    remove(selectedToolToChange!!)
                                                                    put(toolName, oldCount)
                                                                }
                                                                selectedToolToChange = null
                                                                showToolSelector = false
                                                            },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(toolName)
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { 
                                                    selectedToolToChange = null
                                                    showToolSelector = false
                                                }) {
                                                    Text("取消")
                                                }
                                            }
                                        )
                                    }
                                }
                                
                                // 添加工具对话框
                                if (showAddToolDialog) {
                                    // 准备工具列表：如果有置信度信息，按置信度排序；否则按字母排序
                                    val sortedToolNames = if (detectedToolsWithConfidence.isNotEmpty()) {
                                        // 按置信度降序排序，然后按字母排序
                                        allToolNames.sortedWith(compareByDescending<String> { toolName ->
                                            detectedToolsWithConfidence[toolName] ?: 0f
                                        }.thenBy { it })
                                    } else {
                                        // 没有置信度信息，按字母排序
                                        allToolNames.sorted()
                                    }
                                    
                                    AlertDialog(
                                        onDismissRequest = { showAddToolDialog = false },
                                        title = { Text("添加工具") },
                                        text = {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                sortedToolNames.forEach { toolName ->
                                                    val confidence = detectedToolsWithConfidence[toolName]
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        TextButton(
                                                            onClick = {
                                                                val currentCount = manualToolCounts[toolName] ?: 0
                                                                manualToolCounts = manualToolCounts.toMutableMap<String, Int>().apply {
                                                                    put(toolName, currentCount + 1)
                                                                }
                                                                showAddToolDialog = false
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                text = toolName,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                        if (confidence != null && confidence > 0f) {
                                                            Text(
                                                                text = "${String.format("%.0f", confidence * 100)}%",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(end = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showAddToolDialog = false }) {
                                                Text("取消")
                                            }
                                        }
                                    )
                                }
                            } else {
                                // 总数量对比模式：显示总数输入框
                                OutlinedTextField(
                                    value = manualCount.toString(),
                                    onValueChange = { value ->
                                        manualCount = value.toIntOrNull() ?: 0
                                    },
                                    label = { Text("工具总数") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // 更新识别结果
                                if (comparisonMode == "specific_tools") {
                                    val newToolsList = manualToolCounts.map { entry ->
                                        "${entry.key}*${entry.value}"
                                    }
                                    if (newToolsList.isNotEmpty()) {
                                        toolsJson = OnnxToolDetectionService.toolsToJson(newToolsList)
                                        detectedCount = manualToolCounts.values.sum()
                                    } else {
                                        // 如果没有工具，设置为0
                                        toolsJson = OnnxToolDetectionService.toolCountToJson(0)
                                        detectedCount = 0
                                    }
                                } else {
                                    detectedCount = manualCount
                                    toolsJson = OnnxToolDetectionService.toolCountToJson(manualCount)
                                }
                                showManualAdjustDialog = false
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualAdjustDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
            }
            
            // 提交按钮（固定在底部）
            Button(
                onClick = {
                    // 验证作业组是否已选择
                    val isWorkGroupValid = workGroup.isNotBlank() && workGroup != "请选择"
                    workGroupError = !isWorkGroupValid
                    
                    // 验证作业地点是否已填写
                    val isWorkLocationValid = workLocation.isNotBlank()
                    workLocationError = !isWorkLocationValid
                    
                    if (isWorkGroupValid && isWorkLocationValid && imagePath != null && !isDetecting) {
                        // 保存记录（工具已经识别完成）
                        // 简化逻辑：在"具体工具数量对比"模式下，如果toolsJson存在且actualCount>0，直接保存
                        val currentToolsJson = toolsJson // 使用局部变量避免智能转换问题
                        DetectionLogger.log(DetectionLogger.Level.INFO, "保存记录 - 开始保存 - comparisonMode: $comparisonMode, currentToolsJson: $currentToolsJson, actualCount: $actualCount, detectedCount: $detectedCount")
                        val recordToolsJson = if (currentToolsJson != null && currentToolsJson.isNotBlank() && actualCount > 0) {
                            // 有toolsJson且actualCount>0，直接使用（识别服务已经返回了正确格式）
                            // 在"具体工具数量对比"模式下，确保toolsJson是工具列表格式
                            if (comparisonMode == "specific_tools") {
                                // 验证是否为工具列表格式
                                try {
                                    val testList = OnnxToolDetectionService.parseToolsJson(currentToolsJson)
                                    if (testList.isNotEmpty()) {
                                        DetectionLogger.log(DetectionLogger.Level.INFO, "保存记录 - 工具列表格式有效，直接使用: $currentToolsJson")
                                        currentToolsJson
                                    } else {
                                        // 不是工具列表格式，尝试从detectedToolsList重新生成
                                        DetectionLogger.log(DetectionLogger.Level.WARN, "保存记录 - toolsJson不是工具列表格式，尝试从detectedToolsList重新生成")
                                        if (detectedToolsList.isNotEmpty()) {
                                            val regenerated = OnnxToolDetectionService.toolsToJson(detectedToolsList)
                                            DetectionLogger.log(DetectionLogger.Level.INFO, "保存记录 - 重新生成工具列表: $regenerated")
                                            regenerated
                                        } else {
                                            DetectionLogger.log(DetectionLogger.Level.ERROR, "保存记录 - detectedToolsList为空，无法重新生成")
                                            currentToolsJson // 即使格式不对，也先保存，避免丢失数据
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 解析失败，尝试从detectedToolsList重新生成
                                    DetectionLogger.log(DetectionLogger.Level.WARN, "保存记录 - 解析toolsJson失败: ${e.message}，尝试从detectedToolsList重新生成")
                                    if (detectedToolsList.isNotEmpty()) {
                                        val regenerated = OnnxToolDetectionService.toolsToJson(detectedToolsList)
                                        DetectionLogger.log(DetectionLogger.Level.INFO, "保存记录 - 重新生成工具列表: $regenerated")
                                        regenerated
                                    } else {
                                        DetectionLogger.log(DetectionLogger.Level.ERROR, "保存记录 - detectedToolsList为空，无法重新生成")
                                        currentToolsJson // 即使格式不对，也先保存，避免丢失数据
                                    }
                                }
                            } else {
                                // 总数量对比模式，直接使用
                                DetectionLogger.log(DetectionLogger.Level.INFO, "保存记录 - 总数量对比模式，直接使用: $currentToolsJson")
                                currentToolsJson
                            }
                        } else {
                            // 如果没有toolsJson或actualCount=0，根据对比模式生成
                            DetectionLogger.log(DetectionLogger.Level.WARN, "toolsJson为空或actualCount=0，重新生成 - toolsJson: $currentToolsJson, actualCount: $actualCount, detectedToolsList: $detectedToolsList")
                            if (comparisonMode == "specific_tools") {
                                // 具体工具数量对比模式：优先从detectedToolsList生成
                                if (detectedToolsList.isNotEmpty()) {
                                    OnnxToolDetectionService.toolsToJson(detectedToolsList)
                                } else {
                                    // 如果没有工具列表，使用数量格式（向后兼容）
                                    OnnxToolDetectionService.toolCountToJson(actualCount)
                                }
                            } else {
                                // 总数量对比模式：返回数量格式
                                OnnxToolDetectionService.toolCountToJson(actualCount)
                            }
                        }
                        
                        DetectionLogger.log(DetectionLogger.Level.INFO, "最终保存 - comparisonMode: $comparisonMode, recordToolsJson: $recordToolsJson, actualCount: $actualCount, detectedCount: $detectedCount")
                        
                        if (recordType == RecordType.BEFORE_WORK) {
                            // 作业前记录，直接保存
                            val record = ToolRecord(
                                recordType = recordType,
                                timestamp = System.currentTimeMillis(),
                                imagePath = imagePath!!,
                                toolsJson = recordToolsJson,
                                workGroup = workGroup,
                                workLocation = workLocation
                            )
                            viewModel.insertRecord(record)
                            showSuccessDialog = true
                        } else {
                            // 作业后记录，需要关联作业前记录
                            viewModel.insertAfterWorkRecord(
                                imagePath = imagePath!!,
                                toolsJson = recordToolsJson,
                                workGroup = workGroup,
                                workLocation = workLocation
                            ) {
                                showSuccessDialog = true
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = workGroup.isNotBlank() && 
                         workGroup != "请选择" &&
                         workLocation.isNotBlank() && 
                         imagePath != null &&
                         !isDetecting &&
                         actualCount > 0 &&
                         detectedCount > 0
            ) {
                Text("提交保存", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
        
        // 成功提示对话框（高端版）
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = { 
                    Text(
                        "提交成功",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = { Text("工具清点记录已保存", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSuccessDialog = false
                            onSaveComplete()
                        }
                    ) {
                        Text("确定")
                    }
                },
                shape = RoundedCornerShape(24.dp)
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

    // 可识别物件类别弹窗
    if (showCategoriesDialog) {
        CategoriesDialog(onDismiss = { showCategoriesDialog = false })
    }
}

/**
 * 全屏图片查看Dialog（不使用导航，避免状态丢失）
 */
@Composable
fun FullScreenImageDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(imageFile)
                            .build()
                    ),
                    contentDescription = "全屏图片",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // 单击退出全屏
                                    onDismiss()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }
            
            // 缩放提示
            if (scale > 1f) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "缩放: ${String.format("%.1f", scale)}x",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 可识别物件类别弹窗
 * 展示模型能识别的全部工具类别
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesDialog(onDismiss: () -> Unit) {
    val categories = listOf(
        "扳手", "钳子", "内六角套装", "钥匙", "活口扳手", "螺丝刀", "毛刷",
        "套筒扳手", "卷尺", "风机", "移动电源", "内六角", "定标仪", "计时器",
        "口哨", "对讲机", "防护旗", "手灯", "黑体2", "万用表", "磁钢尺",
        "喷壶", "收纳箱", "标签", "探头1", "电子标签", "风机2", "黑体"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "可识别物件（${categories.size}类）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEachIndexed { index, name ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

