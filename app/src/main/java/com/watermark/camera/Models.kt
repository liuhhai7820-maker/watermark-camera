package com.watermark.camera

/**
 * 水印字段数据
 */
data class WatermarkFields(
    val projectName: String = "",       // 项目名称
    val constructionUnit: String = "",  // 施工单位
    val constructionArea: String = "",  // 施工区域
    val constructionContent: String = "", // 施工内容
)

/**
 * 水印模板预设
 */
data class WatermarkTemplate(
    val id: Long = 0,
    val name: String,
    val fields: WatermarkFields,
)

/**
 * 照片记录（用于本地数据库）
 */
data class PhotoRecord(
    val id: Long = 0,
    val fileName: String,           // 文件名
    val filePath: String,           // 本地绝对路径
    val takenAt: Long,              // 拍摄时间戳
    val projectName: String,        // 项目名称（用于展示）
    val areaName: String,           // 施工区域（用于展示）
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
)

enum class UploadStatus {
    PENDING,      // 待上传
    UPLOADING,    // 上传中
    UPLOADED,     // 已上传
    FAILED        // 上传失败
}

/**
 * App 配置常量
 */
object Config {
    // 照片存储目录（应用内部）
    const val PHOTO_DIR = "WatermarkCamera"

    // 预设模板最大数量
    const val MAX_TEMPLATES = 10

    // 上传重试次数
    const val MAX_RETRY_COUNT = 3

    // 上传重试间隔（毫秒）
    val RETRY_DELAYS = longArrayOf(3000, 10000, 30000)

    // mDNS 服务类型
    const val MDNS_SERVICE_TYPE = "_watermarkcam._tcp.local."
}
