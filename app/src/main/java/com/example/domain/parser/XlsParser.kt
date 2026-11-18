package com.example.domain.parser

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.ByteArrayInputStream
import java.io.InputStream

object XlsParser {

    fun parse(inputStream: InputStream): List<ExcelSheetData> {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return emptyList()

        // Check if disguised ZIP (.xlsx renamed to .xls)
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return XlsxParser.parse(ByteArrayInputStream(bytes))
        }

        // Check if disguised HTML table export (.xls saved from web portal)
        val headerSample = String(bytes.take(256).toByteArray(), Charsets.UTF_8).lowercase()
        if (headerSample.contains("<html") || headerSample.contains("<table")) {
            val rows = HtmlTableExtractor.extractRows(String(bytes, Charsets.UTF_8))
            return if (rows.isNotEmpty()) listOf(ExcelSheetData("Sheet 1", rows)) else emptyList()
        }

        val sheets = mutableListOf<ExcelSheetData>()
        val formatter = DataFormatter()
        HSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            for (s in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(s)
                val sheetName = sheet.sheetName ?: "Sheet ${s + 1}"
                val rows = mutableListOf<List<String>>()
                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val lastCell = row.lastCellNum.toInt()
                    if (lastCell <= 0) continue
                    val cells = MutableList(lastCell) { c ->
                        val cell = row.getCell(c)
                        if (cell == null) "" else formatter.formatCellValue(cell).trim()
                    }
                    if (cells.any { it.isNotBlank() }) {
                        rows.add(cells)
                    }
                }
                sheets.add(ExcelSheetData(sheetName = sheetName, rows = rows))
            }
        }
        return sheets
    }
}

internal object HtmlTableExtractor {
    private val TR_REGEX = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TD_REGEX = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG_REGEX = Regex("""<[^>]+>""")

    fun extractRows(html: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (trMatch in TR_REGEX.findAll(html)) {
            val rowHtml = trMatch.groupValues[1]
            val cells = TD_REGEX.findAll(rowHtml).map { td ->
                decodeHtmlEntities(TAG_REGEX.replace(td.groupValues[1], " ").trim())
            }.toList()
            if (cells.any { it.isNotBlank() }) {
                rows.add(cells)
            }
        }
        return rows
    }

    fun stripTags(html: String): String {
        val withBreaks = html
            .replace(Regex("""</(?:p|div|li|tr|h[1-6])>|<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        return decodeHtmlEntities(TAG_REGEX.replace(withBreaks, " "))
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \t]+"), " ")
            .trim()
    }
}
