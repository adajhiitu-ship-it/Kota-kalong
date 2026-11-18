package com.example.domain.parser

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ExcelSheetData(
    val sheetName: String,
    val rows: List<List<String>>
)

object XlsxParser {

    fun parse(inputStream: InputStream): List<ExcelSheetData> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val relsMap = entries["xl/_rels/workbook.xml.rels"]?.let { parseWorkbookRels(it) } ?: emptyMap()
        val sheetDefs = entries["xl/workbook.xml"]?.let { parseWorkbookSheets(it) } ?: emptyList()

        val result = mutableListOf<ExcelSheetData>()
        if (sheetDefs.isNotEmpty()) {
            for ((sheetName, rId) in sheetDefs) {
                val targetPath = relsMap[rId]?.let { normalizeZipTarget(it) } ?: continue
                val sheetBytes = entries[targetPath] ?: continue
                val rows = parseWorksheet(sheetBytes, sharedStrings)
                result.add(ExcelSheetData(sheetName = sheetName, rows = rows))
            }
        } else {
            // Fallback: scan any xl/worksheets/sheet*.xml
            val sheetKeys = entries.keys
                .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
                .sorted()
            for ((idx, key) in sheetKeys.withIndex()) {
                val sheetBytes = entries[key] ?: continue
                val rows = parseWorksheet(sheetBytes, sharedStrings)
                result.add(ExcelSheetData(sheetName = "Sheet ${idx + 1}", rows = rows))
            }
        }
        return result
    }

    private fun normalizeZipTarget(target: String): String {
        val clean = target.removePrefix("/")
        return if (clean.startsWith("xl/")) clean else "xl/$clean"
    }

    private fun parseWorkbookSheets(bytes: ByteArray): List<Pair<String, String>> {
        val sheets = mutableListOf<Pair<String, String>>()
        val parser = createParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.localName() == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val rId = (0 until parser.attributeCount)
                    .firstOrNull { parser.getAttributeName(it).endsWith(":id") || parser.getAttributeName(it) == "id" }
                    ?.let { parser.getAttributeValue(it) }
                    .orEmpty()
                if (rId.isNotEmpty()) {
                    sheets.add(name to rId)
                }
            }
            event = parser.next()
        }
        return sheets
    }

    private fun parseWorkbookRels(bytes: ByteArray): Map<String, String> {
        val rels = HashMap<String, String>()
        val parser = createParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.localName() == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrEmpty() && !target.isNullOrEmpty()) {
                    rels[id] = target
                }
            }
            event = parser.next()
        }
        return rels
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = createParser(bytes)
        var inSi = false
        var inText = false
        val current = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.localName()) {
                        "si" -> {
                            inSi = true
                            current.clear()
                        }
                        "t" -> if (inSi) inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inText) {
                        current.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.localName()) {
                        "t" -> inText = false
                        "si" -> {
                            strings.add(current.toString())
                            inSi = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = createParser(bytes)

        var currentRowCells = mutableMapOf<Int, String>()
        var currentColIndex = -1
        var currentCellType = ""
        var inValue = false
        var inInlineText = false
        val valueBuilder = StringBuilder()
        var sequentialColFallback = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.localName()) {
                        "row" -> {
                            currentRowCells = mutableMapOf()
                            sequentialColFallback = 0
                        }
                        "c" -> {
                            val cellRef = parser.getAttributeValue(null, "r")
                            currentColIndex = if (!cellRef.isNullOrEmpty()) {
                                columnIndexFromRef(cellRef)
                            } else {
                                sequentialColFallback
                            }
                            sequentialColFallback = currentColIndex + 1
                            currentCellType = parser.getAttributeValue(null, "t").orEmpty()
                            valueBuilder.clear()
                        }
                        "v" -> inValue = true
                        "t" -> if (currentCellType == "inlineStr") inInlineText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue || inInlineText) {
                        valueBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.localName()) {
                        "v" -> inValue = false
                        "t" -> inInlineText = false
                        "c" -> {
                            if (currentColIndex >= 0) {
                                val raw = valueBuilder.toString()
                                val resolved = when (currentCellType) {
                                    "s" -> {
                                        val idx = raw.trim().toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                                    }
                                    "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                    "inlineStr", "str", "e" -> raw
                                    else -> formatNumericCell(raw)
                                }
                                if (resolved.isNotEmpty()) {
                                    currentRowCells[currentColIndex] = resolved
                                }
                            }
                            currentColIndex = -1
                        }
                        "row" -> {
                            if (currentRowCells.isNotEmpty()) {
                                val maxCol = currentRowCells.keys.maxOrNull() ?: 0
                                val rowList = MutableList(maxCol + 1) { col ->
                                    currentRowCells[col].orEmpty()
                                }
                                rows.add(rowList)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun formatNumericCell(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val asDouble = trimmed.toDoubleOrNull() ?: return trimmed
        return if (asDouble % 1.0 == 0.0 && asDouble in -9.0e15..9.0e15) {
            asDouble.toLong().toString()
        } else {
            trimmed
        }
    }

    private fun columnIndexFromRef(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun createParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
    }

    private fun XmlPullParser.localName(): String {
        return name?.substringAfter(':') ?: ""
    }
}
