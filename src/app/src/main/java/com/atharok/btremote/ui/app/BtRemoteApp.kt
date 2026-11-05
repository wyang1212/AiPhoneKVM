// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.app

import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atharok.btremote.automation.*
import com.atharok.btremote.common.extensions.getActivity
import com.atharok.btremote.common.utils.*
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.settings.AppearanceSettings
import com.atharok.btremote.presentation.services.BluetoothHidService
import com.atharok.btremote.presentation.viewmodel.AppScopeViewModel
import com.atharok.btremote.presentation.viewmodel.DeviceSelectionViewModel
import com.atharok.btremote.ui.components.*
import com.atharok.btremote.ui.navigation.*
import com.atharok.btremote.ui.screens.*
import com.atharok.btremote.ui.views.ControlMode
import com.atharok.btremote.ui.theme.BtRemoteTheme
import com.atharok.btremote.ui.theme.surfaceElevationLow
import kotlinx.coroutines.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun BtRemoteApp(
    runtime: AutomationRuntime,
    automationScreen: @Composable (DeviceHidConnectionState, () -> Unit, () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController(),
    appScopeViewModel: AppScopeViewModel = koinViewModel(),
    deviceViewModel: DeviceSelectionViewModel = koinViewModel(),
    context: Context = LocalContext.current
) {
    val appearance by appScopeViewModel.appearanceSettingsFlow.collectAsStateWithLifecycle(AppearanceSettings())
    val connection by appScopeViewModel.deviceHidConnectionState.collectAsStateWithLifecycle()
    val serviceRunning by appScopeViewModel.isBluetoothServiceRunning.collectAsStateWithLifecycle()
    val registered by appScopeViewModel.isBluetoothHidProfileRegistered.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var closing by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(appScopeViewModel.isBluetoothEnabled) }
    val entryPrefs = remember { context.getSharedPreferences("control_mode_private", Context.MODE_PRIVATE) }
    var selectedAddress by rememberSaveable { mutableStateOf(entryPrefs.getString("target_address", "") ?: "") }
    val pairedDevices by deviceViewModel.devicesFlow.collectAsStateWithLifecycle()
    val selectedDevice = pairedDevices.firstOrNull { it.macAddress.equals(selectedAddress, true) }
    var activeMode by rememberSaveable { mutableStateOf(PhoneControlMode.KEY_MOUSE) }
    var activeTarget by rememberSaveable { mutableStateOf("") }
    var homeReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }
    var entryError by remember { mutableStateOf<String?>(null) }
    val uiScope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val homeRoute = AppNavDestination.DeviceSelectionDestination.route
    val pickerRoute = AppNavDestination.PairedDevicesDestination.route
    val remoteRoute = AppNavDestination.RemoteDestination.route
    val connector = remember(deviceViewModel, appScopeViewModel) {
        DeviceEntryController(appScopeViewModel.deviceHidConnectionState,
            deviceViewModel::connectDevice, deviceViewModel::disconnectDevice)
    }
    suspend fun quietAutomation() {
        runtime.stopAndAwait()
        context.stopService(Intent(context, AutomationService::class.java))
    }
    fun showHome() {
        if (navController.currentDestination?.route == homeRoute) {
            homeReady = true
            return
        }
        homeReady = false
        if (!navController.popBackStack(homeRoute, false)) navController.navigateTo(homeRoute)
    }
    fun transition(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        transitionJob = uiScope.launch {
            try { action() }
            catch (_: TimeoutCancellationException) { entryError = "连接超时。可从被控设备的蓝牙设置主动连接本机，再点选该设备。" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { entryError = error.message ?: "未能进入控制模式，请重试" }
            finally { busy = false; transitionJob = null }
        }
    }
    val goHome: () -> Unit = { transition { quietAutomation(); showHome() } }
    val settings: () -> Unit = { transition {
        quietAutomation()
        navController.navigateTo(AppNavDestination.SettingsDestination.route)
    } }
    fun rememberDevice(address: String) {
        val device = deviceViewModel.devicesFlow.value.firstOrNull { it.macAddress.equals(address, true) }
        check(device != null) { "设备尚未配对，请先完成蓝牙配对，再选择设备" }
        selectedAddress = device.macAddress
        entryPrefs.edit { putString("target_address", device.macAddress) }
        showHome()
    }
    val selectDevice: (String) -> Unit = { address ->
        transition {
            quietAutomation()
            deviceViewModel.findBondedDevices().join()
            rememberDevice(address)
        }
    }
    val enterModule: (PhoneControlMode) -> Unit = { requestedMode ->
        if (homeReady && !busy) transition {
            quietAutomation()
            deviceViewModel.findBondedDevices().join()
            if (deviceViewModel.devicesFlow.value.none { it.macAddress.equals(selectedAddress, true) }) {
                navController.navigateTo(pickerRoute)
            } else {
                val address = selectedAddress
                currentCoroutineContext().ensureActive()
                connector.select(address)
                activeMode = requestedMode; activeTarget = address
                entryPrefs.edit { putString("selected", requestedMode.name) }
                navController.navigate(remoteRoute) {
                    popUpTo(homeRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    BtRemoteTheme(appearance) {
        Surface(Modifier.fillMaxSize(), tonalElevation = surfaceElevationLow()) {
            if (!appScopeViewModel.isBluetoothSupported) BluetoothNotSupportScreen()
            else {
                val pairing = rememberBluetoothPairingState(
                    bluetoothEnabled = bluetoothEnabled,
                    hidRegistered = registered,
                    connected = connection.state == BluetoothProfile.STATE_CONNECTED,
                    autoRequest = route == homeRoute
                )
                CheckBluetoothStateChanged({ appScopeViewModel.isBluetoothEnabled }) { bluetoothEnabled = it }
                LaunchedEffect(bluetoothEnabled) {
                    if (!bluetoothEnabled) {
                        quietAutomation(); BluetoothHidService.stop(context)
                        navController.popBackStack(AppNavDestination.BluetoothActivationDestination.route, false)
                    }
                }
                LaunchedEffect(route, serviceRunning, bluetoothEnabled, lifecycleState, closing) {
                    if (route == homeRoute && bluetoothEnabled && !serviceRunning && !closing &&
                        lifecycleState == Lifecycle.State.RESUMED) BluetoothHidService.start(context)
                }
                LaunchedEffect(route) {
                    if (route == homeRoute) {
                        homeReady = false
                        quietAutomation()
                        deviceViewModel.findBondedDevices().join()
                        homeReady = true
                    }
                }
                LaunchedEffect(route, connection.state, connection.deviceAddress) {
                    if (route == remoteRoute && !connection.isConnectedTo(activeTarget)) {
                        quietAutomation()
                        showHome()
                    }
                }
                AppNavHost(
                    navController = navController,
                    startDestination = remember {
                        if (arePermissionsGranted(context, bluetoothConnectPermissions)) AppNavDestination.BluetoothActivationDestination.route
                        else AppNavDestination.BluetoothPermissionsDestination.route
                    },
                    bluetoothPermissionsScreen = {
                        BluetoothPermissionsScreen(bluetoothConnectPermissions,
                            arePermissionsGranted(context, bluetoothConnectPermissions),
                            onPermissionsGranted = {
                                navController.navigate(AppNavDestination.BluetoothActivationDestination.route) {
                                    popUpTo(0) { saveState = false }; launchSingleTop = true
                                }
                            }, navigateToSettings = settings, modifier = Modifier)
                    },
                    bluetoothActivationScreen = {
                        BluetoothActivationScreen(bluetoothEnabled,
                            refreshBluetoothState = { appScopeViewModel.isBluetoothEnabled.also { bluetoothEnabled = it } },
                            navigateToBluetoothDeviceSelectionScreen = { navController.navigateTo(homeRoute) },
                            navigateToSettings = settings)
                    },
                    deviceSelectionScreen = {
                        ControlHomeScreen(selectedDevice?.name, selectedAddress, homeReady && !busy,
                            onModule = enterModule,
                            onChooseDevice = { navController.navigateTo(pickerRoute) },
                            onSettings = settings,
                            onClose = {
                                closing = true
                                BluetoothHidService.stop(context)
                                context.getActivity()?.moveTaskToBack(true)
                            },
                            connectionStatus = when (connection.state) {
                                BluetoothProfile.STATE_CONNECTED -> "已连接：${connection.deviceName}"
                                BluetoothProfile.STATE_CONNECTING -> "正在连接：${connection.deviceName}"
                                BluetoothProfile.STATE_DISCONNECTING -> "正在断开：${connection.deviceName}"
                                else -> "未连接"
                            })
                    },
                    pairedDevicesScreen = {
                        DevicesSelectionScreen(
                            isBluetoothEnabled = bluetoothEnabled,
                            isBluetoothServiceRunning = serviceRunning,
                            isBluetoothHidProfileRegistered = registered,
                            bluetoothDeviceHidConnectionState = connection,
                            closeApp = { showHome() },
                            navigateToRemoteScreen = {},
                            navigateToDeviceDiscoveryScreen = { navController.navigateTo(AppNavDestination.DeviceDiscoveryDestination.route) },
                            navigateToDistantDevicePairScreen = { navController.navigateTo(AppNavDestination.DistantDevicePairDestination.route) },
                            navigateToSettings = settings,
                            onSelectDevice = selectDevice,
                            selectedAddress = selectedAddress,
                            navigateUp = { showHome() },
                            modeHeader = {
                                Column(Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BluetoothPairingCard(appScopeViewModel.localDeviceName, registered,
                                        connection, pairing)
                                    OutlinedButton(
                                        onClick = { navController.navigateTo(AppNavDestination.DeviceDiscoveryDestination.route) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("搜索新设备") }
                                    Text("点选已配对设备后返回首页，再选择功能开始控制",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        )
                    },
                    deviceDiscoveryScreen = {
                        DeviceDiscoveryScreen(bluetoothScanPermissions,
                            arePermissionsGranted(context, bluetoothScanPermissions), serviceRunning, connection,
                            navigateUp = { navController.navigateUp() }, navigateToRemoteScreen = {},
                            navigateToSettings = settings, onSelectDevice = { address ->
                                transition {
                                    quietAutomation()
                                    connector.select(address)
                                    deviceViewModel.findBondedDevices().join()
                                    rememberDevice(address)
                                }
                            })
                    },
                    distantDevicePairScreen = {
                        DistantDevicePairScreen(appScopeViewModel.localDeviceName,
                            navigateUp = { navController.navigateUp() }, modifier = Modifier)
                    },
                    remoteScreen = {
                        if (connection.isConnectedTo(activeTarget)) {
                            if (activeMode == PhoneControlMode.AI) automationScreen(connection, goHome, settings)
                            else RemoteScreen(connection, closeApp = goHome, navigateToSettings = settings,
                                restoreFullScreen = appearance.useFullScreen,
                                initialEnglishLandscape = activeMode == PhoneControlMode.KEYBOARD,
                                initialControlMode = when (activeMode) {
                                    PhoneControlMode.XBOX -> ControlMode.GAMEPAD
                                    PhoneControlMode.FC -> ControlMode.FC
                                    PhoneControlMode.MEDIA -> ControlMode.MEDIA
                                    PhoneControlMode.MOUSE -> ControlMode.TOUCHPAD
                                    PhoneControlMode.TV -> ControlMode.ORIGINAL
                                    else -> ControlMode.ENGLISH
                                })
                        }
                    },
                    settingsScreen = {
                        SettingsScreen(navigateUp = { navController.navigateUp() },
                            navigateToThirdLibrariesScreen = { navController.navigateTo(AppNavDestination.ThirdLibrariesDestination.route) }, modifier = Modifier)
                    },
                    thirdLibrariesScreen = { ThirdLibrariesScreen(navigateUp = { navController.navigateUp() }, modifier = Modifier) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout.exclude(WindowInsets.systemBars))
                )
                if (busy) LoadingDialog(title = "正在准备控制模式", message = "正在处理连接和待完成的操作…",
                    buttonText = "取消", onButtonClick = { transitionJob?.cancel() })
                entryError?.let { error ->
                    SimpleDialog(confirmButtonText = "知道了", dismissButtonText = "选择设备",
                        onConfirmation = { entryError = null }, onDismissRequest = { entryError = null; showHome(); navController.navigateTo(pickerRoute) },
                        dialogTitle = "暂时无法进入", dialogText = error)
                }
                OnLifecycleEvent { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) closing = false
                    if (event == Lifecycle.Event.ON_RESUME && bluetoothEnabled && arePermissionsGranted(context, bluetoothConnectPermissions)) deviceViewModel.findBondedDevices()
                    if (event == Lifecycle.Event.ON_STOP && route == homeRoute) {
                        transitionJob?.cancel()
                        // Keep the foreground HID service available for host-initiated pairing/connection.
                    }
                }
            }
        }
    }
}
