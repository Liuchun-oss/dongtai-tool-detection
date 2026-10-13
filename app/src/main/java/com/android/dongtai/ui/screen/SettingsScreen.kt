package com.android.dongtai.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.dongtai.R
import com.android.dongtai.utils.SettingsManager

/**
 * 设置界面 - 简约大气高端设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // 检测设置
    var confidenceThreshold by remember { mutableStateOf(SettingsManager.getConfidenceThreshold(context)) }
    var iouThreshold by remember { mutableStateOf(SettingsManager.getIouThreshold(context)) }
    var blueRegionOptimize by remember { mutableStateOf(SettingsManager.getBlueRegionOptimize(context)) }
    var alternativeMatchThreshold by remember { mutableStateOf(SettingsManager.getAlternativeMatchThreshold(context)) }
    
    // 功能开关
    var autoRecognition by remember { mutableStateOf(SettingsManager.getAutoRecognition(context)) }
    var showDetectionBox by remember { mutableStateOf(SettingsManager.getShowDetectionBox(context)) }
    var enableNnapi by remember { mutableStateOf(SettingsManager.getEnableNnapi(context)) }
    
    // 界面设置
    var themeMode by remember { mutableStateOf(SettingsManager.getThemeMode(context)) }
    var fontSize by remember { mutableStateOf(SettingsManager.getFontSize(context)) }
    var comparisonMode by remember { mutableStateOf(SettingsManager.getComparisonMode(context)) }
    
    // 其他设置
    var showResetDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text(
                            "设置",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "自定义检测参数与界面偏好",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 对比方式 - 放在最上面
            SettingsCard(
                title = "对比方式",
                subtitle = "选择工具清点的对比策略",
                icon = Icons.Default.CompareArrows,
                iconColor = MaterialTheme.colorScheme.tertiary
            ) {
                ModernDropdown(
                    label = "对比模式",
                    options = listOf("总数量对比", "具体工具数量对比"),
                    selectedIndex = when (comparisonMode) {
                        "total_count" -> 0
                        "specific_tools" -> 1
                        else -> 0
                    },
                    onSelectionChange = { index ->
                        comparisonMode = when (index) {
                            0 -> "total_count"
                            1 -> "specific_tools"
                            else -> "total_count"
                        }
                        SettingsManager.setComparisonMode(context, comparisonMode)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 核心设置 - 检测参数
            SettingsCard(
                title = "检测参数",
                subtitle = "调整AI识别的灵敏度与精度",
                icon = Icons.Default.Tune,
                iconColor = MaterialTheme.colorScheme.primary
            ) {
                ModernSlider(
                    label = "置信度阈值",
                    value = confidenceThreshold,
                    onValueChange = { 
                        confidenceThreshold = it
                        SettingsManager.setConfidenceThreshold(context, it)
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    valueDisplay = "${(confidenceThreshold * 100).toInt()}%"
                )
                
                ModernSlider(
                    label = "IOU阈值",
                    value = iouThreshold,
                    onValueChange = { 
                        iouThreshold = it
                        SettingsManager.setIouThreshold(context, it)
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    valueDisplay = "${(iouThreshold * 100).toInt()}%"
                )
                
                ModernSwitch(
                    label = "蓝色区域检测优化",
                    description = "启用后大幅提升识别速度",
                    checked = blueRegionOptimize,
                    onCheckedChange = { 
                        blueRegionOptimize = it
                        SettingsManager.setBlueRegionOptimize(context, it)
                    },
                    icon = Icons.Default.Speed
                )
                
                ModernSlider(
                    label = "备选匹配阈值",
                    value = alternativeMatchThreshold,
                    onValueChange = { 
                        alternativeMatchThreshold = it
                        SettingsManager.setAlternativeMatchThreshold(context, it)
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    valueDisplay = "${(alternativeMatchThreshold * 100).toInt()}%"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 功能设置
            SettingsCard(
                title = "功能设置",
                subtitle = "控制识别行为与显示选项",
                icon = Icons.Default.Settings,
                iconColor = MaterialTheme.colorScheme.secondary
            ) {
                ModernSwitch(
                    label = "自动识别",
                    description = "拍摄后自动开始识别",
                    checked = autoRecognition,
                    onCheckedChange = { 
                        autoRecognition = it
                        SettingsManager.setAutoRecognition(context, it)
                    },
                    icon = Icons.Default.AutoAwesome
                )
                
                ModernSwitch(
                    label = "显示检测框",
                    checked = showDetectionBox,
                    onCheckedChange = { 
                        showDetectionBox = it
                        SettingsManager.setShowDetectionBox(context, it)
                    },
                    icon = Icons.Default.CropFree
                )
                
                ModernSwitch(
                    label = "NNAPI加速",
                    description = "使用硬件加速提升性能",
                    checked = enableNnapi,
                    onCheckedChange = { 
                        enableNnapi = it
                        SettingsManager.setEnableNnapi(context, it)
                    },
                    icon = Icons.Default.Speed
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 界面设置
            SettingsCard(
                title = "界面设置",
                subtitle = "个性化外观与显示",
                icon = Icons.Default.Palette,
                iconColor = MaterialTheme.colorScheme.primary
            ) {
                ModernDropdown(
                    label = "主题模式",
                    options = listOf("跟随系统", "浅色", "深色"),
                    selectedIndex = when (themeMode) {
                        "system" -> 0
                        "light" -> 1
                        "dark" -> 2
                        else -> 0
                    },
                    onSelectionChange = { index ->
                        themeMode = when (index) {
                            0 -> "system"
                            1 -> "light"
                            2 -> "dark"
                            else -> "system"
                        }
                        SettingsManager.setThemeMode(context, themeMode)
                    }
                )
                
                ModernDropdown(
                    label = "字体大小",
                    options = listOf("小", "正常", "大", "超大"),
                    selectedIndex = when (fontSize) {
                        "small" -> 0
                        "normal" -> 1
                        "large" -> 2
                        "extra_large" -> 3
                        else -> 1
                    },
                    onSelectionChange = { index ->
                        fontSize = when (index) {
                            0 -> "small"
                            1 -> "normal"
                            2 -> "large"
                            3 -> "extra_large"
                            else -> "normal"
                        }
                        SettingsManager.setFontSize(context, fontSize)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 重置设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "重置所有设置",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // 重置确认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { 
                Text(
                    "重置设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { 
                Text(
                    "确定要重置所有设置吗？此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SettingsManager.resetAllSettings(context)
                        showResetDialog = false
                        // 重新加载设置
                        confidenceThreshold = SettingsManager.getConfidenceThreshold(context)
                        iouThreshold = SettingsManager.getIouThreshold(context)
                        blueRegionOptimize = SettingsManager.getBlueRegionOptimize(context)
                        alternativeMatchThreshold = SettingsManager.getAlternativeMatchThreshold(context)
                        autoRecognition = SettingsManager.getAutoRecognition(context)
                        showDetectionBox = SettingsManager.getShowDetectionBox(context)
                        enableNnapi = SettingsManager.getEnableNnapi(context)
                        themeMode = SettingsManager.getThemeMode(context)
                        fontSize = SettingsManager.getFontSize(context)
                        comparisonMode = SettingsManager.getComparisonMode(context)
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/**
 * 设置卡片容器（高端版）
 */
@Composable
fun SettingsCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标带渐变背景
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = iconColor
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 内容
            Column(content = content)
        }
    }
}

/**
 * 现代化开关
 */
@Composable
fun ModernSwitch(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

/**
 * 现代化滑块
 */
@Composable
fun ModernSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = valueDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * 现代化下拉菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = options.getOrNull(selectedIndex) ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onSelectionChange(index)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = if (index == selectedIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    )
                }
            }
        }
    }
}


