package com.example.domain.parser

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.QuestionEntity
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.RestoreReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun exportToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val json = exportToJsonString()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("Gagal membuka file tujuan backup")
        database.questionDao().getAllQuestionsOnce().size
    }

    suspend fun exportToJsonString(): String = withContext(Dispatchers.IO) {
        val documents = database.documentDao().getAllDocumentsOnce()
        val questions = database.questionDao().getAllQuestionsOnce()
        val history = database.searchHistoryDao().getAllHistoryOnce()

        val root = JSONObject().apply {
            put("app", "KOTA KALONG FT")
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())

            put("documents", JSONArray().apply {
                for (doc in documents) {
                    put(JSONObject().apply {
                        put("id", doc.id)
                        put("fileName", doc.fileName)
                        put("fileType", doc.fileType)
                        put("fileSizeBytes", doc.fileSizeBytes)
                        put("importedCount", doc.importedCount)
                        put("duplicateCount", doc.duplicateCount)
                        put("failedCount", doc.failedCount)
                        put("importedAt", doc.importedAt)
                    })
                }
            })

            put("questions", JSONArray().apply {
                for (q in questions) {
                    put(JSONObject().apply {
                        put("documentId", q.documentId ?: JSONObject.NULL)
                        put("sourceDocumentName", q.sourceDocumentName)
                        put("sourceLocation", q.sourceLocation)
                        put("questionText", q.questionText)
                        put("optionA", q.optionA)
                        put("optionB", q.optionB)
                        put("optionC", q.optionC)
                        put("optionD", q.optionD)
                        put("optionE", q.optionE)
                        put("correctAnswer", q.correctAnswer)
                        put("category", q.category)
                        put("isFavorite", q.isFavorite)
                        put("createdAt", q.createdAt)
                        put("updatedAt", q.updatedAt)
                    })
                }
            })

            put("searchHistory", JSONArray().apply {
                for (h in history) {
                    put(JSONObject().apply {
                        put("query", h.query)
                        put("resultCount", h.resultCount)
                        put("topMatchType", h.topMatchType)
                        put("searchedAt", h.searchedAt)
                    })
                }
            })
        }
        root.toString(2)
    }

    suspend fun restoreFromUri(uri: Uri, replaceExisting: Boolean): RestoreReport = withContext(Dispatchers.IO) {
        val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: error("Tidak dapat membaca file backup")
        restoreFromJsonString(jsonText, replaceExisting)
    }

    suspend fun restoreFromJsonString(
        jsonText: String,
        replaceExisting: Boolean
    ): RestoreReport = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonText)
        val docsArray = root.optJSONArray("documents") ?: JSONArray()
        val questionsArray = root.optJSONArray("questions") ?: JSONArray()
        val historyArray = root.optJSONArray("searchHistory") ?: JSONArray()

        var importedQuestions = 0
        var skippedDuplicates = 0
        var importedDocuments = 0

        database.withTransaction {
            if (replaceExisting) {
                database.questionDao().deleteAllQuestions()
                database.documentDao().deleteAllDocuments()
                database.searchHistoryDao().clearAllHistory()
            }

            val existingNorms = if (replaceExisting) {
                HashSet()
            } else {
                database.questionDao().getAllNormalizedQuestions().toHashSet()
            }

            val oldDocIdToNewId = HashMap<Long, Long>()
            for (i in 0 until docsArray.length()) {
                val obj = docsArray.getJSONObject(i)
                val oldId = obj.optLong("id", -1L)
                val newId = database.documentDao().insertDocument(
                    DocumentEntity(
                        fileName = obj.optString("fileName", "Backup"),
                        fileType = obj.optString("fileType", "BACKUP"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        importedCount = obj.optInt("importedCount", 0),
                        duplicateCount = obj.optInt("duplicateCount", 0),
                        failedCount = obj.optInt("failedCount", 0),
                        importedAt = obj.optLong("importedAt", System.currentTimeMillis())
                    )
                )
                if (oldId != -1L) oldDocIdToNewId[oldId] = newId
                importedDocuments++
            }

            val questionsToInsert = mutableListOf<QuestionEntity>()
            for (i in 0 until questionsArray.length()) {
                val obj = questionsArray.getJSONObject(i)
                val qText = obj.optString("questionText", "").trim()
                if (qText.isEmpty()) continue
                val norm = TextNormalizer.normalizeForExact(qText)
                if (!replaceExisting && !existingNorms.add(norm)) {
                    skippedDuplicates++
                    continue
                }
                val oldDocId = if (obj.isNull("documentId")) null else obj.optLong("documentId", -1L)
                val mappedDocId = if (oldDocId != null) oldDocIdToNewId[oldDocId] else null

                questionsToInsert.add(
                    QuestionEntity(
                        documentId = mappedDocId,
                        sourceDocumentName = obj.optString("sourceDocumentName", "Backup"),
                        sourceLocation = obj.optString("sourceLocation", ""),
                        questionText = qText,
                        normalizedQuestion = norm,
                        optionA = obj.optString("optionA", ""),
                        optionB = obj.optString("optionB", ""),
                        optionC = obj.optString("optionC", ""),
                        optionD = obj.optString("optionD", ""),
                        optionE = obj.optString("optionE", ""),
                        correctAnswer = obj.optString("correctAnswer", ""),
                        category = obj.optString("category", "Umum").ifBlank { "Umum" },
                        isFavorite = obj.optBoolean("isFavorite", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            if (questionsToInsert.isNotEmpty()) {
                database.questionDao().insertQuestions(questionsToInsert)
                importedQuestions = questionsToInsert.size
            }

            val historyToInsert = mutableListOf<SearchHistoryEntity>()
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.getJSONObject(i)
                val q = obj.optString("query", "").trim()
                if (q.isNotEmpty()) {
                    historyToInsert.add(
                        SearchHistoryEntity(
                            query = q,
                            resultCount = obj.optInt("resultCount", 0),
                            topMatchType = obj.optString("topMatchType", ""),
                            searchedAt = obj.optLong("searchedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            if (historyToInsert.isNotEmpty()) {
                database.searchHistoryDao().insertAllHistory(historyToInsert)
            }
        }

        RestoreReport(
            importedQuestions = importedQuestions,
            skippedDuplicates = skippedDuplicates,
            importedDocuments = importedDocuments,
            replacedExisting = replaceExisting
        )
    }

    /**
     * Checks assets/initial_db/ for any bundled reference Excel file on first launch.
     * Imports all entries preserving original wording without altering or dropping duplicates.
     */
    suspend fun seedBundledInitialExcelIfPresent(importService: DocumentImportService) {
        withContext(Dispatchers.IO) {
            val existingDocs = database.documentDao().getAllDocumentsOnce()
            val existingQuestions = database.questionDao().getAllQuestionsOnce()
            if (existingDocs.isNotEmpty() || existingQuestions.isNotEmpty()) return@withContext

            val assetFiles = runCatching {
                context.assets.list("initial_db")?.toList().orEmpty()
            }.getOrDefault(emptyList())

            for (assetName in assetFiles) {
                val lower = assetName.lowercase()
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                    runCatching {
                        context.assets.open("initial_db/$assetName").use { stream ->
                            importService.importStream(
                                fileName = assetName,
                                inputStream = stream,
                                preventDuplicates = false
                            )
                        }
                    }
                }
            }
        }
    }
}
