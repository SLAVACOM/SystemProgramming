package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.instructionFormatOf
import org.slavacom.zasm.samples.Stage1Samples
import org.slavacom.zasm.samples.Stage3Samples

class Pass1EngineTest {

    private val opcodes = DefaultOpcodeTable.entries

    @Test
    fun `default sample produces no errors and matches reference addresses`() {
        val result = Pass1Engine.run(Stage1Samples.default.lines, opcodes, loadAddress = 0x1000)

        assertTrue(result.errors.isEmpty(), "неожиданные ошибки: ${result.errors}")
        assertEquals("Exampl", result.programName)
        assertEquals(0x2B, result.programLength)

        val symbols = result.symbolTable.associate { it.name to it.address }
        assertEquals(
            mapOf(
                "Loop" to 0x1000,
                "One" to 0x101A,
                "Two" to 0x101E,
                "Rez" to 0x1022,
                "Text" to 0x1026,
            ),
            symbols,
        )
    }

    @Test
    fun `error sample reports duplicate label and unknown mnemonic`() {
        val result = Pass1Engine.run(Stage1Samples.withErrors.lines, opcodes, loadAddress = 0x1000)

        assertEquals(2, result.errors.size, "ошибки: ${result.errors}")
        assertTrue(result.errors[0].message.contains("Loop"))
        assertTrue(result.errors[1].message.contains("MOVE"))

        // повторная метка не перезаписывает первое определение
        assertEquals(0x1000, result.symbolTable.first { it.name == "Loop" }.address)
        assertEquals(1, result.symbolTable.count { it.name == "Loop" })
    }

    @Test
    fun `default opcode table has exactly 4 instruction formats`() {
        val formats = opcodes.map { instructionFormatOf(it.mnemonic) }
        assertTrue(formats.all { it != null }, "не все мнемоники сопоставлены формату: $formats")
        assertEquals(4, formats.toSet().size, "форматов должно быть ровно 4: $formats")
    }

    @Test
    fun `register operands are already encoded to hex after the first pass`() {
        val result = Pass1Engine.run(Stage1Samples.default.lines, opcodes, loadAddress = 0x1000)

        val ld = result.auxTable.first { it.opText == "01" } // LD R1 One
        assertEquals("01", ld.operand1) // регистр R1 -> hex, уже на первом проходе
        assertEquals("One", ld.operand2) // имя ждёт второй проход

        val add = result.auxTable.first { it.opText == "05" } // ADD R1 R2
        assertEquals("01", add.operand1)
        assertEquals("02", add.operand2) // оба регистра — команда полностью готова уже после первого прохода
    }

    @Test
    fun `label matching a mnemonic or pseudo-op is rejected`() {
        val source = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("ADD", "LD", "R1", "One"), // метка "ADD" совпадает с мнемоникой
            SourceLine("One", "WORD", "1", ""),
            SourceLine("", "End", "", ""),
        )

        val result = Pass1Engine.run(source, opcodes, loadAddress = 0x1000)

        assertEquals(1, result.errors.size, "ошибки: ${result.errors}")
        assertTrue(result.errors.single().message.contains("ADD"))
        assertTrue(result.symbolTable.none { it.name == "ADD" })
    }

    @Test
    fun `reference sample marks EXTDEF names as external and collects EXTREF names`() {
        val result = Pass1Engine.run(Stage3Samples.reference.lines, opcodes, loadAddress = 0x0)

        assertTrue(result.errors.isEmpty(), "неожиданные ошибки: ${result.errors}")
        assertEquals(listOf("Str2"), result.externalRefs)

        val externalFlags = result.symbolTable.associate { it.name to it.isExternal }
        assertEquals(
            mapOf("Proc" to false, "Str1" to true, "Str3" to false, "Rez" to false, "buf" to true),
            externalFlags,
        )
    }

    @Test
    fun `withErrors sample reports EXTDEF name never defined by a label`() {
        val result = Pass1Engine.run(Stage3Samples.withErrors.lines, opcodes, loadAddress = 0x1000)

        assertEquals(1, result.errors.size, "ошибки: ${result.errors}")
        assertTrue(result.errors.single().message.contains("Ghost"))
        assertEquals(listOf("Other"), result.externalRefs)
    }

    @Test
    fun `name declared both EXTDEF and EXTREF is rejected`() {
        val source = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("", "EXTDEF", "Shared", ""),
            SourceLine("", "EXTREF", "Shared", ""),
            SourceLine("Shared", "WORD", "1", ""),
            SourceLine("", "End", "", ""),
        )

        val result = Pass1Engine.run(source, opcodes, loadAddress = 0x1000)

        assertEquals(1, result.errors.size, "ошибки: ${result.errors}")
        assertTrue(result.errors.single().message.contains("Shared"))
    }
}
