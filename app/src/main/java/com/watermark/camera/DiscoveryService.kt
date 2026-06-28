package com.watermark.camera

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * mDNS 局域网设备发现服务
 * 扫描网络上运行 PC 服务程序的电脑
 */
class DiscoveryService(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryService"
    }

    data class DiscoveredDevice(
        val name: String,
        val host: String,
        val port: Int,
    )

    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * 开始扫描
     */
    fun startDiscovery() {
        stopDiscovery()

        val deviceList = mutableListOf<DiscoveredDevice>()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "开始扫描: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "发现服务: ${serviceInfo.serviceName}")
                // 解析详细信息
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "解析失败: ${serviceInfo.serviceName}, code=$errorCode")
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        val device = DiscoveredDevice(
                            name = resolvedInfo.serviceName,
                            host = resolvedInfo.host?.hostAddress ?: return,
                            port = resolvedInfo.port,
                        )
                        // 去重
                        if (deviceList.none { it.host == device.host && it.port == device.port }) {
                            deviceList.add(device)
                            _devices.value = deviceList.toList()
                            Log.d(TAG, "已添加设备: ${device.name} @ ${device.host}:${device.port}")
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                deviceList.removeAll { it.name == serviceInfo.serviceName }
                _devices.value = deviceList.toList()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "扫描停止: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "扫描启动失败: $serviceType, code=$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "扫描停止失败: $serviceType, code=$errorCode")
            }
        }

        discoveryListener = listener
        nsdManager.discoverServices(
            Config.MDNS_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    /**
     * 停止扫描
     */
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "停止扫描异常: ${e.message}")
            }
        }
        discoveryListener = null
    }
}
