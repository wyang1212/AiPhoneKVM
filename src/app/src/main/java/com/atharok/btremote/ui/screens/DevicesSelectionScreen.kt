// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.screens

import android.bluetooth.BluetoothHidDevice
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.isMacAddress
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.presentation.services.BluetoothHidService
import com.atharok.btremote.presentation.viewmodel.DeviceSelectionViewModel
import com.atharok.btremote.ui.components.NavigateUpIconButton
import com.atharok.btremote.ui.components.AppScaffold
import com.atharok.btremote.ui.components.BasicDropdownMenuItem
import com.atharok.btremote.ui.components.BasicOverflowMenu
import com.atharok.btremote.ui.components.DefaultElevatedCard
import com.atharok.btremote.ui.components.HelpIconButton
import com.atharok.btremote.ui.components.LoadingDialog
import com.atharok.btremote.ui.components.SettingsIconButton
import com.atharok.btremote.ui.components.SimpleDialog
import com.atharok.btremote.ui.components.TemplateDialog
import com.atharok.btremote.ui.components.TextLarge
import com.atharok.btremote.ui.components.TextMedium
import com.atharok.btremote.ui.components.TextNormal
import com.atharok.btremote.ui.components.TextNormalSecondary
import com.atharok.btremote.ui.views.DeviceItemView
import com.atharok.btremote.ui.views.DevicesSelectionScreenHelpModalBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

internal data class InternalDevice(val name: String, val macAddress: String)

@Composable
fun DevicesSelectionScreen(
    isBluetoothEnabled: Boolean,
    isBluetoothServiceRunning: Boolean,
    isBluetoothHidProfileRegistered: Boolean,
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    closeApp: () -> Unit,
    navigateToRemoteScreen: (deviceName: String) -> Unit,
    navigateToDeviceDiscoveryScreen: () -> Unit,
    navigateToDistantDevicePairScreen: () -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectDevice: ((String) -> Unit)? = null,
    modeHeader: (@Composable () -> Unit)? = null,
    selectedAddress: String = "",
    navigateUp: (() -> Unit)? = null,
    viewModel: DeviceSelectionViewModel = koinViewModel(),
    context: Context = LocalContext.current
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }

    val devices: List<DeviceEntity> by viewModel.devicesFlow.collectAsStateWithLifecycle()
    val autoConnectDeviceAddress: String by viewModel.getAutoConnectDeviceAddressFlow().collectAsStateWithLifecycle("")
    val favoriteDevices: List<String> by viewModel.getFavoriteDevices().collectAsStateWithLifecycle(emptyList())

    var showBluetoothAddressDialog: Boolean by remember { mutableStateOf(false) }
    var showHelpBottomSheet: Boolean by remember { mutableStateOf(false) }
    var deviceToAutoConnect: InternalDevice? by remember { mutableStateOf(null) }
    var deviceToUnpair: InternalDevice? by remember { mutableStateOf(null) }

    BackHandler(enabled = true, onBack = closeApp)

    // If the service isn't started and Bluetooth is enabled, we start the service.
    LaunchedEffect(isBluetoothServiceRunning) {
        if(!isBluetoothServiceRunning && isBluetoothEnabled) {
            BluetoothHidService.start(context)
        }
    }

    // If a connection has been established with a remote device, we open the remote control view.
    LaunchedEffect(bluetoothDeviceHidConnectionState.state) {
        if(onSelectDevice == null && bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_CONNECTED) {
            navigateToRemoteScreen(bluetoothDeviceHidConnectionState.deviceName)
        }
    }

    // Fetch the device's paired Bluetooth devices.
    LaunchedEffect(Unit) {
        viewModel.findBondedDevices()
    }

    StatelessDevicesSelectionScreen(
        snackbarHostState = snackbarHostState,
        isBluetoothServiceRunning = isBluetoothServiceRunning,

        modeHeader = modeHeader,
        selectedAddress = selectedAddress,
        navigateUp = navigateUp,
        connectDevice = {
            if (onSelectDevice != null) onSelectDevice(it)
            else if(!viewModel.connectDevice(macAddress = it)) {
                viewModel.disconnectDevice()
                viewModel.connectDevice(macAddress = it)
            }
        },
        favoriteDevices = devices.filter { it.macAddress in favoriteDevices },
        nonFavoriteDevices = devices.filter { it.macAddress !in favoriteDevices },
        saveFavoriteDevice = {
            if(favoriteDevices.contains(it)) {
                viewModel.saveFavoriteDevices(favoriteDevices - it)
            } else {
                viewModel.saveFavoriteDevices(favoriteDevices + it)
            }
        },
        autoConnectDeviceAddress = autoConnectDeviceAddress,
        onDeviceToAutoConnectChanged = { deviceToAutoConnect = it },
        onDeviceToUnpairChanged = { deviceToUnpair = it },

        topBarActions = {
            TopBarActions(
                navigateToDeviceDiscoveryScreen = navigateToDeviceDiscoveryScreen,
                navigateToDistantDevicePairScreen = navigateToDistantDevicePairScreen,
                navigateToSettings = navigateToSettings,
                onShowBluetoothAddressDialogChanged = { showBluetoothAddressDialog = it },
                showHelpBottomSheet = showHelpBottomSheet,
                onShowHelpBottomSheetChanged = { showHelpBottomSheet = it },
                deviceManagement = onSelectDevice != null
            )
        },

        overlayView = {
            // Dialog / ModalBottomSheet
            when {
                isBluetoothServiceRunning && !isBluetoothHidProfileRegistered -> {
                    SimpleDialog(
                        confirmButtonText = stringResource(id = R.string.retry),
                        dismissButtonText = stringResource(id = R.string.close),
                        onConfirmation = {
                            BluetoothHidService.stop(context) // isBluetoothServiceRunning becomes false, so the service will be restarted via LaunchedEffect(isBluetoothServiceRunning)
                        },
                        onDismissRequest = closeApp,
                        dialogTitle = stringResource(id = R.string.error),
                        dialogText = stringResource(id = R.string.bluetooth_failed_to_register_app_message)
                    )
                }
                onSelectDevice == null && bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_CONNECTING -> {
                    LoadingDialog(
                        title = stringResource(id = R.string.connection),
                        message = stringResource(id = R.string.bluetooth_device_connecting_message, bluetoothDeviceHidConnectionState.deviceName),
                        buttonText = stringResource(id = android.R.string.cancel),
                        onButtonClick = {
                            viewModel.disconnectDevice()
                        }
                    )
                }
                showHelpBottomSheet -> {
                    DevicesSelectionScreenHelpModalBottomSheet(
                        onDismissRequest = { showHelpBottomSheet = false }
                    )
                }
                showBluetoothAddressDialog -> {
                    BluetoothAddressDialog(
                        connectDevice = {
                            showBluetoothAddressDialog = false
                            if (onSelectDevice != null) onSelectDevice(it) else viewModel.connectDevice(it)
                        },
                        close = {
                            showBluetoothAddressDialog = false
                        }
                    )
                }
                deviceToUnpair != null -> {
                    UnpairDeviceDialog(
                        title = deviceToUnpair?.name ?: "",
                        unpairDevice = {
                            val result = viewModel.unpairDevice(deviceToUnpair?.macAddress)
                            coroutineScope.launch { snackbarHostState.showSnackbar(result.message) }
                            deviceToUnpair = null
                        },
                        onDismissRequest = {
                            deviceToUnpair = null
                        }
                    )
                }
                deviceToAutoConnect != null -> {
                    AutoConnectDeviceDialog(
                        deviceName = deviceToAutoConnect?.name ?: "",
                        deviceAddress = deviceToAutoConnect?.macAddress ?: "",
                        currentAutoConnectDeviceAddress = autoConnectDeviceAddress,
                        saveAutoConnectDeviceAddress = {
                            viewModel.saveAutoConnectDeviceAddress(it)
                            deviceToAutoConnect = null
                        },
                        onDismissRequest = {
                            deviceToAutoConnect = null
                        }
                    )
                }
            }
        },

        modifier = modifier
    )
}

@Composable
internal fun StatelessDevicesSelectionScreen(
    snackbarHostState: SnackbarHostState,
    isBluetoothServiceRunning: Boolean,

    connectDevice: (String) -> Unit,
    favoriteDevices: List<DeviceEntity>,
    nonFavoriteDevices: List<DeviceEntity>,
    saveFavoriteDevice: (String) -> Unit,
    autoConnectDeviceAddress: String,
    onDeviceToAutoConnectChanged: (InternalDevice) -> Unit,
    onDeviceToUnpairChanged: (InternalDevice) -> Unit,

    topBarActions: @Composable (RowScope.() -> Unit),
    overlayView: @Composable () -> Unit,
    modeHeader: (@Composable () -> Unit)?,
    selectedAddress: String,
    navigateUp: (() -> Unit)?,

    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = if (navigateUp != null) "蓝牙设备" else if (modeHeader != null) "AiPhoneKVM" else stringResource(id = R.string.devices),
        navigateUp = { navigateUp?.let { NavigateUpIconButton(it) } },
        modifier = modifier,
        topBarActions = topBarActions,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        DevicesListView(
            modeHeader = modeHeader,
            selectedAddress = selectedAddress,
            favoriteDevices = favoriteDevices,
            nonFavoriteDevices = nonFavoriteDevices,
            connectDevice = connectDevice,
            autoConnectDeviceAddress = autoConnectDeviceAddress,
            autoConnect = onDeviceToAutoConnectChanged,
            saveFavoriteDevice = saveFavoriteDevice,
            unpairDevice = onDeviceToUnpairChanged,
            isBluetoothServiceRunning = isBluetoothServiceRunning,
            modifier = Modifier,
            contentPadding = innerPadding
        )

        // Dialog / ModalBottomSheet
        overlayView()
    }
}

@Composable
private fun DevicesListView(
    modeHeader: (@Composable () -> Unit)?,
    selectedAddress: String,
    favoriteDevices: List<DeviceEntity>,
    nonFavoriteDevices: List<DeviceEntity>,
    connectDevice: (String) -> Unit,
    autoConnectDeviceAddress: String,
    autoConnect: (InternalDevice) -> Unit,
    saveFavoriteDevice: (String) -> Unit,
    unpairDevice: (InternalDevice) -> Unit,
    isBluetoothServiceRunning: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        if (modeHeader != null) item { modeHeader() }
        else item {
            InfoView(
                isBluetoothServiceRunning = isBluetoothServiceRunning,
                modifier = Modifier.padding(
                    horizontal = dimensionResource(R.dimen.padding_max),
                    vertical = dimensionResource(R.dimen.padding_large)
                )
            )
        }

        // Favorite devices

        if(favoriteDevices.isNotEmpty()) {
            item {
                TextNormalSecondary(
                    text = stringResource(id = R.string.favorites),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(id = R.dimen.padding_max),
                            vertical = dimensionResource(id = R.dimen.padding_normal)
                        )
                )
            }
            items(favoriteDevices) { device ->
                DeviceItemView(
                    isSelected = selectedAddress.equals(device.macAddress, true),
                    name = device.name,
                    macAddress = device.macAddress,
                    icon = device.imageVector,
                    isAutoConnectDeviceAddress = autoConnectDeviceAddress == device.macAddress,
                    autoConnect = { autoConnect(InternalDevice(device.name, device.macAddress)) },
                    isFavoriteDevice = true,
                    onFavoriteDeviceChanged = {
                        saveFavoriteDevice(device.macAddress)
                    },
                    unpair = { unpairDevice(InternalDevice(device.name, device.macAddress)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { connectDevice(device.macAddress) }
                        .semantics { selected = selectedAddress.equals(device.macAddress, true) }
                        .padding(dimensionResource(id = R.dimen.padding_max))
                )
            }
        }

        // Non favorite devices

        if(nonFavoriteDevices.isNotEmpty()) {
            item {
                TextNormalSecondary(
                    text = stringResource(id = R.string.paired_devices),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(id = R.dimen.padding_max),
                            vertical = dimensionResource(id = R.dimen.padding_normal)
                        )
                )
            }
            items(nonFavoriteDevices) { device ->
                DeviceItemView(
                    isSelected = selectedAddress.equals(device.macAddress, true),
                    name = device.name,
                    macAddress = device.macAddress,
                    icon = device.imageVector,
                    isAutoConnectDeviceAddress = autoConnectDeviceAddress == device.macAddress,
                    autoConnect = { autoConnect(InternalDevice(device.name, device.macAddress)) },
                    isFavoriteDevice = false,
                    onFavoriteDeviceChanged = {
                        saveFavoriteDevice(device.macAddress)
                    },
                    unpair = { unpairDevice(InternalDevice(device.name, device.macAddress)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { connectDevice(device.macAddress) }
                        .semantics { selected = selectedAddress.equals(device.macAddress, true) }
                        .padding(dimensionResource(id = R.dimen.padding_max))
                )
            }
        }

        if(favoriteDevices.isEmpty() && nonFavoriteDevices.isEmpty()) {
            item {
                TextNormalSecondary(
                    text = stringResource(id = R.string.no_device_registered),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(id = R.dimen.padding_max),
                            vertical = dimensionResource(id = R.dimen.padding_normal)
                        )
                )
            }
        }
    }
}

@Composable
private fun InfoView(
    isBluetoothServiceRunning: Boolean,
    modifier: Modifier = Modifier
) {
    DefaultElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.padding_max)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = dimensionResource(id = R.dimen.padding_medium)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_normal))
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = stringResource(R.string.information)
                )
                TextMedium(
                    text = stringResource(R.string.information),
                    maxLines = 1
                )
            }
            if(!isBluetoothServiceRunning) {
                TextNormalSecondary(
                    text = stringResource(R.string.help_info_hid_service_not_initialized_message),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                TextNormalSecondary(text = stringResource(R.string.help_info_message))
            }
        }
    }
}

@Composable
internal fun TopBarActions(
    navigateToDeviceDiscoveryScreen: () -> Unit,
    navigateToDistantDevicePairScreen: () -> Unit,
    navigateToSettings: () -> Unit,
    onShowBluetoothAddressDialogChanged: (Boolean) -> Unit,
    showHelpBottomSheet: Boolean,
    onShowHelpBottomSheetChanged: (Boolean) -> Unit,
    deviceManagement: Boolean = false,
) {
    BasicOverflowMenu(
        icon = if (deviceManagement) AppIcons.MoreVert else AppIcons.BluetoothPairing,
        contentDescription = if (deviceManagement) "更多设备选项" else stringResource(id = R.string.pairing_a_device)
    ) { closeDropdownMenu: () -> Unit ->

        if (!deviceManagement) {
            // Device Discovery
            BasicDropdownMenuItem(
                text = stringResource(id = R.string.pairing_a_device),
                icon = AppIcons.BluetoothPairing,
                onClick = {
                    closeDropdownMenu()
                    navigateToDeviceDiscoveryScreen()
                }
            )

        }

        // Distant Device Pair
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.pairing_from_the_remote_device),
            icon = AppIcons.BluetoothPairing,
            onClick = {
                closeDropdownMenu()
                navigateToDistantDevicePairScreen()
            }
        )

        // Show Bluetooth Address Dialog
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.enter_bluetooth_address_manually),
            icon = AppIcons.Keyboard,
            onClick = {
                closeDropdownMenu()
                onShowBluetoothAddressDialogChanged(true)
            }
        )
    }

    HelpIconButton(onClick = { onShowHelpBottomSheetChanged(!showHelpBottomSheet) })
    SettingsIconButton(navigateToSettings)
}

@Composable
internal fun BluetoothAddressDialog(
    connectDevice: (String) -> Unit,
    close: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val textState = remember { mutableStateOf("") }
    val showError = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TemplateDialog(
        title = {
            TextLarge(text = stringResource(id = R.string.bluetooth_address))
        },
        content = {
            Column {
                TextNormal(text = stringResource(id = R.string.bluetooth_address_prompt))
                OutlinedTextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    modifier = Modifier
                        .padding(vertical = dimensionResource(id = R.dimen.padding_medium))
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if(isMacAddress(textState.value)) {
                                connectDevice(textState.value)
                            } else {
                                showError.value = true
                            }
                        }
                    ),
                    singleLine = true
                )
                if(showError.value) {
                    TextNormal(
                        text = stringResource(id = R.string.bluetooth_address_wrong_format),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        modifier = modifier,
        confirmButtonText = stringResource(id = R.string.connection),
        onConfirmation = {
            if(isMacAddress(textState.value)) {
                connectDevice(textState.value)
            } else {
                showError.value = true
            }
        },
        dismissButtonText = stringResource(id = R.string.close),
        onDismissRequest = close
    )
}

@Composable
internal fun UnpairDeviceDialog(
    title: String,
    unpairDevice: () -> Unit,
    onDismissRequest: () -> Unit
) {
    SimpleDialog(
        confirmButtonText = stringResource(id = R.string.unpair),
        dismissButtonText = stringResource(id = android.R.string.cancel),
        onConfirmation = unpairDevice,
        onDismissRequest = onDismissRequest,
        dialogTitle = title,
        dialogText = stringResource(id = R.string.unpair_device_warning_message)
    )
}

@Composable
internal fun AutoConnectDeviceDialog(
    deviceName: String,
    deviceAddress: String,
    currentAutoConnectDeviceAddress: String,
    saveAutoConnectDeviceAddress: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val macAddressToAutoConnect: String
    @StringRes val messageRes: Int

    if(deviceAddress == currentAutoConnectDeviceAddress) {
        // Disabled auto connect
        macAddressToAutoConnect = ""
        messageRes = R.string.disabled_automatic_connection_message
    } else {
        // Enabled/Replaced auto connect
        macAddressToAutoConnect = deviceAddress
        messageRes = R.string.enabled_automatic_connection_message
    }

    SimpleDialog(
        confirmButtonText = stringResource(id = android.R.string.ok),
        dismissButtonText = stringResource(id = android.R.string.cancel),
        onConfirmation = {
            saveAutoConnectDeviceAddress(macAddressToAutoConnect)
        },
        onDismissRequest = onDismissRequest,
        dialogTitle = deviceName,
        dialogText = stringResource(messageRes)
    )
}