package org.slavacom.zasm.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.samples.Stage1Samples

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
}
