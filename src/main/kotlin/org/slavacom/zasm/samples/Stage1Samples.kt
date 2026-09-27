package org.slavacom.zasm.samples

import org.slavacom.zasm.model.SourceLine

/** Пример исходного текста для ComboBox «Выбор примера». */
data class AssemblerSample(
    val title: String,
    val loadAddress: String,
    val lines: List<SourceLine>,
) {
    override fun toString(): String = title
}

/**
 * Примеры Этапа 1 (абсолютный формат). [default] воспроизводит числа из
 * эталонного скриншота методички (docs/labs/img_4.jpg) — совпадение адресов
 * и байт-кода служит регрессионным подтверждением корректности движка.
 */
object Stage1Samples {

    val default = AssemblerSample(
        title = "по умолчанию (без ошибок)",
        loadAddress = "00001000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("Loop", "LD", "R1", "One"),
            SourceLine("", "LD", "R2", "Two"),
            SourceLine("", "ADD", "R1", "R2"),
            SourceLine("", "SAV", "R1", "Rez"),
            SourceLine("", "JUMP", "Loop", ""),
            SourceLine("One", "WORD", "1", ""),
            SourceLine("Two", "WORD", "2", ""),
            SourceLine("Rez", "WORD", "?", ""),
            SourceLine("Text", "BYTE", "Hello", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val withErrors = AssemblerSample(
        title = "с ошибками (дубликат метки, неизвестная операция)",
        loadAddress = "00001000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("Loop", "LD", "R1", "One"),
            SourceLine("Loop", "LD", "R2", "Two"),
            SourceLine("", "MOVE", "R1", "R2"),
            SourceLine("", "SAV", "R1", "Rez"),
            SourceLine("", "JUMP", "Loop", ""),
            SourceLine("One", "WORD", "1", ""),
            SourceLine("Rez", "WORD", "?", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val all: List<AssemblerSample> = listOf(default, withErrors)
}
