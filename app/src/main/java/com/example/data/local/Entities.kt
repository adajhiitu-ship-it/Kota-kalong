package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val fileType: String,
    val fileSizeBytes: Long = 0L,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val failedCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["category"]),
        Index(value = ["isFavorite"]),
        Index(value = ["normalizedQuestion"])
    ]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val documentId: Long? = null,
    val sourceDocumentName: String,
    val sourceLocation: String = "",
    val questionText: String,
    val normalizedQuestion: String,
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val optionE: String = "",
    val correctAnswer: String = "",
    val category: String = "Umum",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val resultCount: Int,
    val topMatchType: String,
    val searchedAt: Long = System.currentTimeMillis()
)
