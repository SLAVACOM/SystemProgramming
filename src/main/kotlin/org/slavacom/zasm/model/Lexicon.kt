package org.slavacom.zasm.model

/** Директивы ассемблера (зарезервированные слова). */
val DIRECTIVES: Set<String> = setOf("START", "END", "BYTE", "WORD", "RESB", "RESW")

fun isDirective(word: String): Boolean = word.uppercase() in DIRECTIVES

private val REGISTERS = (0..15).map { "R$it" }

/** Номер регистра R0..R15 (регистр букв не важен); null — если это не регистр. */
fun registerNumber(operand: String): Int? = REGISTERS.indexOf(operand.uppercase()).takeIf { it >= 0 }

private val ALPHANUMERIC = Regex("[A-Za-z0-9]*")

/** Только латинские буквы и цифры (пустая строка тоже подходит). */
fun isAlphanumeric(text: String): Boolean = ALPHANUMERIC.matches(text)

/** Метка: латинские буквы и цифры, начинается с буквы. */
fun isValidLabel(text: String): Boolean =
    text.isNotEmpty() && isAlphanumeric(text) && text[0].isLetter()

/** Содержимое символьной строки `C"текст"`; null — если операнд не такого вида. */
fun charStringBody(operand: String): String? =
    quotedBody(operand, prefix = 'C')

/** Содержимое 16-ричной строки `X"2F4C"` (только hex-цифры); null — если операнд не такого вида. */
fun hexStringBody(operand: String): String? =
    quotedBody(operand, prefix = 'X')?.takeIf(::isHexDigits)

private fun quotedBody(operand: String, prefix: Char): String? {
    if (operand.length < 4) return null
    if (!operand[0].equals(prefix, ignoreCase = true) || operand[1] != '"' || operand.last() != '"') return null
    return operand.substring(2, operand.length - 1)
}

/** Имя метки из операнда относительной адресации `[метка]`; null — если операнд не в скобках. */
fun relativeOperandName(operand: String): String? =
    if (operand.length >= 2 && operand.first() == '[' && operand.last() == ']') {
        operand.substring(1, operand.length - 1)
    } else {
        null
    }
