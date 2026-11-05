package com.atharok.btremote.domain.entities.remoteInput

import androidx.annotation.StringRes
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.MOUSE_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.common.utils.REMOTE_REPORT_ID

enum class PhysicalVolumeButtonAction(
    @param:StringRes val stringRes: Int,
    val hidReportId: Int,
    val pressedBytes: ByteArray,
    val releasedBytes: ByteArray
) {
    DEFAULT(
        stringRes = R.string.default_option,
        hidReportId = -1,
        pressedBytes = REMOTE_INPUT_NONE,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    VOLUME_UP(
        stringRes = R.string.volume_up,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.VolumeUpButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    VOLUME_DOWN(
        stringRes = R.string.volume_down,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.VolumeDownButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    BRIGHTNESS_UP(
        stringRes = R.string.brightness_up,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.BrightnessUpButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    BRIGHTNESS_DOWN(
        stringRes = R.string.brightness_down,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.BrightnessDownButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    DIRECTION_UP(
        stringRes = R.string.up,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.MenuUpButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    DIRECTION_DOWN(
        stringRes = R.string.down,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.MenuDownButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    DIRECTION_LEFT(
        stringRes = R.string.left,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.MenuLeftButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    DIRECTION_RIGHT(
        stringRes = R.string.right,
        hidReportId = REMOTE_REPORT_ID,
        pressedBytes = RemoteButtonProperties.MenuRightButton.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    PAGE_UP(
        stringRes = R.string.page_up,
        hidReportId = KEYBOARD_REPORT_ID,
        pressedBytes = RemoteButtonProperties.KeyboardPageUp.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    PAGE_DOWN(
        stringRes = R.string.page_down,
        hidReportId = KEYBOARD_REPORT_ID,
        pressedBytes = RemoteButtonProperties.KeyboardPageDown.input,
        releasedBytes = REMOTE_INPUT_NONE
    ),
    MOUSE_CLICK_LEFT(
        stringRes = R.string.mouse_click_left,
        hidReportId = MOUSE_REPORT_ID,
        pressedBytes = byteArrayOf(MouseAction.MOUSE_CLICK_LEFT.byte, 0x00, 0x00, 0x00),
        releasedBytes = byteArrayOf(MouseAction.NONE.byte, 0x00, 0x00, 0x00)
    ),
    MOUSE_CLICK_RIGHT(
        stringRes = R.string.mouse_click_right,
        hidReportId = MOUSE_REPORT_ID,
        pressedBytes = byteArrayOf(MouseAction.MOUSE_CLICK_RIGHT.byte, 0x00, 0x00, 0x00),
        releasedBytes = byteArrayOf(MouseAction.NONE.byte, 0x00, 0x00, 0x00)
    )
}