package com.atharok.btremote.domain.entities.remoteInput.keyboard.advancedKeyboard

import android.content.Context
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardKey

class BulgarianAdvancedKeyboardLayout(context: Context): AdvancedKeyboardLayout(context) {

    override val line1: Array<AdvancedKeyboardKey> = arrayOf(
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_01.byte,
            weight = 1f,
            text = "1",
            textSecondary = "!"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_02.byte,
            weight = 1f,
            text = "2",
            textSecondary = "?",
            textTertiary = "@"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_03.byte,
            weight = 1f,
            text = "3",
            textSecondary = "+",
            textTertiary = "#"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_04.byte,
            weight = 1f,
            text = "4",
            textSecondary = "\"",
            textTertiary = "$"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_05.byte,
            weight = 1f,
            text = "5",
            textSecondary = "%"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_06.byte,
            weight = 1f,
            text = "6",
            textSecondary = "=",
            textTertiary = "^"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_07.byte,
            weight = 1f,
            text = "7",
            textSecondary = ":",
            textTertiary = "&"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_08.byte,
            weight = 1f,
            text = "8",
            textSecondary = "/",
            textTertiary = "*"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_09.byte,
            weight = 1f,
            text = "9",
            textSecondary = "_",
            textTertiary = "("
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_10.byte,
            weight = 1f,
            text = "0",
            textSecondary = "№",
            textTertiary = ")"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_11.byte,
            weight = 1f,
            text = "-",
            textSecondary = "I",
            textTertiary = "_"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_12.byte,
            weight = 1f,
            text = ".",
            textSecondary = "V",
            textTertiary = "="
        )
    )

    override val line2: Array<AdvancedKeyboardKey> = arrayOf(
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_00.byte,
            weight = 1f,
            text = ",",
            textSecondary = "ы",
            textTertiary = "Q"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_01.byte,
            weight = 1f,
            text = "У",
            textTertiary = "W"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_02.byte,
            weight = 1f,
            text = "Е",
            textTertiary = "E"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_03.byte,
            weight = 1f,
            text = "И",
            textTertiary = "R"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_04.byte,
            weight = 1f,
            text = "Ш",
            textTertiary = "T"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_05.byte,
            weight = 1f,
            text = "Щ",
            textTertiary = "Y"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_06.byte,
            weight = 1f,
            text = "К",
            textTertiary = "U"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_07.byte,
            weight = 1f,
            text = "С",
            textTertiary = "I"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_08.byte,
            weight = 1f,
            text = "Д",
            textTertiary = "O"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_09.byte,
            weight = 1f,
            text = "З",
            textTertiary = "P"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_10.byte,
            weight = 1f,
            text = "Ц",
            textTertiary = "["
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_2_KEY_11.byte,
            weight = 1f,
            text = ";",
            textSecondary = "§",
            textTertiary = "]"
        )
    )

    override val line3: Array<AdvancedKeyboardKey> = arrayOf(
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_00.byte,
            weight = 1f,
            text = "Ь",
            textTertiary = "A"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_01.byte,
            weight = 1f,
            text = "Я",
            textTertiary = "S"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_02.byte,
            weight = 1f,
            text = "А",
            textTertiary = "D"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_03.byte,
            weight = 1f,
            text = "О",
            textTertiary = "F"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_04.byte,
            weight = 1f,
            text = "Ж",
            textTertiary = "G"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_05.byte,
            weight = 1f,
            text = "Г",
            textTertiary = "H"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_06.byte,
            weight = 1f,
            text = "Т",
            textTertiary = "J"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_07.byte,
            weight = 1f,
            text = "Н",
            textTertiary = "K"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_08.byte,
            weight = 1f,
            text = "В",
            textTertiary = "L"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_09.byte,
            weight = 1f,
            text = "М",
            textTertiary = ";"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_10.byte,
            weight = 1f,
            text = "Ч",
            textTertiary = "'"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_3_KEY_11.byte,
            weight = 1f,
            text = "(",
            textSecondary = ")",
            textTertiary = "\\"
        )
    )

    override val line4: Array<AdvancedKeyboardKey> = arrayOf(
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_1_KEY_00.byte,
            weight = 1f,
            text = "`",
            textSecondary = "~"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_00.byte,
            weight = 1f,
            text = "\\",
            textSecondary = "|"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_01.byte,
            weight = 1f,
            text = "Ю",
            textTertiary = "Z"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_02.byte,
            weight = 1f,
            text = "Й",
            textTertiary = "X"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_03.byte,
            weight = 1f,
            text = "Ъ",
            textTertiary = "C"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_04.byte,
            weight = 1f,
            text = "Э",
            textTertiary = "V"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_05.byte,
            weight = 1f,
            text = "Ф",
            textTertiary = "B"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_06.byte,
            weight = 1f,
            text = "Х",
            textTertiary = "N"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_07.byte,
            weight = 1f,
            text = "П",
            textTertiary = "M"
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_08.byte,
            weight = 1f,
            text = "Р",
            textTertiary = ","
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_09.byte,
            weight = 1f,
            text = "Л",
            textTertiary = "."
        ),
        CharacterAdvancedKeyboardKey(
            byte = KeyboardKey.ROW_4_KEY_10.byte,
            weight = 1f,
            text = "Б",
            textSecondary = ":",
            textTertiary = "/"
        )
    )
}