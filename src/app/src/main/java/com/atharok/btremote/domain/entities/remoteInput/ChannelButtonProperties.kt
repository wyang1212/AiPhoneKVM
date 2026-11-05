package com.atharok.btremote.domain.entities.remoteInput

import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardKey

enum class ChannelButtonProperties(
    val input: ByteArray,
    val text: String
) {
    Channel1Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_01.byte),
        text = "1",
    ),

    Channel2Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_02.byte),
        text = "2",
    ),

    Channel3Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_03.byte),
        text = "3",
    ),

    Channel4Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_04.byte),
        text = "4",
    ),

    Channel5Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_05.byte),
        text = "5",
    ),

    Channel6Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_06.byte),
        text = "6",
    ),

    Channel7Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_07.byte),
        text = "7",
    ),

    Channel8Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_08.byte),
        text = "8",
    ),

    Channel9Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_09.byte),
        text = "9",
    ),

    Channel0Button(
        input = byteArrayOf(0x00, KeyboardKey.ROW_1_KEY_10.byte),
        text = "0",
    )
}