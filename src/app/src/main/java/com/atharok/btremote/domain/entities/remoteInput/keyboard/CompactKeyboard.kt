package com.atharok.btremote.domain.entities.remoteInput.keyboard

enum class CompactKeyboardLayer { LETTERS, NUMBERS, SYMBOLS, FUNCTIONS }
enum class ShiftMode { OFF, ONCE, LOCKED }

data class CompactModifiers(
    val shift: ShiftMode = ShiftMode.OFF,
    val control: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
) {
    fun toggleShift() = copy(shift = when (shift) {
        ShiftMode.OFF -> ShiftMode.ONCE
        ShiftMode.ONCE -> ShiftMode.LOCKED
        ShiftMode.LOCKED -> ShiftMode.OFF
    })

    fun report(base: ByteArray): ByteArray {
        val mask = (if (shift != ShiftMode.OFF) 2 else 0) or
            (if (control) 1 else 0) or (if (alt) 4 else 0) or (if (meta) 8 else 0)
        return byteArrayOf((base[0].toInt() or mask).toByte(), base[1])
    }

    fun afterKey(usage: Byte) = copy(
        shift = if (shift == ShiftMode.ONCE && usage.toInt() in 0x04..0x1d) ShiftMode.OFF else shift,
        control = false, alt = false, meta = false
    )
}

data class CompactKey(val label: String, val character: Char? = null, val usage: Int = 0,
    val weight: Float = 1f, val spacer: Boolean = false)

/** Phone-style QWERTY: ten / nine / seven letters; numbers and tools have their own pages. */
object CompactKeyboard {
    private fun chars(value: String) = value.map { CompactKey(it.toString(), character = it) }
    private fun footer() = listOf(CompactKey("ABC", weight = 1.3f), CompactKey("功能", weight = 1.3f),
        CompactKey("空格", character = ' ', weight = 4f), CompactKey(".", character = '.'), CompactKey("回车", usage = 0x28, weight = 2.4f))

    fun rows(layer: CompactKeyboardLayer): List<List<CompactKey>> = when (layer) {
        CompactKeyboardLayer.LETTERS -> listOf(
            chars("qwertyuiop"),
            listOf(CompactKey("", weight = 0.5f, spacer = true)) + chars("asdfghjkl") + CompactKey("", weight = 0.5f, spacer = true),
            listOf(CompactKey("Shift", weight = 1.5f)) + chars("zxcvbnm") + CompactKey("退格", usage = 0x2a, weight = 1.5f),
            footer().map { if (it.label == "ABC") it.copy(label = "123") else it }
        )
        CompactKeyboardLayer.NUMBERS -> listOf(
            chars("1234567890"), chars("-/:;()$&@\""),
            listOf(CompactKey("#+=", weight = 1.5f)) + chars(".,?!'") + CompactKey("退格", usage = 0x2a, weight = 1.5f), footer()
        )
        CompactKeyboardLayer.SYMBOLS -> listOf(
            chars("[]{}#%^*+="), chars("_\\|~<>`"),
            listOf(CompactKey("123", weight = 1.5f)) + chars(".,?!'") + CompactKey("退格", usage = 0x2a, weight = 1.5f), footer()
        )
        CompactKeyboardLayer.FUNCTIONS -> listOf(
            (1..6).map { CompactKey("F$it", usage = 0x39 + it) },
            (7..12).map { CompactKey("F$it", usage = 0x39 + it) },
            listOf(CompactKey("Esc", usage = 0x29), CompactKey("Tab", usage = 0x2b), CompactKey("Ctrl"),
                CompactKey("Alt"), CompactKey("Meta"), CompactKey("退格", usage = 0x2a)),
            listOf(CompactKey("ABC"), CompactKey("←", usage = 0x50), CompactKey("↑", usage = 0x52),
                CompactKey("↓", usage = 0x51), CompactKey("→", usage = 0x4f), CompactKey("回车", usage = 0x28))
        )
    }
}
