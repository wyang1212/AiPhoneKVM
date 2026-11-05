// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

import kotlinx.serialization.json.*

/** Default-display pixel coordinates, never the controller's window or a cropped app capture. */
data class ControlledScreen(
    val width: Int, val height: Int, val rotation: Int, val densityDpi: Int,
    val model: String = "", val manufacturer: String = "", val fullDisplay: Boolean = true
) {
    fun sameCoordinates(other: ControlledScreen?) = other != null && fullDisplay && other.fullDisplay &&
        width == other.width && height == other.height && rotation == other.rotation
    fun matchesImage(w: Int, h: Int) = fullDisplay && width == w && height == h
    fun applyTo(config: AutomationConfig) = if (fullDisplay) config.copy(screenWidth = width, screenHeight = height) else config
    fun json() = buildJsonObject {
        put("width", width); put("height", height); put("rotation", rotation); put("densityDpi", densityDpi)
        put("model", model); put("manufacturer", manufacturer); put("fullDisplay", fullDisplay)
    }
    companion object {
        fun parse(input: JsonObject): ControlledScreen {
            val w = input.intValue("width"); val h = input.intValue("height")
            val r = input.intValue("rotation"); val d = input.intValue("densityDpi")
            require(w in 100..4096 && h in 100..4096 && r in 0..3 && d in 72..1280) { "被控端屏幕参数无效" }
            val model = input.stringValue("model", ""); val manufacturer = input.stringValue("manufacturer", "")
            require(model.length <= 80 && manufacturer.length <= 80) { "被控端设备信息过长" }
            return ControlledScreen(w,h,r,d,model,manufacturer,input.boolValue("fullDisplay",false))
        }
    }
}
