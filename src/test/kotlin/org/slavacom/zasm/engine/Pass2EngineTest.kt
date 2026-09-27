package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.samples.Stage1Samples

class Pass2EngineTest {

    private val opcodes = DefaultOpcodeTable.entries

    @Test
    fun `default sample matches the reference binary code byte for byte`() {
        val pass1 = Pass1Engine.run(Stage1Samples.default.lines, opcodes, loadAddress = 0x1000)
        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertTrue(pass2.errors.isEmpty(), "неожиданные ошибки: ${pass2.errors}")
        assertEquals("Exampl", pass2.header.name)
        assertEquals(0x2B, pass2.header.length)
        assertEquals(0x1000, pass2.header.loadAddress)

        assertEquals(
            listOf(
                "01 01 0000101A", // LD R1 One
                "01 02 0000101E", // LD R2 Two
                "05 01 02",       // ADD R1 R2
                "03 01 00001022", // SAV R1 Rez
                "06 00001000",    // JUMP Loop
                "00000001",       // One WORD 1
                "00000002",       // Two WORD 2
                // Rez WORD ? — не инициализировано, строки в дампе нет
                "48 65 6C 6C 6F", // Text BYTE Hello
            ),
            pass2.binaryLines,
        )
    }

    @Test
    fun `undefined symbol on second pass is reported as an error`() {
        val pass1 = Pass1Engine.run(
            listOf(
                org.slavacom.zasm.model.SourceLine("Exampl", "Start", "00001000", ""),
                org.slavacom.zasm.model.SourceLine("", "JUMP", "Nowhere", ""),
                org.slavacom.zasm.model.SourceLine("", "End", "", ""),
            ),
            opcodes,
            loadAddress = 0x1000,
        )
        assertTrue(pass1.errors.isEmpty())

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertEquals(1, pass2.errors.size)
        assertTrue(pass2.errors.single().message.contains("Nowhere"))
        assertTrue(pass2.binaryLines.isEmpty())
    }
}
