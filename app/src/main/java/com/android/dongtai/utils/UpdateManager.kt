package com.android.dongtai.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val CHECK_URL = "http://154.12.25.209:5002/update/check"
    private const val DOWNLOAD_URL = "http://154.12.25.209:5002/update/download"
    private const val TIMEOUT = 30000  // 30秒超时

    /** 供「用浏览器下载」使用，旧版本用户也可用此链接手动下载安装 */
    fun getUpdateDownloadUrl(): String = DOWNLOAD_URL

    /** 在浏览器中打开更新包下载页，下载后由用户手动安装（适用于应用内安装失败或旧版本） */
    fun openUpdateDownloadInBrowser(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getUpdateDownloadUrl())).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "用浏览器下载安装包"))
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val changelog: String,
        val forceUpdate: Boolean,
        val hasApk: Boolean,
        val apkSize: Long
    )

    suspend fun checkUpdate(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(CHECK_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = TIMEOUT
                conn.readTimeout = TIMEOUT
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(response)
                val remoteCode = json.getInt("version_code")
                val localCode = getLocalVersionCode(context)
                if (remoteCode > localCode && json.getBoolean("has_apk")) {
                    UpdateInfo(
                        versionCode = remoteCode,
                        versionName = json.getString("version_name"),
                        changelog = json.optString("changelog", ""),
                        forceUpdate = json.optBoolean("force_update", false),
                        hasApk = true,
                        apkSize = json.optLong("apk_size", 0)
                    )
                } else null
            } catch (_: Exception) { null }
        }
    }

    /**
     * 下载 APK 到应用专属目录（避免 Android 10+ 公共目录权限问题），带进度回调
     */
    suspend fun downloadApkWithProgress(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = getApkFile(context)
                if (file.exists()) file.delete()

                val url = URL(DOWNLOAD_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 60000  // 1分钟连接超时
                conn.readTimeout = 300000  // 5分钟读取超时
                conn.setRequestProperty("User-Agent", "Android Dongtai/2.3")
                conn.connect()
                
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    return@withContext false
                }

                val total = conn.contentLength.toLong()
                var downloaded = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            onProgress(downloaded, total)
                        }
                    }
                }
                conn.disconnect()
                true
            } catch (_: Exception) { false }
        }
    }

    fun installApk(context: Context) {
        val file = getApkFile(context)
        if (!file.exists()) return
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    /**
     * 使用应用专属缓存目录，无需存储权限，避免 Android 10+ 分区存储导致的 MediaProvider 拒绝访问。
     */
    private fun getApkFile(context: Context): File {
        val dir = context.externalCacheDir ?: context.cacheDir
        return File(dir, "dongtai_update.apk")
    }

    private fun getLocalVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) { 0 }
    }
}