package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.AuxTableRow
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.samples.Stage1Samples

class Pass1EngineTest {

    private val opcodes = DefaultOpcodeTable.entries

    private fun run(vararg lines: SourceLine) = Pass1Engine.run(lines.toList(), opcodes)

    private fun line(label: String, op: String, operand1: String = "", operand2: String = "") =
        SourceLine(label, op, operand1, operand2)

    private fun assertSingleError(result: Pass1Result, fragment: String) {
        assertEquals(1, result.errors.size, "ошибки: ${result.errors}")
        assertTrue(result.errors.single().message.contains(fragment), "ошибки: ${result.errors}")
    }

    @Test
    fun `default sample builds the same tables as the C# version`() {
        val result = Pass1Engine.run(Stage1Samples.default.lines, opcodes)

        assertTrue(result.isSuccess, "неожиданные ошибки: ${result.errors}")
        assertEquals("PROG", result.programName)
        assertEquals(0x100, result.startAddress)
        assertEquals(0x68, result.programLength)
        assertEquals(0x100, result.entryPoint)

        assertEquals(
            mapOf("A1" to 0x104, "A2" to 0x10E, "B1" to 0x14A, "B2" to 0x14D, "B3" to 0x151, "B4" to 0x157, "L1" to 0x158),
            result.symbolTable.associate { it.name to it.address },
        )
        assertEquals(
            listOf(
                AuxTableRow(0x100, "START", "000100", "", 1),
                AuxTableRow(0x100, "05", "L1", "", 2),
                AuxTableRow(0x104, "RESB", "10", "", 3),
                AuxTableRow(0x10E, "RESW", "20", "", 4),
                AuxTableRow(0x14A, "WORD", "40", "", 5),
                AuxTableRow(0x14D, "BYTE", "X\"2F4C008A\"", "", 6),
                AuxTableRow(0x151, "BYTE", "C\"Hello!\"", "", 7),
                AuxTableRow(0x157, "BYTE", "12", "", 8),
                AuxTableRow(0x158, "09", "B1", "", 9),
                AuxTableRow(0x15C, "0D", "B4", "", 10),
                AuxTableRow(0x160, "10", "R1", "R2", 11),
                AuxTableRow(0x162, "15", "B1", "", 12),
                AuxTableRow(0x166, "18", "200", "", 13),
            ),
            result.auxTable,
        )
    }

    @Test
    fun `duplicate label stops the pass`() {
        val result = Pass1Engine.run(Stage1Samples.pass1Error.lines, opcodes)

        assertSingleError(result, "L1")
        assertEquals(3, result.errors.single().line)
    }

    @Test
    fun `program must start with START with a name and a nonzero address`() {
        assertSingleError(run(line("", "JMP", "L1"), line("", "END")), "START")
        assertSingleError(run(line("", "START", "100"), line("", "END")), "имя программы")
        assertSingleError(run(line("PROG", "START", "0"), line("", "END")), "равен 0")
        assertSingleError(run(line("PROG", "START", "XYZ"), line("", "END")), "адрес начала")
        assertSingleError(run(line("PROGRAMNAME1", "START", "100"), line("", "END")), "10 символов")
    }

    @Test
    fun `missing END is an error`() {
        assertSingleError(run(line("PROG", "START", "100"), line("A", "WORD", "1")), "END")
    }

    @Test
    fun `END operand sets the entry point within the program`() {
        val ok = run(line("PROG", "START", "100"), line("A", "WORD", "1"), line("", "END", "103"))
        assertTrue(ok.isSuccess, "ошибки: ${ok.errors}")
        assertEquals(0x103, ok.entryPoint)

        assertSingleError(run(line("PROG", "START", "100"), line("A", "WORD", "1"), line("", "END", "200")), "точки входа")
    }

    @Test
    fun `labels are checked for characters and reserved words`() {
        assertSingleError(run(line("PROG", "START", "100"), line("1A", "WORD", "1"), line("", "END")), "недопустимая метка")
        assertSingleError(run(line("PROG", "START", "100"), line("ADD", "WORD", "1"), line("", "END")), "зарезервированным")
        assertSingleError(run(line("PROG", "START", "100"), line("R3", "WORD", "1"), line("", "END")), "зарезервированным")
        assertSingleError(run(line("PROG", "START", "100"), line("PROG", "WORD", "1"), line("", "END")), "именем программы")
    }

    @Test
    fun `command format follows the length from the opcode table`() {
        assertSingleError(run(line("PROG", "START", "100"), line("", "ADD", "R1"), line("", "END")), "два регистра")
        assertSingleError(run(line("PROG", "START", "100"), line("", "INT", "256"), line("", "END")), "больше 255")
        assertSingleError(run(line("PROG", "START", "100"), line("", "JMP"), line("", "END")), "отсутствует операнд")
        assertSingleError(run(line("PROG", "START", "100"), line("", "MOVE", "R1"), line("", "END")), "MOVE")
    }

    @Test
    fun `BYTE accepts number, C-string, X-string and question mark`() {
        val result = run(
            line("PROG", "START", "100"),
            line("A", "BYTE", "255"),
            line("B", "BYTE", "C\"abc\""),
            line("C", "BYTE", "X\"0A0B\""),
            line("D", "BYTE", "?"),
            line("", "END"),
        )
        assertTrue(result.isSuccess, "ошибки: ${result.errors}")
        assertEquals(listOf(0x100, 0x101, 0x104, 0x106), result.symbolTable.map { it.address })
        assertEquals(0x107, result.endAddress)

        assertSingleError(run(line("PROG", "START", "100"), line("C", "BYTE", "X\"ABC\""), line("", "END")), "нечётное")
        assertSingleError(run(line("PROG", "START", "100"), line("C", "BYTE", "Hello"), line("", "END")), "формат")
    }

    @Test
    fun `address overflow beyond FFFFFF is detected`() {
        assertSingleError(run(line("PROG", "START", "FFFFF0"), line("A", "RESW", "10"), line("", "END")), "переполнение")
    }
}
