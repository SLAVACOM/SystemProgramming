package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.samples.Stage1Samples
import org.slavacom.zasm.samples.Stage2Samples

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

        // прямая адресация (LD, LD, SAV, JUMP) — все 4 попадают в таблицу настройки, ADD (рег+рег) — нет
        assertEquals(listOf(0x1000, 0x1006, 0x100F, 0x1015), pass2.relocationTable)
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

    @Test
    fun `SUB uses the same register-register format as ADD`() {
        val pass1 = Pass1Engine.run(
            listOf(
                org.slavacom.zasm.model.SourceLine("Exampl", "Start", "00001000", ""),
                org.slavacom.zasm.model.SourceLine("", "SUB", "R1", "R2"),
                org.slavacom.zasm.model.SourceLine("", "End", "", ""),
            ),
            opcodes,
            loadAddress = 0x1000,
        )
        assertTrue(pass1.errors.isEmpty())

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertTrue(pass2.errors.isEmpty(), "неожиданные ошибки: ${pass2.errors}")
        assertEquals(listOf("07 01 02"), pass2.binaryLines)
    }

    @Test
    fun `only-direct sample fills the relocation table for every instruction`() {
        val pass1 = Pass1Engine.run(Stage2Samples.onlyDirect.lines, opcodes, loadAddress = 0x1000)
        assertTrue(pass1.errors.isEmpty(), "неожиданные ошибки: ${pass1.errors}")

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertTrue(pass2.errors.isEmpty(), "неожиданные ошибки: ${pass2.errors}")
        // LD, SAV, CALL, JUMP, SAV, JUMP — все прямой адресации, все попадают в таблицу настройки
        assertEquals(
            listOf(0x1000, 0x1006, 0x100C, 0x1011, 0x1016, 0x101C),
            pass2.relocationTable,
        )
    }

    @Test
    fun `only-relative sample leaves the relocation table empty`() {
        val pass1 = Pass1Engine.run(Stage2Samples.onlyRelative.lines, opcodes, loadAddress = 0x1000)
        assertTrue(pass1.errors.isEmpty(), "неожиданные ошибки: ${pass1.errors}")

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertTrue(pass2.errors.isEmpty(), "неожиданные ошибки: ${pass2.errors}")
        assertTrue(pass2.relocationTable.isEmpty(), "таблица настройки должна быть пуста: ${pass2.relocationTable}")
    }

    @Test
    fun `mixed sample puts only the direct-addressing instructions in the relocation table`() {
        val pass1 = Pass1Engine.run(Stage2Samples.mixed.lines, opcodes, loadAddress = 0x1000)
        assertTrue(pass1.errors.isEmpty(), "неожиданные ошибки: ${pass1.errors}")

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertTrue(pass2.errors.isEmpty(), "неожиданные ошибки: ${pass2.errors}")
        // LD (прямая) и JUMP (прямая) — в таблице; LDN/SAVN (относительная) и ADD (рег+рег) — нет
        assertEquals(listOf(0x1000, 0x1011), pass2.relocationTable)
    }
}
