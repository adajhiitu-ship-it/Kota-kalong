package com.example.domain.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.QuestionEntity
import com.example.domain.model.BatchImportReport
import com.example.domain.model.DocumentParseResult
import com.example.domain.model.ImportFileReport
import com.example.domain.model.ImportProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

class DocumentImportService(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun importUris(
        uris: List<Uri>,
        preventDuplicates: Boolean = true,
        defaultCategory: String = "Umum",
        onProgress: (ImportProgress) -> Unit = {}
    ): BatchImportReport = withContext(Dispatchers.IO) {
        val existingNormalized = if (preventDuplicates) {
            database.questionDao().getAllNormalizedQuestions().toHashSet()
        } else {
            HashSet()
        }

        val fileReports = mutableListOf<ImportFileReport>()
        var totalImported = 0
        var totalDuplicates = 0
        var totalFailed = 0

        for ((index, uri) in uris.withIndex()) {
            val (displayName, sizeBytes) = resolveFileMeta(uri)
            onProgress(
                ImportProgress(
                    isImporting = true,
                    currentFileIndex = index + 1,
                    totalFiles = uris.size,
                    currentFileName = displayName,
                    statusMessage = "Membaca $displayName..."
                )
            )

            val parseResult = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    parseDocumentStream(
                        fileName = displayName,
                        inputStream = stream,
                        defaultCategory = defaultCategory
                    )
                } ?: DocumentParseResult(
                    fileName = displayName,
                    fileType = detectFileType(displayName),
                    parsedQuestions = emptyList(),
                    failedBlocks = 1,
                    errorMessage = "Tidak dapat membuka aliran file"
                )
            } catch (e: Exception) {
                DocumentParseResult(
                    fileName = displayName,
                    fileType = detectFileType(displayName),
                    parsedQuestions = emptyList(),
                    failedBlocks = 1,
                    errorMessage = e.localizedMessage ?: "Gagal memproses dokumen"
                )
            }

            if (parseResult.errorMessage != null && parseResult.parsedQuestions.isEmpty()) {
                totalFailed += parseResult.failedBlocks.coerceAtLeast(1)
                fileReports.add(
                    ImportFileReport(
                        fileName = displayName,
                        fileType = parseResult.fileType,
                        totalExtracted = 0,
                        importedCount = 0,
                        duplicateCount = 0,
                        failedCount = parseResult.failedBlocks.coerceAtLeast(1),
                        locationsSummary = "-",
                        errorMessage = parseResult.errorMessage
                    )
                )
                continue
            }

            onProgress(
                ImportProgress(
                    isImporting = true,
                    currentFileIndex = index + 1,
                    totalFiles = uris.size,
                    currentFileName = displayName,
                    statusMessage = "Menyimpan ${parseResult.parsedQuestions.size} soal dari $displayName..."
                )
            )

            val report = persistParsedDocument(
                parseResult = parseResult,
                fileSizeBytes = sizeBytes,
                preventDuplicates = preventDuplicates,
                existingNormalized = existingNormalized
            )
            totalImported += report.importedCount
            totalDuplicates += report.duplicateCount
            totalFailed += report.failedCount
            fileReports.add(report)
        }

        onProgress(ImportProgress(isImporting = false))
        BatchImportReport(
            totalFiles = uris.size,
            totalImported = totalImported,
            totalDuplicates = totalDuplicates,
            totalFailed = totalFailed,
            fileReports = fileReports
        )
    }

    suspend fun importStream(
        fileName: String,
        inputStream: InputStream,
        fileSizeBytes: Long = 0L,
        preventDuplicates: Boolean = true,
        defaultCategory: String = "Umum"
    ): ImportFileReport = withContext(Dispatchers.IO) {
        val existingNormalized = if (preventDuplicates) {
            database.questionDao().getAllNormalizedQuestions().toHashSet()
        } else {
            HashSet()
        }

        val parseResult = try {
            parseDocumentStream(
                fileName = fileName,
                inputStream = inputStream,
                defaultCategory = defaultCategory
            )
        } catch (e: Exception) {
            return@withContext ImportFileReport(
                fileName = fileName,
                fileType = detectFileType(fileName),
                totalExtracted = 0,
                importedCount = 0,
                duplicateCount = 0,
                failedCount = 1,
                locationsSummary = "-",
                errorMessage = e.localizedMessage ?: "Gagal memproses dokumen"
            )
        }

        if (parseResult.errorMessage != null && parseResult.parsedQuestions.isEmpty()) {
            return@withContext ImportFileReport(
                fileName = fileName,
                fileType = parseResult.fileType,
                totalExtracted = 0,
                importedCount = 0,
                duplicateCount = 0,
                failedCount = parseResult.failedBlocks.coerceAtLeast(1),
                locationsSummary = "-",
                errorMessage = parseResult.errorMessage
            )
        }

        persistParsedDocument(
            parseResult = parseResult,
            fileSizeBytes = fileSizeBytes,
            preventDuplicates = preventDuplicates,
            existingNormalized = existingNormalized
        )
    }

    fun parseDocumentStream(
        fileName: String,
        inputStream: InputStream,
        defaultCategory: String = "Umum"
    ): DocumentParseResult {
        val fileType = detectFileType(fileName)
        return when (fileType) {
            "XLSX" -> {
                val sheets = XlsxParser.parse(inputStream)
                parseExcelSheets(fileName, fileType, sheets, defaultCategory)
            }
            "XLS" -> {
                val sheets = XlsParser.parse(inputStream)
                parseExcelSheets(fileName, fileType, sheets, defaultCategory)
            }
            "DOCX" -> {
                val content = DocxParser.parse(inputStream)
                parseWordContent(fileName, fileType, content, defaultCategory)
            }
            "DOC" -> {
                val content = DocParser.parse(inputStream)
                parseWordContent(fileName, fileType, content, defaultCategory)
            }
            "PDF" -> {
                val pages = PdfParser.parsePages(context, inputStream)
                if (pages.isEmpty()) {
                    DocumentParseResult(
                        fileName = fileName,
                        fileType = fileType,
                        parsedQuestions = emptyList(),
                        failedBlocks = 1,
                        errorMessage = "PDF tidak berisi teks yang dapat diekstrak (mungkin hasil scan gambar)."
                    )
                } else {
                    val outcome = QuestionExtractor.extractFromTextPages(pages, defaultCategory)
                    DocumentParseResult(
                        fileName = fileName,
                        fileType = fileType,
                        parsedQuestions = outcome.questions,
                        failedBlocks = outcome.failedBlocks,
                        locationsProcessed = pages.map { it.first }
                    )
                }
            }
            else -> DocumentParseResult(
                fileName = fileName,
                fileType = fileType,
                parsedQuestions = emptyList(),
                failedBlocks = 1,
                errorMessage = "Format file '$fileType' tidak didukung. Gunakan PDF, XLSX, XLS, DOCX, atau DOC."
            )
        }
    }

    private fun parseExcelSheets(
        fileName: String,
        fileType: String,
        sheets: List<ExcelSheetData>,
        defaultCategory: String
    ): DocumentParseResult {
        val allQuestions = mutableListOf<com.example.domain.model.ParsedQuestion>()
        val locations = mutableListOf<String>()
        var totalFailed = 0

        for (sheet in sheets) {
            val location = "Sheet: ${sheet.sheetName}"
            val outcome = QuestionExtractor.extractFromTableRows(
                rows = sheet.rows,
                sourceLocation = location,
                defaultCategory = if (defaultCategory == "Umum" && sheet.sheetName.isNotBlank()) {
                    sheet.sheetName
                } else {
                    defaultCategory
                }
            )
            if (outcome.questions.isNotEmpty()) {
                locations.add(sheet.sheetName)
                allQuestions.addAll(outcome.questions)
            }
            totalFailed += outcome.failedBlocks
        }

        return DocumentParseResult(
            fileName = fileName,
            fileType = fileType,
            parsedQuestions = allQuestions,
            failedBlocks = totalFailed,
            locationsProcessed = locations,
            errorMessage = if (allQuestions.isEmpty()) "Tidak ada soal valid yang ditemukan pada sheet Excel." else null
        )
    }

    private fun parseWordContent(
        fileName: String,
        fileType: String,
        content: WordDocumentContent,
        defaultCategory: String
    ): DocumentParseResult {
        val allQuestions = mutableListOf<com.example.domain.model.ParsedQuestion>()
        var totalFailed = 0
        val locations = mutableListOf<String>()

        content.tables.forEachIndexed { idx, tableRows ->
            val loc = "Tabel ${idx + 1}"
            val outcome = QuestionExtractor.extractFromTableRows(
                rows = tableRows,
                sourceLocation = loc,
                defaultCategory = defaultCategory
            )
            if (outcome.questions.isNotEmpty()) {
                locations.add(loc)
                allQuestions.addAll(outcome.questions)
            }
            totalFailed += outcome.failedBlocks
        }

        if (content.paragraphsText.isNotBlank()) {
            val outcome = QuestionExtractor.extractFromTextBlocks(
                text = content.paragraphsText,
                sourceLocation = "Dokumen Word",
                defaultCategory = defaultCategory
            )
            if (outcome.questions.isNotEmpty()) {
                locations.add("Paragraf")
                allQuestions.addAll(outcome.questions)
            }
            totalFailed += outcome.failedBlocks
        }

        return DocumentParseResult(
            fileName = fileName,
            fileType = fileType,
            parsedQuestions = allQuestions,
            failedBlocks = totalFailed,
            locationsProcessed = locations,
            errorMessage = if (allQuestions.isEmpty()) "Tidak ada soal valid yang ditemukan di dokumen Word." else null
        )
    }

    private suspend fun persistParsedDocument(
        parseResult: DocumentParseResult,
        fileSizeBytes: Long,
        preventDuplicates: Boolean,
        existingNormalized: HashSet<String>
    ): ImportFileReport {
        val now = System.currentTimeMillis()
        val toInsert = mutableListOf<QuestionEntity>()
        var duplicateCount = 0
        var failedCount = parseResult.failedBlocks

        val documentId = database.documentDao().insertDocument(
            DocumentEntity(
                fileName = parseResult.fileName,
                fileType = parseResult.fileType,
                fileSizeBytes = fileSizeBytes,
                importedAt = now
            )
        )

        for (parsed in parseResult.parsedQuestions) {
            val cleanQ = parsed.questionText.trim()
            val normalized = TextNormalizer.normalizeForExact(cleanQ)
            if (cleanQ.isEmpty() || normalized.isEmpty()) {
                failedCount++
                continue
            }
            if (preventDuplicates && !existingNormalized.add(normalized)) {
                duplicateCount++
                continue
            }

            toInsert.add(
                QuestionEntity(
                    documentId = documentId,
                    sourceDocumentName = parseResult.fileName,
                    sourceLocation = parsed.sourceLocation,
                    questionText = cleanQ,
                    normalizedQuestion = normalized,
                    optionA = parsed.optionA.trim(),
                    optionB = parsed.optionB.trim(),
                    optionC = parsed.optionC.trim(),
                    optionD = parsed.optionD.trim(),
                    optionE = parsed.optionE.trim(),
                    correctAnswer = parsed.correctAnswer.trim(),
                    category = parsed.category.ifBlank { "Umum" },
                    isFavorite = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        if (toInsert.isNotEmpty()) {
            database.questionDao().insertQuestions(toInsert)
        }
        database.documentDao().updateImportCounts(
            id = documentId,
            importedCount = toInsert.size,
            duplicateCount = duplicateCount,
            failedCount = failedCount
        )

        val locSummary = when {
            parseResult.locationsProcessed.isEmpty() -> "-"
            parseResult.locationsProcessed.size <= 3 -> parseResult.locationsProcessed.joinToString(", ")
            else -> "${parseResult.locationsProcessed.take(3).joinToString(", ")} (+${parseResult.locationsProcessed.size - 3} lainnya)"
        }

        return ImportFileReport(
            fileName = parseResult.fileName,
            fileType = parseResult.fileType,
            totalExtracted = parseResult.parsedQuestions.size,
            importedCount = toInsert.size,
            duplicateCount = duplicateCount,
            failedCount = failedCount,
            locationsSummary = locSummary,
            errorMessage = parseResult.errorMessage
        )
    }

    private fun resolveFileMeta(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "dokumen"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    private fun detectFileType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "pdf" -> "PDF"
            "xlsx" -> "XLSX"
            "xls" -> "XLS"
            "docx" -> "DOCX"
            "doc" -> "DOC"
            else -> ext.uppercase(Locale.ROOT).ifEmpty { "LAINNYA" }
        }
    }
}
