package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.SymbolTableEntry
import org.slavacom.zasm.model.toHex2

/** Результат первого прохода. */
data class Pass1Result(
    val programName: String,
    val loadAddress: Int,
    val programLength: Int,
    val auxTable: List<AuxTableRow>,
    val symbolTable: List<SymbolTableEntry>,
    val errors: List<AssemblerError>,
)

/**
 * Первый проход: вычисляет адреса меток и формирует вспомогательную таблицу,
 * не разрешая символические операнды (это делает [Pass2Engine]).
 */
object Pass1Engine {

    private const val PSEUDO_START = "START"
    private const val PSEUDO_END = "END"
    private const val PSEUDO_WORD = "WORD"
    private const val PSEUDO_BYTE = "BYTE"

    fun run(source: List<SourceLine>, opcodes: List<OpcodeEntry>, loadAddress: Int): Pass1Result {
        val auxTable = mutableListOf<AuxTableRow>()
        val symbolTable = mutableListOf<SymbolTableEntry>()
        val errors = mutableListOf<AssemblerError>()
        val knownNames = mutableSetOf<String>()

        var address = loadAddress
        var programName = ""
        var sawStart = false
        var sawEnd = false

        fun defineSymbol(name: String, atAddress: Int, lineNo: Int) {
            if (name.isBlank()) return
            if (!knownNames.add(name.uppercase())) {
                errors += AssemblerError(lineNo, "повторное определение имени '$name'")
                return
            }
            symbolTable += SymbolTableEntry(name, atAddress)
        }

        source.forEachIndexed { index, line ->
            val lineNo = index + 1
            if (line.isBlank || sawEnd) return@forEachIndexed

            val op = line.op.trim()
            when {
                op.equals(PSEUDO_START, ignoreCase = true) -> {
                    if (sawStart) {
                        errors += AssemblerError(lineNo, "повторная директива Start")
                    }
                    sawStart = true
                    programName = line.label.trim()
                }

                op.equals(PSEUDO_END, ignoreCase = true) -> {
                    sawEnd = true
                }

                op.equals(PSEUDO_WORD, ignoreCase = true) -> {
                    defineSymbol(line.label.trim(), address, lineNo)
                    auxTable += AuxTableRow(address, "WORD", line.operand1.trim(), "", lineNo)
                    address += 4
                }

                op.equals(PSEUDO_BYTE, ignoreCase = true) -> {
                    defineSymbol(line.label.trim(), address, lineNo)
                    val text = line.operand1
                    auxTable += AuxTableRow(address, "BYTE", text, "", lineNo)
                    address += text.length
                }

                op.isBlank() -> {
                    errors += AssemblerError(lineNo, "не указана операция")
                }

                else -> {
                    val entry = opcodes.find { it.mnemonic.equals(op, ignoreCase = true) }
                    if (entry == null) {
                        errors += AssemblerError(lineNo, "неизвестная операция '$op'")
                    } else {
                        defineSymbol(line.label.trim(), address, lineNo)
                        auxTable += AuxTableRow(
                            address = address,
                            opText = toHex2(entry.code),
                            operand1 = line.operand1.trim(),
                            operand2 = line.operand2.trim(),
                            sourceLine = lineNo,
                        )
                        address += entry.length
                    }
                }
            }
        }

        if (!sawStart) {
            errors += AssemblerError(0, "не найдена директива Start")
        }

        return Pass1Result(
            programName = programName.ifBlank { "NONAME" },
            loadAddress = loadAddress,
            programLength = address - loadAddress,
            auxTable = auxTable,
            symbolTable = symbolTable,
            errors = errors,
        )
    }
}
