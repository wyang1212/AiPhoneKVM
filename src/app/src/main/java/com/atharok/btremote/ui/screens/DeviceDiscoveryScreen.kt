// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.screens

import android.bluetooth.BluetoothHidDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.arePermissionsGranted
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.presentation.viewmodel.DeviceDiscoveryViewModel
import com.atharok.btremote.ui.components.AppScaffold
import com.atharok.btremote.ui.components.BasicIconButton
import com.atharok.btremote.ui.components.HelpIconButton
import com.atharok.btremote.ui.components.LoadingDialog
import com.atharok.btremote.ui.components.NavigateUpIconButton
import com.atharok.btremote.ui.components.SettingsIconButton
import com.atharok.btremote.ui.components.TextNormalSecondary
import com.atharok.btremote.ui.views.BluetoothScanningScreenHelpModalBottomSheet
import com.atharok.btremote.ui.views.DeviceItemView
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeviceDiscoveryScreen(
    permissions: Array<String>,
    arePermissionsGranted: Boolean,
    isBluetoothServiceRunning: Boolean,
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    navigateUp: () -> Unit,
    navigateToRemoteScreen: (deviceName: String) -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectDevice: ((String) -> Unit)? = null
) {
    val permissionState = remember { mutableStateOf(arePermissionsGranted) }

    if(permissionState.value) {
        DeviceDiscoveryScreen(
            isBluetoothServiceRunning = isBluetoothServiceRunning,
            bluetoothDeviceHidConnectionState = bluetoothDeviceHidConnectionState,
            navigateUp = navigateUp,
            navigateToRemoteScreen = navigateToRemoteScreen,
            navigateToSettings = navigateToSettings,
            onSelectDevice = onSelectDevice,
            modifier = modifier
        )
    } else {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { result: Map<String, Boolean> ->
                val granted = arePermissionsGranted(result)
                if(granted) permissionState.value = true else navigateUp()
            }
        )

        LaunchedEffect(Unit) {
            launcher.launch(permissions)
        }
    }
}

@Composable
private fun DeviceDiscoveryScreen(
    isBluetoothServiceRunning: Boolean,
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    navigateUp: () -> Unit,
    navigateToRemoteScreen: (deviceName: String) -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectDevice: ((String) -> Unit)?,
    deviceDiscoveryViewModel: DeviceDiscoveryViewModel = koinViewModel()
) {
    val scannedDevices = deviceDiscoveryViewModel.scannedDevices
    val isDiscovering: Boolean by deviceDiscoveryViewModel.isDiscoveringFlow.collectAsStateWithLifecycle()
    var showHelpBottomSheet: Boolean by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        deviceDiscoveryViewModel.registerBluetoothScannerReceiver()
        deviceDiscoveryViewModel.startDiscovery()
        onDispose {
            deviceDiscoveryViewModel.cancelDiscovery()
            deviceDiscoveryViewModel.unregisterBluetoothScannerReceiver()
        }
    }

    LaunchedEffect(isBluetoothServiceRunning) {
        if(!isBluetoothServiceRunning) {
            navigateUp()
        }
    }

    LaunchedEffect(bluetoothDeviceHidConnectionState.state) {
        if(onSelectDevice == null && bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_CONNECTED) {
            navigateToRemoteScreen(bluetoothDeviceHidConnectionState.deviceName)
        }
    }

    StatelessBluetoothScanningScreen(
        isDiscovering = isDiscovering,
        devices = scannedDevices,
        bluetoothDeviceHidConnectionState = bluetoothDeviceHidConnectionState,
        navigateUp = navigateUp,
        navigateToSettings = navigateToSettings,
        startDiscovery = {
            deviceDiscoveryViewModel.startDiscovery()
        },
        connectToDevice = {
            deviceDiscoveryViewModel.cancelDiscovery()
            if (onSelectDevice != null) onSelectDevice(it) else deviceDiscoveryViewModel.connectDevice(it)
        },
        disconnectDevice = {
            deviceDiscoveryViewModel.disconnectDevice()
        },
        connectionManagedExternally = onSelectDevice != null,
        showHelpBottomSheet = showHelpBottomSheet,
        onShowHelpBottomSheetChanged = { showHelpBottomSheet = it },
        modifier = modifier
    )
}

@Composable
internal fun StatelessBluetoothScanningScreen(
    isDiscovering: Boolean,
    devices: List<DeviceEntity>,
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    navigateUp: () -> Unit,
    navigateToSettings: () -> Unit,
    startDiscovery: () -> Unit,
    connectToDevice: (String) -> Unit,
    disconnectDevice: () -> Unit,
    connectionManagedExternally: Boolean,
    showHelpBottomSheet: Boolean,
    onShowHelpBottomSheetChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = stringResource(id = R.string.pairing_a_device),
        modifier = modifier,
        navigateUp = {
            NavigateUpIconButton(navigateUp)
        },
        topBarActions = {
            BasicIconButton(
                onClick = startDiscovery,
                icon = AppIcons.Refresh,
                contentDescription = stringResource(id = R.string.refresh)
            )
            HelpIconButton(onClick = { onShowHelpBottomSheetChanged(!showHelpBottomSheet) })
            SettingsIconButton(navigateToSettings)
        }
    ) { innerPadding ->

        DiscoveredDevicesListView(
            isDiscovering = isDiscovering,
            devices = devices,
            connectToDevice = connectToDevice,
            modifier = Modifier,
            contentPadding = innerPadding
        )

        // Dialog / ModalBottomSheet
        when {
            !connectionManagedExternally && bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_CONNECTING -> {
                LoadingDialog(
                    title = stringResource(id = R.string.connection),
                    message = stringResource(
                        id = R.string.bluetooth_device_connecting_message,
                        bluetoothDeviceHidConnectionState.deviceName
                    ),
                    buttonText = stringResource(id = android.R.string.cancel),
                    onButtonClick = disconnectDevice
                )
            }
            showHelpBottomSheet -> {
                BluetoothScanningScreenHelpModalBottomSheet(
                    onDismissRequest = { onShowHelpBottomSheetChanged(false) },
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun DiscoveredDevicesListView(
    isDiscovering: Boolean,
    devices: List<DeviceEntity>,
    connectToDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.padding_max),
                        vertical = dimensionResource(id = R.dimen.padding_normal)
                    )
            ) {
                if(isDiscovering || devices.isNotEmpty()) {
                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.large_icon_size))
                    ) {
                        TextNormalSecondary(
                            text = stringResource(id = R.string.available_devices),
                        )
                        if(isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                } else {
                    TextNormalSecondary(
                        text = stringResource(id = R.string.bluetooth_pairing_device_not_found),
                        modifier = Modifier
                    )
                }
            }
        }
        items(devices) { device ->
            DeviceItemView(
                name = device.name,
                macAddress = device.macAddress,
                icon = device.imageVector,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { connectToDevice(device.macAddress) }
                    .padding(dimensionResource(id = R.dimen.padding_max))
            )
        }
    }
}