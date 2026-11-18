package com.example.domain.parser

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.ByteArrayInputStream
import java.io.InputStream

object DocParser {

    fun parse(inputStream: InputStream): WordDocumentContent {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return WordDocumentContent(emptyList(), "")

        // Check if disguised ZIP (.docx renamed to .doc)
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            return DocxParser.parse(ByteArrayInputStream(bytes))
        }

        val headerSample = String(bytes.take(256).toByteArray(), Charsets.UTF_8).lowercase()
        if (headerSample.contains("<html") || headerSample.contains("<!doctype")) {
            val html = String(bytes, Charsets.UTF_8)
            val tables = HtmlTableExtractor.extractRows(html)
            return if (tables.isNotEmpty()) {
                WordDocumentContent(tables = listOf(tables), paragraphsText = "")
            } else {
                WordDocumentContent(emptyList(), HtmlTableExtractor.stripTags(html))
            }
        }
        if (headerSample.startsWith("{\\rtf")) {
            val plain = stripBasicRtf(String(bytes, Charsets.UTF_8))
            return WordDocumentContent(emptyList(), plain)
        }

        // Standard OLE2 .doc binary via Apache POI HWPFDocument
        HWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            WordExtractor(doc).use { extractor ->
                val text = extractor.paragraphText
                    .map { it.replace("\r\n", "\n").replace('\r', '\n').replace('\u0007', '\t').trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                return WordDocumentContent(emptyList(), text)
            }
        }
    }

    private fun stripBasicRtf(rtf: String): String {
        val noGroups = rtf
            .replace(Regex("""\\par\b"""), "\n")
            .replace(Regex("""\\line\b"""), "\n")
            .replace(Regex("""\\tab\b"""), "\t")
            .replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
            .replace("{", "")
            .replace("}", "")
        return noGroups.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
