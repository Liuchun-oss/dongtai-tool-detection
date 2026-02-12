package com.android.dongtai.ui.screen

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
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
import com.android.dongtai.data.entity.WorkRecord
import com.android.dongtai.service.OnnxToolDetectionService
import com.android.dongtai.service.ShareImageGenerator
import com.android.dongtai.ui.viewmodel.ToolRecordViewModel
import com.android.dongtai.utils.SettingsManager
import com.android.dongtai.utils.DetectionLogger
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 记录详情界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    workRecord: WorkRecord,
    viewModel: ToolRecordViewModel,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onImageClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showFullScreenImage by remember { mutableStateOf(false) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val comparisonMode = SettingsManager.getComparisonMode(context)
    
    // 检查工具JSON格式：如果是列表格式（以[开头），说明是具体工具数量对比模式的数据
    val isListFormat = workRecord.beforeRecord.toolsJson.trim().startsWith("[") || 
                       workRecord.afterRecord?.toolsJson?.trim()?.startsWith("[") == true
    
    val beforeCount = remember(workRecord.beforeRecord.toolsJson, comparisonMode, isListFormat) {
        if (comparisonMode == "specific_tools" || isListFormat) {
            // 具体工具数量对比模式或数据是列表格式：从工具列表计算数量
            val toolsList = OnnxToolDetectionService.parseToolsJson(workRecord.beforeRecord.toolsJson)
            DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - 计算beforeCount - 列表格式 - toolsList: $toolsList")
            val count = toolsList.sumOf { tool ->
                val parts = tool.split("*")
                if (parts.size == 2) {
                    // 移除状态信息（如果有）
                    val countPart = parts[1].trim().split(" ")[0]
                    countPart.toIntOrNull() ?: 0
                } else {
                    0
                }
            }
            DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - beforeCount: $count")
            count
        } else {
            // 总数量对比模式：使用parseToolCount
            val count = OnnxToolDetectionService.parseToolCount(workRecord.beforeRecord.toolsJson)
            DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - beforeCount (parseToolCount): $count")
            count
        }
    }
    
    val afterCount = remember(workRecord.afterRecord?.toolsJson, comparisonMode, isListFormat) {
        workRecord.afterRecord?.let { afterRecord ->
            if (comparisonMode == "specific_tools" || isListFormat) {
                // 具体工具数量对比模式或数据是列表格式：从工具列表计算数量
                val toolsList = OnnxToolDetectionService.parseToolsJson(afterRecord.toolsJson)
                DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - 计算afterCount - 列表格式 - toolsList: $toolsList")
                val count = toolsList.sumOf { tool ->
                    val parts = tool.split("*")
                    if (parts.size == 2) {
                        // 移除状态信息（如果有）
                        val countPart = parts[1].trim().split(" ")[0]
                        countPart.toIntOrNull() ?: 0
                    } else {
                        0
                    }
                }
                DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - afterCount: $count")
                count
            } else {
                // 总数量对比模式：使用parseToolCount
                val count = OnnxToolDetectionService.parseToolCount(afterRecord.toolsJson)
                DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - afterCount (parseToolCount): $count")
                count
            }
        } ?: 0
    }
    
    val missingTools = remember(workRecord.beforeRecord.toolsJson, workRecord.afterRecord?.toolsJson, comparisonMode) {
        if (workRecord.afterRecord != null) {
            // 根据设置选择对比方式
            DetectionLogger.log(DetectionLogger.Level.INFO, "RecordDetailScreen - 计算缺失工具 - comparisonMode: $comparisonMode, beforeToolsJson: ${workRecord.beforeRecord.toolsJson}, afterToolsJson: ${workRecord.afterRecord.toolsJson}")
            OnnxToolDetectionService.compareToolsByMode(
                context,
                workRecord.beforeRecord.toolsJson,
                workRecord.afterRecord.toolsJson
            )
        } else {
            emptyList()
        }
    }
    
    // 图片列表（作业前和作业后）
    val imagePaths = remember {
        if (workRecord.afterRecord != null) {
            listOf(workRecord.beforeRecord.imagePath, workRecord.afterRecord.imagePath)
        } else {
            listOf(workRecord.beforeRecord.imagePath)
        }
    }
    
    val pagerState = rememberPagerState(pageCount = { imagePaths.size }, initialPage = 0)
    
    var isGeneratingShareImage by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    // 分享功能
    fun shareReport() {
        if (workRecord.afterRecord == null) {
            // 如果还没有作业后记录，提示用户
            showShareDialog = true
            return
        }
        
        scope.launch {
            isGeneratingShareImage = true
            try {
                // 生成分享图片
                val shareImagePath = ShareImageGenerator.generateShareImage(
                    context = context,
                    workRecord = workRecord,
                    beforeCount = beforeCount,
                    afterCount = afterCount,
                    missingTools = missingTools
                )
                
                // 分享图片
                val shareFile = File(shareImagePath)
                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_TEXT, "工具清点对比报告")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, "分享到"))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isGeneratingShareImage = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { shareReport() },
                        enabled = !isGeneratingShareImage
                    ) {
                        if (isGeneratingShareImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.share))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 图片滑动区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val imagePath = imagePaths[page]
                    // 优先显示带标注的图片
                    val annotatedPath = imagePath.replace(".jpg", "_annotated.jpg")
                    val annotatedFile = File(annotatedPath)
                    val imageFile = if (annotatedFile.exists()) annotatedFile else File(imagePath)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // 点击区域打开全屏查看（使用Dialog）
                                val finalImageFile = if (annotatedFile.exists()) annotatedFile else File(imagePath)
                                if (finalImageFile.exists()) {
                                    fullScreenImagePath = finalImageFile.absolutePath
                                    showFullScreenImage = true
                                }
                            }
                    ) {
                        if (imageFile.exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(imageFile)
                                        .build()
                                ),
                                contentDescription = "作业${if (page == 0) "前" else "后"}照片",
                                modifier = Modifier.fillMaxSize(),
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
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("图片不存在")
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
                
                // 左右切换按钮（仅在有多张图片时显示）
                if (imagePaths.size > 1) {
                    // 左箭头
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "上一张",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // 右箭头
                    if (pagerState.currentPage < imagePaths.size - 1) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "下一张",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            // 总结报告区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (workRecord.afterRecord != null && missingTools.isEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (workRecord.afterRecord != null && missingTools.isNotEmpty()) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 标题
                    Text(
                        text = "清点总结报告",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Divider()
                    
                    // 作业信息
                    if (workRecord.beforeRecord.workGroup.isNotBlank()) {
                        Text(
                            text = "作业组：${workRecord.beforeRecord.workGroup}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (workRecord.beforeRecord.workLocation.isNotBlank()) {
                        Text(
                            text = "作业地点：${workRecord.beforeRecord.workLocation}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Divider()
                    
                    // 时间信息
                    Text(
                        text = "作业前时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(workRecord.beforeRecord.timestamp))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (workRecord.afterRecord != null) {
                        Text(
                            text = "作业后时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(workRecord.afterRecord.timestamp))}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "作业后时间：作业中，未结束",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    Divider()
                    
                    // 根据对比模式显示不同的内容
                    if (comparisonMode == "specific_tools" || isListFormat) {
                        // 具体工具数量对比模式：显示具体工具列表（并排显示）
                        val beforeToolsList = OnnxToolDetectionService.parseToolsJson(workRecord.beforeRecord.toolsJson)
                        val afterToolsList = workRecord.afterRecord?.let { 
                            OnnxToolDetectionService.parseToolsJson(it.toolsJson) 
                        } ?: emptyList()
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 作业前工具列表
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "作业前工具：",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                beforeToolsList.forEach { tool ->
                                    // 移除状态信息（如果有）
                                    val toolText = tool.split(" ")[0]
                                    Text(
                                        text = "  • $toolText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            
                            // 作业后工具列表
                            if (workRecord.afterRecord != null) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "作业后工具：",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    afterToolsList.forEach { tool ->
                                        // 检查是否包含状态信息
                                        val hasStatus = tool.contains("已存在") || tool.contains("缺失") || tool.contains("未找到")
                                        val textColor = if (hasStatus) {
                                            if (tool.contains("已存在")) {
                                                Color(0xFF4CAF50) // 绿色
                                            } else {
                                                MaterialTheme.colorScheme.error // 红色
                                            }
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            text = "  • $tool",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textColor,
                                            fontWeight = if (hasStatus) FontWeight.Medium else FontWeight.Normal,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // 总数量对比模式：显示总数（并排显示）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "作业前：识别到共 $beforeCount 件工具",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (workRecord.afterRecord != null) {
                                Text(
                                    text = "作业后：识别到共 $afterCount 件工具",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    
                    if (workRecord.afterRecord != null) {
                        Divider()
                        
                        // 对比结果
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (missingTools.isEmpty()) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Warning
                                },
                                contentDescription = null,
                                tint = if (missingTools.isEmpty()) {
                                    Color(0xFF4CAF50)
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (missingTools.isEmpty()) {
                                    "✓ 工具清点完整，无缺失工具"
                                } else {
                                    // missingTools 格式为 ["发现缺失X个工具"]，直接显示即可
                                    missingTools.joinToString("、")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (missingTools.isEmpty()) {
                                    Color(0xFF4CAF50)
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    } else {
                        Divider()
                        Text(
                            text = "⏳ 作业尚未完成，等待作业后清点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        
        // 提示对话框（作业未完成时）
        if (showShareDialog) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                title = { Text("无法分享") },
                text = { Text("请先完成作业后清点，才能生成对比报告分享") },
                confirmButton = {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text("确定")
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
