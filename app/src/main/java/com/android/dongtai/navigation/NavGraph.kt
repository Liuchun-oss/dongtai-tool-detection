package com.android.dongtai.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.dongtai.data.entity.RecordType
import com.android.dongtai.data.entity.ToolRecord
import com.android.dongtai.ui.screen.*
import com.android.dongtai.ui.viewmodel.ToolRecordViewModel
import kotlinx.coroutines.launch

/**
 * 导航路由
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object WorkInfo : Screen("work_info/{recordType}") {
        fun createRoute(recordType: RecordType) = "work_info/${recordType.name}"
    }
    object Camera : Screen("camera/{recordType}/{workGroup}/{workLocation}") {
        fun createRoute(recordType: RecordType, workGroup: String, workLocation: String) = 
            "camera/${recordType.name}/${workGroup}/${workLocation}"
    }
    object History : Screen("history")
    object RecordDetail : Screen("record_detail/{recordId}") {
        fun createRoute(recordId: Long) = "record_detail/$recordId"
    }
    object AfterWork : Screen("after_work/{beforeRecordId}") {
        fun createRoute(beforeRecordId: Long) = "after_work/$beforeRecordId"
    }
    object LogViewer : Screen("log_viewer")
    object Settings : Screen("settings")
    object FullScreenImage : Screen("full_screen_image/{imagePath}") {
        fun createRoute(imagePath: String) = "full_screen_image/${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
    }
}

/**
 * 导航图
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: ToolRecordViewModel = viewModel(
        factory = ToolRecordViewModelFactory(application)
    )
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            // 使用Flow监听状态变化
            val hasUncompletedBefore by viewModel.hasUncompletedBeforeRecord.collectAsState(initial = false)
            val uncompletedRecords by viewModel.uncompletedWorkRecords.collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()
            var showBeforeWorkDialog by remember { mutableStateOf(false) }
            var showAfterWorkDialog by remember { mutableStateOf(false) }
            var showSelectRecordDialog by remember { mutableStateOf(false) }
            
            HomeScreen(
                onSettingsClick = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != Screen.Settings.route) {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                },
                hasUncompletedBeforeRecord = hasUncompletedBefore,
                onBeforeWorkClick = {
                    // 直接查询最新数据，避免Flow状态延迟导致的判断错误
                    scope.launch {
                        val hasUncompleted = viewModel.hasUncompletedBeforeRecord()
                        if (hasUncompleted) {
                            // 如果有未完成的记录，显示确认对话框
                            showBeforeWorkDialog = true
                        } else {
                            // 先进入信息输入界面
                            val route = Screen.WorkInfo.createRoute(RecordType.BEFORE_WORK)
                            val currentRoute = navController.currentBackStackEntry?.destination?.route
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                },
                onAfterWorkClick = {
                    // 直接查询最新数据，避免Flow状态延迟导致的判断错误
                    scope.launch {
                        val latestBeforeRecord = viewModel.getUncompletedBeforeRecord()
                        if (latestBeforeRecord != null) {
                            // 确保导航操作在主线程执行，并检查是否已经在目标页面
                            val currentRoute = navController.currentBackStackEntry?.destination?.route
                            if (currentRoute != Screen.AfterWork.createRoute(latestBeforeRecord.id)) {
                                navController.navigate(Screen.AfterWork.createRoute(latestBeforeRecord.id)) {
                                    // 防止重复导航
                                    launchSingleTop = true
                                    // 如果已经在back stack中，弹出到该页面
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                            }
                        } else {
                            // 如果获取失败，显示提示
                            showAfterWorkDialog = true
                        }
                    }
                },
                onHistoryClick = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != Screen.History.route) {
                        navController.navigate(Screen.History.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onLogViewerClick = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != Screen.LogViewer.route) {
                        navController.navigate(Screen.LogViewer.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
            
            // 作业前清点确认对话框
            if (showBeforeWorkDialog) {
                AlertDialog(
                    onDismissRequest = { showBeforeWorkDialog = false },
                    title = { Text("提示") },
                    text = { 
                        Text("检测到有未完成的作业前记录。创建新的作业前记录将结束之前的记录，是否继续？")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBeforeWorkDialog = false
                                val route = Screen.WorkInfo.createRoute(RecordType.BEFORE_WORK)
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Text("继续")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showBeforeWorkDialog = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
            
            // 作业后清点提示对话框
            if (showAfterWorkDialog) {
                AlertDialog(
                    onDismissRequest = { showAfterWorkDialog = false },
                    title = { Text("提示") },
                    text = { 
                        Text("请先进行作业前清点，才能进行作业后清点。")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showAfterWorkDialog = false }
                        ) {
                            Text("确定")
                        }
                    }
                )
            }
            
            // 选择未完成记录对话框
            if (showSelectRecordDialog) {
                SelectWorkRecordDialog(
                    uncompletedRecords = uncompletedRecords,
                    onRecordSelected = { workRecord ->
                        showSelectRecordDialog = false
                        val route = Screen.AfterWork.createRoute(workRecord.getId())
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    },
                    onDismiss = { showSelectRecordDialog = false }
                )
            }
        }
        
        // 作业信息输入界面
        composable(
            route = Screen.WorkInfo.route,
            arguments = listOf(navArgument("recordType") { type = NavType.StringType })
        ) { backStackEntry ->
            val recordTypeStr = backStackEntry.arguments?.getString("recordType") ?: "BEFORE_WORK"
            val recordType = RecordType.valueOf(recordTypeStr)
            
            WorkInfoScreen(
                recordType = recordType,
                viewModel = viewModel,
                onSaveComplete = {
                    // 保存成功后返回首页
                    navController.popBackStack(navController.graph.startDestinationId, false)
                },
                onCancel = {
                    navController.popBackStack()
                },
                onImageClick = { imagePath ->
                    val route = Screen.FullScreenImage.createRoute(imagePath)
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        composable(
            route = Screen.Camera.route,
            arguments = listOf(
                navArgument("recordType") { type = NavType.StringType },
                navArgument("workGroup") { type = NavType.StringType },
                navArgument("workLocation") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recordTypeStr = backStackEntry.arguments?.getString("recordType") ?: "BEFORE_WORK"
            val recordType = RecordType.valueOf(recordTypeStr)
            val workGroup = backStackEntry.arguments?.getString("workGroup")?.let { 
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""
            val workLocation = backStackEntry.arguments?.getString("workLocation")?.let { 
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""
            
            var isProcessing by remember { mutableStateOf(false) }
            
            CameraScreen(
                recordType = recordType,
                onPhotoTaken = { imagePath ->
                    // 保存记录到数据库并自动识别工具
                    val record = ToolRecord(
                        recordType = recordType,
                        imagePath = imagePath,
                        toolsJson = "[]", // 将在识别后更新
                        workGroup = workGroup,
                        workLocation = workLocation
                    )
                    isProcessing = true
                    viewModel.insertRecordWithDetection(record) { updatedRecord ->
                        isProcessing = false
                        // 返回到首页
                        navController.popBackStack(navController.graph.startDestinationId, false)
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
            
            // 显示处理中的对话框
            if (isProcessing) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("正在识别工具...") },
                    text = { 
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("请稍候，正在分析图片中的工具")
                        }
                    },
                    confirmButton = { }
                )
            }
        }
        
        composable(Screen.History.route) {
            val workRecords by viewModel.allWorkRecords.collectAsState(initial = emptyList())
            
            HistoryScreen(
                workRecords = workRecords,
                onRecordClick = { workRecord ->
                    val route = Screen.RecordDetail.createRoute(workRecord.getId())
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                onDeleteRecord = { workRecord ->
                    viewModel.deleteWorkRecord(workRecord)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 作业后清点界面
        composable(
            route = Screen.AfterWork.route,
            arguments = listOf(navArgument("beforeRecordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val beforeRecordId = backStackEntry.arguments?.getLong("beforeRecordId") ?: 0L
            
            var workRecord by remember { mutableStateOf<com.android.dongtai.data.entity.WorkRecord?>(null) }
            
            LaunchedEffect(beforeRecordId) {
                if (beforeRecordId > 0) {
                    workRecord = viewModel.getWorkRecordById(beforeRecordId)
                } else {
                    // 如果ID无效，返回首页
                    navController.popBackStack(Screen.Home.route, false)
                }
            }
            
            workRecord?.let {
                AfterWorkScreen(
                    workRecord = it,
                    viewModel = viewModel,
                    onSaveComplete = {
                        navController.popBackStack(navController.graph.startDestinationId, false)
                    },
                    onCancel = {
                        navController.popBackStack()
                    },
                    onImageClick = { imagePath ->
                        navController.navigate(Screen.FullScreenImage.createRoute(imagePath))
                    }
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("加载记录中...", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        
        composable(
            route = Screen.RecordDetail.route,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            
            var workRecord by remember { mutableStateOf<com.android.dongtai.data.entity.WorkRecord?>(null) }
            
            LaunchedEffect(recordId) {
                if (recordId > 0) {
                    workRecord = viewModel.getWorkRecordById(recordId)
                } else {
                    // 如果ID无效，返回首页
                    navController.popBackStack(Screen.Home.route, false)
                }
            }
            
            workRecord?.let {
                RecordDetailScreen(
                    workRecord = it,
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onShare = { },
                    onImageClick = { imagePath ->
                        navController.navigate(Screen.FullScreenImage.createRoute(imagePath))
                    }
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("加载记录中...", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        
        // 日志查看界面
        composable(Screen.LogViewer.route) {
            LogViewerScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 设置界面
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 全屏图片查看界面
        composable(
            route = Screen.FullScreenImage.route,
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val imagePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
            
            FullScreenImageScreen(
                imagePath = imagePath,
                onClose = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// ViewModel Factory
class ToolRecordViewModelFactory(
    private val application: android.app.Application
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ToolRecordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ToolRecordViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
