package org.slavacom.zasm.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slavacom.zasm.engine.Pass1Engine
import org.slavacom.zasm.engine.Pass2Engine
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.samples.Stage3Samples

class ObjectFileWriterTest {

    @Test
    fun `reference sample formats to the documented text layout`() {
        val opcodes = DefaultOpcodeTable.entries
        val pass1 = Pass1Engine.run(Stage3Samples.reference.lines, opcodes, loadAddress = 0x0)
        val pass2 = Pass2Engine.run(pass1, opcodes)

        val text = ObjectFileWriter.format(pass1, pass2)

        assertEquals(
            """
            H Exampl 0000002A 00000000
            D 00000018 Str1
            D 00000024 buf
            R Str2
            M 00000000
            M 00000006 Str2
            M 00000013
            T 01 01 0000001C
            T 01 02 00000000
            T 05 01 02
            T 04 01 000D
            T 06 00000000
            T 00000003
            T 00000001
            T 48 65 6C 6C 6F 21
            E
            """.trimIndent(),
            text,
        )
    }
}
