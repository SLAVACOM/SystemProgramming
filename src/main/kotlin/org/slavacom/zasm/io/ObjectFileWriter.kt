package org.slavacom.zasm.io

import org.slavacom.zasm.engine.Pass1Result
import org.slavacom.zasm.engine.Pass2Result
import org.slavacom.zasm.model.toHex8

/**
 * Текстовый (для простоты) объектный модуль в полном перемещаемом формате:
 * заголовок, внешние имена (EXTDEF), внешние ссылки (EXTREF), таблица
 * настройки, тело.
 *
 * Формат:
 * ```
 * H <имя> <длина> <адрес загрузки>
 * D <адрес> <имя>       — по строке на запись «Внешние имена»
 * R <имя>               — по строке на запись «Внешние ссылки»
 * M <адрес> [<имя>]     — таблица настройки; имя — только для внешних ссылок
 * T <hex-байты>         — тело, как в «Двоичном коде», по строке на запись
 * E
 * ```
 */
object ObjectFileWriter {

    fun format(pass1: Pass1Result, pass2: Pass2Result): String = buildString {
        appendLine("H ${pass2.header.name} ${toHex8(pass2.header.length)} ${toHex8(pass2.header.loadAddress)}")
        pass2.externalNames.forEach { appendLine("D ${toHex8(it.address)} ${it.name}") }
        pass1.externalRefs.forEach { appendLine("R $it") }
        pass2.relocationTable.forEach { entry ->
            val suffix = entry.externalName?.let { " $it" }.orEmpty()
            appendLine("M ${toHex8(entry.address)}$suffix")
        }
        pass2.binaryLines.forEach { appendLine("T $it") }
        append("E")
    }
}
