package com.atharok.btremote.ui.views.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.R
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.TemplateDialog
import com.atharok.btremote.ui.components.TextLarge
import com.atharok.btremote.ui.components.TextSmall
import com.atharok.btremote.ui.components.customButtons.RemoteIconSurfaceButton
import com.atharok.btremote.ui.theme.surfaceElevationHigh
import com.atharok.btremote.ui.theme.surfaceElevationMedium

@Composable
fun MoreButtonsDialog(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    TemplateDialog(
        title = {
            TextLarge(text = stringResource(R.string.more_buttons))
        },
        content = {
            MoreButtonsLayout(
                sendRemoteKeyReport = sendRemoteKeyReport,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier
            )
        },
        modifier = modifier,
        dismissButtonText = stringResource(R.string.close),
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun MoreButtonsLayout(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    buttonShape: Shape = CircleShape,
    buttonElevation: Dp = surfaceElevationHigh(),
    padding: Dp = dimensionResource(id = R.dimen.padding_large)
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(padding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.PlayButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.PauseButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.StopButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.RepeatButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.RewindButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.ForwardButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.PreviousTrackButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.NextTrackButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.ClosedCaptionsButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.KeyboardPrintScreenButton,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.BrightnessDownButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.BrightnessUpButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.RedMenuButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation,
                buttonIconTint = Color.Red.copy(alpha = 0.5f)
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.GreenMenuButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation,
                buttonIconTint = Color.Green.copy(alpha = 0.5f)
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.BlueMenuButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation,
                buttonIconTint = Color.Blue.copy(alpha = 0.5f)
            )

            MoreRemoteIconLayout(
                properties = RemoteButtonProperties.YellowMenuButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                buttonShape = buttonShape,
                buttonElevation = buttonElevation,
                buttonIconTint = Color.Yellow.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun MoreRemoteIconLayout(
    properties: RemoteButtonProperties,
    sendKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    buttonShape: Shape = RectangleShape,
    buttonElevation: Dp = surfaceElevationMedium(),
    buttonIconTint: Color = LocalContentColor.current
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RemoteIconSurfaceButton(
            properties = properties,
            sendKeyReport = sendKeyReport,
            modifier = Modifier.aspectRatio(1f),
            shape = buttonShape,
            elevation = buttonElevation,
            iconTint = buttonIconTint
        )
        TextSmall(text = stringResource(properties.stringRes))
    }
}