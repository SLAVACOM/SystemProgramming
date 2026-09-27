package org.slavacom.zasm.model

/**
 * Ровно 4 формата команды абстрактного процессора. Регистровые операнды
 * известны уже на первом проходе (кодируются сразу в hex), символьный адрес —
 * только на втором (после построения полной ТСИ).
 */
enum class InstructionFormat {
    /** opcode + регистр + 4-байтный адрес (прямая адресация): LD, SAV. */
    REG_ADDR,

    /** opcode + регистр + 2-байтное смещение (относительная адресация): LDN, SAVN. */
    REG_OFFSET,

    /** opcode + 4-байтный адрес, без регистра (прямая адресация): JUMP, CALL. */
    ADDR_ONLY,

    /** opcode + регистр + регистр — полностью известно уже после первого прохода: ADD, SUB. */
    REG_REG,
}

fun instructionFormatOf(mnemonic: String): InstructionFormat? = when (mnemonic.uppercase()) {
    "LD", "SAV" -> InstructionFormat.REG_ADDR
    "LDN", "SAVN" -> InstructionFormat.REG_OFFSET
    "JUMP", "CALL" -> InstructionFormat.ADDR_ONLY
    "ADD", "SUB" -> InstructionFormat.REG_REG
    else -> null
}
