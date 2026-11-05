// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.automation

/** Stable names keep layout independent of translated labels and legacy MANUAL state. */
object HomeModuleOrder {
    val defaults = listOf(PhoneControlMode.AI, PhoneControlMode.XBOX, PhoneControlMode.FC,
        PhoneControlMode.KEY_MOUSE, PhoneControlMode.KEYBOARD, PhoneControlMode.MEDIA,
        PhoneControlMode.MOUSE, PhoneControlMode.TV)

    fun decode(saved: String?): List<PhoneControlMode> {
        val names = saved.orEmpty().split(",")
        return (names.mapNotNull { name -> defaults.find { it.name == name } } + defaults).distinct()
    }

    fun encode(order: List<PhoneControlMode>): String = order.joinToString(",") { it.name }

    fun move(order: List<PhoneControlMode>, from: String, to: String): List<PhoneControlMode> {
        val source = order.indexOfFirst { it.name == from }
        val target = order.indexOfFirst { it.name == to }
        if (source < 0 || target < 0 || source == target) return order
        return order.toMutableList().apply { add(target, removeAt(source)) }
    }
}
