package com.example.data.repository

import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryCountTuple
import com.example.data.local.DocumentEntity
import com.example.data.local.QuestionEntity
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.BatchImportReport
import com.example.domain.model.ImportProgress
import com.example.domain.model.RestoreReport
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SearchTier
import com.example.domain.parser.BackupManager
import com.example.domain.parser.DocumentImportService
import com.example.domain.parser.TextNormalizer
import com.example.domain.search.OfflineSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class QuestionBankRepository(
    private val database: AppDatabase,
    private val importService: DocumentImportService,
    private val backupManager: BackupManager,
    private val searchEngine: OfflineSearchEngine = OfflineSearchEngine()
) {
    private val questionDao = database.questionDao()
    private val documentDao = database.documentDao()
    private val historyDao = database.searchHistoryDao()

    val allQuestions: Flow<List<QuestionEntity>> = questionDao.observeAllQuestions()
    val favoriteQuestions: Flow<List<QuestionEntity>> = questionDao.observeFavoriteQuestions()
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()
    val questionCount: Flow<Int> = questionDao.observeQuestionCount()
    val documentCount: Flow<Int> = documentDao.observeDocumentCount()
    val favoriteCount: Flow<Int> = questionDao.observeFavoriteCount()
    val categoryCounts: Flow<List<CategoryCountTuple>> = questionDao.observeCategoryCounts()
    val searchHistory: Flow<List<SearchHistoryEntity>> = historyDao.observeRecentHistory()

    suspend fun initializeSeedIfAvailable() {
        backupManager.seedBundledInitialExcelIfPresent(importService)
        searchEngine.invalidate()
    }

    suspend fun searchOffline(
        query: String,
        questionsSnapshot: List<QuestionEntity>,
        tierFilter: SearchTier? = null,
        recordHistory: Boolean = false
    ): List<SearchResultItem> = withContext(Dispatchers.Default) {
        val results = searchEngine.search(
            query = query,
            questions = questionsSnapshot,
            tierFilter = tierFilter
        )
        if (recordHistory && query.trim().length >= 2) {
            val topTier = results.firstOrNull()?.tier?.label ?: "Tidak ada hasil"
            historyDao.insertHistory(
                SearchHistoryEntity(
                    query = query.trim(),
                    resultCount = results.size,
                    topMatchType = topTier
                )
            )
        }
        results
    }

    suspend fun importDocuments(
        uris: List<Uri>,
        preventDuplicates: Boolean,
        defaultCategory: String = "Umum",
        onProgress: (ImportProgress) -> Unit
    ): BatchImportReport {
        val report = importService.importUris(
            uris = uris,
            preventDuplicates = preventDuplicates,
            defaultCategory = defaultCategory,
            onProgress = onProgress
        )
        searchEngine.invalidate()
        return report
    }

    suspend fun saveManualQuestion(
        id: Long = 0L,
        questionText: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        optionE: String,
        correctAnswer: String,
        category: String,
        sourceDocumentName: String,
        sourceLocation: String,
        existingCreatedAt: Long? = null,
        existingIsFavorite: Boolean = false,
        existingDocumentId: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val trimmedQ = questionText.trim()
        val entity = QuestionEntity(
            id = id,
            documentId = existingDocumentId,
            sourceDocumentName = sourceDocumentName.trim().ifBlank { "Input Manual" },
            sourceLocation = sourceLocation.trim().ifBlank { "Manual" },
            questionText = trimmedQ,
            normalizedQuestion = TextNormalizer.normalizeForExact(trimmedQ),
            optionA = optionA.trim(),
            optionB = optionB.trim(),
            optionC = optionC.trim(),
            optionD = optionD.trim(),
            optionE = optionE.trim(),
            correctAnswer = correctAnswer.trim(),
            category = category.trim().ifBlank { "Umum" },
            isFavorite = existingIsFavorite,
            createdAt = existingCreatedAt ?: now,
            updatedAt = now
        )
        if (id == 0L) {
            questionDao.insertQuestion(entity)
        } else {
            questionDao.updateQuestion(entity)
        }
        searchEngine.invalidate()
    }

    suspend fun toggleFavorite(question: QuestionEntity) {
        questionDao.setFavorite(question.id, !question.isFavorite)
    }

    suspend fun deleteQuestion(questionId: Long) {
        questionDao.deleteQuestionById(questionId)
        searchEngine.invalidate()
    }

    suspend fun deleteDocumentAndQuestions(documentId: Long) {
        questionDao.deleteQuestionsByDocumentId(documentId)
        documentDao.deleteDocumentById(documentId)
        searchEngine.invalidate()
    }

    suspend fun exportBackup(uri: Uri): Int = backupManager.exportToUri(uri)

    suspend fun restoreBackup(uri: Uri, replaceExisting: Boolean): RestoreReport {
        val report = backupManager.restoreFromUri(uri, replaceExisting)
        searchEngine.invalidate()
        return report
    }

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteHistoryById(id)

    suspend fun clearHistory() = historyDao.clearAllHistory()
}
