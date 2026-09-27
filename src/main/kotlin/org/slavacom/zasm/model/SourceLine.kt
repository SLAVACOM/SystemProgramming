package org.slavacom.zasm.model

/** Строка исходного текста — 4 столбца сетки: Метка | Операция | Операнд1 | Операнд2. */
data class SourceLine(
    val label: String,
    val op: String,
    val operand1: String,
    val operand2: String,
) {
    val isBlank: Boolean
        get() = label.isBlank() && op.isBlank() && operand1.isBlank() && operand2.isBlank()
}
