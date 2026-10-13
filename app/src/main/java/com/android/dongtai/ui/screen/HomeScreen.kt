package com.android.dongtai.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.dongtai.R
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * 首页界面 — 卡片式布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasUncompletedBeforeRecord: Boolean,
    onBeforeWorkClick: () -> Unit,
    onAfterWorkClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogViewerClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.android.dongtai.utils.UpdateManager.UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedMB by remember { mutableStateOf("0") }
    var totalMB by remember { mutableStateOf("0") }
    var downloadDone by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // 启动时检查更新
    LaunchedEffect(Unit) {
        val info = com.android.dongtai.utils.UpdateManager.checkUpdate(context)
        if (info != null) {
            updateInfo = info
            showUpdateDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        HomeContent(
            paddingValues = paddingValues,
            hasUncompletedBeforeRecord = hasUncompletedBeforeRecord,
            onBeforeWorkClick = onBeforeWorkClick,
            onAfterWorkClick = onAfterWorkClick,
            onHistoryClick = onHistoryClick,
            onLogViewerClick = onLogViewerClick
        )
    }

    // 更新提示对话框（带下载进度）
    if (showUpdateDialog && updateInfo != null) {
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = {
                if (!updateInfo!!.forceUpdate && !isDownloading) showUpdateDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    if (downloadError != null) "下载失败"
                    else if (downloadDone) "下载完成"
                    else if (isDownloading) "正在下载..."
                    else "发现新版本 v${updateInfo!!.versionName}"
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (downloadError != null) {
                        Text(
                            text = downloadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (!isDownloading && !downloadDone) {
                        // 更新信息
                        if (updateInfo!!.changelog.isNotEmpty()) {
                            Text(
                                text = updateInfo!!.changelog,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "安装包大小: ${updateInfo!!.apkSize / 1024 / 1024}MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        // 下载进度
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${downloadedMB}MB / ${totalMB}MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (downloadDone) {
                        Button(onClick = {
                            com.android.dongtai.utils.UpdateManager.installApk(context)
                        }) { Text("立即安装") }
                    } else if (!isDownloading) {
                        Button(onClick = {
                            downloadError = null
                            isDownloading = true
                            scope.launch {
                                val success = com.android.dongtai.utils.UpdateManager.downloadApkWithProgress(context) { dl, total ->
                                    if (total > 0) {
                                        downloadProgress = dl.toFloat() / total.toFloat()
                                        downloadedMB = "${dl / 1024 / 1024}"
                                        totalMB = "${total / 1024 / 1024}"
                                    }
                                }
                                isDownloading = false
                                if (success) {
                                    downloadDone = true
                                } else {
                                    downloadError = "下载失败，请检查网络"
                                }
                            }
                        }) {
                            Text(if (downloadError != null) "重试" else "立即更新")
                        }
                    }
                    // 用浏览器下载：应用内安装失败或旧版本用户可用此方式手动安装
                    TextButton(onClick = {
                        com.android.dongtai.utils.UpdateManager.openUpdateDownloadInBrowser(context)
                    }) {
                        Text("用浏览器下载")
                    }
                }
            },
            dismissButton = {
                if (!isDownloading && !downloadDone && !updateInfo!!.forceUpdate) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后再说")
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeContent(
    paddingValues: PaddingValues,
    hasUncompletedBeforeRecord: Boolean,
    onBeforeWorkClick: () -> Unit,
    onAfterWorkClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogViewerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 状态提示卡片
        StatusBanner(hasUncompletedBeforeRecord)

        // 主操作区：作业前 + 作业后 并排
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.before_work),
                subtitle = "拍照识别工具",
                gradientColors = listOf(
                    Color(0xFF2952B3),
                    Color(0xFF3D6FD9)
                ),
                onClick = onBeforeWorkClick
            )

            ActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Checklist,
                title = stringResource(R.string.after_work),
                subtitle = if (hasUncompletedBeforeRecord) "对比清点工具" else "请先作业前清点",
                gradientColors = if (hasUncompletedBeforeRecord) {
                    listOf(Color(0xFFE66800), Color(0xFFFF7D1A))
                } else {
                    listOf(Color(0xFF8E8E8E), Color(0xFFAAAAAA))
                },
                enabled = hasUncompletedBeforeRecord,
                onClick = onAfterWorkClick
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 次要操作区
        SecondaryActionRow(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.history),
            subtitle = "查看清点记录",
            onClick = onHistoryClick
        )

        SecondaryActionRow(
            icon = Icons.Outlined.Description,
            title = "检测日志",
            subtitle = "查看AI识别详情",
            onClick = onLogViewerClick
        )

        Spacer(modifier = Modifier.weight(1f))

        // 底部版本信息（动态读取）
        val context = LocalContext.current
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1"
            } catch (_: Exception) { "2.1" }
        }
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * 状态提示横幅
 */
@Composable
private fun StatusBanner(hasUncompletedBeforeRecord: Boolean) {
    val containerColor = if (hasUncompletedBeforeRecord) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (hasUncompletedBeforeRecord) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hasUncompletedBeforeRecord) 
                    Icons.Filled.PendingActions else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (hasUncompletedBeforeRecord) 
                    "有未完成的作业，请进行作业后清点" else "当前无进行中的作业",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 主操作卡片（渐变背景）
 */
@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(160.dp)
            .shadow(
                elevation = if (enabled) 6.dp else 2.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = gradientColors.first().copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        onClick = { if (enabled) onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(gradientColors),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 次要操作行
 */
@Composable
private fun SecondaryActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
