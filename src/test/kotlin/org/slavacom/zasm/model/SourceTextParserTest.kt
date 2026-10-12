package org.slavacom.zasm.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slavacom.zasm.samples.Stage1Samples

class SourceTextParserTest {

    @Test
    fun `parses labeled and unlabeled lines by leading whitespace`() {
        val text = """
            |PROG START 100
            |     JMP L1
            |A1   RESB 10
            |A2   RESW 20
            |B1   WORD 40
            |B2   BYTE X"2F4C008A"
            |B3   BYTE C"Hello!"
            |B4   BYTE 12
            |L1   LOADR1 B1
            |     LOADR2 B4
            |     ADD R1 R2
            |     SAVER1 B1
            |     INT 200
            |     END 100
        """.trimMargin()

        assertEquals(Stage1Samples.default.lines, parseSourceText(text))
    }

    @Test
    fun `quoted operand preserves internal spaces`() {
        val parsed = parseSourceText("Text BYTE \"Hello world\"")

        assertEquals(SourceLine("Text", "BYTE", "Hello world", ""), parsed.single())
    }

    @Test
    fun `C-string with spaces is one token`() {
        val parsed = parseSourceText("Text BYTE C\"Hello world\"")

        assertEquals(SourceLine("Text", "BYTE", "C\"Hello world\"", ""), parsed.single())
        assertEquals(parsed, parseSourceText(formatSourceText(parsed)))
    }

    @Test
    fun `format then parse round-trips the default sample`() {
        val text = formatSourceText(Stage1Samples.default.lines)

        assertEquals(Stage1Samples.default.lines, parseSourceText(text))
    }

    @Test
    fun `blank lines in pasted text are ignored`() {
        val text = """
            |Exampl Start 00001000
            |
            |      End
        """.trimMargin()

        assertEquals(
            listOf(
                SourceLine("Exampl", "Start", "00001000", ""),
                SourceLine("", "End", "", ""),
            ),
            parseSourceText(text),
        )
    }
}
