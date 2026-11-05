package com.atharok.btremote.domain.entities.remoteInput

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardKey

enum class RemoteButtonProperties(
    val input: ByteArray,
    val icon: ImageVector,
    @param:StringRes val stringRes: Int
) {

    // ---- Remote Control ----
    // https://source.android.com/docs/core/interaction/input/keyboard-devices#hid-consumer-page-0x0c

    // Navigation

    MenuButton(
        input = byteArrayOf(0x40, 0x00),
        icon = AppIcons.Menu,
        stringRes = R.string.menu
    ),

    MenuPickButton(
        input = byteArrayOf(0x41, 0x00),
        icon = AppIcons.Pick,
        stringRes = R.string.pick
    ),

    MenuUpButton(
        input = byteArrayOf(0x42, 0x00),
        icon = AppIcons.Up,
        stringRes = R.string.up
    ),

    MenuDownButton(
        input = byteArrayOf(0x43, 0x00),
        icon = AppIcons.Down,
        stringRes = R.string.down
    ),

    MenuLeftButton(
        input = byteArrayOf(0x44, 0x00),
        icon = AppIcons.Left,
        stringRes = R.string.left
    ),

    MenuRightButton(
        input = byteArrayOf(0x45, 0x00),
        icon = AppIcons.Right,
        stringRes = R.string.right
    ),

    // Multimedia

    PlayPauseButton(
        input = byteArrayOf(0xCD.toByte(), 0x00),
        icon = AppIcons.PlayPause,
        stringRes = R.string.play_pause
    ),

    PlayButton(
        input = byteArrayOf(0xB0.toByte(), 0x00),
        icon = AppIcons.Play,
        stringRes = R.string.play
    ),

    PauseButton(
        input = byteArrayOf(0xB1.toByte(), 0x00),
        icon = AppIcons.Pause,
        stringRes = R.string.pause
    ),

    StopButton(
        input = byteArrayOf(0xB7.toByte(), 0x00),
        icon = AppIcons.Stop,
        stringRes = R.string.stop
    ),

    RepeatButton(
        input = byteArrayOf(0xBC.toByte(), 0x00),
        icon = AppIcons.Repeat,
        stringRes = R.string.repeat
    ),

    PreviousTrackButton(
        input = byteArrayOf(0xB6.toByte(), 0x00),
        icon = AppIcons.PreviousTrack,
        stringRes = R.string.previous_track
    ),

    NextTrackButton(
        input = byteArrayOf(0xB5.toByte(), 0x00),
        icon = AppIcons.NextTrack,
        stringRes = R.string.next_track
    ),

    RewindButton(
        input = byteArrayOf(0xB4.toByte(), 0x00),
        icon = AppIcons.Rewind,
        stringRes = R.string.rewind
    ),

    ForwardButton(
        input = byteArrayOf(0xB3.toByte(), 0x00),
        icon = AppIcons.Forward,
        stringRes = R.string.forward
    ),

    ClosedCaptionsButton(
        input = byteArrayOf(0x61, 0x00),
        icon = AppIcons.ClosedCaption,
        stringRes = R.string.closed_captions
    ),

    // Volume

    VolumeUpButton(
        input = byteArrayOf(0xE9.toByte(), 0x00),
        icon = AppIcons.VolumeUp,
        stringRes = R.string.volume_up
    ),

    VolumeDownButton(
        input = byteArrayOf(0xEA.toByte(), 0x00),
        icon = AppIcons.VolumeDown,
        stringRes = R.string.volume_down
    ),

    VolumeMuteButton(
        input = byteArrayOf(0xE2.toByte(), 0x00),
        icon = AppIcons.Mute,
        stringRes = R.string.mute
    ),

    // Brightness

    BrightnessUpButton(
        input = byteArrayOf(0x6F.toByte(), 0x00),
        icon = AppIcons.BrightnessUp,
        stringRes = R.string.brightness_up
    ),

    BrightnessDownButton(
        input = byteArrayOf(0x70.toByte(), 0x00),
        icon = AppIcons.BrightnessDown,
        stringRes = R.string.brightness_down
    ),

    // Channel

    ChannelUpButton(
        input = byteArrayOf(0x9C.toByte(), 0x00),
        icon = AppIcons.TVChannelUp,
        stringRes = R.string.next_channel
    ),

    ChannelDownButton(
        input = byteArrayOf(0x9D.toByte(), 0x00),
        icon = AppIcons.TVChannelDown,
        stringRes = R.string.previous_channel
    ),

    // Others

    HomeButton(
        input = byteArrayOf(0x23, 0x02),
        icon = AppIcons.Home,
        stringRes = R.string.home
    ),

    BackButton(
        input = byteArrayOf(0x24, 0x02),
        icon = AppIcons.Back,
        stringRes = R.string.back
    ),

    PowerButton(
        input = byteArrayOf(0x30, 0x00),
        icon = AppIcons.Power,
        stringRes = R.string.power
    ),

    RedMenuButton(
        input = byteArrayOf(0x69, 0x00),
        icon = AppIcons.Round,
        stringRes = R.string.red_menu_button
    ),

    GreenMenuButton(
        input = byteArrayOf(0x6A, 0x00),
        icon = AppIcons.Round,
        stringRes = R.string.green_menu_button
    ),

    BlueMenuButton(
        input = byteArrayOf(0x6B, 0x00),
        icon = AppIcons.Round,
        stringRes = R.string.blue_menu_button
    ),

    YellowMenuButton(
        input = byteArrayOf(0x6C, 0x00),
        icon = AppIcons.Round,
        stringRes = R.string.yellow_menu_button
    ),

    // ---- Keyboard ----
    // https://source.android.com/docs/core/interaction/input/keyboard-devices#hid-keyboard-and-keypad-page-0x07

    KeyboardEnterButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_ENTER.byte),
        icon = AppIcons.KeyboardEnter,
        stringRes = R.string.enter
    ),

    KeyboardBackspaceButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_DELETE.byte),
        icon = AppIcons.KeyboardBackspace,
        stringRes = R.string.delete
    ),

    KeyboardSpaceBarButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_SPACE_BAR.byte),
        icon = AppIcons.SpaceBar,
        stringRes = R.string.space_bar
    ),

    KeyboardUpButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_UP_ARROW.byte),
        icon = AppIcons.KeyboardArrowUp,
        stringRes = R.string.up
    ),

    KeyboardDownButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_DOWN_ARROW.byte),
        icon = AppIcons.KeyboardArrowDown,
        stringRes = R.string.down
    ),

    KeyboardLeftButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_LEFT_ARROW.byte),
        icon = AppIcons.KeyboardArrowLeft,
        stringRes = R.string.left
    ),

    KeyboardRightButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_RIGHT_ARROW.byte),
        icon = AppIcons.KeyboardArrowRight,
        stringRes = R.string.right
    ),

    KeyboardPrintScreenButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_PRINT_SCREEN.byte),
        icon = AppIcons.KeyboardScreenshot,
        stringRes = R.string.screenshot
    ),

    KeyboardTabButton(
        input = byteArrayOf(0x00, KeyboardKey.KEY_TAB.byte),
        icon = AppIcons.KeyboardTab,
        stringRes = R.string.keyboard_tab
    ),

    KeyboardPageUp(
        input = byteArrayOf(0x00, KeyboardKey.KEY_PAGE_UP.byte),
        icon = AppIcons.KeyboardPageUp,
        stringRes = R.string.page_up
    ),

    KeyboardPageDown(
        input = byteArrayOf(0x00, KeyboardKey.KEY_PAGE_DOWN.byte),
        icon = AppIcons.KeyboardPageDown,
        stringRes = R.string.page_down
    )
}