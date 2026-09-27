package org.slavacom.zasm.model

/** Аналог `StrToInt('$'+текст)` из методички: опциональный префикс `$`/`0x`. */
fun parseHex(text: String): Int? {
    val cleaned = text.trim().removePrefix("$").removePrefix("0x").removePrefix("0X")
    if (cleaned.isEmpty()) return null
    return try {
        Integer.parseUnsignedInt(cleaned, 16)
    } catch (e: NumberFormatException) {
        null
    }
}

/** Числовой литерал: `$`-префикс — 16-ричный, иначе десятичный (для операндов WORD). */
fun parseNumberOrHex(text: String): Int? {
    val trimmed = text.trim()
    return if (trimmed.startsWith("$")) parseHex(trimmed) else trimmed.toIntOrNull()
}

/** Аналог `IntToHex(число, 8)` — адрес, 4 байта. */
fun toHex8(value: Int): String = "%08X".format(value)

/** Смещение относительной адресации — 2 байта (со знаком, по модулю 0x10000). */
fun toHex4(value: Int): String = "%04X".format(value and 0xFFFF)

/** Код операции/номер регистра — 1 байт. */
fun toHex2(value: Int): String = "%02X".format(value and 0xFF)

/** Номер регистра из операнда вида "R1"/"r1"; null, если это не регистр. */
fun registerNumber(operand: String): Int? =
    operand.trim().removePrefix("R").removePrefix("r").toIntOrNull()
