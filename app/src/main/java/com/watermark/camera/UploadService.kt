package com.watermark.camera

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 照片上传前台服务
 * 支持自动/手动模式，失败自动重试，成功后自动清理
 */
class UploadService : Service() {

    companion object {
        private const val TAG = "UploadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "upload_channel"

        const val ACTION_UPLOAD = "com.watermark.camera.UPLOAD"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_DEVICE_HOST = "device_host"
        const val EXTRA_DEVICE_PORT = "device_port"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_AREA_NAME = "area_name"
        const val EXTRA_AUTO_MODE = "auto_mode"

        fun startUpload(
            context: Context,
            fileNames: List<String>,
            host: String,
            port: Int,
            projectName: String,
            areaName: String,
            autoMode: Boolean = false
        ) {
            val intent = Intent(context, UploadService::class.java).apply {
                action = ACTION_UPLOAD
                putExtra(EXTRA_FILE_NAMES, fileNames.toTypedArray())
                putExtra(EXTRA_DEVICE_HOST, host)
                putExtra(EXTRA_DEVICE_PORT, port)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_AREA_NAME, areaName)
                putExtra(EXTRA_AUTO_MODE, autoMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var photoRepo: PhotoRepository
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var uploadJob: Job? = null

    // 回调（可用 Flow/LiveData 替代）
    var onUploadProgress: ((fileName: String, status: UploadStatus) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        photoRepo = PhotoRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPLOAD) {
            val fileNames = intent.getStringArrayExtra(EXTRA_FILE_NAMES)?.toList() ?: emptyList()
            val host = intent.getStringExtra(EXTRA_DEVICE_HOST) ?: ""
            val port = intent.getIntExtra(EXTRA_DEVICE_PORT, 8080)
            val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: ""
            val areaName = intent.getStringExtra(EXTRA_AREA_NAME) ?: ""

            startForeground(NOTIFICATION_ID, buildNotification("准备上传..."))

            uploadJob?.cancel()
            uploadJob = serviceScope.launch {
                uploadFiles(fileNames, host, port, projectName, areaName)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        uploadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun uploadFiles(
        fileNames: List<String>,
        host: String,
        port: Int,
        projectName: String,
        areaName: String
    ) {
        val baseUrl = "http://$host:$port/upload"
        var successCount = 0
        var failCount = 0

        withContext(Dispatchers.Main) {
            updateNotification("上传中 (0/${fileNames.size})")
        }

        fileNames.forEachIndexed { index, fileName ->
            val file = photoRepo.getPhotoFile(fileName)
            if (!file.exists()) {
                Log.w(TAG, "文件不存在: $fileName")
                failCount++
                return@forEachIndexed
            }

            onUploadProgress?.invoke(fileName, UploadStatus.UPLOADING)

            var retryCount = 0
            var uploaded = false

            while (retryCount < Config.MAX_RETRY_COUNT && !uploaded) {
                try {
                    // 计算 SHA256
                    val sha256 = file.sha256()

                    // 构建 multipart 请求
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "photo", fileName,
                            file.asRequestBody("image/jpeg".toMediaType())
                        )
                        .addFormDataPart("info", JSONObject().apply {
                            put("project_name", projectName)
                            put("area_name", areaName)
                            put("sha256", sha256)
                        }.toString())
                        .build()

                    val request = Request.Builder()
                        .url(baseUrl)
                        .post(requestBody)
                        .build()

                    val response = withContext(Dispatchers.IO) {
                        client.newCall(request).execute()
                    }

                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.getString("status") == "ok") {
                            uploaded = true
                            successCount++

                            // 上传成功 → 清理本地文件
                            photoRepo.deletePhoto(fileName)
                            onUploadProgress?.invoke(fileName, UploadStatus.UPLOADED)

                            Log.d(TAG, "上传成功: $fileName -> ${json.getString("filename")}")
                        }
                    } else {
                        Log.w(TAG, "上传失败: $fileName, code=${response.code}")
                    }
                    response.close()

                } catch (e: Exception) {
                    Log.e(TAG, "上传异常: $fileName, ${e.message}")
                }

                if (!uploaded) {
                    retryCount++
                    if (retryCount < Config.MAX_RETRY_COUNT) {
                        delay(Config.RETRY_DELAYS[retryCount])
                    }
                }
            }

            if (!uploaded) {
                failCount++
                onUploadProgress?.invoke(fileName, UploadStatus.FAILED)
            }

            withContext(Dispatchers.Main) {
                updateNotification("上传中 (${index + 1}/${fileNames.size})")
            }
        }

        // 完成
        withContext(Dispatchers.Main) {
            if (failCount == 0) {
                updateNotification("上传完成: $successCount 张照片")
            } else {
                updateNotification("上传完成: 成功 $successCount, 失败 $failCount")
            }
        }

        delay(3000)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "照片上传",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "水印照片上传状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("水印相机")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}

/**
 * 计算文件 SHA256
 */
fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    this.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
