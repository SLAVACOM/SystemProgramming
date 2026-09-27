package org.slavacom.zasm.ui

import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.input.Clipboard
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.slavacom.zasm.engine.AssemblerEngine
import org.slavacom.zasm.io.ObjectFileWriter
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.formatSourceText
import org.slavacom.zasm.model.parseHex
import org.slavacom.zasm.model.parseSourceText
import org.slavacom.zasm.model.toHex8
import org.slavacom.zasm.samples.AssemblerSample
import org.slavacom.zasm.samples.Stage1Samples
import org.slavacom.zasm.samples.Stage2Samples
import org.slavacom.zasm.samples.Stage3Samples

private const val PANEL_WIDTH = 380.0
private const val SOURCE_EXTRA_ROWS = 6
private const val OPCODE_EXTRA_ROWS = 4

/**
 * Этапы 1-2: ассемблер в перемещаемом формате — проход 1 и проход 2
 * подключены к движку из пакета `engine`, плюс таблица настройки для команд
 * прямой адресации.
 */
class MainApp : Application() {

    private val engine = AssemblerEngine()

    private lateinit var firstPassButton: Button
    private lateinit var secondPassButton: Button
    private lateinit var exampleBox: ComboBox<AssemblerSample>

    private lateinit var sourceGrid: StringGrid
    private lateinit var loadAddressField: TextField
    private lateinit var opcodeGrid: StringGrid

    private lateinit var auxGrid: StringGrid
    private lateinit var symbolGrid: StringGrid
    private lateinit var errors1List: ListView<String>

    private lateinit var headerGrid: StringGrid
    private lateinit var relocationGrid: StringGrid
    private lateinit var externalNamesGrid: StringGrid
    private lateinit var externalRefsList: ListView<String>
    private lateinit var binaryCodeList: ListView<String>
    private lateinit var saveButton: Button
    private lateinit var errors2List: ListView<String>

    override fun start(stage: Stage) {
        val root = BorderPane()
        root.top = buildToolbar()
        root.center = buildPanels()

        loadSample(Stage1Samples.default)

        val scene = Scene(root, 1300.0, 780.0)
        scene.stylesheets.add(javaClass.getResource("/zasm.css")!!.toExternalForm())

        stage.title = "Zasm 3.0 — Двухпросмотровый ассемблер в полном перемещаемом формате"
        stage.scene = scene
        stage.show()
    }

    // ---------------------------------------------------------------- toolbar

    private fun buildToolbar(): HBox {
        firstPassButton = Button("Первый проход").apply { setOnAction { onFirstPass() } }
        secondPassButton = Button("Второй проход").apply {
            isDisable = true
            setOnAction { onSecondPass() }
        }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

        exampleBox = ComboBox<AssemblerSample>().apply {
            items.addAll(Stage1Samples.all)
            items.addAll(Stage2Samples.all)
            items.addAll(Stage3Samples.all)
            selectionModel.selectFirst()
            setOnAction { selectionModel.selectedItem?.let { loadSample(it) } }
        }

        return HBox(10.0, firstPassButton, secondPassButton, spacer, Label("Выбор примера:"), exampleBox).apply {
            padding = Insets(8.0)
            alignment = Pos.CENTER_LEFT
        }
    }

    // ----------------------------------------------------------------- panels

    private fun buildPanels(): HBox {
        val left = buildSourcePanel()
        val middle = buildMiddlePanel()
        val right = buildObjectPanel()

        listOf(left, middle, right).forEach {
            HBox.setHgrow(it, Priority.ALWAYS)
            it.maxWidth = Double.MAX_VALUE
        }

        return HBox(10.0, left, middle, right).apply { padding = Insets(8.0) }
    }

    private fun buildSourcePanel(): VBox {
        sourceGrid = StringGrid(
            columnTitles = listOf("Метка", "Операция", "Операнд 1", "Операнд 2"),
            rowCount = 1,
            editable = true,
        )
        VBox.setVgrow(sourceGrid, Priority.ALWAYS)
        installPasteSupport(sourceGrid)

        val sourceHeader = HBox(
            8.0,
            Label("Исходный текст"),
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            Button("Вставить текст…").apply { setOnAction { showInsertTextDialog() } },
        ).apply { alignment = Pos.CENTER_LEFT }

        loadAddressField = TextField()
        val loadAddressBox = HBox(8.0, Label("Адрес загрузки:"), loadAddressField).apply {
            alignment = Pos.CENTER_LEFT
        }

        opcodeGrid = StringGrid(
            columnTitles = listOf("Мнемоника", "Код", "Длина"),
            rowCount = 1,
            editable = true,
        )
        opcodeGrid.loadRows(
            rows = DefaultOpcodeTable.entries.map { listOf(it.mnemonic, "%02X".format(it.code), it.length.toString()) },
            minRowCount = DefaultOpcodeTable.entries.size + OPCODE_EXTRA_ROWS,
        )
        VBox.setVgrow(opcodeGrid, Priority.ALWAYS)

        return VBox(
            8.0,
            sourceHeader, sourceGrid,
            loadAddressBox,
            Label("Таблица кодов операций"), opcodeGrid,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    private fun buildMiddlePanel(): VBox {
        auxGrid = StringGrid(listOf("Адрес", "Код", "Операнд 1", "Операнд 2"), rowCount = 0)
        symbolGrid = StringGrid(listOf("Имя", "Адрес", "Внешнее имя"), rowCount = 0)
        errors1List = ListView()

        VBox.setVgrow(auxGrid, Priority.ALWAYS)
        VBox.setVgrow(symbolGrid, Priority.ALWAYS)
        VBox.setVgrow(errors1List, Priority.SOMETIMES)

        return VBox(
            8.0,
            Label("Вспомогательная таблица"), auxGrid,
            Label("Таблица символических имён"), symbolGrid,
            Label("Ошибки первого прохода"), errors1List,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    private fun buildObjectPanel(): VBox {
        headerGrid = StringGrid(listOf("Имя", "Длина", "Адрес загрузки"), rowCount = 1)
        relocationGrid = StringGrid(listOf("Адрес команды", "Имя внешней ссылки"), rowCount = 0)
        externalNamesGrid = StringGrid(listOf("Адрес", "Имя"), rowCount = 0)
        externalRefsList = ListView()
        binaryCodeList = ListView()
        saveButton = Button("Сохранить…").apply {
            isDisable = true
            setOnAction { onSave() }
        }
        errors2List = ListView()

        VBox.setVgrow(relocationGrid, Priority.SOMETIMES)
        VBox.setVgrow(externalNamesGrid, Priority.SOMETIMES)
        VBox.setVgrow(externalRefsList, Priority.SOMETIMES)
        VBox.setVgrow(binaryCodeList, Priority.ALWAYS)
        VBox.setVgrow(errors2List, Priority.SOMETIMES)

        return VBox(
            8.0,
            Label("Заголовок объектного модуля"), headerGrid,
            Label("Таблица настройки"), relocationGrid,
            Label("Внешние имена"), externalNamesGrid,
            Label("Внешние ссылки"), externalRefsList,
            Label("Двоичный код"), binaryCodeList,
            saveButton,
            Label("Ошибки второго прохода"), errors2List,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    // -------------------------------------------------------------- handlers

    /**
     * Ctrl+V прямо на сетке (когда ни одна ячейка не редактируется) заменяет
     * содержимое всей сетки текстом из буфера обмена — быстрая вставка
     * готовой программы без открытия диалога. Пока идёт редактирование
     * конкретной ячейки, Ctrl+V работает как обычная вставка в текстовое поле.
     */
    private fun installPasteSupport(grid: StringGrid) {
        grid.setOnKeyPressed { event ->
            if (event.isControlDown && event.code == KeyCode.V && grid.editingCell == null) {
                val clipboard = Clipboard.getSystemClipboard()
                if (clipboard.hasString()) {
                    applyParsedSource(clipboard.string)
                    event.consume()
                }
            }
        }
    }

    private fun showInsertTextDialog() {
        val dialog = Dialog<ButtonType>()
        dialog.title = "Вставить исходный текст"
        dialog.headerText = "По одной команде на строку: [Метка] Операция [Операнд1] [Операнд2].\n" +
            "Метка учитывается, только если строка НЕ начинается с пробела.\n" +
            "Операнд с пробелами внутри — в кавычках, например: Text BYTE \"Hello world\""

        val textArea = TextArea(formatSourceText(readSource().filterNot { it.isBlank })).apply {
            prefRowCount = 18
            prefColumnCount = 44
            isWrapText = false
            font = Font.font("Monospaced", 13.0)
        }
        dialog.dialogPane.content = textArea
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.showAndWait()
            .filter { it == ButtonType.OK }
            .ifPresent { applyParsedSource(textArea.text) }
    }

    private fun applyParsedSource(text: String) {
        val parsed = parseSourceText(text)
        sourceGrid.loadRows(
            rows = parsed.map { listOf(it.label, it.op, it.operand1, it.operand2) },
            minRowCount = parsed.size + SOURCE_EXTRA_ROWS,
        )
        engine.reset()
        resetResults()
    }

    private fun loadSample(sample: AssemblerSample) {
        sourceGrid.loadRows(
            rows = sample.lines.map { listOf(it.label, it.op, it.operand1, it.operand2) },
            minRowCount = sample.lines.size + SOURCE_EXTRA_ROWS,
        )
        loadAddressField.text = sample.loadAddress
        engine.reset()
        resetResults()
    }

    private fun resetResults() {
        auxGrid.clearDataRows(0)
        symbolGrid.clearDataRows(0)
        errors1List.items.clear()
        headerGrid.clearDataRows(1)
        relocationGrid.clearDataRows(0)
        externalNamesGrid.clearDataRows(0)
        externalRefsList.items.clear()
        binaryCodeList.items.clear()
        errors2List.items.clear()
        secondPassButton.isDisable = true
        saveButton.isDisable = true
    }

    private fun readSource(): List<SourceLine> =
        sourceGrid.rowsAsText().map { cells ->
            SourceLine(
                label = cells.getOrElse(0) { "" },
                op = cells.getOrElse(1) { "" },
                operand1 = cells.getOrElse(2) { "" },
                operand2 = cells.getOrElse(3) { "" },
            )
        }

    private fun readOpcodes(): List<OpcodeEntry> =
        opcodeGrid.rowsAsText().mapNotNull { cells ->
            val mnemonic = cells.getOrElse(0) { "" }.trim()
            if (mnemonic.isBlank()) return@mapNotNull null
            val code = parseHex(cells.getOrElse(1) { "" }) ?: return@mapNotNull null
            val length = cells.getOrElse(2) { "" }.trim().toIntOrNull() ?: return@mapNotNull null
            OpcodeEntry(mnemonic, code, length)
        }

    private fun onFirstPass() {
        val loadAddress = parseHex(loadAddressField.text)
        if (loadAddress == null) {
            errors1List.items.setAll("Некорректный адрес загрузки: '${loadAddressField.text}'")
            secondPassButton.isDisable = true
            return
        }

        val result = engine.runPass1(readSource(), readOpcodes(), loadAddress)

        auxGrid.loadRows(result.auxTable.map { listOf(toHex8(it.address), it.opText, it.operand1, it.operand2) })
        symbolGrid.loadRows(
            result.symbolTable.map { listOf(it.name, toHex8(it.address), if (it.isExternal) "1" else "0") },
        )
        externalRefsList.items.setAll(result.externalRefs)
        errors1List.items.setAll(result.errors.map { it.toString() })

        headerGrid.clearDataRows(1)
        relocationGrid.clearDataRows(0)
        externalNamesGrid.clearDataRows(0)
        binaryCodeList.items.clear()
        errors2List.items.clear()
        saveButton.isDisable = true

        secondPassButton.isDisable = result.errors.isNotEmpty()
    }

    private fun onSecondPass() {
        val result = engine.runPass2(readOpcodes())

        headerGrid.loadRows(
            listOf(listOf(result.header.name, toHex8(result.header.length), toHex8(result.header.loadAddress))),
        )
        relocationGrid.loadRows(result.relocationTable.map { listOf(toHex8(it.address), it.externalName ?: "") })
        externalNamesGrid.loadRows(result.externalNames.map { listOf(toHex8(it.address), it.name) })
        binaryCodeList.items.setAll(result.binaryLines)
        errors2List.items.setAll(result.errors.map { it.toString() })
        saveButton.isDisable = result.errors.isNotEmpty()
    }

    private fun onSave() {
        val (pass1, pass2) = engine.currentResults() ?: return
        val fileChooser = FileChooser().apply {
            title = "Сохранить объектный модуль"
            extensionFilters.add(FileChooser.ExtensionFilter("Объектный модуль (*.obj)", "*.obj"))
            initialFileName = "${pass1.programName}.obj"
        }
        val file = fileChooser.showSaveDialog(saveButton.scene?.window) ?: return
        file.writeText(ObjectFileWriter.format(pass1, pass2))
    }
}

fun main() {
    Application.launch(MainApp::class.java)
}
