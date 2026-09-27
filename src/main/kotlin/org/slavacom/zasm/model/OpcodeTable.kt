package org.slavacom.zasm.model

/** Строка Таблицы Кодов Операций (ТКО). */
data class OpcodeEntry(val mnemonic: String, val code: Int, val length: Int)

/**
 * Набор команд абстрактного процессора по умолчанию.
 *
 * Команды с суффиксом N используют относительную адресацию (смещение,
 * настройка при загрузке не требуется), без суффикса — прямую (требует
 * записи в таблицу настройки, см. Этап 2).
 */
object DefaultOpcodeTable {
    val entries: List<OpcodeEntry> = listOf(
        OpcodeEntry("LD", 0x01, 6),
        OpcodeEntry("LDN", 0x02, 4),
        OpcodeEntry("SAV", 0x03, 6),
        OpcodeEntry("SAVN", 0x04, 4),
        OpcodeEntry("ADD", 0x05, 3),
        OpcodeEntry("JUMP", 0x06, 5),
        OpcodeEntry("JUMPN", 0x07, 3),
        OpcodeEntry("CALL", 0x08, 5),
    )
}
