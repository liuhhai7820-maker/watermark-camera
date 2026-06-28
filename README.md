# 水印相机 Android App

施工管理用水印相机，支持自定义水印字段、自动发现局域网 PC 服务端、一键上传照片。

## 功能

- CameraX 拍照 + 自定义水印叠加
- 专属存储隔离（不污染系统相册）
- mDNS 自动发现 PC 服务端
- 批量上传 + 上传后自动清理
- 施工字段模板管理

## GitHub Actions 自动编译

Push 到 main/master 分支自动触发编译，也可在 Actions 页面手动触发。

编译完成后在 [Actions](actions) 页面下载 APK 产物：
- `watermark-camera-debug` — Debug 版本
- `watermark-camera-release` — Release 版本（未签名）

## 本地编译

需要 Android Studio Hedgehog (2023.1.1) 或更高版本，JDK 17。
