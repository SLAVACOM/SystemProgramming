package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.InstructionFormat
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.SymbolTableEntry
import org.slavacom.zasm.model.instructionFormatOf
import org.slavacom.zasm.model.registerNumber
import org.slavacom.zasm.model.toHex2

/** Результат первого прохода. */
data class Pass1Result(
    val programName: String,
    val loadAddress: Int,
    val programLength: Int,
    val auxTable: List<AuxTableRow>,
    val symbolTable: List<SymbolTableEntry>,
    val externalRefs: List<String>,
    val errors: List<AssemblerError>,
)

/**
 * Первый проход: вычисляет адреса меток и формирует вспомогательную таблицу
 * с ЧАСТИЧНО СГЕНЕРИРОВАННЫМИ командами — регистровые операнды известны сразу
 * и кодируются в hex уже здесь; символический адрес/смещение остаётся
 * неразрешённым текстом (именем), это довершает [Pass2Engine], когда
 * построена полная ТСИ.
 *
 * Раздельное ассемблирование (Этап 3): `EXTDEF name1 [name2]` помечает
 * локально определяемые (меткой) имена как экспортируемые
 * ([SymbolTableEntry.isExternal]); `EXTREF name1 [name2]` объявляет имена,
 * определённые в ДРУГИХ модулях — такие операнды не считаются ошибкой
 * «не определено» ни здесь, ни в [Pass2Engine].
 */
object Pass1Engine {

    private const val PSEUDO_START = "START"
    private const val PSEUDO_END = "END"
    private const val PSEUDO_WORD = "WORD"
    private const val PSEUDO_BYTE = "BYTE"
    private const val PSEUDO_EXTDEF = "EXTDEF"
    private const val PSEUDO_EXTREF = "EXTREF"
    private val PSEUDO_OPS = setOf(PSEUDO_START, PSEUDO_END, PSEUDO_WORD, PSEUDO_BYTE, PSEUDO_EXTDEF, PSEUDO_EXTREF)

    fun run(source: List<SourceLine>, opcodes: List<OpcodeEntry>, loadAddress: Int): Pass1Result {
        val auxTable = mutableListOf<AuxTableRow>()
        val symbolTable = mutableListOf<SymbolTableEntry>()
        val errors = mutableListOf<AssemblerError>()
        val knownNames = mutableSetOf<String>()
        val reservedNames = PSEUDO_OPS + opcodes.map { it.mnemonic.uppercase() }

        val externalDefs = collectExternalNames(source, PSEUDO_EXTDEF)
        val externalRefDecls = collectExternalNames(source, PSEUDO_EXTREF)
        val externalRefs = externalRefDecls.map { it.first }
        val externalDefNamesUpper = externalDefs.map { it.first.uppercase() }.toSet()
        val externalRefNamesUpper = externalRefs.map { it.uppercase() }.toSet()

        externalDefNamesUpper.intersect(externalRefNamesUpper).forEach { clashUpper ->
            val original = externalDefs.first { it.first.uppercase() == clashUpper }.first
            errors += AssemblerError(0, "имя '$original' объявлено одновременно как EXTDEF и EXTREF")
        }

        var address = loadAddress
        var programName = ""
        var sawStart = false
        var sawEnd = false

        fun defineSymbol(name: String, atAddress: Int, lineNo: Int) {
            if (name.isBlank()) return
            if (name.uppercase() in reservedNames) {
                errors += AssemblerError(lineNo, "метка '$name' совпадает с зарезервированным словом (мнемоника/директива)")
                return
            }
            if (!knownNames.add(name.uppercase())) {
                errors += AssemblerError(lineNo, "повторное определение имени '$name'")
                return
            }
            symbolTable += SymbolTableEntry(name, atAddress, isExternal = name.uppercase() in externalDefNamesUpper)
        }

        fun encodeRegister(operand: String, lineNo: Int): String {
            val number = registerNumber(operand)
            if (number == null) {
                errors += AssemblerError(lineNo, "ожидался регистр, получено '$operand'")
                return operand
            }
            return toHex2(number)
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

                op.equals(PSEUDO_EXTDEF, ignoreCase = true) -> {
                    if (line.operand1.isBlank()) {
                        errors += AssemblerError(lineNo, "не указаны имена для EXTDEF")
                    }
                    // сами имена уже собраны заранее (collectExternalNames); адрес не резервирует
                }

                op.equals(PSEUDO_EXTREF, ignoreCase = true) -> {
                    if (line.operand1.isBlank()) {
                        errors += AssemblerError(lineNo, "не указаны имена для EXTREF")
                    }
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
                        val operand1 = line.operand1.trim()
                        val operand2 = line.operand2.trim()
                        val (encoded1, encoded2) = when (instructionFormatOf(entry.mnemonic)) {
                            InstructionFormat.REG_ADDR, InstructionFormat.REG_OFFSET ->
                                encodeRegister(operand1, lineNo) to operand2 // операнд2 — имя, разрешит проход 2
                            InstructionFormat.ADDR_ONLY -> operand1 to "" // сам операнд1 — имя, разрешит проход 2
                            InstructionFormat.REG_REG ->
                                encodeRegister(operand1, lineNo) to encodeRegister(operand2, lineNo)
                            null -> operand1 to operand2 // нестандартная мнемоника из отредактированной ТКО
                        }
                        auxTable += AuxTableRow(
                            address = address,
                            opText = toHex2(entry.code),
                            operand1 = encoded1,
                            operand2 = encoded2,
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

        externalDefs.forEach { (name, lineNo) ->
            if (symbolTable.none { it.name.equals(name, ignoreCase = true) }) {
                errors += AssemblerError(lineNo, "внешнее имя '$name' (EXTDEF) не определено меткой в этом модуле")
            }
        }

        return Pass1Result(
            programName = programName.ifBlank { "NONAME" },
            loadAddress = loadAddress,
            programLength = address - loadAddress,
            auxTable = auxTable,
            symbolTable = symbolTable,
            externalRefs = externalRefs,
            errors = errors,
        )
    }

    /**
     * Собирает уникальные имена (с сохранением исходного написания и номера
     * строки первого упоминания) из операндов строк с указанной псевдокомандой
     * (`EXTDEF`/`EXTREF`), которых может быть до двух на строку.
     */
    private fun collectExternalNames(source: List<SourceLine>, pseudo: String): List<Pair<String, Int>> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<String, Int>>()
        source.forEachIndexed { index, line ->
            if (!line.op.trim().equals(pseudo, ignoreCase = true)) return@forEachIndexed
            listOf(line.operand1.trim(), line.operand2.trim()).forEach { name ->
                if (name.isNotBlank() && seen.add(name.uppercase())) {
                    result += name to (index + 1)
                }
            }
        }
        return result
    }
}
