package com.watermark.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSelectAll: Button
    private lateinit var btnUpload: Button
    private lateinit var tvEmpty: TextView

    private lateinit var photoRepo: PhotoRepository
    private lateinit var discoveryService: DiscoveryService
    private var photos: List<PhotoRecord> = emptyList()
    private val selectedPhotos = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        photoRepo = PhotoRepository(this)
        discoveryService = DiscoveryService(this)

        recyclerView = findViewById(R.id.recyclerView)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnUpload = findViewById(R.id.btnUpload)
        tvEmpty = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = GridLayoutManager(this, 3)

        btnSelectAll.setOnClickListener {
            if (selectedPhotos.size == photos.size) {
                selectedPhotos.clear()
                btnSelectAll.text = "全选"
            } else {
                selectedPhotos.addAll(photos.map { it.fileName })
                btnSelectAll.text = "取消"
            }
            recyclerView.adapter?.notifyDataSetChanged()
            updateUploadButton()
        }

        btnUpload.setOnClickListener {
            uploadSelected()
        }

        loadPhotos()
        discoveryService.startDiscovery()
    }

    private fun loadPhotos() {
        photos = photoRepo.getAllPhotos()
        if (photos.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = PhotoAdapter()
        }
    }

    private fun updateUploadButton() {
        btnUpload.isEnabled = selectedPhotos.isNotEmpty()
        btnUpload.text = "上传 (${selectedPhotos.size})"
    }

    private fun uploadSelected() {
        val devices = discoveryService.devices.value
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("未发现电脑")
                .setMessage("请确保电脑端服务程序已运行且在同一 WiFi 网络下")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val device = devices.first()
        val selectedList = selectedPhotos.toList()

        // 从照片记录中获取字段信息
        val firstPhoto = photos.find { it.fileName == selectedList.first() }

        UploadService.startUpload(
            this,
            selectedList,
            device.host,
            device.port,
            firstPhoto?.projectName ?: "",
            firstPhoto?.areaName ?: ""
        )

        Toast.makeText(this, "开始上传 ${selectedList.size} 张照片", Toast.LENGTH_SHORT).show()
        selectedPhotos.clear()
        loadPhotos()
    }

    inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.ivPhoto)
            val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
            val tvName: TextView = view.findViewById(R.id.tvPhotoName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val photo = photos[position]
            val isSelected = photo.fileName in selectedPhotos

            // 异步加载缩略图
            val bitmap = photoRepo.loadBitmap(photo.fileName)
            if (bitmap != null) {
                holder.imageView.setImageBitmap(
                    android.graphics.Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                )
                bitmap.recycle()
            }

            holder.tvName.text = photo.fileName
            holder.checkBox.isChecked = isSelected

            holder.itemView.setOnClickListener {
                if (photo.fileName in selectedPhotos) {
                    selectedPhotos.remove(photo.fileName)
                } else {
                    selectedPhotos.add(photo.fileName)
                }
                notifyItemChanged(position)
                updateUploadButton()
            }
        }

        override fun getItemCount(): Int = photos.size
    }

    override fun onDestroy() {
        discoveryService.stopDiscovery()
        super.onDestroy()
    }
}
