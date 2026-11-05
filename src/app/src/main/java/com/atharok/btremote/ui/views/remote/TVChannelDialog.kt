package com.atharok.btremote.ui.views.remote

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.R
import com.atharok.btremote.domain.entities.remoteInput.ChannelButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.TemplateDialog
import com.atharok.btremote.ui.components.TextLarge
import com.atharok.btremote.ui.components.customButtons.ChannelSurfaceButton
import com.atharok.btremote.ui.components.customButtons.RemoteIconSurfaceButton
import com.atharok.btremote.ui.theme.surfaceElevationHigh

@Composable
fun TVChannelDialog(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orientation = LocalConfiguration.current.orientation
    TemplateDialog(
        title = {
            TextLarge(text = stringResource(R.string.tv))
        },
        content = {
            TVChannelLayout(
                sendRemoteKeyReport = sendRemoteKeyReport,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = if(orientation == Configuration.ORIENTATION_LANDSCAPE)
                    Modifier.fillMaxSize()
                else
                    Modifier.aspectRatio(3f / 4f)
            )
        },
        modifier = modifier,
        dismissButtonText = stringResource(R.string.close),
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun TVChannelLayout(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    buttonShape: Shape = CircleShape,
    buttonElevation: Dp = surfaceElevationHigh(),
    padding: Dp = dimensionResource(id = R.dimen.padding_large)
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Absolute.spacedBy(padding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel1Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel2Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel3Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel4Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel5Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel6Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.Absolute.spacedBy(padding)
        ) {
            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel7Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel8Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel9Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(padding)
        ) {
            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.ChannelDownButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel0Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.ChannelUpButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f),
                shape = buttonShape,
                elevation = buttonElevation
            )
        }
    }
}