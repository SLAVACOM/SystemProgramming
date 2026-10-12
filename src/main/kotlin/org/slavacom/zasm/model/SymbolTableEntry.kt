package org.slavacom.zasm.model

/** Строка Таблицы Символических Имён (ТСИ). */
data class SymbolTableEntry(
    val name: String,
    val address: Int,
)
