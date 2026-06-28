package com.watermark.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private lateinit var etProjectName: EditText
    private lateinit var etConstructionUnit: EditText
    private lateinit var etConstructionArea: EditText
    private lateinit var etConstructionContent: EditText
    private lateinit var tvDeviceStatus: TextView
    private lateinit var tvPhotoCount: TextView
    private lateinit var switchAutoUpload: Switch
    private lateinit var btnGallery: Button
    private lateinit var btnTemplates: Button

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var photoRepo: PhotoRepository
    private lateinit var discoveryService: DiscoveryService
    private var imageCapture: ImageCapture? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var lastLocation: String = "未知位置"

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CAMERA_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化
        cameraExecutor = Executors.newSingleThreadExecutor()
        photoRepo = PhotoRepository(this)
        discoveryService = DiscoveryService(this)

        // 绑定视图
        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        etProjectName = findViewById(R.id.etProjectName)
        etConstructionUnit = findViewById(R.id.etConstructionUnit)
        etConstructionArea = findViewById(R.id.etConstructionArea)
        etConstructionContent = findViewById(R.id.etConstructionContent)
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus)
        tvPhotoCount = findViewById(R.id.tvPhotoCount)
        switchAutoUpload = findViewById(R.id.switchAutoUpload)
        btnGallery = findViewById(R.id.btnGallery)
        btnTemplates = findViewById(R.id.btnTemplates)

        // 加载上次字段值
        loadFields()

        // 更新照片数量
        updatePhotoCount()

        // 请求权限
        requestPermissions()

        // 启动设备发现
        discoveryService.startDiscovery()
        observeDevices()

        // 定位
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 按钮事件
        btnCapture.setOnClickListener { capturePhoto() }
        btnGallery.setOnClickListener {
            startActivity(android.content.Intent(this, GalleryActivity::class.java))
        }
        btnTemplates.setOnClickListener { showTemplateDialog() }

        switchAutoUpload.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateDeviceStatus()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CAMERA_PERMISSION)
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display?.rotation ?: 0)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "相机绑定失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val fields = WatermarkFields(
            projectName = etProjectName.text.toString().trim(),
            constructionUnit = etConstructionUnit.text.toString().trim(),
            constructionArea = etConstructionArea.text.toString().trim(),
            constructionContent = etConstructionContent.text.toString().trim()
        )

        // 保存字段
        saveFields(fields)

        // 获取位置
        getCurrentLocation { location ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        processPhoto(image, fields, location)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(this@MainActivity, "拍照失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun processPhoto(imageProxy: ImageProxy, fields: WatermarkFields, location: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                imageProxy.close()

                var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                // 旋转修正
                val matrix = Matrix()
                matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

                // 渲染水印
                val watermarkData = WatermarkRenderer.WatermarkData(
                    projectName = fields.projectName,
                    constructionUnit = fields.constructionUnit,
                    constructionArea = fields.constructionArea,
                    constructionContent = fields.constructionContent,
                    shootTime = WatermarkRenderer.formatTime(System.currentTimeMillis()),
                    shootLocation = location
                )
                WatermarkRenderer.render(bmp, watermarkData)
            }

            // 保存照片
            val record = photoRepo.savePhoto(bitmap, fields)
            bitmap.recycle()

            withContext(Dispatchers.Main) {
                updatePhotoCount()
                Toast.makeText(this@MainActivity, "已保存: ${record.fileName}", Toast.LENGTH_SHORT).show()

                // 自动上传
                if (switchAutoUpload.isChecked) {
                    autoUploadPhoto(record.fileName, fields)
                }
            }
        }
    }

    private fun autoUploadPhoto(fileName: String, fields: WatermarkFields) {
        val devices = discoveryService.devices.value
        if (devices.isEmpty()) {
            Toast.makeText(this, "未发现可用电脑", Toast.LENGTH_SHORT).show()
            return
        }

        val device = devices.first() // 选第一个发现的设备
        UploadService.startUpload(
            this,
            listOf(fileName),
            device.host,
            device.port,
            fields.projectName,
            fields.constructionArea,
            autoMode = true
        )
    }

    private fun getCurrentLocation(callback: (String) -> Unit) {
        val client = fusedLocationClient ?: run {
            callback("未知位置")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback("未知位置")
            return
        }

        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lifecycleScope.launch {
                    val geocoder = Geocoder(this@MainActivity, Locale.CHINA)
                    try {
                        val addresses = withContext(Dispatchers.IO) {
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        }
                        val address = addresses?.firstOrNull()
                        val city = address?.locality ?: ""
                        val district = address?.subLocality ?: ""
                        val result = if (city.isNotEmpty()) "$city·$district" else "未知位置"
                        lastLocation = result
                        callback(result)
                    } catch (e: Exception) {
                        callback("未知位置")
                    }
                }
            } else {
                callback("未知位置")
            }
        }.addOnFailureListener {
            callback("未知位置")
        }
    }

    private fun observeDevices() {
        discoveryService.devices.observe(this) { devices ->
            updateDeviceStatus()
        }
    }

    private fun updateDeviceStatus() {
        val devices = discoveryService.devices.value
        tvDeviceStatus.text = if (devices.isEmpty()) {
            "未发现设备"
        } else {
            "已连接: ${devices.first().name}"
        }
    }

    private fun updatePhotoCount() {
        val count = photoRepo.getPhotoCount()
        tvPhotoCount.text = "$count 张待传"
    }

    private fun loadFields() {
        val prefs = getSharedPreferences("watermark_fields", MODE_PRIVATE)
        etProjectName.setText(prefs.getString("project_name", ""))
        etConstructionUnit.setText(prefs.getString("construction_unit", ""))
        etConstructionArea.setText(prefs.getString("construction_area", ""))
        etConstructionContent.setText(prefs.getString("construction_content", ""))
    }

    private fun saveFields(fields: WatermarkFields) {
        val prefs = getSharedPreferences("watermark_fields", MODE_PRIVATE)
        prefs.edit().apply {
            putString("project_name", fields.projectName)
            putString("construction_unit", fields.constructionUnit)
            putString("construction_area", fields.constructionArea)
            putString("construction_content", fields.constructionContent)
            apply()
        }
    }

    private fun showTemplateDialog() {
        val templates = loadTemplates()
        val names = templates.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择模板")
            .setItems(names) { _, which ->
                val t = templates[which]
                etProjectName.setText(t.fields.projectName)
                etConstructionUnit.setText(t.fields.constructionUnit)
                etConstructionArea.setText(t.fields.constructionArea)
                etConstructionContent.setText(t.fields.constructionContent)
            }
            .setPositiveButton("保存当前") { _, _ ->
                saveAsTemplate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadTemplates(): List<WatermarkTemplate> {
        val prefs = getSharedPreferences("watermark_templates", MODE_PRIVATE)
        val count = prefs.getInt("template_count", 0)
        return (0 until count).mapNotNull { i ->
            val name = prefs.getString("template_${i}_name", null) ?: return@mapNotNull null
            WatermarkTemplate(
                id = i.toLong(),
                name = name,
                fields = WatermarkFields(
                    projectName = prefs.getString("template_${i}_project", "") ?: "",
                    constructionUnit = prefs.getString("template_${i}_unit", "") ?: "",
                    constructionArea = prefs.getString("template_${i}_area", "") ?: "",
                    constructionContent = prefs.getString("template_${i}_content", "") ?: "",
                )
            )
        }
    }

    private fun saveAsTemplate() {
        val prefs = getSharedPreferences("watermark_templates", MODE_PRIVATE)
        val count = prefs.getInt("template_count", 0)

        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val input = dialogView.findViewById<TextView>(android.R.id.text1)
        // 简化：直接用 EditText
        val editText = EditText(this).apply {
            hint = "模板名称"
            setText(etProjectName.text)
        }

        AlertDialog.Builder(this)
            .setTitle("保存模板")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    prefs.edit().apply {
                        putInt("template_count", count + 1)
                        putString("template_${count}_name", name)
                        putString("template_${count}_project", etProjectName.text.toString())
                        putString("template_${count}_unit", etConstructionUnit.text.toString())
                        putString("template_${count}_area", etConstructionArea.text.toString())
                        putString("template_${count}_content", etConstructionContent.text.toString())
                        apply()
                    }
                    Toast.makeText(this@MainActivity, "模板已保存: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        discoveryService.stopDiscovery()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
