package com.atharok.btremote.ui.views.remoteNavigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atharok.btremote.common.utils.ArcShape
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.customButtons.RemoteEmptySurfaceButton
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.theme.surfaceElevationShadow

private val TopArcShape = ArcShape(-45f, -90f)
private val BottomArcShape = ArcShape(45f, 90f)
private val LeftArcShape = ArcShape(135f, 90f)
private val RightArcShape = ArcShape(-45f, 90f)

@Composable
fun RemoteDirectionalPadNavigation(
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    useEnterForSelection: Boolean,
    modifier: Modifier = Modifier,
    elevation: Dp = surfaceElevationMedium()
) {
    val upButtonProperties = RemoteButtonProperties.MenuUpButton
    val downButtonProperties = RemoteButtonProperties.MenuDownButton
    val leftButtonProperties = RemoteButtonProperties.MenuLeftButton
    val rightButtonProperties = RemoteButtonProperties.MenuRightButton
    val pickButtonProperties = RemoteButtonProperties.MenuPickButton

    Box(
        modifier = modifier.shadow(
            elevation = surfaceElevationShadow(),
            shape = CircleShape
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            // ---- Top ----
            RemoteEmptySurfaceButton(
                properties = upButtonProperties,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.fillMaxSize(),
                shape = TopArcShape,
                elevation = elevation,
                shadowElevation = 0.dp
            )

            // ---- Bottom ----
            RemoteEmptySurfaceButton(
                properties = downButtonProperties,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.fillMaxSize(),
                shape = BottomArcShape,
                elevation = elevation,
                shadowElevation = 0.dp
            )

            // ---- Left ----
            RemoteEmptySurfaceButton(
                properties = leftButtonProperties,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.fillMaxSize(),
                shape = LeftArcShape,
                elevation = elevation,
                shadowElevation = 0.dp
            )

            // ---- Right ----
            RemoteEmptySurfaceButton(
                properties = rightButtonProperties,
                sendKeyReport = sendRemoteKeyReport,
                modifier = Modifier.fillMaxSize(),
                shape = RightArcShape,
                elevation = elevation,
                shadowElevation = 0.dp
            )

            // ---- Center ----
            if(useEnterForSelection) {
                RemoteEmptySurfaceButton(
                    properties = RemoteButtonProperties.KeyboardEnterButton,
                    sendKeyReport = sendKeyboardKeyReport,
                    modifier = Modifier.fillMaxSize(1f / 3f),
                    shape = CircleShape,
                    elevation = elevation,
                    shadowElevation = 0.dp
                )
            } else {
                RemoteEmptySurfaceButton(
                    properties = pickButtonProperties,
                    sendKeyReport = sendRemoteKeyReport,
                    modifier = Modifier.fillMaxSize(1f / 3f),
                    shape = CircleShape,
                    elevation = elevation,
                    shadowElevation = 0.dp
                )
            }
        }

        // ---- Icons ----
        Column(modifier = Modifier.wrapContentSize()) {
            Row(
                modifier = Modifier.fillMaxSize().weight(1f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(Modifier.weight(1f).padding(8.dp))
                DPadIcon(
                    properties = upButtonProperties,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                Spacer(Modifier.weight(1f).padding(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                DPadIcon(
                    properties = leftButtonProperties,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                DPadIcon(
                    properties = pickButtonProperties,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                DPadIcon(
                    properties = rightButtonProperties,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(0.dp).weight(1f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(Modifier.weight(1f).padding(8.dp))
                DPadIcon(
                    properties = downButtonProperties,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                Spacer(Modifier.weight(1f).padding(8.dp))
            }
        }
    }
}

@Composable
private fun DPadIcon(
    properties: RemoteButtonProperties,
    modifier: Modifier = Modifier
) {
    Icon(
        modifier = modifier,
        imageVector = properties.icon,
        contentDescription = stringResource(id = properties.stringRes)
    )
}