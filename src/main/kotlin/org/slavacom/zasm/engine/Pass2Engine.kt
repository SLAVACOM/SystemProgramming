package org.slavacom.zasm.engine

import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.DIRECT_ADDRESSING
import org.slavacom.zasm.model.RELATIVE_ADDRESSING
import org.slavacom.zasm.model.SymbolTableEntry
import org.slavacom.zasm.model.charStringBody
import org.slavacom.zasm.model.hexStringBody
import org.slavacom.zasm.model.parseDecimal
import org.slavacom.zasm.model.parseHex
import org.slavacom.zasm.model.registerNumber
import org.slavacom.zasm.model.toAsciiHex
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex6

/**
 * Результат второго прохода: записи объектного модуля H/T/M/E и таблица
 * настройки — адреса команд, операнды которых настраиваются при загрузке
 * (всё пусто при ошибке).
 */
data class Pass2Result(
    val records: List<String>,
    val errors: List<AssemblerError>,
    val relocations: List<Int> = emptyList(),
)

/**
 * Второй проход: формирует объектный модуль —
 * `H  имя  адрес_начала  длина`, по записи `T  адрес  длина  код` на каждую
 * строку вспомогательной таблицы, в перемещаемом формате — по записи
 * `M  адрес_команды` на каждую команду с прямой адресацией, и `E  точка_входа`.
 * Прямая адресация заменяет метку её адресом из ТСИ, относительная — смещением
 * от адреса следующей команды (дополнительный код); регистры — номером
 * (1 hex-цифра), числа — hex. Останавливается на первой ошибке.
 */
object Pass2Engine {

    fun run(pass1: Pass1Result): Pass2Result = Runner(pass1).execute()

    private class Failure(val error: AssemblerError) : Exception(error.message)

    private class Runner(private val pass1: Pass1Result) {
        private val records = mutableListOf<String>()
        private val relocations = mutableListOf<Int>()

        fun execute(): Pass2Result = try {
            pass1.auxTable.forEach(::processRow)
            relocations.forEach { records += "M  ${toHex6(it)}" }
            records += "E  ${toHex6(pass1.entryPoint)}"
            Pass2Result(records, emptyList(), relocations)
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
            val operands = when (val addressingType = opByte and 0x03) {
                0 -> encodeOperand(row, row.operand1) + encodeOperand(row, row.operand2)
                DIRECT_ADDRESSING -> {
                    if (pass1.mode?.allowsDirect == false) {
                        fail(row, "прямая адресация запрещена: выбран режим «${pass1.mode.title.lowercase()}»")
                    }
                    if (pass1.mode != null) relocations += row.address
                    resolveAddress(row, entry.length)
                }
                RELATIVE_ADDRESSING -> {
                    if (pass1.mode?.allowsRelative != true) {
                        fail(row, "относительная адресация запрещена в ${pass1.mode?.let { "режиме «${it.title.lowercase()}»" } ?: "абсолютном формате"}")
                    }
                    resolveOffset(row, entry.length)
                }
                else -> fail(row, "неизвестный тип адресации $addressingType в МКОП '${row.opText}'")
            }
            codeRecord(row, row.opText + operands)
        }

        /** Операнд прямой/относительной адресации обязан быть меткой из ТСИ. */
        private fun findSymbol(row: AuxTableRow): SymbolTableEntry {
            val name = row.operand1
            return pass1.symbolTable.find { it.name == name } ?: run {
                if (registerNumber(name) != null || parseDecimal(name) != null) {
                    fail(row, "для данного типа адресации операнд должен быть меткой: '$name'")
                }
                fail(row, "ошибка в операнде, имя '$name' отсутствует в ТСИ")
            }
        }

        /**
         * Относительная адресация: смещение = адрес метки − адрес следующей команды,
         * в дополнительном коде на 2 байта (длина 3) или 3 байта (длина 4).
         */
        private fun resolveOffset(row: AuxTableRow, commandLength: Int): String {
            val symbol = findSymbol(row)
            val offset = symbol.address - (row.address + commandLength)
            val bits = (commandLength - 1) * 8
            if (offset !in -(1 shl (bits - 1)) until (1 shl (bits - 1))) {
                fail(row, "смещение до метки '${symbol.name}' не помещается в ${commandLength - 1} байта команды")
            }
            return "%0${bits / 4}X".format(offset and ((1 shl bits) - 1))
        }

        /** Прямая адресация: адрес — 2 байта для длины 3, 3 байта для длины 4. */
        private fun resolveAddress(row: AuxTableRow, commandLength: Int): String {
            val name = row.operand1
            val symbol = findSymbol(row)
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
