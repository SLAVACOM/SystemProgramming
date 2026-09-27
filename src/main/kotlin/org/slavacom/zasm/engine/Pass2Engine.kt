package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.ObjectHeader
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.parseNumberOrHex
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex4
import org.slavacom.zasm.model.toHex8

/** Результат второго прохода. */
data class Pass2Result(
    val header: ObjectHeader,
    val binaryLines: List<String>,
    val errors: List<AssemblerError>,
)

/**
 * Второй проход: разрешает символические операнды вспомогательной таблицы
 * и формирует двоичный код.
 *
 * Форма команды (прямая/относительная адресация, состав операндов) жёстко
 * привязана к конкретной мнемонике из набора по умолчанию (см.
 * [org.slavacom.zasm.model.DefaultOpcodeTable]). Изменение кодов/длин в
 * редактируемой ТКО поддерживается, но добавление принципиально новой
 * мнемоники потребует расширения [encodeInstruction].
 */
object Pass2Engine {

    fun run(pass1: Pass1Result, opcodes: List<OpcodeEntry>): Pass2Result {
        return Runner(pass1, opcodes).execute()
    }

    private class Runner(private val pass1: Pass1Result, private val opcodes: List<OpcodeEntry>) {
        private val errors = mutableListOf<AssemblerError>()
        private val binaryLines = mutableListOf<String>()

        fun execute(): Pass2Result {
            pass1.auxTable.forEach { row -> processRow(row) }
            val header = ObjectHeader(pass1.programName, pass1.programLength, pass1.loadAddress)
            return Pass2Result(header, binaryLines, errors)
        }

        private fun processRow(row: AuxTableRow) {
            val opcodeEntry = opcodes.find { toHex2(it.code) == row.opText }
            if (opcodeEntry != null) {
                encodeInstruction(opcodeEntry, row)?.let { binaryLines += it }
                return
            }
            when (row.opText.uppercase()) {
                "WORD" -> encodeWord(row)
                "BYTE" -> encodeByte(row)
                else -> errors += AssemblerError(row.sourceLine, "неизвестный код операции '${row.opText}'")
            }
        }

        private fun encodeWord(row: AuxTableRow) {
            val text = row.operand1.trim()
            if (text.isEmpty() || text == "?") return
            val value = parseNumberOrHex(text)
            if (value == null) {
                errors += AssemblerError(row.sourceLine, "некорректная константа '$text'")
            } else {
                binaryLines += toHex8(value)
            }
        }

        private fun encodeByte(row: AuxTableRow) {
            if (row.operand1.isEmpty()) return
            binaryLines += row.operand1.map { toHex2(it.code) }.joinToString(" ")
        }

        private fun resolve(name: String, lineNo: Int): Int? {
            val entry = pass1.symbolTable.find { it.name.equals(name, ignoreCase = true) }
            if (entry == null) {
                errors += AssemblerError(lineNo, "не определено имя '$name'")
                return null
            }
            return entry.address
        }

        private fun register(operand: String, lineNo: Int): Int? {
            val number = operand.trim().removePrefix("R").removePrefix("r").toIntOrNull()
            if (number == null) {
                errors += AssemblerError(lineNo, "ожидался регистр, получено '$operand'")
            }
            return number
        }

        private fun encodeInstruction(entry: OpcodeEntry, row: AuxTableRow): String? {
            val nextAddress = row.address + entry.length
            val parts = mutableListOf(toHex2(entry.code))

            return when (entry.mnemonic.uppercase()) {
                "LD", "SAV" -> {
                    val reg = register(row.operand1, row.sourceLine) ?: return null
                    val target = resolve(row.operand2, row.sourceLine) ?: return null
                    parts += toHex2(reg)
                    parts += toHex8(target)
                    parts.joinToString(" ")
                }

                "LDN", "SAVN" -> {
                    val reg = register(row.operand1, row.sourceLine) ?: return null
                    val target = resolve(row.operand2, row.sourceLine) ?: return null
                    parts += toHex2(reg)
                    parts += toHex4(target - nextAddress)
                    parts.joinToString(" ")
                }

                "JUMP", "CALL" -> {
                    val target = resolve(row.operand1, row.sourceLine) ?: return null
                    parts += toHex8(target)
                    parts.joinToString(" ")
                }

                "JUMPN" -> {
                    val target = resolve(row.operand1, row.sourceLine) ?: return null
                    parts += toHex4(target - nextAddress)
                    parts.joinToString(" ")
                }

                "ADD" -> {
                    val reg1 = register(row.operand1, row.sourceLine) ?: return null
                    val reg2 = register(row.operand2, row.sourceLine) ?: return null
                    parts += toHex2(reg1)
                    parts += toHex2(reg2)
                    parts.joinToString(" ")
                }

                else -> {
                    errors += AssemblerError(row.sourceLine, "неизвестная форма команды '${entry.mnemonic}'")
                    null
                }
            }
        }
    }
}
