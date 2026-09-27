package org.slavacom.zasm.ui

import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.slavacom.zasm.model.DefaultOpcodeTable

private const val DEFAULT_LOAD_ADDRESS = "00001000"
private const val PANEL_WIDTH = 380.0

/**
 * Этап 0: пустой каркас формы — три панели с сетками, без логики проходов.
 * Разметка соответствует Шагу 1 Этапа 1 методички (см. docs/labs/img_2.jpg).
 */
class MainApp : Application() {

    override fun start(stage: Stage) {
        val root = BorderPane()
        root.top = buildToolbar()
        root.center = buildPanels()

        val scene = Scene(root, 1300.0, 780.0)
        scene.stylesheets.add(javaClass.getResource("/zasm.css")!!.toExternalForm())

        stage.title = "Zasm 1.0 — Двухпросмотровый ассемблер в абсолютном формате"
        stage.scene = scene
        stage.show()
    }

    private fun buildToolbar(): HBox {
        val firstPass = Button("Первый проход")
        val secondPass = Button("Второй проход").apply { isDisable = true }
        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val exampleBox = ComboBox<String>().apply {
            items.addAll("по умолчанию (без ошибок)")
            selectionModel.selectFirst()
        }

        return HBox(10.0, firstPass, secondPass, spacer, Label("Выбор примера:"), exampleBox).apply {
            padding = Insets(8.0)
            alignment = Pos.CENTER_LEFT
        }
    }

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
        val sourceGrid = StringGrid(
            columnTitles = listOf("Метка", "Операция", "Операнд 1", "Операнд 2"),
            rowCount = 16,
            editable = true,
        )
        VBox.setVgrow(sourceGrid, Priority.ALWAYS)

        val loadAddressField = TextField(DEFAULT_LOAD_ADDRESS)
        val loadAddressBox = HBox(8.0, Label("Адрес загрузки:"), loadAddressField).apply {
            alignment = Pos.CENTER_LEFT
        }

        val opcodeGrid = StringGrid(
            columnTitles = listOf("Мнемоника", "Код", "Длина"),
            rowCount = DefaultOpcodeTable.entries.size,
            editable = true,
        )
        DefaultOpcodeTable.entries.forEachIndexed { row, entry ->
            opcodeGrid.setCellText(row, 0, entry.mnemonic)
            opcodeGrid.setCellText(row, 1, "%02X".format(entry.code))
            opcodeGrid.setCellText(row, 2, entry.length.toString())
        }
        VBox.setVgrow(opcodeGrid, Priority.ALWAYS)

        return VBox(
            8.0,
            Label("Исходный текст"), sourceGrid,
            loadAddressBox,
            Label("Таблица кодов операций"), opcodeGrid,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    private fun buildMiddlePanel(): VBox {
        val auxGrid = StringGrid(listOf("Адрес", "Код", "Операнд 1", "Операнд 2"), rowCount = 16)
        val symbolGrid = StringGrid(listOf("Имя", "Адрес"), rowCount = 10)
        val errors1 = ListView<String>()

        VBox.setVgrow(auxGrid, Priority.ALWAYS)
        VBox.setVgrow(symbolGrid, Priority.ALWAYS)
        VBox.setVgrow(errors1, Priority.SOMETIMES)

        return VBox(
            8.0,
            Label("Вспомогательная таблица"), auxGrid,
            Label("Таблица символических имён"), symbolGrid,
            Label("Ошибки первого прохода"), errors1,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }

    private fun buildObjectPanel(): VBox {
        val headerGrid = StringGrid(listOf("Имя", "Длина", "Адрес загрузки"), rowCount = 1)
        val binaryCode = ListView<String>()
        val errors2 = ListView<String>()

        VBox.setVgrow(binaryCode, Priority.ALWAYS)
        VBox.setVgrow(errors2, Priority.SOMETIMES)

        return VBox(
            8.0,
            Label("Заголовок объектного модуля"), headerGrid,
            Label("Двоичный код"), binaryCode,
            Label("Ошибки второго прохода"), errors2,
        ).apply {
            padding = Insets(4.0)
            prefWidth = PANEL_WIDTH
        }
    }
}

fun main() {
    Application.launch(MainApp::class.java)
}
