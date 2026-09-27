package org.slavacom.zasm.samples

import org.slavacom.zasm.model.SourceLine

/**
 * Примеры Этапа 3 (раздельное ассемблирование). [reference] воспроизводит
 * числа из эталонного скриншота методички (docs/labs/img_7.jpg) — совпадение
 * адресов, ТСИ и двоичного кода служит регрессионным подтверждением
 * корректности EXTDEF/EXTREF.
 */
object Stage3Samples {

    val reference = AssemblerSample(
        title = "Этап 3: раздельное ассемблирование (пример методички)",
        loadAddress = "00000000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00000000", ""),
            SourceLine("", "EXTDEF", "buf", "Str1"),
            SourceLine("", "EXTREF", "Str2", ""),
            SourceLine("Proc", "LD", "R1", "str3"),
            SourceLine("", "LD", "R2", "str2"),
            SourceLine("", "ADD", "R1", "R2"),
            SourceLine("", "SAVN", "R1", "Rez"),
            SourceLine("", "JUMP", "Proc", ""),
            SourceLine("Str1", "WORD", "3", ""),
            SourceLine("Str3", "WORD", "1", ""),
            SourceLine("Rez", "WORD", "?", ""),
            SourceLine("buf", "BYTE", "Hello!", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val withErrors = AssemblerSample(
        title = "Этап 3: с ошибками (EXTDEF без метки, неопределённое имя)",
        loadAddress = "00001000",
        lines = listOf(
            SourceLine("Exampl", "Start", "00001000", ""),
            SourceLine("", "EXTDEF", "Ghost", ""),
            SourceLine("", "EXTREF", "Other", ""),
            SourceLine("Loop", "LD", "R1", "Missing"),
            SourceLine("", "JUMP", "Loop", ""),
            SourceLine("", "End", "", ""),
        ),
    )

    val all: List<AssemblerSample> = listOf(reference, withErrors)
}
