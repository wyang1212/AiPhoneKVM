package com.atharok.btremote.common.extensions

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Rounds this value to the given number of decimal places.
 */
fun Float.round(decimals: Int = 2): Float {
    val factor = 10f.pow(decimals)
    return (this * factor).roundToInt() / factor
}