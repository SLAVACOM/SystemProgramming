package org.slavacom.zasm.ui

import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.Stage
import org.slavacom.zasm.engine.AssemblerEngine
import org.slavacom.zasm.engine.Pass1Engine
import org.slavacom.zasm.model.DefaultOpcodeTable
import org.slavacom.zasm.model.OpcodeEntry
import org.slavacom.zasm.model.SourceLine
import org.slavacom.zasm.model.formatSourceText
import org.slavacom.zasm.model.parseHex
import org.slavacom.zasm.model.parseSourceText
import org.slavacom.zasm.model.toHex8
import org.slavacom.zasm.samples.AssemblerSample
import org.slavacom.zasm.samples.Stage1Samples

private const val PANEL_WIDTH = 380.0
private const val OPCODE_EXTRA_ROWS = 4

/**
 * Этап 1: простейший ассемблер в абсолютном формате — проход 1 и проход 2
 * подключены к движку из пакета `engine`.
 */
class MainApp : Application() {

    private val engine = AssemblerEngine()

    private lateinit var firstPassButton: Button
    private lateinit var secondPassButton: Button
    private lateinit var exampleBox: ComboBox<AssemblerSample>
    private lateinit var loadAddressField: TextField

    private lateinit var sourceTextArea: TextArea
    private lateinit var opcodeGrid: StringGrid

    private lateinit var auxGrid: StringGrid
    private lateinit var symbolGrid: StringGrid
    private lateinit var errors1List: ListView<String>

    private lateinit var headerGrid: StringGrid
    private lateinit var binaryCodeList: ListView<String>
    private lateinit var errors2List: ListView<String>

    override fun start(stage: Stage) {
        val root = BorderPane()
        root.top = buildToolbar()
        root.center = buildPanels()

        loadSample(Stage1Samples.default)

        val scene = Scene(root, 1300.0, 780.0)
        scene.stylesheets.add(javaClass.getResource("/zasm.css")!!.toExternalForm())

        stage.title = "Zasm 1.0 — Двухпросмотровый ассемблер в абсолютном формате"
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

        loadAddressField = TextField().apply {
            promptText = "необязательно, напр. 00001000"
            prefColumnCount = 12
            tooltip = Tooltip(
                "Необязательный адрес загрузки по умолчанию.\n" +
                    "Если в исходном тексте директива Start указывает свой\n" +
                    "адрес (например 'Exampl Start 00001000') — он ИМЕЕТ\n" +
                    "ПРИОРИТЕТ и переопределяет значение этого поля.",
            )
        }

        exampleBox = ComboBox<AssemblerSample>().apply {
            items.addAll(Stage1Samples.all)
            selectionModel.selectFirst()
            setOnAction { selectionModel.selectedItem?.let { loadSample(it) } }
        }

        return HBox(
            10.0,
            firstPassButton, secondPassButton,
            spacer,
            Label("Адрес загрузки (опц.):"), loadAddressField,
            Label("Выбор примера:"), exampleBox,
        ).apply {
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
        sourceTextArea = TextArea().apply {
            font = Font.font("Monospaced", 13.0)
            isWrapText = false
        }
        VBox.setVgrow(sourceTextArea, Priority.ALWAYS)

        val sourceHeader = HBox(
            8.0,
            Label("Исходный текст"),
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            Button("Проверить синтаксис").apply { setOnAction { onCheckSyntax() } },
            Button("Отформатировать").apply { setOnAction { onFormatSource() } },
        ).apply { alignment = Pos.CENTER_LEFT }

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
            sourceHeader, sourceTextArea,
            Label("Таблица кодов операций"), opcodeGrid,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    private fun buildMiddlePanel(): VBox {
        auxGrid = StringGrid(listOf("Адрес", "Код", "Операнд 1", "Операнд 2"), rowCount = 0)
        symbolGrid = StringGrid(listOf("Имя", "Адрес"), rowCount = 0)
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
        binaryCodeList = ListView()
        errors2List = ListView()

        VBox.setVgrow(binaryCodeList, Priority.ALWAYS)
        VBox.setVgrow(errors2List, Priority.SOMETIMES)

        return VBox(
            8.0,
            Label("Заголовок объектного модуля"), headerGrid,
            Label("Двоичный код"), binaryCodeList,
            Label("Ошибки второго прохода"), errors2List,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    // -------------------------------------------------------------- handlers

    private fun onFormatSource() {
        sourceTextArea.text = formatSourceText(parseSourceText(sourceTextArea.text))
    }

    private fun onCheckSyntax() {
        val addressInput = readLoadAddressField()
        if (addressInput is LoadAddressInput.Invalid) {
            errors1List.items.setAll("Некорректный адрес загрузки в поле: '${addressInput.text}'")
            return
        }
        val fieldAddress = (addressInput as? LoadAddressInput.Valid)?.value
        val result = Pass1Engine.run(readSource(), readOpcodes(), fieldAddress)
        errors1List.items.setAll(
            if (result.errors.isEmpty()) listOf("Ошибок не найдено.") else result.errors.map { it.toString() },
        )
    }

    private fun loadSample(sample: AssemblerSample) {
        sourceTextArea.text = formatSourceText(sample.lines)
        loadAddressField.text = sample.loadAddress
        engine.reset()
        resetResults()
    }

    private fun resetResults() {
        auxGrid.clearDataRows(0)
        symbolGrid.clearDataRows(0)
        errors1List.items.clear()
        headerGrid.clearDataRows(1)
        binaryCodeList.items.clear()
        errors2List.items.clear()
        secondPassButton.isDisable = true
    }

    private fun readSource(): List<SourceLine> = parseSourceText(sourceTextArea.text)

    private fun readOpcodes(): List<OpcodeEntry> =
        opcodeGrid.rowsAsText().mapNotNull { cells ->
            val mnemonic = cells.getOrElse(0) { "" }.trim()
            if (mnemonic.isBlank()) return@mapNotNull null
            val code = parseHex(cells.getOrElse(1) { "" }) ?: return@mapNotNull null
            val length = cells.getOrElse(2) { "" }.trim().toIntOrNull() ?: return@mapNotNull null
            OpcodeEntry(mnemonic, code, length)
        }

    /** Поле адреса загрузки необязательно — пустое отдаёт `null` (см. [Pass1Engine]: тогда решает `Start`). */
    private sealed class LoadAddressInput {
        data object Absent : LoadAddressInput()
        data class Valid(val value: Int) : LoadAddressInput()
        data class Invalid(val text: String) : LoadAddressInput()
    }

    private fun readLoadAddressField(): LoadAddressInput {
        val text = loadAddressField.text.trim()
        if (text.isEmpty()) return LoadAddressInput.Absent
        val parsed = parseHex(text)
        return if (parsed != null) LoadAddressInput.Valid(parsed) else LoadAddressInput.Invalid(text)
    }

    private fun onFirstPass() {
        val addressInput = readLoadAddressField()
        if (addressInput is LoadAddressInput.Invalid) {
            resetResults()
            errors1List.items.setAll("Некорректный адрес загрузки в поле: '${addressInput.text}'")
            return
        }
        val fieldAddress = (addressInput as? LoadAddressInput.Valid)?.value

        val result = engine.runPass1(readSource(), readOpcodes(), fieldAddress)

        auxGrid.loadRows(result.auxTable.map { listOf(toHex8(it.address), it.opText, it.operand1, it.operand2) })
        symbolGrid.loadRows(result.symbolTable.map { listOf(it.name, toHex8(it.address)) })
        errors1List.items.setAll(result.errors.map { it.toString() })

        headerGrid.clearDataRows(1)
        binaryCodeList.items.clear()
        errors2List.items.clear()

        secondPassButton.isDisable = result.errors.isNotEmpty()
    }

    private fun onSecondPass() {
        val result = engine.runPass2(readOpcodes())

        headerGrid.loadRows(
            listOf(listOf(result.header.name, toHex8(result.header.length), toHex8(result.header.loadAddress))),
        )
        binaryCodeList.items.setAll(result.binaryLines)
        errors2List.items.setAll(result.errors.map { it.toString() })
    }
}

fun main() {
    Application.launch(MainApp::class.java)
}
