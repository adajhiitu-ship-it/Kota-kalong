package com.example.domain.model

import com.example.data.local.QuestionEntity

enum class SearchTier(val label: String, val priority: Int) {
    EXACT_MATCH("Sama Persis", 0),
    SIMILARITY("Kemiripan Makna", 1),
    KEYWORD("Kata Kunci", 2)
}

data class SearchResultItem(
    val question: QuestionEntity,
    val tier: SearchTier,
    val matchScore: Double,
    val matchPercentage: Int
)

data class ParsedQuestion(
    val questionText: String,
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val optionE: String = "",
    val correctAnswer: String = "",
    val category: String = "Umum",
    val sourceLocation: String = ""
)

data class DocumentParseResult(
    val fileName: String,
    val fileType: String,
    val parsedQuestions: List<ParsedQuestion>,
    val failedBlocks: Int = 0,
    val locationsProcessed: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class ImportFileReport(
    val fileName: String,
    val fileType: String,
    val totalExtracted: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val locationsSummary: String,
    val errorMessage: String? = null
)

data class BatchImportReport(
    val totalFiles: Int,
    val totalImported: Int,
    val totalDuplicates: Int,
    val totalFailed: Int,
    val fileReports: List<ImportFileReport>
)

data class ImportProgress(
    val isImporting: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val statusMessage: String = ""
)

data class RestoreReport(
    val importedQuestions: Int,
    val skippedDuplicates: Int,
    val importedDocuments: Int,
    val replacedExisting: Boolean
)
