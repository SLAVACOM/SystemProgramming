package org.slavacom.zasm.model

/**
 * Строка Таблицы Кодов Операций (ТКО). Формат команды определяется длиной:
 * 1 — без операндов; 2 — число 0..255 или два регистра; 3/4 — операнд-метка
 * (прямая адресация, адрес 2 или 3 байта).
 */
data class OpcodeEntry(val mnemonic: String, val code: Int, val length: Int)

/** Код операции занимает старшие 6 бит байта, младшие 2 — тип адресации. */
const val MAX_OPCODE = 0x3F

object DefaultOpcodeTable {
    val entries: List<OpcodeEntry> = listOf(
        OpcodeEntry("JMP", 0x01, 4),
        OpcodeEntry("LOADR1", 0x02, 4),
        OpcodeEntry("LOADR2", 0x03, 4),
        OpcodeEntry("ADD", 0x04, 2),
        OpcodeEntry("SAVER1", 0x05, 4),
        OpcodeEntry("INT", 0x06, 2),
    )
}

/** Результат проверки ТКО: при ошибке [entries] не используются. */
data class OpcodeTableCheck(val entries: List<OpcodeEntry>, val error: AssemblerError?)

/**
 * Проверяет ТКО, введённую в сетке ([rows] — ячейки Команда | МКОП (hex) | Длина).
 * Полностью пустые строки пропускаются. Останавливается на первой ошибке.
 */
fun validateOpcodeTable(rows: List<List<String>>): OpcodeTableCheck {
    val entries = mutableListOf<OpcodeEntry>()
    val names = mutableSetOf<String>()
    val codes = mutableSetOf<Int>()

    fun fail(row: Int, message: String) = OpcodeTableCheck(emptyList(), AssemblerError(row, message))

    rows.forEachIndexed { index, cells ->
        val row = index + 1
        val name = cells.getOrElse(0) { "" }.trim()
        val codeText = cells.getOrElse(1) { "" }.trim()
        val lengthText = cells.getOrElse(2) { "" }.trim()
        if (name.isEmpty() && codeText.isEmpty() && lengthText.isEmpty()) return@forEachIndexed

        if (name.isEmpty() || codeText.isEmpty() || lengthText.isEmpty()) {
            return fail(row, "пустая ячейка в таблице кодов операций недопустима")
        }
        if (name.length > 6 || codeText.length > 2 || lengthText.length > 1) {
            return fail(row, "неверный размер поля (команда — от 1 до 6 символов, МКОП — до 2, длина — 1)")
        }
        if (!isAlphanumeric(name)) {
            return fail(row, "в поле команды недопустимый символ")
        }
        if (isDirective(name) || registerNumber(name) != null) {
            return fail(row, "команда '$name' является зарезервированным словом")
        }
        val code = parseHex(codeText) ?: return fail(row, "посторонние символы в поле МКОП")
        if (code > MAX_OPCODE) {
            return fail(row, "код команды не должен превышать ${toHex2(MAX_OPCODE)}")
        }
        val length = parseDecimal(lengthText) ?: return fail(row, "в поле длины недопустимый символ")
        if (length !in 1..4) {
            return fail(row, "длина команды должна быть от 1 до 4")
        }
        if (!names.add(name.uppercase())) {
            return fail(row, "повторяющаяся команда '$name'")
        }
        if (!codes.add(code)) {
            return fail(row, "повторяющийся код команды ${toHex2(code)}")
        }
        entries += OpcodeEntry(name.uppercase(), code, length)
    }
    return OpcodeTableCheck(entries, null)
}
