package org.slavacom.zasm.engine

import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine

/**
 * Держит результат последнего первого прохода между кликами по кнопкам —
 * второй проход в UI работает только с уже сохранёнными данными.
 */
class AssemblerEngine {
    private var lastPass1: Pass1Result? = null

    val hasPass1Result: Boolean
        get() = lastPass1 != null

    fun runPass1(source: List<SourceLine>, opcodes: List<OpcodeEntry>, loadAddress: Int?): Pass1Result {
        val result = Pass1Engine.run(source, opcodes, loadAddress)
        lastPass1 = result
        return result
    }

    fun runPass2(opcodes: List<OpcodeEntry>): Pass2Result {
        val pass1 = requireNotNull(lastPass1) { "Первый проход ещё не выполнялся" }
        return Pass2Engine.run(pass1, opcodes)
    }

    fun reset() {
        lastPass1 = null
    }
}
