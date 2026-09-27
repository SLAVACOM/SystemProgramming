package org.slavacom.zasm.ui

import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.TextFieldTableCell

/** Один ряд сетки — аналог строки Delphi `TStringGrid.Cells[*, Row]`. */
class GridRow(columnCount: Int) {
    val cells: List<SimpleStringProperty> = List(columnCount) { SimpleStringProperty("") }

    operator fun get(col: Int): String = cells[col].get()
    operator fun set(col: Int, value: String) {
        cells[col].set(value)
    }
}

/**
 * Аналог Delphi `TStringGrid`: фиксированное число столбцов и строк,
 * опционально редактируемая (соответствует рекомендации методички
 * `Options/goEditing = true` только для входных таблиц).
 */
class StringGrid(
    columnTitles: List<String>,
    rowCount: Int,
    editable: Boolean = false,
) : TableView<GridRow>() {

    init {
        isEditable = editable
        columnResizePolicy = CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        items = FXCollections.observableArrayList(List(rowCount) { GridRow(columnTitles.size) })

        columnTitles.forEachIndexed { index, title ->
            val column = TableColumn<GridRow, String>(title)
            column.isEditable = editable
            column.setCellValueFactory { data -> data.value.cells[index] }
            if (editable) {
                column.cellFactory = TextFieldTableCell.forTableColumn()
                column.setOnEditCommit { event -> event.rowValue[index] = event.newValue }
            }
            columns.add(column)
        }
        styleClass.add("zasm-grid")
    }

    fun cellText(row: Int, col: Int): String = items[row][col]

    fun setCellText(row: Int, col: Int, value: String) {
        items[row][col] = value
    }

    /** Текущее содержимое сетки построчно (для передачи в движок ассемблера). */
    fun rowsAsText(): List<List<String>> = items.map { row -> row.cells.map { it.get() } }

    /**
     * Полностью заменяет данные сетки. [minRowCount] задаёт минимальное число строк
     * (лишние — пустые, оставлены для дальнейшего ручного редактирования).
     */
    fun loadRows(rows: List<List<String>>, minRowCount: Int = rows.size) {
        val totalRows = maxOf(rows.size, minRowCount)
        val columnCount = columns.size
        val newItems = FXCollections.observableArrayList<GridRow>()
        for (r in 0 until totalRows) {
            val gridRow = GridRow(columnCount)
            if (r < rows.size) {
                rows[r].forEachIndexed { c, value -> if (c < columnCount) gridRow[c] = value }
            }
            newItems += gridRow
        }
        items = newItems
    }

    /** Очищает сетку, оставляя [rowCount] пустых строк. */
    fun clearDataRows(rowCount: Int) {
        items = FXCollections.observableArrayList(List(rowCount) { GridRow(columns.size) })
    }
}
