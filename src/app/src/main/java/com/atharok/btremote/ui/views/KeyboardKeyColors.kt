// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.ui.views

import androidx.compose.ui.graphics.Color

internal data class KeyboardKeyColors(val background: Color, val foreground: Color)

private val homeKeys = KeyboardKeyColors(Color(0xffc62828), Color.White)
private val letters = KeyboardKeyColors(Color(0xffdceaff), Color(0xff173b65))
private val numbers = KeyboardKeyColors(Color(0xffd5f3e4), Color(0xff17523b))
private val symbols = KeyboardKeyColors(Color(0xffffefb8), Color(0xff664b0b))
private val functions = KeyboardKeyColors(Color(0xffe8ddfb), Color(0xff4c2873))
private val modifiers = KeyboardKeyColors(Color(0xffffdfbd), Color(0xff703b13))
private val selectedModifier = KeyboardKeyColors(Color(0xffa64b00), Color.White)
private val editing = KeyboardKeyColors(Color(0xffcef0f4), Color(0xff18505b))
private val space = KeyboardKeyColors(Color(0xffdde3ea), Color(0xff293845))

/** Color only: never infer or change the key's HID report from its visual group. */
internal fun keyboardKeyColors(
    character: Char? = null,
    usage: Int = 0,
    modifier: Boolean = false,
    layerSwitch: Boolean = false,
    active: Boolean = false
): KeyboardKeyColors = when {
    modifier -> if (active) selectedModifier else modifiers
    character?.lowercaseChar() in listOf('f', 'j') -> homeKeys
    character?.isLetter() == true -> letters
    character?.isDigit() == true -> numbers
    character == ' ' || usage == 0x2c -> space
    character != null -> symbols
    layerSwitch || usage == 0x29 || usage in 0x3a..0x45 -> functions
    else -> editing
}
