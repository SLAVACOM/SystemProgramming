package org.slavacom.zasm.model

/** Заголовок объектного модуля: имя программы, длина кода, адрес загрузки. */
data class ObjectHeader(
    val name: String,
    val length: Int,
    val loadAddress: Int,
)
