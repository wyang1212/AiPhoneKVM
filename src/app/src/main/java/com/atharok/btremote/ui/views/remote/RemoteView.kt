package com.atharok.btremote.ui.views.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.ChannelButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.customButtons.ChannelSurfaceButton
import com.atharok.btremote.ui.components.customButtons.IconSurfaceButton
import com.atharok.btremote.ui.components.customButtons.RemoteIconSurfaceButton

@Composable
fun RemoteView(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    buttonShape: Shape = CircleShape
) {
    val padding: Dp = dimensionResource(id = R.dimen.padding_normal)

    Column(modifier = modifier) {
        MultimediaButtonsLayout(
            sendRemoteKeyReport = sendRemoteKeyReport,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(padding),
            shape = buttonShape
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                VerticalButtonsLayout(
                    topButtonProperties = RemoteButtonProperties.VolumeUpButton,
                    bottomButtonProperties = RemoteButtonProperties.VolumeDownButton,
                    sendRemoteKeyReport = sendRemoteKeyReport,
                    modifier = Modifier
                        .weight(2f)
                        .padding(padding)
                        .align(Alignment.Start),
                    shape = buttonShape
                )

                RemoteIconSurfaceButton(
                    properties = RemoteButtonProperties.VolumeMuteButton,
                    sendKeyReport = sendRemoteKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel1Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )

                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel4Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )

                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel7Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel2Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )

                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel5Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )

                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel8Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel3Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )

                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel6Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )

                ChannelSurfaceButton(
                    properties = ChannelButtonProperties.Channel9Button,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                VerticalButtonsLayout(
                    topButtonProperties = RemoteButtonProperties.ChannelUpButton,
                    bottomButtonProperties = RemoteButtonProperties.ChannelDownButton,
                    sendRemoteKeyReport = sendRemoteKeyReport,
                    modifier = Modifier
                        .weight(2f)
                        .padding(padding)
                        .align(Alignment.End),
                    shape = buttonShape
                )

                RemoteIconSurfaceButton(
                    properties = RemoteButtonProperties.ClosedCaptionsButton,
                    sendKeyReport = sendRemoteKeyReport,
                    modifier = Modifier.weight(1f).padding(padding),
                    shape = buttonShape
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.BackButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.HomeButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            ChannelSurfaceButton(
                properties = ChannelButtonProperties.Channel0Button,
                sendKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.MenuButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.PowerButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )
        }
    }
}

@Composable
fun MinimalistRemoteView(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    onTVChannelButtonsChanged: () -> Unit,
    onMoreButtonsVisibleChanged: () -> Unit,
    modifier: Modifier = Modifier,
    buttonShape: Shape = CircleShape
) {
    val padding: Dp = dimensionResource(id = R.dimen.padding_normal)

    Column(modifier = modifier) {
        MultimediaButtonsLayout(
            sendRemoteKeyReport = sendRemoteKeyReport,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(padding),
            shape = buttonShape
        )

        Row(
            modifier = Modifier.fillMaxWidth().weight(2f),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            Box(
                modifier = Modifier.weight(1f).padding(padding),
                contentAlignment = Alignment.CenterStart
            ) {
                VerticalButtonsLayout(
                    topButtonProperties = RemoteButtonProperties.VolumeUpButton,
                    bottomButtonProperties = RemoteButtonProperties.VolumeDownButton,
                    sendRemoteKeyReport = sendRemoteKeyReport,
                    modifier = Modifier,
                    shape = buttonShape
                )
            }

            Column(
                modifier = Modifier.weight(2f)
            ) {
                Row(
                    modifier = Modifier.weight(1f)
                ) {
                    RemoteIconSurfaceButton(
                        properties = RemoteButtonProperties.VolumeMuteButton,
                        sendKeyReport = sendRemoteKeyReport,
                        modifier = Modifier.weight(1f).padding(padding),
                        shape = buttonShape
                    )

                    RemoteIconSurfaceButton(
                        properties = RemoteButtonProperties.ClosedCaptionsButton,
                        sendKeyReport = sendRemoteKeyReport,
                        modifier = Modifier.weight(1f).padding(padding),
                        shape = buttonShape
                    )
                }

                Row(
                    modifier = Modifier.weight(1f)
                ) {
                    IconSurfaceButton(
                        image = AppIcons.TVChannel,
                        contentDescription = stringResource(id = R.string.tv),
                        touchDown = {},
                        touchUp = onTVChannelButtonsChanged,
                        modifier = Modifier.weight(1f).padding(padding),
                        shape = buttonShape
                    )

                    IconSurfaceButton(
                        image = AppIcons.MoreHoriz,
                        contentDescription = stringResource(id = R.string.more_buttons),
                        touchDown = {},
                        touchUp = onMoreButtonsVisibleChanged,
                        modifier = Modifier.weight(1f).padding(padding),
                        shape = buttonShape
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).padding(padding),
                contentAlignment = Alignment.CenterEnd
            ) {
                VerticalButtonsLayout(
                    topButtonProperties = RemoteButtonProperties.BrightnessUpButton,
                    bottomButtonProperties = RemoteButtonProperties.BrightnessDownButton,
                    sendRemoteKeyReport = sendRemoteKeyReport,
                    modifier = Modifier,
                    shape = buttonShape
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.BackButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.HomeButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.MenuButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )

            RemoteIconSurfaceButton(
                properties = RemoteButtonProperties.PowerButton,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.weight(1f).padding(padding),
                shape = buttonShape
            )
        }
    }
}