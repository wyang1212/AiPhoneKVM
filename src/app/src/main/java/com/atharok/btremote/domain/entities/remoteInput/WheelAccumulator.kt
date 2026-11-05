package com.atharok.btremote.domain.entities.remoteInput

/** Converts a vertical finger drag into signed HID wheel steps, retaining fractional movement. */
class WheelAccumulator(private val pixelsPerStep: Float) {
    private var pending = 0f
    init { require(pixelsPerStep > 0f) }

    fun add(deltaY: Float, speed: Float, invert: Boolean): Int {
        pending += deltaY * speed * if (invert) -1f else 1f
        val steps = (pending / pixelsPerStep).toInt().coerceIn(-127, 127)
        pending -= steps * pixelsPerStep
        return steps
    }

    fun reset() { pending = 0f }
}
