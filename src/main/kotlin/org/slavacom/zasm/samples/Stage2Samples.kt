package org.slavacom.zasm.samples

import org.slavacom.zasm.model.AddressingMode
import org.slavacom.zasm.model.SourceLine

/** Примеры Этапа 2 (перемещаемый формат): программа начинается с адреса 0. */
object Stage2Samples {

    /** Программа примера по умолчанию Этапа 1; [operand] оформляет операнд-метку под режим. */
    private fun program(operand: (String) -> String) = listOf(
        SourceLine("PROG", "START", "0", ""),
        SourceLine("", "JMP", operand("L1"), ""),
        SourceLine("A1", "RESB", "10", ""),
        SourceLine("A2", "RESW", "20", ""),
        SourceLine("B1", "WORD", "40", ""),
        SourceLine("B2", "BYTE", "X\"2F4C008A\"", ""),
        SourceLine("B3", "BYTE", "C\"Hello!\"", ""),
        SourceLine("B4", "BYTE", "12", ""),
        SourceLine("L1", "LOADR1", operand("B1"), ""),
        SourceLine("", "LOADR2", operand("B4"), ""),
        SourceLine("", "ADD", "R1", "R2"),
        SourceLine("", "SAVER1", operand("B1"), ""),
        SourceLine("", "INT", "200", ""),
        SourceLine("", "END", "", ""),
    )

    private fun bracketed(label: String) = "[$label]"

    val direct = AssemblerSample(
        title = "по умолчанию (только прямая адресация)",
        lines = program { it },
        mode = AddressingMode.DIRECT,
    )

    val relative = AssemblerSample(
        title = "только относительная адресация",
        lines = program(::bracketed),
        mode = AddressingMode.RELATIVE,
    )

    val mixed = AssemblerSample(
        title = "смешанная адресация",
        lines = program { if (it == "L1" || it == "B4") bracketed(it) else it },
        mode = AddressingMode.MIXED,
    )

    val relativeInDirectMode = AssemblerSample(
        title = "ошибка: относительный операнд в режиме прямой адресации",
        lines = program { if (it == "B4") bracketed(it) else it },
        mode = AddressingMode.DIRECT,
    )

    val directInRelativeMode = AssemblerSample(
        title = "ошибка: прямой операнд в режиме относительной адресации",
        lines = program { if (it == "B4") it else bracketed(it) },
        mode = AddressingMode.RELATIVE,
    )

    val nonzeroStart = AssemblerSample(
        title = "ошибка: ненулевой адрес начала",
        lines = listOf(SourceLine("PROG", "START", "100", "")) + program { it }.drop(1),
        mode = AddressingMode.DIRECT,
    )

    val undefinedLabel = AssemblerSample(
        title = "ошибка второго прохода (метка не определена)",
        lines = listOf(
            SourceLine("PROG", "START", "0", ""),
            SourceLine("", "LOADR1", "[B1]", ""),
            SourceLine("", "JMP", "[NOWHERE]", ""),
            SourceLine("B1", "WORD", "40", ""),
            SourceLine("", "END", "", ""),
        ),
        mode = AddressingMode.RELATIVE,
    )

    val all: List<AssemblerSample> =
        listOf(direct, relative, mixed, relativeInDirectMode, directInRelativeMode, nonzeroStart, undefinedLabel)
}
