package org.slavacom.zasm.samples

import org.slavacom.zasm.model.SourceLine

/** Пример исходного текста для ComboBox «Выбор примера». */
data class AssemblerSample(
    val title: String,
    val lines: List<SourceLine>,
) {
    override fun toString(): String = title
}

/** Примеры Этапа 1 (абсолютный формат). [default] — демонстрационная программа C#-версии лабораторной. */
object Stage1Samples {

    val default = AssemblerSample(
        title = "по умолчанию (без ошибок)",
        lines = listOf(
            SourceLine("PROG", "START", "100", ""),
            SourceLine("", "JMP", "L1", ""),
            SourceLine("A1", "RESB", "10", ""),
            SourceLine("A2", "RESW", "20", ""),
            SourceLine("B1", "WORD", "40", ""),
            SourceLine("B2", "BYTE", "X\"2F4C008A\"", ""),
            SourceLine("B3", "BYTE", "C\"Hello!\"", ""),
            SourceLine("B4", "BYTE", "12", ""),
            SourceLine("L1", "LOADR1", "B1", ""),
            SourceLine("", "LOADR2", "B4", ""),
            SourceLine("", "ADD", "R1", "R2"),
            SourceLine("", "SAVER1", "B1", ""),
            SourceLine("", "INT", "200", ""),
            SourceLine("", "END", "100", ""),
        ),
    )

    val pass1Error = AssemblerSample(
        title = "ошибка первого прохода (повторная метка)",
        lines = listOf(
            SourceLine("PROG", "START", "100", ""),
            SourceLine("L1", "LOADR1", "B1", ""),
            SourceLine("L1", "LOADR2", "B1", ""),
            SourceLine("B1", "WORD", "40", ""),
            SourceLine("", "END", "", ""),
        ),
    )

    val pass2Error = AssemblerSample(
        title = "ошибка второго прохода (метка не определена)",
        lines = listOf(
            SourceLine("PROG", "START", "100", ""),
            SourceLine("", "LOADR1", "B1", ""),
            SourceLine("", "JMP", "NOWHERE", ""),
            SourceLine("B1", "WORD", "40", ""),
            SourceLine("", "END", "", ""),
        ),
    )

    val all: List<AssemblerSample> = listOf(default, pass1Error, pass2Error)
}
