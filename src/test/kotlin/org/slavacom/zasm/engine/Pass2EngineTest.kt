package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.samples.Stage1Samples

class Pass2EngineTest {

    private val opcodes = DefaultOpcodeTable.entries

    private fun assemble(lines: List<SourceLine>, table: List<OpcodeEntry> = opcodes): Pass2Result {
        val pass1 = Pass1Engine.run(lines, table)
        assertTrue(pass1.isSuccess, "ошибки первого прохода: ${pass1.errors}")
        return Pass2Engine.run(pass1)
    }

    @Test
    fun `default sample produces the H-T-E object module of the C# version`() {
        val result = assemble(Stage1Samples.default.lines)

        assertTrue(result.errors.isEmpty(), "неожиданные ошибки: ${result.errors}")
        assertEquals(
            listOf(
                "H  PROG  000100  000068",
                "T  000100  04   05000158",     // JMP L1
                "T  000104  0A",                // A1 RESB 10
                "T  00010E  3C",                // A2 RESW 20
                "T  00014A  03   000028",       // B1 WORD 40
                "T  00014D  04   2F4C008A",     // B2 BYTE X"2F4C008A"
                "T  000151  06   48656C6C6F21", // B3 BYTE C"Hello!"
                "T  000157  01   0C",           // B4 BYTE 12
                "T  000158  04   0900014A",     // LOADR1 B1
                "T  00015C  04   0D000157",     // LOADR2 B4
                "T  000160  02   1012",         // ADD R1 R2
                "T  000162  04   1500014A",     // SAVER1 B1
                "T  000166  02   18C8",         // INT 200
                "E  000100",
            ),
            result.records,
        )
    }

    @Test
    fun `undefined label is a second pass error and clears the output`() {
        val result = assemble(Stage1Samples.pass2Error.lines)

        assertEquals(1, result.errors.size)
        assertEquals(3, result.errors.single().line)
        assertTrue(result.errors.single().message.contains("NOWHERE"))
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun `direct addressing requires a label operand`() {
        val result = assemble(
            listOf(SourceLine("PROG", "START", "100", ""), SourceLine("", "JMP", "R1", ""), SourceLine("", "END", "", "")),
        )
        assertTrue(result.errors.single().message.contains("меткой"), "ошибки: ${result.errors}")
    }

    @Test
    fun `uninitialized WORD and BYTE reserve memory without code`() {
        val result = assemble(
            listOf(
                SourceLine("PROG", "START", "100", ""),
                SourceLine("A", "WORD", "?", ""),
                SourceLine("B", "BYTE", "?", ""),
                SourceLine("", "END", "", ""),
            ),
        )
        assertEquals(listOf("H  PROG  000100  000004", "T  000100  03", "T  000103  01", "E  000100"), result.records)
    }

    @Test
    fun `length 1 and length 3 commands from an edited opcode table`() {
        val table = opcodes + OpcodeEntry("NOP", 0x07, 1) + OpcodeEntry("JS", 0x08, 3)
        val result = assemble(
            listOf(
                SourceLine("PROG", "START", "100", ""),
                SourceLine("L", "NOP", "", ""),
                SourceLine("", "JS", "L", ""),
                SourceLine("", "END", "", ""),
            ),
            table,
        )
        assertEquals(listOf("H  PROG  000100  000004", "T  000100  01   1C", "T  000101  03   210100", "E  000100"), result.records)
    }
}
