package org.slavacom.zasm.model

/**
 * Режим ассемблирования программы в перемещаемом формате (Этап 2): разрешён
 * только один тип адресации, операнд другого типа — ошибка.
 * [typeBits] — тип адресации в младших 2 битах байта МКОП (`код*4 + тип`).
 */
enum class AddressingMode(val title: String, val typeBits: Int) {
    DIRECT("Только прямая адресация", 1),
    RELATIVE("Только относительная адресация", 2),
    ;

    override fun toString(): String = title
}
