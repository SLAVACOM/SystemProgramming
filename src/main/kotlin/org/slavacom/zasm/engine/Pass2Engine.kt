package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.ExternalNameEntry
import org.slavacom.zasm.model.InstructionFormat
import org.slavacom.zasm.model.ObjectHeader
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.RelocationEntry
import org.slavacom.zasm.model.instructionFormatOf
import org.slavacom.zasm.model.parseNumberOrHex
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex4
import org.slavacom.zasm.model.toHex8

/**
 * Результат второго прохода.
 * [relocationTable] — команды с прямой адресацией символьного операнда
 * ([InstructionFormat.REG_ADDR]/[InstructionFormat.ADDR_ONLY]):
 * [RelocationEntry.externalName] == null — обычная внутренняя настройка
 * (адрес определён в этом же модуле, Этап 2), иначе — операнд ссылается на
 * внешнее имя (EXTREF), и адрес должен подставить компоновщик (Этап 3).
 * [externalNames] — локальные имена, экспортируемые этим модулем через
 * EXTDEF, с их адресом.
 */
data class Pass2Result(
    val header: ObjectHeader,
    val binaryLines: List<String>,
    val relocationTable: List<RelocationEntry>,
    val externalNames: List<ExternalNameEntry>,
    val errors: List<AssemblerError>,
)

/**
 * Второй проход: довершает частично сгенерированные командные строки
 * вспомогательной таблицы — разрешает символический операнд (адрес или
 * смещение), дописывает уже готовые регистровые байты из [Pass1Engine] и
 * формирует таблицу настройки для прямой адресации (в т.ч. внешние ссылки).
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
        private val relocationTable = mutableListOf<RelocationEntry>()

        fun execute(): Pass2Result {
            pass1.auxTable.forEach { row -> processRow(row) }
            val header = ObjectHeader(pass1.programName, pass1.programLength, pass1.loadAddress)
            val externalNames = pass1.symbolTable
                .filter { it.isExternal }
                .map { ExternalNameEntry(it.address, it.name) }
            return Pass2Result(header, binaryLines, relocationTable, externalNames, errors)
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

        private fun encodeInstruction(entry: OpcodeEntry, row: AuxTableRow): String? {
            val format = instructionFormatOf(entry.mnemonic)
            if (format == null) {
                errors += AssemblerError(row.sourceLine, "неизвестная форма команды '${entry.mnemonic}'")
                return null
            }

            val nextAddress = row.address + entry.length

            return when (format) {
                InstructionFormat.REG_ADDR -> {
                    val target = resolveDirect(row.operand2, row.address, row.sourceLine) ?: return null
                    listOf(toHex2(entry.code), row.operand1, toHex8(target)).joinToString(" ")
                }

                InstructionFormat.REG_OFFSET -> {
                    val target = resolveRelative(row.operand2, row.sourceLine) ?: return null
                    listOf(toHex2(entry.code), row.operand1, toHex4(target - nextAddress)).joinToString(" ")
                }

                InstructionFormat.ADDR_ONLY -> {
                    val target = resolveDirect(row.operand1, row.address, row.sourceLine) ?: return null
                    listOf(toHex2(entry.code), toHex8(target)).joinToString(" ")
                }

                InstructionFormat.REG_REG -> {
                    // оба операнда — регистры, полностью закодированы уже на первом проходе
                    listOf(toHex2(entry.code), row.operand1, row.operand2).joinToString(" ")
                }
            }
        }

        /**
         * Резолв операнда для прямой адресации (абсолютный адрес зашит в код):
         * сначала локальная ТСИ; если не найдено — внешняя ссылка (EXTREF):
         * адрес неизвестен, кодируется 0, а имя уходит в таблицу настройки
         * для компоновщика; иначе — ошибка «не определено имя».
         */
        private fun resolveDirect(name: String, commandAddress: Int, lineNo: Int): Int? {
            val local = pass1.symbolTable.find { it.name.equals(name, ignoreCase = true) }
            if (local != null) {
                relocationTable += RelocationEntry(commandAddress)
                return local.address
            }
            val external = pass1.externalRefs.find { it.equals(name, ignoreCase = true) }
            if (external != null) {
                relocationTable += RelocationEntry(commandAddress, external)
                return 0
            }
            errors += AssemblerError(lineNo, "не определено имя '$name'")
            return null
        }

        /** Резолв операнда для относительной адресации — только локальные имена (внешние сюда не годятся). */
        private fun resolveRelative(name: String, lineNo: Int): Int? {
            val local = pass1.symbolTable.find { it.name.equals(name, ignoreCase = true) }
            if (local != null) return local.address
            if (pass1.externalRefs.any { it.equals(name, ignoreCase = true) }) {
                errors += AssemblerError(lineNo, "внешнее имя '$name' нельзя использовать при относительной адресации")
                return null
            }
            errors += AssemblerError(lineNo, "не определено имя '$name'")
            return null
        }
    }
}
