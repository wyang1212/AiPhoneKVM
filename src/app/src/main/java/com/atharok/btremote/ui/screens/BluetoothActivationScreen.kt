// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.R
import com.atharok.btremote.common.extensions.getActivity
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.presentation.viewmodel.BluetoothActivationViewModel
import com.atharok.btremote.ui.views.ActivationView
import org.koin.androidx.compose.koinViewModel

@Composable
fun BluetoothActivationScreen(
    isBluetoothEnabled: Boolean,
    refreshBluetoothState: () -> Boolean,
    navigateToBluetoothDeviceSelectionScreen: () -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bluetoothActivationViewModel: BluetoothActivationViewModel = koinViewModel(),
    context: Context = LocalContext.current
) {
    var requesting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requesting=false
        error=if(refreshBluetoothState()) "" else "蓝牙尚未开启，请在系统蓝牙设置中检查后返回。"
    }
    val hideBluetoothActivationButton by bluetoothActivationViewModel.hideBluetoothActivationButton
        .collectAsStateWithLifecycle(false)

    LaunchedEffect(isBluetoothEnabled) {
        if(isBluetoothEnabled) {
            navigateToBluetoothDeviceSelectionScreen()
        }
    }

    ActivationView(
        topBarTitle = stringResource(id = R.string.activation),
        image = AppIcons.BluetoothDisabled,
        title = stringResource(id = R.string.bluetooth_disabled_info),
        message = stringResource(id = R.string.bluetooth_disabled_message) + if(requesting) "\n等待系统确认…" else if(error.isNotBlank()) "\n$error" else "",
        buttonIcon = AppIcons.Bluetooth,
        buttonText = stringResource(id = R.string.bluetooth_enabled_button),
        buttonOnClick = {
            if (!requesting) {
                error=""
                if(!checkBluetoothConnectPermission(context)) error="请在应用权限中允许附近设备权限。"
                else if(!refreshBluetoothState()) {
                    try { requesting=true;enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    catch(e:Exception) { requesting=false;error="无法请求开启蓝牙，请在系统设置中检查后返回。" }
                }
            }
        },
        hideButton = hideBluetoothActivationButton,
        navigateToSettings = navigateToSettings,
        modifier = modifier
    )
}