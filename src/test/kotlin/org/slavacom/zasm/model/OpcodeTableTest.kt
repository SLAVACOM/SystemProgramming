package org.slavacom.zasm.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpcodeTableTest {

    private fun errorOf(vararg rows: List<String>): String? = validateOpcodeTable(rows.toList()).error?.message

    @Test
    fun `default table is valid and blank rows are skipped`() {
        val rows = DefaultOpcodeTable.entries.map { listOf(it.mnemonic, toHex2(it.code), it.length.toString()) } +
            listOf(listOf("", "", ""))
        val check = validateOpcodeTable(rows)

        assertNull(check.error)
        assertEquals(DefaultOpcodeTable.entries, check.entries)
    }

    @Test
    fun `invalid rows are reported`() {
        assertTrue(errorOf(listOf("ADD", "", "2"))!!.contains("пустая"))
        assertTrue(errorOf(listOf("ADD", "40", "2"))!!.contains("3F"))
        assertTrue(errorOf(listOf("ADD", "0G", "2"))!!.contains("МКОП"))
        assertTrue(errorOf(listOf("ADD", "04", "5"))!!.contains("от 1 до 4"))
        assertTrue(errorOf(listOf("WORD", "04", "2"))!!.contains("зарезервированным"))
        assertTrue(errorOf(listOf("ADD", "04", "2"), listOf("add", "05", "2"))!!.contains("повторяющаяся команда"))
        assertTrue(errorOf(listOf("ADD", "04", "2"), listOf("SUB", "4", "2"))!!.contains("повторяющийся код"))
    }
}
