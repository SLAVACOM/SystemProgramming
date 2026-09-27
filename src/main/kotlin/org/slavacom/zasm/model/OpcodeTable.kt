package org.slavacom.zasm.model

/** Строка Таблицы Кодов Операций (ТКО). */
data class OpcodeEntry(val mnemonic: String, val code: Int, val length: Int)

/**
 * Набор команд абстрактного процессора по умолчанию — ровно 4 формата
 * (см. [InstructionFormat]): LD/SAV — регистр+адрес (прямая адресация),
 * LDN/SAVN — регистр+смещение (относительная адресация), JUMP/CALL — только
 * адрес (прямая адресация), ADD/SUB — регистр+регистр (известно уже после
 * первого прохода, настройка не требуется).
 */
object DefaultOpcodeTable {
    val entries: List<OpcodeEntry> = listOf(
        OpcodeEntry("LD", 0x01, 6),
        OpcodeEntry("LDN", 0x02, 4),
        OpcodeEntry("SAV", 0x03, 6),
        OpcodeEntry("SAVN", 0x04, 4),
        OpcodeEntry("ADD", 0x05, 3),
        OpcodeEntry("JUMP", 0x06, 5),
        OpcodeEntry("SUB", 0x07, 3),
        OpcodeEntry("CALL", 0x08, 5),
    )
}
