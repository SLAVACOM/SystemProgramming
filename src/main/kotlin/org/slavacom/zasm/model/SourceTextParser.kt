package org.slavacom.zasm.model

private val TOKEN_REGEX = Regex("\"[^\"]*\"|\\S+")

/**
 * Разбирает исходный текст ассемблера построчно в [SourceLine] — удобная
 * альтернатива ручному вводу по одной ячейке. Формат строки — классический:
 * `[Метка] Операция [Операнд1] [Операнд2]`, разделители — пробелы/табы.
 *
 * Метка распознаётся, только если строка начинается БЕЗ пробела (как в
 * исходниках методички: `Loop  LD  R1  One` — с меткой, `      LD  R2  Two` —
 * без). Операнд со пробелами внутри (например, строка для `BYTE`) можно
 * взять в кавычки: `Text BYTE "Hello world"`.
 */
fun parseSourceText(text: String): List<SourceLine> =
    text.lineSequence()
        .filter { it.isNotBlank() }
        .map(::parseSourceLine)
        .toList()

/** Обратное преобразование — для предзаполнения диалога вставки текущим содержимым сетки. */
fun formatSourceText(lines: List<SourceLine>): String =
    lines.joinToString("\n") { line ->
        val body = listOf(line.op, quoteIfNeeded(line.operand1), quoteIfNeeded(line.operand2))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (line.label.isNotBlank()) "${line.label} $body" else " $body"
    }

private fun parseSourceLine(line: String): SourceLine {
    val hasLabel = line.isNotEmpty() && !line[0].isWhitespace()
    val tokens = TOKEN_REGEX.findAll(line).map { unquote(it.value) }.toList()
    return if (hasLabel) {
        SourceLine(
            label = tokens.getOrElse(0) { "" },
            op = tokens.getOrElse(1) { "" },
            operand1 = tokens.getOrElse(2) { "" },
            operand2 = tokens.getOrElse(3) { "" },
        )
    } else {
        SourceLine(
            label = "",
            op = tokens.getOrElse(0) { "" },
            operand1 = tokens.getOrElse(1) { "" },
            operand2 = tokens.getOrElse(2) { "" },
        )
    }
}

private fun unquote(token: String): String =
    if (token.length >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
        token.substring(1, token.length - 1)
    } else {
        token
    }

private fun quoteIfNeeded(value: String): String =
    if (value.any { it.isWhitespace() }) "\"$value\"" else value
