package org.slavacom.zasm.model

/** Строка «Внешние имена» — локальное имя, экспортируемое этим модулем через EXTDEF, с его адресом. */
data class ExternalNameEntry(val address: Int, val name: String)
