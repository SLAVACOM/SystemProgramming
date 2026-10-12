package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AddressingMode
import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.DIRECT_ADDRESSING
import org.slavacom.zasm.model.MAX_ADDRESS
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.RELATIVE_ADDRESSING
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.SymbolTableEntry
import org.slavacom.zasm.model.charStringBody
import org.slavacom.zasm.model.hexStringBody
import org.slavacom.zasm.model.isAlphanumeric
import org.slavacom.zasm.model.isDirective
import org.slavacom.zasm.model.isValidLabel
import org.slavacom.zasm.model.parseDecimal
import org.slavacom.zasm.model.parseHex
import org.slavacom.zasm.model.registerNumber
import org.slavacom.zasm.model.relativeOperandName
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex6

/** Результат первого прохода. При ошибке таблицы содержат то, что успели построить до неё. */
data class Pass1Result(
    val programName: String,
    val startAddress: Int,
    /** Адрес, следующий за последним байтом программы. */
    val endAddress: Int,
    /** Точка входа — операнд END (по умолчанию адрес начала). */
    val entryPoint: Int,
    val auxTable: List<AuxTableRow>,
    val symbolTable: List<SymbolTableEntry>,
    /** ТКО, по которой выполнен проход, — второй проход использует именно её. */
    val opcodes: List<OpcodeEntry>,
    /** Режим перемещаемого формата; null — абсолютный формат (Этап 1). */
    val mode: AddressingMode?,
    val errors: List<AssemblerError>,
    /** Замечания, не мешающие второму проходу (например, лишний операнд START). */
    val warnings: List<AssemblerError>,
) {
    val programLength: Int get() = endAddress - startAddress
    val isSuccess: Boolean get() = errors.isEmpty()
}

/**
 * Первый проход ассемблера: проверяет синтаксис, считает адреса, строит ТСИ и
 * вспомогательную таблицу. Для команд в таблицу пишется код операции с типом
 * адресации (`код*4` — без адресации, `код*4+1` — прямая, `код*4+2` —
 * относительная, для команд длиной 3/4); операнды переносятся как есть (метка
 * без скобок) и разрешаются на втором проходе. Останавливается на первой ошибке.
 *
 * [mode] = null — абсолютный формат (Этап 1): адрес начала ненулевой, только
 * прямая адресация. Иначе — перемещаемый формат (Этап 2): программа
 * ассемблируется с адреса 0 и допускает только адресацию, разрешённую режимом.
 */
object Pass1Engine {

    fun run(source: List<SourceLine>, opcodes: List<OpcodeEntry>, mode: AddressingMode? = null): Pass1Result =
        Runner(source, opcodes, mode).execute()

    private class Failure(val error: AssemblerError) : Exception(error.message)

    private class Runner(
        private val source: List<SourceLine>,
        private val opcodes: List<OpcodeEntry>,
        private val mode: AddressingMode?,
    ) {
        private val auxTable = mutableListOf<AuxTableRow>()
        private val symbolTable = mutableListOf<SymbolTableEntry>()
        private val warnings = mutableListOf<AssemblerError>()

        private var programName = ""
        private var startAddress = 0
        private var address = 0
        private var entryPoint = 0
        private var sawStart = false

        fun execute(): Pass1Result {
            val error = try {
                process()
                null
            } catch (failure: Failure) {
                failure.error
            }
            return Pass1Result(
                programName = programName,
                startAddress = startAddress,
                endAddress = address,
                entryPoint = entryPoint,
                auxTable = auxTable,
                symbolTable = symbolTable,
                opcodes = opcodes,
                mode = mode,
                errors = listOfNotNull(error),
                warnings = warnings,
            )
        }

        private fun fail(line: Int, message: String): Nothing = throw Failure(AssemblerError(line, message))

        private fun process() {
            val lines = source.withIndex().filterNot { it.value.isBlank }
            if (lines.isEmpty()) fail(0, "исходный текст программы пуст")

            for ((index, line) in lines) {
                val lineNo = index + 1
                val label = line.label.trim()
                val op = line.op.trim().uppercase()
                val operand1 = line.operand1.trim()
                val operand2 = line.operand2.trim()

                checkSyntax(lineNo, label, op, operand1, operand2)

                if (!sawStart) {
                    if (op != "START") fail(lineNo, "программа должна начинаться с директивы START")
                    processStart(lineNo, label, operand1, operand2)
                    continue
                }

                if (label.isNotEmpty()) {
                    if (symbolTable.any { it.name == label }) fail(lineNo, "найдена уже существующая метка '$label'")
                    symbolTable += SymbolTableEntry(label, address)
                }

                when (op) {
                    "START" -> fail(lineNo, "повторная директива START")
                    "END" -> {
                        processEnd(lineNo, operand1)
                        return
                    }
                    "WORD" -> processWord(lineNo, operand1, operand2)
                    "BYTE" -> processByte(lineNo, operand1, operand2)
                    "RESB" -> processReserve(lineNo, op, operand1, operand2, unitSize = 1)
                    "RESW" -> processReserve(lineNo, op, operand1, operand2, unitSize = 3)
                    "" -> fail(lineNo, "не указан МКОП")
                    else -> processCommand(lineNo, op, operand1, operand2)
                }
            }
            fail(0, "не найдена директива END (точка входа в программу)")
        }

        private fun isMnemonic(word: String): Boolean = opcodes.any { it.mnemonic.equals(word, ignoreCase = true) }

        private fun checkSyntax(lineNo: Int, label: String, op: String, operand1: String, operand2: String) {
            if (label.isNotEmpty()) {
                if (!isValidLabel(label)) {
                    fail(lineNo, "недопустимая метка '$label' (латинские буквы и цифры, первая — буква)")
                }
                if (isDirective(label) || isMnemonic(label) || registerNumber(label) != null) {
                    fail(lineNo, "метка '$label' совпадает с зарезервированным словом")
                }
                if (sawStart && label.equals(programName, ignoreCase = true)) {
                    fail(lineNo, "метка '$label' совпадает с именем программы")
                }
            }
            if (!isAlphanumeric(op)) fail(lineNo, "недопустимые символы в поле МКОП '$op'")
            if (!isAlphanumeric(operand2)) fail(lineNo, "недопустимые символы во втором операнде '$operand2'")
            for (operand in listOf(operand1, operand2)) {
                if (isDirective(operand) || isMnemonic(operand)) {
                    fail(lineNo, "операнд '$operand' совпадает с зарезервированным словом")
                }
            }
        }

        /** Сдвигает счётчик адреса, проверяя выход за границу памяти. */
        private fun advance(lineNo: Int, size: Int) {
            if (address.toLong() + size > MAX_ADDRESS) {
                fail(lineNo, "произошло переполнение — выход за границу памяти ${toHex6(MAX_ADDRESS)}")
            }
            address += size
        }

        private fun addRow(lineNo: Int, opText: String, operand1: String, operand2: String = "") {
            auxTable += AuxTableRow(address, opText, operand1, operand2, lineNo)
        }

        private fun rejectSecondOperand(lineNo: Int, op: String, operand2: String) {
            if (operand2.isNotEmpty()) fail(lineNo, "второй операнд $op не рассматривается")
        }

        private fun processStart(lineNo: Int, label: String, operand1: String, operand2: String) {
            val start = if (mode != null && operand1.isEmpty()) 0 else {
                parseHex(operand1) ?: fail(lineNo, "неверный адрес начала программы '$operand1'")
            }
            if (mode == null) {
                if (start == 0) fail(lineNo, "адрес начала программы не может быть равен 0")
                if (start > MAX_ADDRESS) fail(lineNo, "неправильный адрес загрузки (больше ${toHex6(MAX_ADDRESS)})")
            } else if (start != 0) {
                fail(lineNo, "в перемещаемом формате адрес начала программы должен быть равен 0")
            }
            if (label.isEmpty()) fail(lineNo, "не задано имя программы")
            if (label.length > 10) fail(lineNo, "превышена длина имени программы (> 10 символов)")

            sawStart = true
            programName = label
            startAddress = start
            address = start
            entryPoint = start
            addRow(lineNo, "START", toHex6(start))
            if (operand2.isNotEmpty()) {
                warnings += AssemblerError(lineNo, "второй операнд директивы START не рассматривается")
            }
        }

        private fun processEnd(lineNo: Int, operand1: String) {
            if (operand1.isEmpty()) return
            val entry = parseHex(operand1) ?: fail(lineNo, "неверный адрес входа в программу '$operand1'")
            if (entry !in startAddress..address) {
                fail(lineNo, "адрес точки входа ${toHex6(entry)} вне программы (${toHex6(startAddress)}..${toHex6(address)})")
            }
            entryPoint = entry
        }

        private fun processWord(lineNo: Int, operand1: String, operand2: String) {
            if (operand1 != "?") {
                val value = parseDecimal(operand1) ?: fail(lineNo, "невозможно выполнить преобразование в число '$operand1'")
                if (value > MAX_ADDRESS) fail(lineNo, "превышено максимальное значение слова (${MAX_ADDRESS})")
            }
            rejectSecondOperand(lineNo, "директивы WORD", operand2)
            addRow(lineNo, "WORD", operand1)
            advance(lineNo, 3)
        }

        private fun processByte(lineNo: Int, operand1: String, operand2: String) {
            val number = parseDecimal(operand1)
            val chars = charStringBody(operand1)
            val hexDigits = hexStringBody(operand1)
            val size = when {
                operand1 == "?" -> 1
                number != null -> {
                    if (number > 255) fail(lineNo, "значение байта больше 255")
                    1
                }
                chars != null -> chars.length
                hexDigits != null -> {
                    if (hexDigits.length % 2 != 0) fail(lineNo, "нечётное количество символов в строке $operand1")
                    hexDigits.length / 2
                }
                else -> fail(lineNo, "неверный формат операнда BYTE '$operand1' (число 0..255, C\"…\", X\"…\" или ?)")
            }
            rejectSecondOperand(lineNo, "директивы BYTE", operand2)
            addRow(lineNo, "BYTE", operand1)
            advance(lineNo, size)
        }

        private fun processReserve(lineNo: Int, op: String, operand1: String, operand2: String, unitSize: Int) {
            val count = parseDecimal(operand1) ?: fail(lineNo, "невозможно выполнить преобразование в число '$operand1'")
            if (count <= 0) fail(lineNo, "количество для $op должно быть больше нуля")
            rejectSecondOperand(lineNo, "директивы $op", operand2)
            addRow(lineNo, op, count.toString())
            advance(lineNo, count * unitSize)
        }

        private fun processCommand(lineNo: Int, op: String, operand1: String, operand2: String) {
            val entry = opcodes.find { it.mnemonic.equals(op, ignoreCase = true) }
                ?: fail(lineNo, "МКОП '$op' не найден в таблице кодов операций")

            when (entry.length) {
                1 -> {
                    if (operand1.isNotEmpty() || operand2.isNotEmpty()) {
                        fail(lineNo, "операнды не рассматриваются в команде ${entry.mnemonic}")
                    }
                    addRow(lineNo, toHex2(entry.code * 4), "")
                }
                2 -> {
                    val number = parseDecimal(operand1)
                    when {
                        number != null -> {
                            if (number > 255) fail(lineNo, "число в команде ${entry.mnemonic} больше 255")
                            rejectSecondOperand(lineNo, "команды ${entry.mnemonic}", operand2)
                        }
                        registerNumber(operand1) != null && registerNumber(operand2) != null -> Unit
                        else -> fail(lineNo, "ошибка в команде ${entry.mnemonic}: ожидается число 0..255 или два регистра")
                    }
                    addRow(lineNo, toHex2(entry.code * 4), operand1, operand2)
                }
                3, 4 -> {
                    if (operand1.isEmpty()) fail(lineNo, "у команды ${entry.mnemonic} отсутствует операнд")
                    rejectSecondOperand(lineNo, "команды ${entry.mnemonic}", operand2)
                    val target = relativeOperandName(operand1)
                    checkAddressingMode(lineNo, operand1, isRelative = target != null)
                    if (target != null && !isValidLabel(target)) {
                        fail(lineNo, "ошибка в операнде '$operand1': в квадратных скобках должна быть метка")
                    }
                    val type = if (target != null) RELATIVE_ADDRESSING else DIRECT_ADDRESSING
                    addRow(lineNo, toHex2(entry.code * 4 + type), target ?: operand1)
                }
                else -> fail(lineNo, "недопустимая длина команды ${entry.mnemonic}: ${entry.length}")
            }
            advance(lineNo, entry.length)
        }

        /** Операнд `[метка]` — относительная адресация, иначе прямая; должна быть разрешена режимом. */
        private fun checkAddressingMode(lineNo: Int, operand: String, isRelative: Boolean) {
            when {
                mode == null -> if (isRelative) {
                    fail(lineNo, "относительная адресация '$operand' недоступна в абсолютном формате")
                }
                isRelative -> if (!mode.allowsRelative) {
                    fail(lineNo, "относительная адресация '$operand' запрещена: выбран режим «${mode.title.lowercase()}»")
                }
                else -> if (!mode.allowsDirect) {
                    fail(
                        lineNo,
                        "прямая адресация '$operand' запрещена: выбран режим «${mode.title.lowercase()}» " +
                            "(операнд записывается как [метка])",
                    )
                }
            }
        }
    }
}
