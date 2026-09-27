package org.slavacom.zasm.samples

import org.slavacom.zasm.model.SourceLine

/**
 * Примеры Этапа 2 (перемещаемый формат) — три варианта адресации, требуемые
 * методичкой: только прямая, только относительная, смешанный случай.
 */
object Stage2Samples {

    val onlyDirect = AssemblerSample(
        title = "Этап 2: только прямая адресация",
        loadAddress = "00001000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("Loop", "LD", "R1", "One"),
            SourceLine("", "SAV", "R1", "Two"),
            SourceLine("", "CALL", "Helper", ""),
            SourceLine("", "JUMP", "Loop", ""),
            SourceLine("Helper", "SAV", "R1", "Two"),
            SourceLine("", "JUMP", "Loop", ""),
            SourceLine("One", "WORD", "5", ""),
            SourceLine("Two", "WORD", "?", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val onlyRelative = AssemblerSample(
        title = "Этап 2: только относительная адресация",
        loadAddress = "00001000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("", "LDN", "R1", "One"),
            SourceLine("", "ADD", "R1", "R1"),
            SourceLine("", "SAVN", "R1", "One"),
            SourceLine("", "LDN", "R2", "One"),
            SourceLine("", "SUB", "R2", "R1"),
            SourceLine("One", "WORD", "10", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val mixed = AssemblerSample(
        title = "Этап 2: смешанная адресация",
        loadAddress = "00001000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("Loop", "LD", "R1", "One"),
            SourceLine("", "LDN", "R2", "Two"),
            SourceLine("", "ADD", "R1", "R2"),
            SourceLine("", "SAVN", "R1", "Two"),
            SourceLine("", "JUMP", "Loop", ""),
            SourceLine("One", "WORD", "5", ""),
            SourceLine("Two", "WORD", "?", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val all: List<AssemblerSample> = listOf(onlyDirect, onlyRelative, mixed)
}
