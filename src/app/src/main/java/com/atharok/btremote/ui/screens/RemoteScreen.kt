// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.screens

import android.bluetooth.BluetoothHidDevice
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.R
import com.atharok.btremote.common.extensions.getActivity
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.getKeyboardLayout
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.RemoteNavigationEntity
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.PhysicalVolumeButtonAction
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardLanguage
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.presentation.services.BluetoothHidService
import com.atharok.btremote.presentation.viewmodel.RemoteViewModel
import com.atharok.btremote.ui.components.GamepadFullScreen
import com.atharok.btremote.ui.components.BasicDropdownMenuItem
import com.atharok.btremote.ui.components.BasicIconButton
import com.atharok.btremote.ui.components.FadeAnimatedContent
import com.atharok.btremote.ui.components.LoadingDialog
import com.atharok.btremote.ui.components.MoreOverflowMenu
import com.atharok.btremote.ui.components.PhysicalVolumeButtonsHandler
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.views.ControlMode
import com.atharok.btremote.ui.views.EnglishKeyboardPanel
import com.atharok.btremote.ui.views.GamepadControlPanel
import com.atharok.btremote.ui.views.MediaControlPanel
import com.atharok.btremote.ui.views.TouchpadControlPanel
import com.atharok.btremote.ui.views.RemoteScreenHelpModalBottomSheet
import com.atharok.btremote.ui.views.keyboard.AdvancedKeyboard
import com.atharok.btremote.ui.views.keyboard.AdvancedKeyboardModalBottomSheet
import com.atharok.btremote.ui.views.keyboard.VirtualKeyboardModalBottomSheet
import com.atharok.btremote.ui.views.mouse.MousePadLayout
import com.atharok.btremote.ui.views.remote.MinimalistRemoteView
import com.atharok.btremote.ui.views.remote.MoreButtonsDialog
import com.atharok.btremote.ui.views.remote.RemoteView
import com.atharok.btremote.ui.views.remote.TVChannelDialog
import com.atharok.btremote.ui.views.remoteNavigation.RemoteDirectionalPadNavigation
import com.atharok.btremote.ui.views.remoteNavigation.RemoteSwipeNavigation
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch

internal enum class NavigationToggle {
    DIRECTION,
    MOUSE
}

@Composable
fun RemoteScreen(
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    closeApp: () -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialControlMode: ControlMode = ControlMode.ENGLISH,
    initialEnglishLandscape: Boolean = false,
    restoreFullScreen: Boolean = false,
    remoteViewModel: RemoteViewModel = koinViewModel(),
    context: Context = LocalContext.current
) {
    val configuration = LocalConfiguration.current

    val remoteSettings by remoteViewModel
        .remoteSettingsFlow.collectAsStateWithLifecycle(RemoteSettings())

    val controlMode by rememberSaveable(initialControlMode, bluetoothDeviceHidConnectionState.deviceAddress) { mutableStateOf(initialControlMode) }
    GamepadFullScreen(controlMode == ControlMode.GAMEPAD || controlMode == ControlMode.FC, restoreFullScreen)
    val englishLandscape by rememberSaveable(initialControlMode, initialEnglishLandscape, bluetoothDeviceHidConnectionState.deviceAddress) { mutableStateOf(initialEnglishLandscape) }
    val activity = context.getActivity()
    val previousOrientation = remember(activity) { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = previousOrientation }
    }
    LaunchedEffect(activity, controlMode, englishLandscape) {
        activity?.requestedOrientation = if (controlMode == ControlMode.ENGLISH) {
            if (englishLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (controlMode == ControlMode.GAMEPAD || controlMode == ControlMode.FC) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else previousOrientation
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controlMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) remoteViewModel.releaseAllInputs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            remoteViewModel.releaseAllInputs()
        }
    }

    // Remote
    var navigationToggle by rememberSaveable(remoteSettings.useMouseNavigationByDefault) { mutableStateOf(if(remoteSettings.useMouseNavigationByDefault) NavigationToggle.MOUSE else NavigationToggle.DIRECTION) }

    // Keyboard
    var isKeyboardVisible: Boolean by rememberSaveable { mutableStateOf(false) }

    // More Buttons
    var isMoreButtonsVisible: Boolean by rememberSaveable { mutableStateOf(false) }

    // Help
    var isHelpBottomSheetVisible: Boolean by remember { mutableStateOf(false) }

    val exitScope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    fun leave(action: () -> Unit) {
        if (leaving) return
        leaving = true
        exitScope.launch {
            try {
                remoteViewModel.releaseAllInputsAndAwait()
                action()
            } finally { leaving = false }
        }
    }
    BackHandler(enabled = true) { leave(closeApp) }

    PhysicalVolumeButtonsHandler(
        upAction = remoteSettings.physicalVolumeUpButtonAction,
        downAction = remoteSettings.physicalVolumeDownButtonAction,
        remoteViewModel = remoteViewModel
    )

    StatelessRemoteScreen(
        isLandscapeMode = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        controlMode = controlMode,
        modeContent = {
            key(controlMode) {
                when (controlMode) {
                    ControlMode.ENGLISH -> EnglishKeyboardPanel(remoteViewModel.sendKeyboardReport, remoteSettings, remoteViewModel.sendMouseReport, Modifier.fillMaxSize())
                    ControlMode.MEDIA -> MediaControlPanel(remoteViewModel.sendRemoteReport, Modifier.fillMaxSize())
                    ControlMode.TOUCHPAD -> TouchpadControlPanel(remoteSettings, remoteViewModel.sendMouseReport, Modifier.fillMaxSize())
                    ControlMode.GAMEPAD -> GamepadControlPanel(remoteViewModel.sendGamepadReport, Modifier.fillMaxSize())
                    ControlMode.FC -> GamepadControlPanel(remoteViewModel.sendGamepadReport, Modifier.fillMaxSize(), fc = true)
                    ControlMode.ORIGINAL -> Unit
                }
            }
        },
        originalActions = {
            TopBarActions(
                navigateToSettings = { leave(navigateToSettings) },
                disconnectDevice = {
                    leave { remoteViewModel.disconnectDevice() }
                },
                navigationToggle = navigationToggle,
                onNavigationToggleChanged = { navigationToggle = it },
                useAdvancedKeyboardIntegrated = remoteSettings.useAdvancedKeyboard && remoteSettings.useAdvancedKeyboardIntegrated,
                isKeyboardVisible = isKeyboardVisible,
                onKeyboardVisibleChanged = { isKeyboardVisible = it },
                isMoreButtonsVisible = isMoreButtonsVisible,
                onMoreButtonsVisibleChanged = { isMoreButtonsVisible = it },
                isHelpBottomSheetVisible = isHelpBottomSheetVisible,
                onHelpBottomSheetVisibleChanged = { isHelpBottomSheetVisible = it }
            )
        },
        remoteLayout = {
            RemoteLayout(
                useMinimalistRemote = remoteSettings.useMinimalistRemote,
                isAdvancedKeyboardVisible = remoteSettings.useAdvancedKeyboard && remoteSettings.useAdvancedKeyboardIntegrated && isKeyboardVisible,
                onMoreButtonsVisibleChanged = { isMoreButtonsVisible = !isMoreButtonsVisible },
                keyboardLanguage = remoteSettings.keyboardLanguage,
                sendRemoteKeyReport = remoteViewModel.sendRemoteReport,
                sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport
            )
        },
        navigationLayout = {
            NavigationLayout(
                remoteSettings = remoteSettings,
                sendRemoteKeyReport = remoteViewModel.sendRemoteReport,
                sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport,
                sendMouseKeyReport = remoteViewModel.sendMouseReport,
                navigationToggle = navigationToggle,
            )
        },
        overlayView = {
            // Dialog / ModalBottomSheet
            when {
                bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_DISCONNECTING -> {
                    LoadingDialog(
                        title = stringResource(id = R.string.connection),
                        message = stringResource(id = R.string.bluetooth_device_disconnecting_message, bluetoothDeviceHidConnectionState.deviceName),
                        buttonText = stringResource(id = R.string.disconnect),
                        onButtonClick = {
                            remoteViewModel.disconnectDevice()
                            BluetoothHidService.stop(context) // force disconnection
                        }
                    )
                }
                isHelpBottomSheetVisible -> {
                    RemoteScreenHelpModalBottomSheet(
                        onDismissRequest = { isHelpBottomSheetVisible = false },
                        modifier = modifier
                    )
                }
                isKeyboardVisible && (!remoteSettings.useAdvancedKeyboard || !remoteSettings.useAdvancedKeyboardIntegrated) -> {
                    KeyboardModalBottomSheet(
                        useAdvancedKeyboard = remoteSettings.useAdvancedKeyboard,
                        keyboardLanguage = remoteSettings.keyboardLanguage,
                        mustClearInputField = remoteSettings.mustClearInputField,
                        sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport,
                        sendTextReport = remoteViewModel.sendTextReport,
                        onShowKeyboardChanged = { isKeyboardVisible = it }
                    )
                }
                isMoreButtonsVisible -> {
                    MoreButtonsDialog(
                        sendRemoteKeyReport = remoteViewModel.sendRemoteReport,
                        sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport,
                        onDismissRequest = { isMoreButtonsVisible = false }
                    )
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatelessRemoteScreen(
    isLandscapeMode: Boolean,
    controlMode: ControlMode,
    modeContent: @Composable () -> Unit,
    originalActions: @Composable (RowScope.() -> Unit),
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    overlayView: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (controlMode == ControlMode.GAMEPAD || controlMode == ControlMode.FC) {
        Box(modifier.fillMaxSize()) { modeContent() }
        overlayView()
        return
    }
    Scaffold(modifier = modifier) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (controlMode == ControlMode.ORIGINAL) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = originalActions)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (controlMode != ControlMode.ORIGINAL) {
                    modeContent()
                } else if (isLandscapeMode) {
                    RemoteLandscapeView(remoteLayout, navigationLayout, Modifier.fillMaxSize())
                } else {
                    RemotePortraitView(remoteLayout, navigationLayout, Modifier.fillMaxSize())
                }
            }
        }

        // Dialog / ModalBottomSheet
        overlayView()
    }
}

@Composable
private fun RemoteLandscapeView(
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var rowSize by remember { mutableStateOf(Size.Zero) }

    Row(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            rowSize = layoutCoordinates.size.toSize() },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = with(LocalDensity.current) { (0.5f * rowSize.width).toDp() })
                .align(Alignment.CenterVertically)
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    top = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                ),
        ) {
            navigationLayout()
        }

        Box(
            modifier = Modifier.align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            remoteLayout()
        }
    }
}

@Composable
private fun RemotePortraitView(
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .heightIn(
                    max = with(LocalConfiguration.current) {
                        if (screenHeightDp >= screenWidthDp * 1.9f) // Si la hauteur de l'appareil est suffisamment haute par rapport à sa largeur (ratio ~ 1/2)
                            screenWidthDp.dp // On peut se permettre de prendre pour hauteur la largeur de l'écran
                        else // Sinon
                            (screenHeightDp * 0.50).dp // On prend 50% de la hauteur de l'écran
                    }
                )
                .align(Alignment.CenterHorizontally),
        ) {
            remoteLayout()
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    end = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                ),
            contentAlignment = Alignment.Center
        ) {
            navigationLayout()
        }
    }
}

@Composable
internal fun RemoteLayout(
    useMinimalistRemote: Boolean,
    isAdvancedKeyboardVisible: Boolean,
    onMoreButtonsVisibleChanged: () -> Unit,
    keyboardLanguage: KeyboardLanguage,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit
) {
    FadeAnimatedContent(targetState = isAdvancedKeyboardVisible) {
        if (it) {
            AdvancedKeyboard(
                keyboardLanguage = keyboardLanguage,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_large)),
                keyElevation = surfaceElevationMedium()
            )
        } else {
            RemoteButtonsLayouts(
                useMinimalistRemote = useMinimalistRemote,
                onMoreButtonsVisibleChanged = onMoreButtonsVisibleChanged,
                sendRemoteKeyReport = sendRemoteKeyReport,
                sendKeyboardKeyReport = sendKeyboardKeyReport
            )
        }
    }
}

@Composable
private fun RemoteButtonsLayouts(
    useMinimalistRemote: Boolean,
    onMoreButtonsVisibleChanged: () -> Unit,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit
) {
    if (useMinimalistRemote) {
        var isTVChannelButtonsVisible: Boolean by remember { mutableStateOf(false) }

        MinimalistRemoteView(
            sendRemoteKeyReport = sendRemoteKeyReport,
            onTVChannelButtonsChanged = {
                isTVChannelButtonsVisible = !isTVChannelButtonsVisible
            },
            onMoreButtonsVisibleChanged = onMoreButtonsVisibleChanged,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_normal))
        )

        if (isTVChannelButtonsVisible) {
            TVChannelDialog(
                sendRemoteKeyReport = sendRemoteKeyReport,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                onDismissRequest = {
                    isTVChannelButtonsVisible = false
                }
            )
        }
    } else {
        RemoteView(
            sendRemoteKeyReport = sendRemoteKeyReport,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_normal))
        )
    }
}

@Composable
internal fun NavigationLayout(
    remoteSettings: RemoteSettings,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendMouseKeyReport: (input: MouseAction, x: Float, y: Float, wheel: Float) -> Unit,
    navigationToggle: NavigationToggle
) {
    FadeAnimatedContent(targetState = navigationToggle) {
        when(it) {
            NavigationToggle.DIRECTION -> {
                RemotePadLayout(
                    remoteNavigationMode = remoteSettings.remoteNavigationEntity,
                    sendRemoteKeyReport = sendRemoteKeyReport,
                    sendKeyboardKeyReport = sendKeyboardKeyReport,
                    useEnterForSelection = remoteSettings.useEnterForSelection,
                )
            }

            NavigationToggle.MOUSE -> {
                MousePadLayout(
                    mouseSpeed = remoteSettings.mouseSpeed,
                    scrollSpeed = remoteSettings.mouseScrollSpeed,
                    shouldInvertMouseScrollingDirection = remoteSettings.shouldInvertMouseScrollingDirection,
                    useGyroscope = remoteSettings.useGyroscope,
                    sendMouseInput = sendMouseKeyReport,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun RemotePadLayout(
    remoteNavigationMode: RemoteNavigationEntity,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    useEnterForSelection: Boolean
) {
    if(remoteNavigationMode == RemoteNavigationEntity.D_PAD) {
        RemoteDirectionalPadNavigation(
            sendRemoteKeyReport = sendRemoteKeyReport,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            useEnterForSelection = useEnterForSelection,
            modifier = Modifier.aspectRatio(1f)
        )
    } else {
        RemoteSwipeNavigation(
            sendRemoteKeyReport = sendRemoteKeyReport,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            useEnterForSelection = useEnterForSelection,
            modifier = Modifier
        )
    }
}

@Composable
private fun KeyboardModalBottomSheet(
    useAdvancedKeyboard: Boolean,
    keyboardLanguage: KeyboardLanguage,
    mustClearInputField: Boolean,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, VirtualKeyboardLayout, Boolean) -> Unit,
    onShowKeyboardChanged: (Boolean) -> Unit,
) {
    if (useAdvancedKeyboard) {
        AdvancedKeyboardModalBottomSheet(
            keyboardLanguage = keyboardLanguage,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            onShowKeyboardBottomSheetChanged = onShowKeyboardChanged
        )
    } else {
        var virtualKeyboardLayout: VirtualKeyboardLayout by remember {
            mutableStateOf(getKeyboardLayout(keyboardLanguage))
        }

        LaunchedEffect(keyboardLanguage) {
            virtualKeyboardLayout = getKeyboardLayout(keyboardLanguage)
        }

        VirtualKeyboardModalBottomSheet(
            mustClearInputField = mustClearInputField,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            sendTextReport = { text: String, shouldSendEnter: Boolean ->
                sendTextReport(text, virtualKeyboardLayout, shouldSendEnter)
            },
            onShowKeyboardBottomSheetChanged = onShowKeyboardChanged,
            virtualKeyboardLayout = virtualKeyboardLayout
        )
    }
}

@Composable
internal fun TopBarActions(
    navigateToSettings: () -> Unit,
    disconnectDevice: () -> Unit,
    navigationToggle: NavigationToggle,
    onNavigationToggleChanged: (NavigationToggle) -> Unit,
    useAdvancedKeyboardIntegrated: Boolean,
    isKeyboardVisible: Boolean,
    onKeyboardVisibleChanged: (Boolean) -> Unit,
    isMoreButtonsVisible: Boolean,
    onMoreButtonsVisibleChanged: (Boolean) -> Unit,
    isHelpBottomSheetVisible: Boolean,
    onHelpBottomSheetVisibleChanged: (Boolean) -> Unit
) {
    FadeAnimatedContent(targetState = navigationToggle) {
        when (it) {
            NavigationToggle.MOUSE -> {
                BasicIconButton(
                    onClick = {
                        onNavigationToggleChanged(NavigationToggle.DIRECTION)
                    },
                    icon = AppIcons.Controller,
                    contentDescription = stringResource(id = R.string.direction_arrows),
                )
            }

            NavigationToggle.DIRECTION -> {
                BasicIconButton(
                    onClick = {
                        onNavigationToggleChanged(NavigationToggle.MOUSE)
                    },
                    icon = AppIcons.Mouse,
                    contentDescription = stringResource(id = R.string.mouse),
                )
            }
        }
    }

    if(useAdvancedKeyboardIntegrated) {
        FadeAnimatedContent(targetState = isKeyboardVisible) {
            when (it) {
                true -> {
                    // Remote
                    BasicIconButton(
                        onClick = { onKeyboardVisibleChanged(false) },
                        icon = AppIcons.RemoteControl,
                        contentDescription = stringResource(id = R.string.remote)
                    )
                }

                false -> {
                    // Keyboard
                    BasicIconButton(
                        onClick = { onKeyboardVisibleChanged(true) },
                        icon = AppIcons.Keyboard,
                        contentDescription = stringResource(id = R.string.keyboard)
                    )
                }
            }
        }
    } else {
        // Keyboard
        BasicIconButton(
            onClick = { onKeyboardVisibleChanged(!isKeyboardVisible) },
            icon = AppIcons.Keyboard,
            contentDescription = stringResource(id = R.string.keyboard)
        )
    }

    MoreOverflowMenu { closeDropdownMenu: () -> Unit ->

        // More Buttons
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.more_buttons),
            icon = AppIcons.MoreHoriz,
            onClick = {
                closeDropdownMenu()
                onMoreButtonsVisibleChanged(!isMoreButtonsVisible)
            }
        )

        // Disconnect
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.disconnect),
            icon = AppIcons.Disconnect,
            onClick = {
                closeDropdownMenu()
                disconnectDevice()
            }
        )

        // Divider
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.padding_medium))
        )

        // Help
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.help),
            icon = AppIcons.Help,
            onClick = {
                closeDropdownMenu()
                onHelpBottomSheetVisibleChanged(!isHelpBottomSheetVisible)
            }
        )

        // Settings
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.settings),
            icon = AppIcons.Settings,
            onClick = {
                closeDropdownMenu()
                navigateToSettings()
            }
        )
    }
}

@Composable
private fun PhysicalVolumeButtonsHandler(
    upAction: PhysicalVolumeButtonAction,
    downAction: PhysicalVolumeButtonAction,
    remoteViewModel: RemoteViewModel
) {
    if(upAction != PhysicalVolumeButtonAction.DEFAULT || downAction != PhysicalVolumeButtonAction.DEFAULT) {
        PhysicalVolumeButtonsHandler(
            onVolumeUpPressed = {
                upAction.doPhysicalVolumeAction {
                    remoteViewModel.sendReport(it.hidReportId, it.pressedBytes)
                }
            },
            onVolumeUpReleased = {
                upAction.doPhysicalVolumeAction {
                    remoteViewModel.sendReport(it.hidReportId, it.releasedBytes)
                }
            },
            onVolumeDownPressed = {
                downAction.doPhysicalVolumeAction {
                    remoteViewModel.sendReport(it.hidReportId, it.pressedBytes)
                }
            },
            onVolumeDownReleased = {
                downAction.doPhysicalVolumeAction {
                    remoteViewModel.sendReport(it.hidReportId, it.releasedBytes)
                }
            }
        )
    }
}

private fun PhysicalVolumeButtonAction.doPhysicalVolumeAction(
    sendReport: (PhysicalVolumeButtonAction) -> Unit
): Boolean {
    return when (this) {
        PhysicalVolumeButtonAction.DEFAULT -> false
        else -> { sendReport(this); true }
    }
}
