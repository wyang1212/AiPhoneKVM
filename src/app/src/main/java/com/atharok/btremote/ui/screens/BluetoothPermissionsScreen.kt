package com.atharok.btremote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.arePermissionsGranted
import com.atharok.btremote.ui.views.ActivationView

@Composable
fun BluetoothPermissionsScreen(
    permissions: Array<String>,
    arePermissionsGranted: Boolean,
    onPermissionsGranted: () -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionState = remember { mutableStateOf(arePermissionsGranted) }

    if(permissionState.value) {
        LaunchedEffect(Unit) {
            onPermissionsGranted()
        }
    } else {
        var shouldAskPermissions: Boolean by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { result: Map<String, Boolean> ->
                shouldAskPermissions = false
                permissionState.value = arePermissionsGranted(result)
            }
        )

        LaunchedEffect(shouldAskPermissions) {
            if(shouldAskPermissions) {
                launcher.launch(permissions)
            }
        }

        ActivationView(
            topBarTitle = stringResource(id = R.string.permission),
            image = AppIcons.Lock,
            title = stringResource(id = R.string.bluetooth_permission_not_granted),
            message = stringResource(id = R.string.bluetooth_permission_message),
            buttonIcon = AppIcons.Key,
            buttonText = stringResource(id = R.string.bluetooth_permission_button),
            buttonOnClick = { shouldAskPermissions = true },
            hideButton = false,
            navigateToSettings = navigateToSettings,
            modifier = modifier
        )
    }
}