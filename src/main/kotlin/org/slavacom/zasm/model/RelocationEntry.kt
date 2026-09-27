package org.slavacom.zasm.model

/**
 * Строка таблицы настройки: адрес команды с прямой адресацией символьного
 * операнда. [externalName] == null — обычная внутренняя настройка (адрес
 * определён в этом же модуле, Этап 2); иначе — операнд ссылается на внешнее
 * имя (EXTREF, Этап 3), и адрес должен подставить компоновщик.
 */
data class RelocationEntry(val address: Int, val externalName: String? = null)
