package com.watermark.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 照片存储管理器
 * 所有照片写入应用内部专属目录 /files/WatermarkCamera/
 * 与系统相册物理隔离，防止误操作其他照片
 */
class PhotoRepository(private val context: Context) {

    private val photoDir: File
        get() = File(context.filesDir, Config.PHOTO_DIR).also { it.mkdirs() }

    /**
     * 保存水印照片到内部存储
     * @return 保存后的文件
     */
    fun savePhoto(bitmap: Bitmap, fields: WatermarkFields): PhotoRecord {
        val timestamp = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date(timestamp))
        val fileName = "WMC_${timeStr}.jpg"
        val file = File(photoDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return PhotoRecord(
            fileName = fileName,
            filePath = file.absolutePath,
            takenAt = timestamp,
            projectName = fields.projectName,
            areaName = fields.constructionArea,
            uploadStatus = UploadStatus.PENDING
        )
    }

    /**
     * 获取所有照片记录
     */
    fun getAllPhotos(): List<PhotoRecord> {
        return photoDir.listFiles()
            ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                PhotoRecord(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    takenAt = file.lastModified(),
                    projectName = "",
                    areaName = "",
                    uploadStatus = UploadStatus.PENDING
                )
            } ?: emptyList()
    }

    /**
     * 按文件名加载照片 Bitmap
     */
    fun loadBitmap(fileName: String): Bitmap? {
        val file = File(photoDir, fileName)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    /**
     * 删除照片
     */
    fun deletePhoto(fileName: String): Boolean {
        val file = File(photoDir, fileName)
        // 软删除：移动到应用内部回收站目录
        val trashDir = File(context.filesDir, "${Config.PHOTO_DIR}_trash")
        trashDir.mkdirs()
        val trashFile = File(trashDir, "${System.currentTimeMillis()}_$fileName")
        return file.renameTo(trashFile)
    }

    /**
     * 获取照片文件
     */
    fun getPhotoFile(fileName: String): File = File(photoDir, fileName)

    /**
     * 照片目录路径
     */
    fun getPhotoDirPath(): String = photoDir.absolutePath

    /**
     * 照片总数
     */
    fun getPhotoCount(): Int = photoDir.listFiles()?.size ?: 0
}
