package com.watermark.camera

/**
 * 应用配置常量
 */
object Config {
    // mDNS 服务类型
    const val MDNS_SERVICE_TYPE = "_watermark-upload._tcp."

    // 应用专属存储目录
    const val PHOTO_DIR = "WatermarkCamera"

    // 上传重试配置
    const val MAX_RETRY_COUNT = 3
    val RETRY_DELAYS = longArrayOf(3000L, 10000L, 30000L)  // 3s, 10s, 30s

    // 传输超时 (秒)
    const val CONNECT_TIMEOUT = 30
    const val WRITE_TIMEOUT = 60
    const val READ_TIMEOUT = 30
}
