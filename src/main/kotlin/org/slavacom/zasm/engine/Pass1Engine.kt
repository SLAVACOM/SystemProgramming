package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.InstructionFormat
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.SymbolTableEntry
import org.slavacom.zasm.model.instructionFormatOf
import org.slavacom.zasm.model.parseHex
import org.slavacom.zasm.model.registerNumber
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
 * Первый проход: вычисляет адреса меток и формирует вспомогательную таблицу
 * с ЧАСТИЧНО СГЕНЕРИРОВАННЫМИ командами — регистровые операнды известны сразу
 * и кодируются в hex уже здесь; символический адрес/смещение остаётся
 * неразрешённым текстом (именем), это довершает [Pass2Engine], когда
 * построена полная ТСИ.
 *
 * [loadAddress] — необязательный адрес из поля UI ("по умолчанию"); если
 * директива `Start` в тексте сама указывает адрес операндом (`Exampl Start
 * 00001000`), он ИМЕЕТ ПРИОРИТЕТ и переопределяет значение поля. Если ни
 * поле, ни `Start` адреса не задают — ошибка «не указан адрес загрузки».
 */
object Pass1Engine {

    private const val PSEUDO_START = "START"
    private const val PSEUDO_END = "END"
    private const val PSEUDO_WORD = "WORD"
    private const val PSEUDO_BYTE = "BYTE"
    private val PSEUDO_OPS = setOf(PSEUDO_START, PSEUDO_END, PSEUDO_WORD, PSEUDO_BYTE)

    fun run(source: List<SourceLine>, opcodes: List<OpcodeEntry>, loadAddress: Int?): Pass1Result {
        val auxTable = mutableListOf<AuxTableRow>()
        val symbolTable = mutableListOf<SymbolTableEntry>()
        val errors = mutableListOf<AssemblerError>()
        val knownNames = mutableSetOf<String>()
        val reservedNames = PSEUDO_OPS + opcodes.map { it.mnemonic.uppercase() }

        var resolvedLoadAddress = loadAddress
        var address = loadAddress ?: 0
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
            symbolTable += SymbolTableEntry(name, atAddress)
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

                    val addressText = line.operand1.trim()
                    if (addressText.isNotEmpty()) {
                        val parsedAddress = parseHex(addressText)
                        if (parsedAddress == null) {
                            errors += AssemblerError(lineNo, "некорректный адрес в директиве Start: '$addressText'")
                        } else {
                            address = parsedAddress
                            resolvedLoadAddress = parsedAddress
                        }
                    } else if (resolvedLoadAddress == null) {
                        errors += AssemblerError(lineNo, "не указан адрес загрузки (ни в поле, ни в Start)")
                    }
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

        val finalLoadAddress = resolvedLoadAddress ?: 0
        return Pass1Result(
            programName = programName.ifBlank { "NONAME" },
            loadAddress = finalLoadAddress,
            programLength = address - finalLoadAddress,
            auxTable = auxTable,
            symbolTable = symbolTable,
            errors = errors,
        )
    }
}
