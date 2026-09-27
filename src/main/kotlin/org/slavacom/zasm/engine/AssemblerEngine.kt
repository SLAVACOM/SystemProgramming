package org.slavacom.zasm.engine

import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine

/**
 * Держит результаты последних проходов между кликами по кнопкам — второй
 * проход и сохранение в файл в UI работают с уже сохранёнными данными.
 */
class AssemblerEngine {
    private var lastPass1: Pass1Result? = null
    private var lastPass2: Pass2Result? = null

    val hasPass1Result: Boolean
        get() = lastPass1 != null

    fun runPass1(source: List<SourceLine>, opcodes: List<OpcodeEntry>, loadAddress: Int): Pass1Result {
        val result = Pass1Engine.run(source, opcodes, loadAddress)
        lastPass1 = result
        lastPass2 = null
        return result
    }

    fun runPass2(opcodes: List<OpcodeEntry>): Pass2Result {
        val pass1 = requireNotNull(lastPass1) { "Первый проход ещё не выполнялся" }
        val result = Pass2Engine.run(pass1, opcodes)
        lastPass2 = result
        return result
    }

    /** Результаты обоих проходов для сохранения в файл — null, пока второй проход не выполнен. */
    fun currentResults(): Pair<Pass1Result, Pass2Result>? {
        val pass1 = lastPass1 ?: return null
        val pass2 = lastPass2 ?: return null
        return pass1 to pass2
    }

    fun reset() {
        lastPass1 = null
        lastPass2 = null
    }
}
