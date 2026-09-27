package org.slavacom.zasm.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slavacom.zasm.samples.Stage1Samples

class SourceTextParserTest {

    @Test
    fun `parses labeled and unlabeled lines by leading whitespace`() {
        val text = """
            |Exampl Start 00001000
            |Loop  LD  R1 One
            |      LD  R2 Two
            |      ADD R1 R2
            |      SAV R1 Rez
            |      JUMP Loop
            |One   WORD 1
            |Two   WORD 2
            |Rez   WORD ?
            |Text  BYTE Hello
            |      End
        """.trimMargin()

        assertEquals(Stage1Samples.default.lines, parseSourceText(text))
    }

    @Test
    fun `quoted operand preserves internal spaces`() {
        val parsed = parseSourceText("Text BYTE \"Hello world\"")

        assertEquals(SourceLine("Text", "BYTE", "Hello world", ""), parsed.single())
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
