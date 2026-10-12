package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.AddressingMode
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.samples.AssemblerSample
import org.slavacom.zasm.samples.Stage2Samples

class RelocationTest {

    private val opcodes = DefaultOpcodeTable.entries

    private fun pass1(sample: AssemblerSample) = Pass1Engine.run(sample.lines, opcodes, sample.mode)

    private fun assemble(lines: List<SourceLine>, mode: AddressingMode, table: List<OpcodeEntry> = opcodes): Pass2Result {
        val pass1 = Pass1Engine.run(lines, table, mode)
        assertTrue(pass1.isSuccess, "ошибки первого прохода: ${pass1.errors}")
        return Pass2Engine.run(pass1)
    }

    private fun line(label: String, op: String, operand1: String = "", operand2: String = "") =
        SourceLine(label, op, operand1, operand2)

    private fun assertSingleError(errors: List<*>, fragment: String) {
        assertEquals(1, errors.size, "ошибки: $errors")
        assertTrue(errors.single().toString().contains(fragment), "ошибки: $errors")
    }

    @Test
    fun `direct mode relocates every direct-addressing command`() {
        val result = assemble(Stage2Samples.direct.lines, AddressingMode.DIRECT)

        assertTrue(result.errors.isEmpty(), "неожиданные ошибки: ${result.errors}")
        assertEquals(listOf(0x00, 0x58, 0x5C, 0x62), result.relocations)
        assertEquals(
            listOf(
                "H  PROG  000000  000068",
                "T  000000  04   05000058",     // JMP L1
                "T  000004  0A",
                "T  00000E  3C",
                "T  00004A  03   000028",
                "T  00004D  04   2F4C008A",
                "T  000051  06   48656C6C6F21",
                "T  000057  01   0C",
                "T  000058  04   0900004A",     // LOADR1 B1
                "T  00005C  04   0D000057",     // LOADR2 B4
                "T  000060  02   1012",         // ADD R1 R2 — не настраивается
                "T  000062  04   1500004A",     // SAVER1 B1
                "T  000066  02   18C8",
                "M  000000",
                "M  000058",
                "M  00005C",
                "M  000062",
                "E  000000",
            ),
            result.records,
        )
    }

    @Test
    fun `relative mode encodes offsets from the next command and needs no relocation`() {
        val result = assemble(Stage2Samples.relative.lines, AddressingMode.RELATIVE)

        assertTrue(result.errors.isEmpty(), "неожиданные ошибки: ${result.errors}")
        assertTrue(result.relocations.isEmpty())
        val code = result.records.filter { it.startsWith("T") && it.length > 16 }.map { it.substringAfterLast(' ') }
        assertEquals("06000054", code.first())                 // JMP [L1]: 58 − 04 = +54
        assertTrue("0AFFFFEE" in code, "код: $code")           // LOADR1 [B1]: 4A − 5C = −12
        assertTrue("0EFFFFF7" in code, "код: $code")           // LOADR2 [B4]: 57 − 60 = −9
        assertTrue("16FFFFE4" in code, "код: $code")           // SAVER1 [B1]: 4A − 66 = −1C
        assertTrue(result.records.none { it.startsWith("M") })
    }

    @Test
    fun `mixed mode relocates only direct-addressing commands`() {
        val result = assemble(Stage2Samples.mixed.lines, AddressingMode.MIXED)

        assertTrue(result.errors.isEmpty(), "неожиданные ошибки: ${result.errors}")
        assertEquals(listOf(0x58, 0x62), result.relocations)
        val code = result.records.filter { it.startsWith("T") && it.length > 16 }.map { it.substringAfterLast(' ') }
        assertEquals("06000054", code.first())                 // JMP [L1] — относительная
        assertTrue("0900004A" in code, "код: $code")           // LOADR1 B1 — прямая
        assertTrue("0EFFFFF7" in code, "код: $code")           // LOADR2 [B4] — относительная
        assertTrue("1500004A" in code, "код: $code")           // SAVER1 B1 — прямая
        assertEquals(listOf("M  000058", "M  000062"), result.records.filter { it.startsWith("M") })
    }

    @Test
    fun `operand of the other addressing type is a first pass error`() {
        assertSingleError(pass1(Stage2Samples.relativeInDirectMode).errors, "относительная адресация '[B4]' запрещена")
        assertSingleError(pass1(Stage2Samples.directInRelativeMode).errors, "прямая адресация 'B4' запрещена")
    }

    @Test
    fun `relocatable program must start at address 0`() {
        assertSingleError(pass1(Stage2Samples.nonzeroStart).errors, "должен быть равен 0")

        val implicitZero = Pass1Engine.run(
            listOf(line("PROG", "START"), line("", "END")),
            opcodes,
            AddressingMode.DIRECT,
        )
        assertTrue(implicitZero.isSuccess, "ошибки: ${implicitZero.errors}")
        assertEquals(0, implicitZero.startAddress)
    }

    @Test
    fun `brackets must contain a label and are rejected in absolute format`() {
        val bad = Pass1Engine.run(
            listOf(line("PROG", "START", "0"), line("", "JMP", "[1A]"), line("", "END")),
            opcodes,
            AddressingMode.RELATIVE,
        )
        assertSingleError(bad.errors, "в квадратных скобках должна быть метка")

        val absolute = Pass1Engine.run(
            listOf(line("PROG", "START", "100"), line("L", "JMP", "[L]"), line("", "END")),
            opcodes,
        )
        assertSingleError(absolute.errors, "недоступна в абсолютном формате")
    }

    @Test
    fun `undefined label in relative mode is a second pass error`() {
        val result = Pass2Engine.run(pass1(Stage2Samples.undefinedLabel))
        assertSingleError(result.errors, "NOWHERE")
        assertTrue(result.records.isEmpty() && result.relocations.isEmpty())
    }

    @Test
    fun `offset range depends on command length`() {
        val table = opcodes + OpcodeEntry("JS", 0x08, 3)
        val near = assemble(
            listOf(line("PROG", "START", "0"), line("L", "JS", "[L]"), line("", "END")),
            AddressingMode.RELATIVE,
            table,
        )
        assertEquals("T  000000  03   22FFFD", near.records[1]) // 0 − 3 = −3 на 2 байта

        val far = Pass2Engine.run(
            Pass1Engine.run(
                listOf(line("PROG", "START", "0"), line("", "JS", "[B]"), line("", "RESB", "40000"), line("B", "WORD", "1"), line("", "END")),
                table,
                AddressingMode.RELATIVE,
            ),
        )
        assertSingleError(far.errors, "не помещается в 2 байта")
    }
}
