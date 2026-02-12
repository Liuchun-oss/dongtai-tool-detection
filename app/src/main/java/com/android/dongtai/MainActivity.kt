package com.android.dongtai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.android.dongtai.navigation.NavGraph
import com.android.dongtai.ui.theme.DongtaiTheme
import com.android.dongtai.utils.DetectionLogger
import com.android.dongtai.utils.AutoCleanupManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化检测日志
        DetectionLogger.init(this)
        DetectionLogger.cleanOldLogs(this)
        
        // 执行自动清理任务
        AutoCleanupManager.performCleanup(this)
        
        enableEdgeToEdge()
        setContent {
            DongtaiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}