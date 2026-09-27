package org.slavacom.zasm.ui

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.util.Callback

/** Один ряд сетки — аналог строки Delphi `TStringGrid.Cells[*, Row]`. */
class GridRow(columnCount: Int) {
    val cells: List<SimpleStringProperty> = List(columnCount) { SimpleStringProperty("") }

    operator fun get(col: Int): String = cells[col].get()
    operator fun set(col: Int, value: String) {
        cells[col].set(value)
    }
}

/**
 * Ячейка редактируемой сетки с поведением, привычным по Excel/Google Sheets —
 * то, чего не хватает стандартной `TextFieldTableCell`:
 * - изменение сохраняется не только по Enter, но и при потере фокуса (клик
 *   мимо, переключение окна) — иначе правка молча терялась;
 * - Tab/Shift+Tab переходят к следующей/предыдущей ячейке той же строки
 *   (с переносом на соседнюю строку на границе), Enter/Shift+Enter — к той
 *   же колонке в следующей/предыдущей строке — и сразу открывают её на
 *   редактирование, без повторного клика.
 */
private class NavigableTextFieldCell : TableCell<GridRow, String>() {
    private val textField = TextField().apply {
        setOnAction { commitEdit(text) }
        focusedProperty().addListener { _, wasFocused, isFocused ->
            if (wasFocused && !isFocused && this@NavigableTextFieldCell.isEditing) {
                commitEdit(text)
            }
        }
        setOnKeyPressed(::handleKeyPressed)
    }

    private fun handleKeyPressed(event: KeyEvent) {
        when (event.code) {
            KeyCode.ESCAPE -> {
                cancelEdit()
                event.consume()
            }
            KeyCode.TAB -> {
                commitEdit(textField.text)
                event.consume()
                navigate(colDelta = if (event.isShiftDown) -1 else 1, rowDelta = 0)
            }
            KeyCode.ENTER -> {
                commitEdit(textField.text)
                event.consume()
                navigate(colDelta = 0, rowDelta = if (event.isShiftDown) -1 else 1)
            }
            else -> {}
        }
    }

    private fun navigate(colDelta: Int, rowDelta: Int) {
        val table = tableView ?: return
        val columnCount = table.columns.size
        if (columnCount == 0) return
        var col = table.columns.indexOf(tableColumn) + colDelta
        var row = index + rowDelta
        if (col >= columnCount) {
            col = 0
            row += 1
        } else if (col < 0) {
            col = columnCount - 1
            row -= 1
        }
        if (row < 0 || row >= table.items.size) return
        val targetColumn = table.columns[col]
        Platform.runLater {
            table.scrollTo(row)
            table.selectionModel.select(row, targetColumn)
            table.edit(row, targetColumn)
        }
    }

    override fun startEdit() {
        if (!isEditable || !tableView.isEditable || !tableColumn.isEditable) return
        super.startEdit()
        textField.text = item ?: ""
        text = null
        graphic = textField
        textField.requestFocus()
        textField.selectAll()
    }

    override fun cancelEdit() {
        super.cancelEdit()
        text = item
        graphic = null
    }

    override fun commitEdit(newValue: String) {
        if (!isEditing) return
        super.commitEdit(newValue)
        text = newValue
        graphic = null
    }

    override fun updateItem(item: String?, empty: Boolean) {
        super.updateItem(item, empty)
        when {
            empty -> {
                text = null
                graphic = null
            }
            isEditing -> {
                textField.text = item ?: ""
                text = null
                graphic = textField
            }
            else -> {
                text = item
                graphic = null
            }
        }
    }
}

private fun navigableCellFactory(): Callback<TableColumn<GridRow, String>, TableCell<GridRow, String>> =
    Callback { NavigableTextFieldCell() }

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
                column.cellFactory = navigableCellFactory()
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
