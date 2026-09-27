package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.InstructionFormat
import org.slavacom.zasm.model.ObjectHeader
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.instructionFormatOf
import org.slavacom.zasm.model.parseNumberOrHex
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex4
import org.slavacom.zasm.model.toHex8

/**
 * Результат второго прохода.
 * [relocationTable] — адреса команд с прямой адресацией символьного операнда
 * ([InstructionFormat.REG_ADDR]/[InstructionFormat.ADDR_ONLY]): их операнд —
 * абсолютный адрес, зашитый в код, и должен быть скорректирован при загрузке
 * модуля по другому адресу. Относительная адресация (`REG_OFFSET`) самонастраивающаяся
 * и в таблицу не попадает; `REG_REG` адресов не содержит.
 */
data class Pass2Result(
    val header: ObjectHeader,
    val binaryLines: List<String>,
    val relocationTable: List<Int>,
    val errors: List<AssemblerError>,
)

/**
 * Второй проход: довершает частично сгенерированные командные строки
 * вспомогательной таблицы — разрешает символический операнд (адрес или
 * смещение), дописывает уже готовые регистровые байты из [Pass1Engine] и
 * формирует таблицу настройки для прямой адресации.
 *
 * Формат команды строго один из четырёх [InstructionFormat]; изменение
 * кодов/длин в редактируемой ТКО поддерживается, добавление принципиально
 * новой мнемоники потребует расширения [org.slavacom.zasm.model.instructionFormatOf].
 */
object Pass2Engine {

    fun run(pass1: Pass1Result, opcodes: List<OpcodeEntry>): Pass2Result {
        return Runner(pass1, opcodes).execute()
    }

    private class Runner(private val pass1: Pass1Result, private val opcodes: List<OpcodeEntry>) {
        private val errors = mutableListOf<AssemblerError>()
        private val binaryLines = mutableListOf<String>()
        private val relocationTable = mutableListOf<Int>()

        fun execute(): Pass2Result {
            pass1.auxTable.forEach { row -> processRow(row) }
            val header = ObjectHeader(pass1.programName, pass1.programLength, pass1.loadAddress)
            return Pass2Result(header, binaryLines, relocationTable, errors)
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

        private fun encodeInstruction(entry: OpcodeEntry, row: AuxTableRow): String? {
            val format = instructionFormatOf(entry.mnemonic)
            if (format == null) {
                errors += AssemblerError(row.sourceLine, "неизвестная форма команды '${entry.mnemonic}'")
                return null
            }

            val nextAddress = row.address + entry.length
            val parts = mutableListOf(toHex2(entry.code))

            return when (format) {
                InstructionFormat.REG_ADDR -> {
                    val target = resolve(row.operand2, row.sourceLine) ?: return null
                    relocationTable += row.address // прямая адресация — нужна настройка при загрузке
                    parts += row.operand1 // уже hex-байт регистра, закодирован на первом проходе
                    parts += toHex8(target)
                    parts.joinToString(" ")
                }

                InstructionFormat.REG_OFFSET -> {
                    val target = resolve(row.operand2, row.sourceLine) ?: return null
                    parts += row.operand1
                    parts += toHex4(target - nextAddress)
                    parts.joinToString(" ")
                }

                InstructionFormat.ADDR_ONLY -> {
                    val target = resolve(row.operand1, row.sourceLine) ?: return null
                    relocationTable += row.address // прямая адресация — нужна настройка при загрузке
                    parts += toHex8(target)
                    parts.joinToString(" ")
                }

                InstructionFormat.REG_REG -> {
                    // оба операнда — регистры, полностью закодированы уже на первом проходе
                    parts += row.operand1
                    parts += row.operand2
                    parts.joinToString(" ")
                }
            }
        }
    }
}
