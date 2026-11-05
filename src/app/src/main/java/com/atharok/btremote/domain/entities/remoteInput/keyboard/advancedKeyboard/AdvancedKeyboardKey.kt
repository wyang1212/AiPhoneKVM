package com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.customButtons.CharacterKeyboardKey
import com.atharok.btremote.ui.components.customButtons.IconSurfaceButton
import com.atharok.btremote.ui.components.customButtons.TextKeyboardKey

sealed class AdvancedKeyboardKey {
    abstract val byte: Byte
    abstract val weight: Float

    @Composable
    abstract fun KeyboardKeyView(
        touchDown: (Byte) -> Unit,
        touchUp: (Byte) -> Unit,
        modifier: Modifier,
        shape: Shape,
        elevation: Dp
    )
}

data class CharacterAdvancedKeyboardKey(
    override val byte: Byte,
    override val weight: Float,
    val text: String,
    val textSecondary: String? = null,
    val textTertiary: String? = null
): AdvancedKeyboardKey() {

    @Composable
    override fun KeyboardKeyView(
        touchDown: (Byte) -> Unit,
        touchUp: (Byte) -> Unit,
        modifier: Modifier,
        shape: Shape,
        elevation: Dp
    ) {
        CharacterKeyboardKey(
            text = text,
            touchDown = { touchDown(byte) },
            touchUp = { touchUp(byte) },
            modifier = modifier,
            textSecondary = textSecondary,
            textTertiary = textTertiary,
            shape = shape,
            elevation = elevation
        )
    }
}

data class TextAdvancedKeyboardKey(
    override val byte: Byte,
    override val weight: Float,
    val text: String,
    val textAlign: TextAlign = TextAlign.Center
): AdvancedKeyboardKey() {

    @Composable
    override fun KeyboardKeyView(
        touchDown: (Byte) -> Unit,
        touchUp: (Byte) -> Unit,
        modifier: Modifier,
        shape: Shape,
        elevation: Dp
    ) {
        TextKeyboardKey(
            text = text,
            touchDown = { touchDown(byte) },
            touchUp = { touchUp(byte) },
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            textAlign = textAlign
        )
    }
}

data class IconAdvancedKeyboardKey(
    val properties: RemoteButtonProperties,
    override val weight: Float
): AdvancedKeyboardKey() {

    override val byte: Byte = properties.input[1]

    @Composable
    override fun KeyboardKeyView(
        touchDown: (Byte) -> Unit,
        touchUp: (Byte) -> Unit,
        modifier: Modifier,
        shape: Shape,
        elevation: Dp
    ) {
        IconSurfaceButton(
            image = properties.icon,
            contentDescription = stringResource(properties.stringRes),
            touchDown = {
                touchDown(byte)
            },
            touchUp = { touchUp(byte) },
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            iconFillFraction = 0.4f
        )
    }
}