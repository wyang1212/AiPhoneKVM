// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.atharok.btremote.domain.entities.DeviceHidConnectionState

class BluetoothPairingState(
    val discoverable: Boolean,
    val requesting: Boolean,
    val message: String?,
    val requestDiscovery: () -> Unit
)

/** One automatic prompt per launch, after HID registration. Rejection/expiry requires a button press. */
@Composable
internal fun BluetoothPairingAutoRequest(
    bluetoothEnabled: Boolean, ready: Boolean, connected: Boolean, discoverable: Boolean,
    foreground: Boolean, requestDiscovery: () -> Unit
) {
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bluetoothEnabled, ready, connected, discoverable, foreground) {
        if (!bluetoothEnabled) requested = false
        if (bluetoothEnabled && ready && !connected && foreground && !requested) {
            requested = true
            if (!discoverable) requestDiscovery()
        }
    }
}

@Composable
fun rememberBluetoothPairingState(
    bluetoothEnabled: Boolean, hidRegistered: Boolean, connected: Boolean, autoRequest: Boolean
): BluetoothPairingState {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var discoverable by remember { mutableStateOf(false) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    fun refresh() {
        discoverable = try {
            bluetoothEnabled && adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
        } catch (_: SecurityException) { false }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        requesting = false
        refresh()
        message = if (result.resultCode > 0) null else "未开启可发现。点击下方按钮可重试。"
    }
    DisposableEffect(context, bluetoothEnabled) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        refresh()
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(lifecycle, bluetoothEnabled) { refresh() }
    val request: () -> Unit = {
        if (bluetoothEnabled && hidRegistered && !requesting) {
            requesting = true
            message = null
            try {
                launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                })
            } catch (_: SecurityException) {
                requesting = false
                message = "缺少蓝牙可发现权限，请在系统设置允许附近设备权限后重试。"
            } catch (_: android.content.ActivityNotFoundException) {
                requesting = false
                message = "系统未提供蓝牙可发现授权界面，请检查系统蓝牙设置。"
            }
        }
    }
    BluetoothPairingAutoRequest(bluetoothEnabled, hidRegistered, connected, discoverable,
        autoRequest && lifecycle == Lifecycle.State.RESUMED, request)
    return BluetoothPairingState(discoverable, requesting, message, request)
}

@Composable
fun BluetoothPairingCard(
    localName: String, hidRegistered: Boolean, connection: DeviceHidConnectionState,
    state: BluetoothPairingState
) {
    val connected = connection.state == BluetoothProfile.STATE_CONNECTED
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (connected) "蓝牙已连接：${connection.deviceName}" else "蓝牙键盘与鼠标",
                style = MaterialTheme.typography.titleMedium)
            Text("本机蓝牙名称：$localName", style = MaterialTheme.typography.bodyMedium)
            Text(when {
                !hidRegistered -> "正在准备键鼠服务；若长时间未就绪，请检查手机是否支持蓝牙 HID。"
                connected -> "在下方选择该设备，再点击功能开始控制。"
                state.requesting -> "请在系统弹窗允许其他设备发现本机。"
                state.discoverable -> "可被发现：请在另一台设备的蓝牙设置搜索本机并配对。"
                else -> "键鼠服务已就绪。允许被发现后，可从另一台设备发起配对。"
            }, style = MaterialTheme.typography.bodySmall)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!connected) OutlinedButton(onClick = state.requestDiscovery,
                enabled = hidRegistered && !state.requesting) {
                Text(if (state.discoverable) "延长可发现时间" else "允许其他设备发现（5分钟）")
            }
        }
    }
}
