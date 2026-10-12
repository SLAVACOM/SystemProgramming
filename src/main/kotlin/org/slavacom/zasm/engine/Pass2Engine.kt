package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.charStringBody
import org.slavacom.zasm.model.hexStringBody
import org.slavacom.zasm.model.parseDecimal
import org.slavacom.zasm.model.parseHex
import org.slavacom.zasm.model.registerNumber
import org.slavacom.zasm.model.toAsciiHex
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex6

/** Результат второго прохода: записи объектного модуля H/T/E (пусто при ошибке). */
data class Pass2Result(
    val records: List<String>,
    val errors: List<AssemblerError>,
)

/**
 * Второй проход: формирует объектный модуль в абсолютном формате —
 * `H  имя  адрес_начала  длина`, по записи `T  адрес  длина  код` на каждую
 * строку вспомогательной таблицы и `E  точка_входа`. Операнды-метки
 * заменяются адресами из ТСИ, регистры — номером (1 hex-цифра), числа — hex.
 * Останавливается на первой ошибке.
 */
object Pass2Engine {

    fun run(pass1: Pass1Result): Pass2Result = Runner(pass1).execute()

    private class Failure(val error: AssemblerError) : Exception(error.message)

    private class Runner(private val pass1: Pass1Result) {
        private val records = mutableListOf<String>()

        fun execute(): Pass2Result = try {
            pass1.auxTable.forEach(::processRow)
            records += "E  ${toHex6(pass1.entryPoint)}"
            Pass2Result(records, emptyList())
        } catch (failure: Failure) {
            Pass2Result(emptyList(), listOf(failure.error))
        }

        private fun fail(row: AuxTableRow, message: String): Nothing =
            throw Failure(AssemblerError(row.sourceLine, message))

        private fun textRecord(row: AuxTableRow, length: Int, code: String = "") {
            records += "T  ${toHex6(row.address)}  ${toHex2(length)}   $code".trimEnd()
        }

        /** Запись с кодом: длина — число байт в [code]. */
        private fun codeRecord(row: AuxTableRow, code: String) = textRecord(row, (code.length + 1) / 2, code)

        private fun processRow(row: AuxTableRow) {
            when (row.opText) {
                "START" -> records += "H  ${pass1.programName}  ${toHex6(pass1.startAddress)}  ${toHex6(pass1.programLength)}"
                "WORD" ->
                    if (row.operand1 == "?") textRecord(row, 3) else codeRecord(row, toHex6(parseDecimal(row.operand1)!!))
                "BYTE" -> processByte(row)
                "RESB" -> textRecord(row, row.operand1.toInt())
                "RESW" -> textRecord(row, row.operand1.toInt() * 3)
                else -> processCommand(row)
            }
        }

        private fun processByte(row: AuxTableRow) {
            val operand = row.operand1
            if (operand == "?") return textRecord(row, 1)
            val code = parseDecimal(operand)?.let(::toHex2)
                ?: charStringBody(operand)?.let(::toAsciiHex)
                ?: hexStringBody(operand)?.uppercase()
                ?: fail(row, "неверный формат операнда BYTE '$operand'")
            codeRecord(row, code)
        }

        private fun processCommand(row: AuxTableRow) {
            val opByte = parseHex(row.opText) ?: fail(row, "неизвестный МКОП '${row.opText}'")
            val entry = pass1.opcodes.find { it.code == opByte shr 2 }
                ?: fail(row, "МКОП '${row.opText}' отсутствует в таблице кодов операций")
            val addressingType = opByte and 0x03

            val operands = if (addressingType == 1) {
                resolveAddress(row, entry.length)
            } else {
                encodeOperand(row, row.operand1) + encodeOperand(row, row.operand2)
            }
            codeRecord(row, row.opText + operands)
        }

        /** Прямая адресация: операнд обязан быть меткой; адрес — 2 байта для длины 3, 3 байта для длины 4. */
        private fun resolveAddress(row: AuxTableRow, commandLength: Int): String {
            val name = row.operand1
            val symbol = pass1.symbolTable.find { it.name == name }
            if (symbol == null) {
                if (registerNumber(name) != null || parseDecimal(name) != null) {
                    fail(row, "для данного типа адресации операнд должен быть меткой: '$name'")
                }
                fail(row, "ошибка в операнде, имя '$name' отсутствует в ТСИ")
            }
            return if (commandLength == 3) {
                if (symbol.address > 0xFFFF) fail(row, "адрес метки '$name' не помещается в 2 байта команды")
                "%04X".format(symbol.address)
            } else {
                toHex6(symbol.address)
            }
        }

        /** Операнд без адресации: регистр — 1 hex-цифра, число — 1 байт. */
        private fun encodeOperand(row: AuxTableRow, operand: String): String {
            if (operand.isEmpty()) return ""
            registerNumber(operand)?.let { return "%X".format(it) }
            parseDecimal(operand)?.let { return toHex2(it) }
            fail(row, "ошибка в операнде '$operand'")
        }
    }
}
