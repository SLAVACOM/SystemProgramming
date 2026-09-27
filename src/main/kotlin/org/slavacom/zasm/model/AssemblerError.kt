package org.slavacom.zasm.model

/** Сообщение об ошибке ассемблирования. [line] — номер строки исходного текста (0 — общая ошибка). */
data class AssemblerError(val line: Int, val message: String) {
    override fun toString(): String = if (line > 0) "Строка $line: $message" else message
}
