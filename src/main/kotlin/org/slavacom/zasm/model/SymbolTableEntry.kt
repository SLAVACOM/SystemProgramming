package org.slavacom.zasm.model

/** Строка Таблицы Символических Имён (ТСИ). Признак внешнего имени — задел под Этап 3. */
data class SymbolTableEntry(
    val name: String,
    val address: Int,
    val isExternal: Boolean = false,
)
