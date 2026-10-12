package org.slavacom.zasm.model

/** Тип адресации в младших 2 битах байта МКОП (`код*4 + тип`). */
const val DIRECT_ADDRESSING = 1
const val RELATIVE_ADDRESSING = 2

/**
 * Режим ассемблирования программы в перемещаемом формате (Этап 2): какие типы
 * адресации разрешены; операнд запрещённого типа — ошибка.
 */
enum class AddressingMode(val title: String, val allowsDirect: Boolean, val allowsRelative: Boolean) {
    DIRECT("Только прямая адресация", allowsDirect = true, allowsRelative = false),
    RELATIVE("Только относительная адресация", allowsDirect = false, allowsRelative = true),
    MIXED("Смешанная адресация", allowsDirect = true, allowsRelative = true),
    ;

    override fun toString(): String = title
}
