package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.ExternalNameEntry
import org.slavacom.zasm.model.RelocationEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.samples.Stage1Samples
import org.slavacom.zasm.samples.Stage2Samples
import org.slavacom.zasm.samples.Stage3Samples

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
        assertEquals(
            listOf(RelocationEntry(0x1000), RelocationEntry(0x1006), RelocationEntry(0x100F), RelocationEntry(0x1015)),
            pass2.relocationTable,
        )
        assertTrue(pass2.externalNames.isEmpty())
    }

    @Test
    fun `undefined symbol on second pass is reported as an error`() {
        val pass1 = Pass1Engine.run(
            listOf(
                SourceLine("Exampl", "Start", "00001000", ""),
                SourceLine("", "JUMP", "Nowhere", ""),
                SourceLine("", "End", "", ""),
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
                SourceLine("Exampl", "Start", "00001000", ""),
                SourceLine("", "SUB", "R1", "R2"),
                SourceLine("", "End", "", ""),
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
            listOf(0x1000, 0x1006, 0x100C, 0x1011, 0x1016, 0x101C).map { RelocationEntry(it) },
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
        assertEquals(listOf(RelocationEntry(0x1000), RelocationEntry(0x1011)), pass2.relocationTable)
    }

    @Test
    fun `reference sample resolves external reference and exports external names`() {
        val pass1 = Pass1Engine.run(Stage3Samples.reference.lines, opcodes, loadAddress = 0x0)
        assertTrue(pass1.errors.isEmpty(), "неожиданные ошибки: ${pass1.errors}")

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertTrue(pass2.errors.isEmpty(), "неожиданные ошибки: ${pass2.errors}")
        assertEquals("Exampl", pass2.header.name)
        assertEquals(0x2A, pass2.header.length)

        assertEquals(
            listOf(
                "01 01 0000001C", // LD R1 str3 -> Str3 (локально)
                "01 02 00000000", // LD R2 str2 -> Str2 (внешняя ссылка, адрес неизвестен -> 0)
                "05 01 02",       // ADD R1 R2
                "04 01 000D",     // SAVN R1 Rez (относительная, всегда локально)
                "06 00000000",    // JUMP Proc -> Proc (локально, адрес 0)
                "00000003",       // Str1 WORD 3
                "00000001",       // Str3 WORD 1
                // Rez WORD ? — не инициализировано
                "48 65 6C 6C 6F 21", // buf BYTE Hello!
            ),
            pass2.binaryLines,
        )

        // LD (локально) и JUMP (локально) — обычная настройка; LD R2 str2 — внешняя ссылка Str2
        assertEquals(
            listOf(RelocationEntry(0x0), RelocationEntry(0x6, "Str2"), RelocationEntry(0x13)),
            pass2.relocationTable,
        )

        // Str1 и buf экспортированы через EXTDEF
        assertEquals(
            listOf(ExternalNameEntry(0x18, "Str1"), ExternalNameEntry(0x24, "buf")),
            pass2.externalNames,
        )
    }

    @Test
    fun `external name cannot be used with relative addressing`() {
        val pass1 = Pass1Engine.run(
            listOf(
                SourceLine("Exampl", "Start", "00001000", ""),
                SourceLine("", "EXTREF", "Other", ""),
                SourceLine("", "SAVN", "R1", "Other"),
                SourceLine("", "End", "", ""),
            ),
            opcodes,
            loadAddress = 0x1000,
        )
        assertTrue(pass1.errors.isEmpty())

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertEquals(1, pass2.errors.size, "ошибки: ${pass2.errors}")
        assertTrue(pass2.errors.single().message.contains("Other"))
        assertTrue(pass2.binaryLines.isEmpty())
    }

    @Test
    fun `withErrors sample reports the undefined operand on the second pass`() {
        // EXTDEF-без-метки — ошибка первого прохода; движок можно вызвать напрямую,
        // минуя блокировку кнопки "Второй проход" в UI, чтобы проверить оба слоя.
        val pass1 = Pass1Engine.run(Stage3Samples.withErrors.lines, opcodes, loadAddress = 0x1000)
        assertEquals(1, pass1.errors.size, "ошибки: ${pass1.errors}")
        assertTrue(pass1.errors.single().message.contains("Ghost"))

        val pass2 = Pass2Engine.run(pass1, opcodes)

        assertEquals(1, pass2.errors.size, "ошибки: ${pass2.errors}")
        assertTrue(pass2.errors.single().message.contains("Missing"))
    }
}
