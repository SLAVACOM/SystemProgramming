package org.slavacom.zasm.model

/**
 * Строка вспомогательной таблицы — результат первого прохода.
 * [opText] — 2-значный 16-ричный код операции с типом адресации в младших
 * 2 битах (`код*4 + тип`) для команд, либо имя директивы (START/WORD/BYTE/RESB/RESW).
 * [sourceLine] — номер строки исходного текста (1-based), для сообщений об ошибках.
 */
data class AuxTableRow(
    val address: Int,
    val opText: String,
    val operand1: String,
    val operand2: String,
    val sourceLine: Int,
)
