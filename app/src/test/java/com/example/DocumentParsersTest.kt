package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.parser.DocParser
import com.example.domain.parser.DocxParser
import com.example.domain.parser.PdfParser
import com.example.domain.parser.QuestionExtractor
import com.example.domain.parser.XlsParser
import com.example.domain.parser.XlsxParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentParsersTest {

    @Test
    fun `parse multi-sheet xlsx preserves sheet names options answers and blank answer`() {
        val xlsxBytes = buildMultiSheetXlsx()
        val sheets = XlsxParser.parse(ByteArrayInputStream(xlsxBytes))
        assertEquals(2, sheets.size)
        assertEquals("Matematika", sheets[0].sheetName)
        assertEquals("Biologi", sheets[1].sheetName)

        val mathOutcome = QuestionExtractor.extractFromTableRows(
            rows = sheets[0].rows,
            sourceLocation = "Sheet: ${sheets[0].sheetName}",
            defaultCategory = sheets[0].sheetName
        )
        assertEquals(2, mathOutcome.questions.size)
        assertEquals("Berapakah hasil dari 12 x 12?", mathOutcome.questions[0].questionText)
        assertEquals("144", mathOutcome.questions[0].optionC)
        assertEquals("C", mathOutcome.questions[0].correctAnswer)
        assertEquals("Sheet: Matematika", mathOutcome.questions[0].sourceLocation)
        // Second question intentionally has no answer -> must remain blank (never fabricated)
        assertEquals("", mathOutcome.questions[1].correctAnswer)

        val bioOutcome = QuestionExtractor.extractFromTableRows(
            rows = sheets[1].rows,
            sourceLocation = "Sheet: ${sheets[1].sheetName}",
            defaultCategory = sheets[1].sheetName
        )
        assertEquals(1, bioOutcome.questions.size)
        assertEquals("Klorofil", bioOutcome.questions[0].correctAnswer)
        assertEquals("Sheet: Biologi", bioOutcome.questions[0].sourceLocation)
    }

    @Test
    fun `parse multi-sheet xls binary workbook via Apache POI HSSF`() {
        val out = ByteArrayOutputStream()
        HSSFWorkbook().use { wb ->
            val s1 = wb.createSheet("Fisika")
            s1.createRow(0).apply {
                createCell(0).setCellValue("Pertanyaan")
                createCell(1).setCellValue("A")
                createCell(2).setCellValue("B")
                createCell(3).setCellValue("C")
                createCell(4).setCellValue("D")
                createCell(5).setCellValue("Kunci Jawaban")
            }
            s1.createRow(1).apply {
                createCell(0).setCellValue("Satuan SI untuk gaya adalah?")
                createCell(1).setCellValue("Joule")
                createCell(2).setCellValue("Newton")
                createCell(3).setCellValue("Watt")
                createCell(4).setCellValue("Pascal")
                createCell(5).setCellValue("B")
            }

            val s2 = wb.createSheet("Kimia")
            s2.createRow(0).apply {
                createCell(0).setCellValue("Pertanyaan")
                createCell(1).setCellValue("Jawaban")
            }
            s2.createRow(1).apply {
                createCell(0).setCellValue("Lambang unsur kimia emas adalah?")
                createCell(1).setCellValue("Au")
            }
            wb.write(out)
        }

        val sheets = XlsParser.parse(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, sheets.size)
        assertEquals("Fisika", sheets[0].sheetName)
        assertEquals("Kimia", sheets[1].sheetName)

        val fisika = QuestionExtractor.extractFromTableRows(sheets[0].rows, "Sheet: Fisika", "Fisika")
        assertEquals(1, fisika.questions.size)
        assertEquals("Newton", fisika.questions[0].optionB)
        assertEquals("B", fisika.questions[0].correctAnswer)

        val kimia = QuestionExtractor.extractFromTableRows(sheets[1].rows, "Sheet: Kimia", "Kimia")
        assertEquals(1, kimia.questions.size)
        assertEquals("Au", kimia.questions[0].correctAnswer)
    }

    @Test
    fun `parse docx with table and numbered paragraphs plus answer key`() {
        val docxBytes = buildDocxWithTableAndParagraphs()
        val content = DocxParser.parse(ByteArrayInputStream(docxBytes))

        assertEquals(1, content.tables.size)
        val tableQuestions = QuestionExtractor.extractFromTableRows(
            content.tables[0],
            sourceLocation = "Tabel 1",
            defaultCategory = "Word"
        )
        assertEquals(1, tableQuestions.questions.size)
        assertEquals("Apa nama planet terdekat dari Matahari?", tableQuestions.questions[0].questionText)
        assertEquals("Merkurius", tableQuestions.questions[0].correctAnswer)

        val paraQuestions = QuestionExtractor.extractFromTextBlocks(
            content.paragraphsText,
            sourceLocation = "Dokumen Word",
            defaultCategory = "Word"
        )
        assertEquals(2, paraQuestions.questions.size)
        assertEquals("A", paraQuestions.questions[0].correctAnswer)
        assertEquals("D", paraQuestions.questions[1].correctAnswer)
    }

    @Test
    fun `parse doc html-table and rtf fallback formats`() {
        val rtfDoc = """
            {\rtf1\ansi
            1. Siapa proklamator kemerdekaan Indonesia?\par
            A. Soekarno dan Hatta\par
            B. Sudirman\par
            C. Diponegoro\par
            D. Kartini\par
            Kunci: A\par
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val parsed = DocParser.parse(ByteArrayInputStream(rtfDoc))
        val outcome = QuestionExtractor.extractFromTextBlocks(parsed.paragraphsText, "Dokumen Word")
        assertEquals(1, outcome.questions.size)
        assertEquals("A", outcome.questions[0].correctAnswer)
        assertTrue(outcome.questions[0].optionA.contains("Soekarno"))
    }

    @Test
    fun `parse multi-page pdf preserves page numbers and resolves trailing answer key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PdfParser.ensureInitialized(context)

        val pdfOut = ByteArrayOutputStream()
        PDDocument().use { doc ->
            val page1 = PDPage()
            doc.addPage(page1)
            PDPageContentStream(doc, page1).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("1. Gunung tertinggi di Pulau Jawa adalah?")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("A. Semeru")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("B. Merapi")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("C. Bromo")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("D. Kelud")
                cs.endText()
            }

            val page2 = PDPage()
            doc.addPage(page2)
            PDPageContentStream(doc, page2).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("2. Sungai terpanjang di Kalimantan adalah?")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("A. Mahakam  B. Kapuas  C. Barito  D. Musi")
                cs.newLineAtOffset(0f, -30f)
                cs.showText("KUNCI JAWABAN")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("1. A")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("2. B")
                cs.endText()
            }
            doc.save(pdfOut)
        }

        val pages = PdfParser.parsePages(context, ByteArrayInputStream(pdfOut.toByteArray()))
        assertEquals(2, pages.size)
        assertEquals("Halaman 1", pages[0].first)
        assertEquals("Halaman 2", pages[1].first)

        val outcome = QuestionExtractor.extractFromTextPages(pages, "Geografi")
        assertEquals(2, outcome.questions.size)
        assertEquals("Halaman 1", outcome.questions[0].sourceLocation)
        assertEquals("A", outcome.questions[0].correctAnswer)
        assertEquals("Halaman 2", outcome.questions[1].sourceLocation)
        assertEquals("Kapuas", outcome.questions[1].optionB)
        assertEquals("B", outcome.questions[1].correctAnswer)
    }

    private fun buildMultiSheetXlsx(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            addEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Matematika" sheetId="1" r:id="rId1"/>
                    <sheet name="Biologi" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>"""
            )
            addEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
                </Relationships>"""
            )
            addEntry(
                "xl/sharedStrings.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Pertanyaan</t></si>
                  <si><t>A</t></si>
                  <si><t>B</t></si>
                  <si><t>C</t></si>
                  <si><t>D</t></si>
                  <si><t>Kunci</t></si>
                  <si><t>Berapakah hasil dari 12 x 12?</t></si>
                  <si><t>Berapakah akar kuadrat dari 225?</t></si>
                  <si><t>Zat hijau daun pada tumbuhan disebut?</t></si>
                  <si><t>Klorofil</t></si>
                </sst>"""
            )
            addEntry(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                      <c r="E1" t="s"><v>4</v></c>
                      <c r="F1" t="s"><v>5</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>6</v></c>
                      <c r="B2" t="n"><v>120</v></c>
                      <c r="C2" t="n"><v>132</v></c>
                      <c r="D2" t="n"><v>144</v></c>
                      <c r="E2" t="n"><v>156</v></c>
                      <c r="F2" t="inlineStr"><is><t>C</t></is></c>
                    </row>
                    <row r="3">
                      <c r="A3" t="s"><v>7</v></c>
                      <c r="B3" t="n"><v>12</v></c>
                      <c r="C3" t="n"><v>13</v></c>
                      <c r="D3" t="n"><v>14</v></c>
                      <c r="E3" t="n"><v>15</v></c>
                    </row>
                  </sheetData>
                </worksheet>"""
            )
            addEntry(
                "xl/worksheets/sheet2.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>5</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>8</v></c>
                      <c r="B2" t="s"><v>9</v></c>
                    </row>
                  </sheetData>
                </worksheet>"""
            )
        }
        return out.toByteArray()
    }

    private fun buildDocxWithTableAndParagraphs(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>Pertanyaan</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>Jawaban</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>Apa nama planet terdekat dari Matahari?</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>Merkurius</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                    <w:p><w:r><w:t>1. Proses tumbuhan hijau membuat makanan dengan bantuan cahaya disebut?</w:t></w:r></w:p>
                    <w:p><w:r><w:t>A. Fotosintesis</w:t></w:r></w:p>
                    <w:p><w:r><w:t>B. Respirasi</w:t></w:r></w:p>
                    <w:p><w:r><w:t>C. Fermentasi</w:t></w:r></w:p>
                    <w:p><w:r><w:t>D. Transpirasi</w:t></w:r></w:p>
                    <w:p><w:r><w:t>2. Gas yang paling banyak di atmosfer bumi adalah?</w:t></w:r></w:p>
                    <w:p><w:r><w:t>A. Oksigen</w:t></w:r></w:p>
                    <w:p><w:r><w:t>B. Karbon dioksida</w:t></w:r></w:p>
                    <w:p><w:r><w:t>C. Argon</w:t></w:r></w:p>
                    <w:p><w:r><w:t>D. Nitrogen</w:t></w:r></w:p>
                    <w:p><w:r><w:t>KUNCI JAWABAN</w:t></w:r></w:p>
                    <w:p><w:r><w:t>1. A</w:t></w:r></w:p>
                    <w:p><w:r><w:t>2. D</w:t></w:r></w:p>
                  </w:body>
                </w:document>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }
        return out.toByteArray()
    }
}
