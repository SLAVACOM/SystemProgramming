package org.slavacom.zasm.engine

import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine

/**
 * Держит результат последнего успешного первого прохода между кликами по
 * кнопкам — второй проход работает только с ним (и с ТКО, по которой он выполнен).
 */
class AssemblerEngine {
    private var lastPass1: Pass1Result? = null

    val hasPass1Result: Boolean
        get() = lastPass1 != null

    fun runPass1(source: List<SourceLine>, opcodes: List<OpcodeEntry>): Pass1Result {
        val result = Pass1Engine.run(source, opcodes)
        lastPass1 = result.takeIf { it.isSuccess }
        return result
    }

    fun runPass2(): Pass2Result {
        val pass1 = requireNotNull(lastPass1) { "Первый проход ещё не выполнялся" }
        return Pass2Engine.run(pass1)
    }

    fun reset() {
        lastPass1 = null
    }
}
