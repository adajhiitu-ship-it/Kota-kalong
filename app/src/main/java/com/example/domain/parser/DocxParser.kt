package com.example.domain.parser

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class WordDocumentContent(
    val tables: List<List<List<String>>>,
    val paragraphsText: String
)

object DocxParser {

    fun parse(inputStream: InputStream): WordDocumentContent {
        var documentXml: ByteArray? = null
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val xmlBytes = documentXml ?: return WordDocumentContent(emptyList(), "")
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply {
            setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        }

        val tables = mutableListOf<List<List<String>>>()
        val paragraphLines = mutableListOf<String>()

        var tableDepth = 0
        var currentTableRows = mutableListOf<List<String>>()
        var currentRowCells = mutableListOf<String>()
        var inTableCell = false
        val cellBuffer = StringBuilder()

        var inParagraph = false
        val paragraphBuffer = StringBuilder()
        var inText = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val localName = parser.name?.substringAfter(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localName) {
                        "tbl" -> {
                            if (tableDepth == 0) {
                                currentTableRows = mutableListOf()
                            }
                            tableDepth++
                        }
                        "tr" -> if (tableDepth == 1) {
                            currentRowCells = mutableListOf()
                        }
                        "tc" -> if (tableDepth == 1) {
                            inTableCell = true
                            cellBuffer.clear()
                        }
                        "p" -> {
                            inParagraph = true
                            paragraphBuffer.clear()
                        }
                        "t", "delText" -> if (inParagraph) inText = true
                        "tab" -> if (inParagraph) paragraphBuffer.append('\t')
                        "br", "cr" -> if (inParagraph) paragraphBuffer.append('\n')
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        paragraphBuffer.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (localName) {
                        "t", "delText" -> inText = false
                        "p" -> {
                            val text = paragraphBuffer.toString().trim()
                            if (inTableCell) {
                                if (text.isNotEmpty()) {
                                    if (cellBuffer.isNotEmpty()) cellBuffer.append('\n')
                                    cellBuffer.append(text)
                                }
                            } else if (tableDepth == 0 && text.isNotEmpty()) {
                                paragraphLines.add(text)
                            }
                            inParagraph = false
                        }
                        "tc" -> if (tableDepth == 1 && inTableCell) {
                            currentRowCells.add(cellBuffer.toString().trim())
                            inTableCell = false
                        }
                        "tr" -> if (tableDepth == 1) {
                            if (currentRowCells.any { it.isNotBlank() }) {
                                currentTableRows.add(currentRowCells.toList())
                            }
                        }
                        "tbl" -> {
                            tableDepth--
                            if (tableDepth == 0 && currentTableRows.isNotEmpty()) {
                                tables.add(currentTableRows.toList())
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        return WordDocumentContent(
            tables = tables,
            paragraphsText = paragraphLines.joinToString("\n")
        )
    }
}
