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
}
