package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.domain.parser.BackupManager
import com.example.domain.parser.DocumentImportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import androidx.compose.ui.test.performScrollTo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class KotaKalongEndToEndRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app declares zero internet permission for 100 percent offline airplane mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "App must not require INTERNET permission",
            permissions.contains(Manifest.permission.INTERNET)
        )
    }

    @Test
    fun `database persists across close and reopen and prevents duplicates on reimport`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_persist_kota_kalong.db"
        context.deleteDatabase(dbName)

        var db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val importService = DocumentImportService(context, db)
        val sampleXlsx = buildSampleXlsx()

        val firstReport = importService.importStream(
            fileName = "referensi_soal.xlsx",
            inputStream = ByteArrayInputStream(sampleXlsx),
            preventDuplicates = true
        )
        assertEquals(2, firstReport.importedCount)
        assertEquals(0, firstReport.duplicateCount)

        // Second upload of identical file with preventDuplicates = true -> duplicates skipped
        val secondReport = importService.importStream(
            fileName = "referensi_duplikat.xlsx",
            inputStream = ByteArrayInputStream(sampleXlsx),
            preventDuplicates = true
        )
        assertEquals(0, secondReport.importedCount)
        assertEquals(2, secondReport.duplicateCount)

        // Close database and reopen to verify persistence across app restart
        db.close()

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val persistedQuestions = db.questionDao().getAllQuestionsOnce()
        assertEquals(2, persistedQuestions.size)
        val qWithNoAns = persistedQuestions.first { it.questionText.contains("Tanpa Jawaban") }
        assertEquals("", qWithNoAns.correctAnswer)
        assertEquals("referensi_soal.xlsx", qWithNoAns.sourceDocumentName)
        assertEquals("Sheet: Paket1", qWithNoAns.sourceLocation)

        // Test JSON Backup & Restore round-trip
        val backupManager = BackupManager(context, db)
        val backupJson = backupManager.exportToJsonString()
        assertTrue(backupJson.contains("referensi_soal.xlsx"))

        val restoreReport = backupManager.restoreFromJsonString(backupJson, replaceExisting = true)
        assertEquals(2, restoreReport.importedQuestions)
        assertEquals(2, db.questionDao().getAllQuestionsOnce().size)

        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `compose cuj home search manual add missing answer notice and navigation`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("KOTA KALONG").assertIsDisplayed()
        composeRule.onNodeWithTag("home_menu_upload").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_menu_database").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_menu_favorites").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_menu_history").performScrollTo().assertIsDisplayed()

        // Navigate to Database tab and add a manual question without an answer
        composeRule.onNodeWithTag("nav_database").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("db_add_question_fab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor_question_input")
            .performTextInput("Siapa penemu mesin uap modern?")
        composeRule.onNodeWithTag("editor_save_button").performClick()
        composeRule.waitForIdle()

        // Verify "Jawaban belum tersedia" is displayed (never fabricated)
        composeRule.onNodeWithText("Jawaban belum tersedia").performScrollTo().assertIsDisplayed()

        // Navigate back to Beranda and perform Exact Match offline search
        composeRule.onNodeWithTag("nav_beranda").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_search_input")
            .performScrollTo()
            .performTextInput("Siapa penemu mesin uap modern?")
        composeRule.onNodeWithTag("home_search_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sama Persis • 100%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Input Manual").performScrollTo().assertIsDisplayed()
    }

    private fun buildSampleXlsx(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun add(name: String, xml: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            add(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Paket1" sheetId="1" r:id="rId1"/></sheets>
                </workbook>"""
            )
            add(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                </Relationships>"""
            )
            add(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="inlineStr"><is><t>Pertanyaan</t></is></c>
                      <c r="B1" t="inlineStr"><is><t>A</t></is></c>
                      <c r="C1" t="inlineStr"><is><t>B</t></is></c>
                      <c r="D1" t="inlineStr"><is><t>Kunci</t></is></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="inlineStr"><is><t>Apa warna bendera Indonesia?</t></is></c>
                      <c r="B2" t="inlineStr"><is><t>Merah Putih</t></is></c>
                      <c r="C2" t="inlineStr"><is><t>Biru Putih</t></is></c>
                      <c r="D2" t="inlineStr"><is><t>A</t></is></c>
                    </row>
                    <row r="3">
                      <c r="A3" t="inlineStr"><is><t>Soal Tanpa Jawaban untuk uji verifikasi</t></is></c>
                      <c r="B3" t="inlineStr"><is><t>Opsi 1</t></is></c>
                      <c r="C3" t="inlineStr"><is><t>Opsi 2</t></is></c>
                    </row>
                  </sheetData>
                </worksheet>"""
            )
        }
        return out.toByteArray()
    }
}
