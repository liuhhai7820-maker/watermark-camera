package com.watermark.camera

import android.graphics.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 水印渲染引擎
 * 在照片上叠加左下角半透明蓝色信息框，参考"今日水印相机"样式
 */
object WatermarkRenderer {

    // 水印区域占照片宽度的比例
    private const val WATERMARK_WIDTH_RATIO = 0.42f
    private const val WATERMARK_PADDING = 30f

    // 颜色
    private val COLOR_TITLE_BG = Color.rgb(30, 60, 140)       // 深蓝标题栏
    private val COLOR_TITLE_TEXT = Color.WHITE                 // 标题白色字
    private val COLOR_BODY_BG = Color.argb(200, 220, 230, 245) // 半透明浅蓝
    private val COLOR_LABEL_TEXT = Color.rgb(50, 50, 50)       // 字段标签
    private val COLOR_VALUE_TEXT = Color.BLACK                  // 字段值
    private val COLOR_BORDER = Color.argb(100, 100, 140, 200)  // 边框

    // 圆角
    private const val CORNER_RADIUS = 16f

    // 字体大小（按照片宽度缩放）
    private const val TITLE_SIZE_RATIO = 0.028f
    private const val FIELD_SIZE_RATIO = 0.020f
    private const val LINE_SPACING_RATIO = 0.012f

    data class WatermarkData(
        val projectName: String,
        val constructionUnit: String,
        val constructionArea: String,
        val constructionContent: String,
        val shootTime: String,
        val shootLocation: String,
    )

    fun render(
        photo: Bitmap,
        data: WatermarkData
    ): Bitmap {
        val result = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val photoW = photo.width.toFloat()
        val photoH = photo.height.toFloat()

        val titleSize = photoW * TITLE_SIZE_RATIO
        val fieldSize = photoW * FIELD_SIZE_RATIO
        val lineSpacing = photoW * LINE_SPACING_RATIO

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = titleSize
            color = COLOR_TITLE_TEXT
            typeface = Typeface.DEFAULT_BOLD
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fieldSize
            color = COLOR_LABEL_TEXT
            typeface = Typeface.DEFAULT_BOLD
        }

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fieldSize
            color = COLOR_VALUE_TEXT
            typeface = Typeface.DEFAULT
        }

        // 水印字段列表
        val fields = listOf(
            "施工单位" to data.constructionUnit,
            "施工区域" to data.constructionArea,
            "施工内容" to data.constructionContent,
            "拍摄时间" to data.shootTime,
            "拍摄地点" to data.shootLocation,
        )

        // 计算水印框尺寸
        val titleHeight = titleSize + lineSpacing * 3
        val fieldCount = fields.size
        val bodyHeight = fieldCount * (fieldSize + lineSpacing) + lineSpacing * 2
        val watermarkHeight = titleHeight + bodyHeight

        // 计算项目名称宽度
        val titleTextWidth = titlePaint.measureText(data.projectName)
        val fieldMaxWidth = fields.maxOf { (label, value) ->
            labelPaint.measureText("$label：$value")
        }
        val watermarkWidth = maxOf(titleTextWidth, fieldMaxWidth) + WATERMARK_PADDING * 4

        // 位置：左下角
        val left = WATERMARK_PADDING
        val bottom = photoH - WATERMARK_PADDING
        val top = bottom - watermarkHeight
        val right = left + watermarkWidth

        // 绘制外层半透明背景
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BODY_BG
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            CORNER_RADIUS, CORNER_RADIUS, bgPaint
        )

        // 绘制标题栏
        val titleBottom = top + titleHeight
        val titleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TITLE_BG
            style = Paint.Style.FILL
        }
        // 上半部分圆角
        canvas.drawRoundRect(
            RectF(left, top, right, titleBottom),
            CORNER_RADIUS, CORNER_RADIUS, titleBgPaint
        )
        // 底部直角补齐
        canvas.drawRect(
            RectF(left, titleBottom - CORNER_RADIUS, right, titleBottom),
            titleBgPaint
        )

        // 绘制标题文字
        val titleTextX = left + WATERMARK_PADDING * 2
        val titleTextY = top + titleSize + lineSpacing
        canvas.drawText(data.projectName, titleTextX, titleTextY, titlePaint)

        // 绘制字段
        var fieldY = titleBottom + lineSpacing
        fields.forEach { (label, value) ->
            val text = "$label：$value"
            canvas.drawText(text, titleTextX, fieldY + fieldSize, labelPaint)
            fieldY += fieldSize + lineSpacing
        }

        // 绘制边框
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            CORNER_RADIUS, CORNER_RADIUS, borderPaint
        )

        return result
    }

    /**
     * 生成拍摄时间字符串
     */
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA)
        return sdf.format(Date(timestamp))
    }
}
