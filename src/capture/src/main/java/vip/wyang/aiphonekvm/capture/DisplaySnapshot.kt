// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package vip.wyang.aiphonekvm.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import org.json.JSONObject

/** Whole default-display coordinates used by dispatchGesture, including system bars. */
object DisplaySnapshot {
    fun signature(value: JSONObject) = listOf("width","height","rotation","densityDpi")
        .joinToString(":") { value.getInt(it).toString() }
    @Suppress("DEPRECATION")
    fun read(context: Context): JSONObject {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            ?: error("DEFAULT_DISPLAY_MISSING")
        val metrics = DisplayMetrics(); display.getRealMetrics(metrics)
        return JSONObject().put("width",metrics.widthPixels).put("height",metrics.heightPixels)
            .put("rotation",display.rotation).put("densityDpi",metrics.densityDpi)
            .put("model",Build.MODEL.take(80)).put("manufacturer",Build.MANUFACTURER.take(80))
            .put("fullDisplay",true)
    }
    fun matches(expected: JSONObject, current: JSONObject) =
        expected.optBoolean("fullDisplay",false) && current.optBoolean("fullDisplay",false) &&
        listOf("width","height","rotation").all { expected.has(it) && expected.optInt(it,-1) == current.optInt(it,-2) }
}
