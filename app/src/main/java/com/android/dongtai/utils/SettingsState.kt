package com.android.dongtai.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 设置状态管理
 * 用于在 Compose 中实时监听设置变化
 */
object SettingsState {
    
    /**
     * 记住主题模式设置，并在设置变化时自动更新
     */
    @Composable
    fun rememberThemeMode(): String {
        val context = LocalContext.current
        var themeMode by remember { mutableStateOf(SettingsManager.getThemeMode(context)) }
        
        DisposableEffect(Unit) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "theme_mode" || key == null) { // null 表示任何键变化
                    themeMode = SettingsManager.getThemeMode(context)
                }
            }
            
            val prefs = context.getSharedPreferences("dongtai_settings", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        
        return themeMode
    }
    
    /**
     * 记住字体大小设置，并在设置变化时自动更新
     */
    @Composable
    fun rememberFontSize(): String {
        val context = LocalContext.current
        var fontSize by remember { mutableStateOf(SettingsManager.getFontSize(context)) }
        
        DisposableEffect(Unit) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "font_size" || key == null) { // null 表示任何键变化
                    fontSize = SettingsManager.getFontSize(context)
                }
            }
            
            val prefs = context.getSharedPreferences("dongtai_settings", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        
        return fontSize
    }
    
    /**
     * 记住语言设置，并在设置变化时自动更新
     */
    @Composable
    fun rememberLanguage(): String {
        val context = LocalContext.current
        var language by remember { mutableStateOf(SettingsManager.getLanguage(context)) }
        
        DisposableEffect(Unit) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "language" || key == null) { // null 表示任何键变化
                    language = SettingsManager.getLanguage(context)
                }
            }
            
            val prefs = context.getSharedPreferences("dongtai_settings", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        
        return language
    }
}
