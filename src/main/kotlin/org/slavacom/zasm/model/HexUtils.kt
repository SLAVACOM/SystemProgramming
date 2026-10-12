package org.slavacom.zasm.model

/** Максимальный адрес оперативной памяти — 3 байта (FFFFFF). */
const val MAX_ADDRESS = 0xFFFFFF

private val HEX_DIGITS = Regex("[0-9A-Fa-f]+")
private val DEC_DIGITS = Regex("[0-9]+")

/** Непустая строка только из 16-ричных цифр (любой длины). */
fun isHexDigits(text: String): Boolean = HEX_DIGITS.matches(text)

/** Строго 16-ричное число: только цифры 0-9, A-F, без префиксов; null — если формат неверен. */
fun parseHex(text: String): Int? {
    if (!HEX_DIGITS.matches(text)) return null
    return text.toLongOrNull(16)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
}

/** Неотрицательное десятичное число (только цифры); null — если формат неверен. */
fun parseDecimal(text: String): Int? = if (DEC_DIGITS.matches(text)) text.toIntOrNull() else null

/** Адрес — 3 байта (6 hex-символов). */
fun toHex6(value: Int): String = "%06X".format(value)

/** Байт — 2 hex-символа (длина записи, код операции, однобайтовое число). */
fun toHex2(value: Int): String = "%02X".format(value)

/** Текст в ASCII-коды (символы вне ASCII кодируются как '?'). */
fun toAsciiHex(text: String): String =
    text.map { if (it.code < 128) it.code else '?'.code }.joinToString("") { toHex2(it) }
