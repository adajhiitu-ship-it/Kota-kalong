package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CategoryCountTuple(
    val category: String,
    val total: Int
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    suspend fun getAllDocumentsOnce(): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents")
    fun observeDocumentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("UPDATE documents SET importedCount = :importedCount, duplicateCount = :duplicateCount, failedCount = :failedCount WHERE id = :id")
    suspend fun updateImportCounts(id: Long, importedCount: Int, duplicateCount: Int, failedCount: Int)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: Long)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY id DESC")
    fun observeAllQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY id DESC")
    suspend fun getAllQuestionsOnce(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavoriteQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions")
    fun observeQuestionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE isFavorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT category, COUNT(*) AS total FROM questions GROUP BY category ORDER BY total DESC, category ASC")
    fun observeCategoryCounts(): Flow<List<CategoryCountTuple>>

    @Query("SELECT normalizedQuestion FROM questions")
    suspend fun getAllNormalizedQuestions(): List<String>

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: Long): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>): List<Long>

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Query("UPDATE questions SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE questions SET category = :category, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long)

    @Query("DELETE FROM questions WHERE documentId = :documentId")
    suspend fun deleteQuestionsByDocumentId(documentId: Long)

    @Query("DELETE FROM questions")
    suspend fun deleteAllQuestions()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 100")
    fun observeRecentHistory(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC")
    suspend fun getAllHistoryOnce(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: SearchHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllHistory(entries: List<SearchHistoryEntity>)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()
}
