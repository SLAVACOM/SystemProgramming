package org.slavacom.zasm.ui

import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.RadioButton
import javafx.scene.control.TextArea
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.RowConstraints
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.Stage
import org.slavacom.zasm.engine.AssemblerEngine
import org.slavacom.zasm.engine.Pass1Engine
import org.slavacom.zasm.model.AddressingMode
import org.slavacom.zasm.model.AssemblerError
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.formatSourceText
import org.slavacom.zasm.model.parseSourceText
import org.slavacom.zasm.model.toHex2
import org.slavacom.zasm.model.toHex6
import org.slavacom.zasm.model.validateOpcodeTable
import org.slavacom.zasm.samples.AssemblerSample
import org.slavacom.zasm.samples.Stage2Samples

private const val MESSAGE_LIST_HEIGHT = 110.0
private const val RELOCATION_LIST_HEIGHT = 140.0

/**
 * Этап 2: двухпросмотровый ассемблер для программы в перемещаемом формате.
 * Три колонки (исходные данные | результаты первого прохода | результаты
 * второго прохода) и кнопки проходов внизу. Программа ассемблируется в одном
 * из режимов «только прямая» / «только относительная» / «смешанная» адресация. Любая правка
 * исходного текста или ТКО и смена режима сбрасывают результаты — второй
 * проход возможен только после успешного первого по актуальным данным.
 */
class MainApp : Application() {

    private val engine = AssemblerEngine()

    private lateinit var firstPassButton: Button
    private lateinit var secondPassButton: Button
    private lateinit var exampleBox: ComboBox<AssemblerSample>
    private val modeGroup = ToggleGroup()

    private lateinit var sourceTextArea: TextArea
    private lateinit var opcodeGrid: StringGrid

    private lateinit var auxGrid: StringGrid
    private lateinit var symbolGrid: StringGrid
    private lateinit var errors1List: ListView<String>

    private lateinit var relocationList: ListView<String>
    private lateinit var binaryCodeList: ListView<String>
    private lateinit var errors2List: ListView<String>

    override fun start(stage: Stage) {
        val root = BorderPane().apply {
            top = buildToolbar()
            center = buildColumns()
            bottom = buildPassButtons()
        }

        loadSample(Stage2Samples.direct)

        val scene = Scene(root, 1400.0, 900.0)
        scene.stylesheets.add(javaClass.getResource("/zasm.css")!!.toExternalForm())

        stage.title = "Zasm 2.0 — Двухпросмотровый ассемблер для программы в перемещаемом формате"
        stage.scene = scene
        stage.show()
    }

    // ------------------------------------------------------------- layout

    private fun buildToolbar(): HBox {
        val modeButtons = AddressingMode.entries.map { mode ->
            RadioButton(mode.title).apply {
                toggleGroup = modeGroup
                userData = mode
            }
        }
        modeGroup.selectToggle(modeButtons.first())
        modeGroup.selectedToggleProperty().addListener { _, _, _ -> invalidatePasses() }

        exampleBox = ComboBox<AssemblerSample>().apply {
            items.addAll(Stage2Samples.all)
            selectionModel.selectFirst()
            setOnAction { selectionModel.selectedItem?.let { loadSample(it) } }
        }
        return HBox(
            10.0,
            Label("Режим:"),
            *modeButtons.toTypedArray(),
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            Label("Выбор примера:"),
            exampleBox,
        ).apply {
            padding = Insets(8.0, 10.0, 0.0, 10.0)
            alignment = Pos.CENTER_LEFT
        }
    }

    /** Колонки в пропорции 1.1 : 1.4 : 1. */
    private fun buildColumns(): GridPane = GridPane().apply {
        hgap = 8.0
        padding = Insets(10.0)
        listOf(1.1, 1.4, 1.0).forEach { weight ->
            columnConstraints += ColumnConstraints().apply {
                percentWidth = weight / 3.5 * 100
                hgrow = Priority.ALWAYS
            }
        }
        rowConstraints += RowConstraints().apply { vgrow = Priority.ALWAYS }
        addRow(0, buildSourceColumn(), buildPass1Column(), buildPass2Column())
    }

    private fun buildPassButtons(): HBox {
        firstPassButton = Button("Первый проход").apply {
            styleClass += "pass-button"
            setOnAction { onFirstPass() }
        }
        secondPassButton = Button("Второй проход").apply {
            styleClass += "pass-button"
            isDisable = true
            setOnAction { onSecondPass() }
        }
        return HBox(20.0, firstPassButton, secondPassButton).apply {
            alignment = Pos.CENTER
            padding = Insets(2.0, 10.0, 12.0, 10.0)
        }
    }

    /**
     * Список сообщений об ошибках: фиксированная высота (растущие таблицы над
     * ним не сжимают его до одной строки) и перенос длинных сообщений.
     */
    private fun messageList() = ListView<String>().apply {
        minHeight = MESSAGE_LIST_HEIGHT
        prefHeight = MESSAGE_LIST_HEIGHT
        setCellFactory { list ->
            object : ListCell<String>() {
                init {
                    isWrapText = true
                    prefWidthProperty().bind(list.widthProperty().subtract(20.0))
                }

                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty) null else item
                }
            }
        }
    }

    private fun title(text: String) = Label(text).apply { styleClass += "section-title" }

    private fun column(vararg children: Node) = VBox(4.0, *children).apply { maxWidth = Double.MAX_VALUE }

    private fun buildSourceColumn(): VBox {
        sourceTextArea = TextArea().apply {
            font = Font.font("Monospaced", 13.0)
            isWrapText = false
            textProperty().addListener { _, _, _ -> invalidatePasses() }
        }
        VBox.setVgrow(sourceTextArea, Priority.ALWAYS)

        val sourceHeader = HBox(
            8.0,
            title("Исходный текст программы"),
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            Button("Проверить синтаксис").apply { setOnAction { onCheckSyntax() } },
            Button("Отформатировать").apply { setOnAction { onFormatSource() } },
        ).apply { alignment = Pos.CENTER_LEFT }

        opcodeGrid = StringGrid(listOf("Команда", "МКОП (hex)", "Длина"), rowCount = 0, editable = true, growable = true).apply {
            onCellEdited = ::invalidatePasses
            prefHeight = 220.0
            minHeight = 160.0
        }
        opcodeGrid.loadRows(DefaultOpcodeTable.entries.map { listOf(it.mnemonic, toHex2(it.code), it.length.toString()) })

        val opcodeButtons = HBox(
            6.0,
            Button("Удалить").apply {
                tooltip = Tooltip(
                    "Удаляет выделенные строки (Shift/Cmd+клик — несколько).\n" +
                        "Если ничего не выделено — очищает всю таблицу (с подтверждением).",
                )
                setOnAction { onDeleteOpcodeRows() }
            },
        )

        return column(sourceHeader, sourceTextArea, title("Таблица кодов операций"), opcodeGrid, opcodeButtons)
    }

    private fun buildPass1Column(): VBox {
        auxGrid = StringGrid(listOf("Адрес", "МКОП", "Операнд 1", "Операнд 2"), rowCount = 0)
        symbolGrid = StringGrid(listOf("Метка", "Адрес"), rowCount = 0)
        errors1List = messageList()

        VBox.setVgrow(auxGrid, Priority.ALWAYS)
        VBox.setVgrow(symbolGrid, Priority.ALWAYS)

        return column(
            title("Вспомогательная таблица"), auxGrid,
            title("Таблица символических имён"), symbolGrid,
            title("Ошибки первого прохода"), errors1List,
        )
    }

    private fun buildPass2Column(): VBox {
        relocationList = ListView<String>().apply {
            styleClass += "monospace-list"
            minHeight = RELOCATION_LIST_HEIGHT
            prefHeight = RELOCATION_LIST_HEIGHT
        }
        binaryCodeList = ListView<String>().apply { styleClass += "monospace-list" }
        errors2List = messageList()

        VBox.setVgrow(binaryCodeList, Priority.ALWAYS)

        return column(
            title("Таблица настройки"), relocationList,
            title("Двоичный код"), binaryCodeList,
            title("Ошибки второго прохода"), errors2List,
        )
    }

    // ----------------------------------------------------------- handlers

    private val selectedMode: AddressingMode
        get() = modeGroup.selectedToggle.userData as AddressingMode

    private fun loadSample(sample: AssemblerSample) {
        sample.mode?.let { mode -> modeGroup.selectToggle(modeGroup.toggles.first { it.userData == mode }) }
        sourceTextArea.text = formatSourceText(sample.lines)
        invalidatePasses()
    }

    private fun onFormatSource() {
        sourceTextArea.text = formatSourceText(parseSourceText(sourceTextArea.text))
    }

    /** Результаты проходов больше не соответствуют исходным данным. */
    private fun invalidatePasses() {
        engine.reset()
        auxGrid.clearDataRows(0)
        symbolGrid.clearDataRows(0)
        errors1List.items.clear()
        relocationList.items.clear()
        binaryCodeList.items.clear()
        errors2List.items.clear()
        secondPassButton.isDisable = true
    }

    private fun onDeleteOpcodeRows() {
        when {
            opcodeGrid.hasSelectedRows -> opcodeGrid.deleteSelectedRows()
            opcodeGrid.hasData && confirm("Строки не выбраны. Удалить ВСЕ команды из таблицы кодов операций?") ->
                opcodeGrid.deleteAllRows()
            else -> return
        }
        invalidatePasses()
    }

    private fun confirm(question: String): Boolean =
        Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO).apply {
            title = "Подтверждение"
            headerText = null
            initOwner(opcodeGrid.scene.window)
        }.showAndWait().filter { it == ButtonType.YES }.isPresent

    private fun readSource(): List<SourceLine> = parseSourceText(sourceTextArea.text)

    /** ТКО из сетки; при ошибке проверки — сообщение в списке ошибок первого прохода и null. */
    private fun readOpcodes(): List<OpcodeEntry>? {
        val check = validateOpcodeTable(opcodeGrid.rowsAsText())
        check.error?.let {
            errors1List.items.setAll("Таблица кодов операций, строка ${it.line}: ${it.message}")
            return null
        }
        return check.entries
    }

    private fun onCheckSyntax() {
        invalidatePasses()
        val opcodes = readOpcodes() ?: return
        val result = Pass1Engine.run(readSource(), opcodes, selectedMode)
        errors1List.items.setAll(
            if (result.isSuccess) listOf("Ошибок не найдено.") + messages(result.warnings) else messages(result.errors),
        )
    }

    private fun onFirstPass() {
        invalidatePasses()
        val opcodes = readOpcodes() ?: return

        val result = engine.runPass1(readSource(), opcodes, selectedMode)

        auxGrid.loadRows(result.auxTable.map { row ->
            val address = if (row.opText == "START") result.programName else toHex6(row.address)
            listOf(address, row.opText, row.operand1, row.operand2)
        })
        symbolGrid.loadRows(result.symbolTable.map { listOf(it.name, toHex6(it.address)) })
        errors1List.items.setAll(messages(result.errors + result.warnings))

        secondPassButton.isDisable = !result.isSuccess
    }

    private fun onSecondPass() {
        if (!engine.hasPass1Result) return
        val result = engine.runPass2()
        relocationList.items.setAll(result.relocations.map(::toHex6))
        binaryCodeList.items.setAll(result.records)
        errors2List.items.setAll(messages(result.errors))
    }

    private fun messages(errors: List<AssemblerError>): List<String> = errors.map { it.toString() }
}

fun main() {
    Application.launch(MainApp::class.java)
}
