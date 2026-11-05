package com.atharok.btremote.ui.components.customButtons

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.ChannelButtonProperties
import com.atharok.btremote.ui.components.AdaptiveText
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.theme.surfaceElevationShadow

// ---- Channel Button ----

@Composable
fun ChannelSurfaceButton(
    properties: ChannelButtonProperties,
    sendKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    @FloatRange(from = 0.0, to = 1.0) percent: Float = 0.45f,
    shape: Shape = RectangleShape,
    elevation: Dp = surfaceElevationMedium(),
    shadowElevation: Dp = surfaceElevationShadow()
) {
    SurfaceButton(
        touchDown = { sendKeyReport(properties.input) },
        touchUp = { sendKeyReport(REMOTE_INPUT_NONE) },
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        shadowElevation = shadowElevation
    ) {
        AdaptiveText(
            text = properties.text,
            percent = percent,
            modifier = Modifier.fillMaxSize(),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ---- Keyboard Key ----

@Composable
fun CharacterKeyboardKey(
    text: String,
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    modifier: Modifier = Modifier,
    textSecondary: String? = null,
    textTertiary: String? = null,
    shape: Shape = RoundedCornerShape(dimensionResource(id = R.dimen.keyboard_key_corner_radius)),
    elevation: Dp = surfaceElevationMedium()
) {
    SurfaceButton(
        touchDown = touchDown,
        touchUp = touchUp,
        modifier = modifier,
        shape = shape,
        elevation = elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.padding_min)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            textSecondary?.let {
                AdaptiveText(
                    text = it,
                    percent = 0.8f,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textAlign = TextAlign.Start
                )
            } ?: run {
                Spacer(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            AdaptiveText(
                text = text,
                percent = 0.8f,
                modifier = Modifier.fillMaxWidth().weight(1f),
                textAlign = TextAlign.Center
            )

            textTertiary?.let {
                AdaptiveText(
                    text = it,
                    percent = 0.8f,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textAlign = TextAlign.End
                )
            } ?: run {
                Spacer(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
fun TextKeyboardKey(
    text: String,
    touchDown: () -> Unit,
    touchUp: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(dimensionResource(id = R.dimen.keyboard_key_corner_radius)),
    elevation: Dp = surfaceElevationMedium(),
    textAlign: TextAlign = TextAlign.Center
) {
    SurfaceButton(
        touchDown = touchDown,
        touchUp = touchUp,
        modifier = modifier,
        shape = shape,
        elevation = elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.padding_small)),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveText(
                text = text,
                percent = 0.8f,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(1f / 3f),
                textAlign = textAlign
            )
        }
    }
}