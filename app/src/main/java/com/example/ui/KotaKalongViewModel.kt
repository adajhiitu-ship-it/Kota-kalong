package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.KotaKalongApp
import com.example.data.local.CategoryCountTuple
import com.example.data.local.DocumentEntity
import com.example.data.local.QuestionEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.QuestionBankRepository
import com.example.domain.model.BatchImportReport
import com.example.domain.model.ImportProgress
import com.example.domain.model.RestoreReport
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SearchTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KotaKalongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: QuestionBankRepository = (application as KotaKalongApp).repository

    val allQuestions: StateFlow<List<QuestionEntity>> = repository.allQuestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteQuestions: StateFlow<List<QuestionEntity>> = repository.favoriteQuestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionCount: StateFlow<Int> = repository.questionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val documentCount: StateFlow<Int> = repository.documentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteCount: StateFlow<Int> = repository.favoriteCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val categoryCounts: StateFlow<List<CategoryCountTuple>> = repository.categoryCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTierFilter = MutableStateFlow<SearchTier?>(null)
    val selectedTierFilter: StateFlow<SearchTier?> = _selectedTierFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Upload / Import state
    private val _preventDuplicates = MutableStateFlow(true)
    val preventDuplicates: StateFlow<Boolean> = _preventDuplicates.asStateFlow()

    private val _defaultUploadCategory = MutableStateFlow("Umum")
    val defaultUploadCategory: StateFlow<String> = _defaultUploadCategory.asStateFlow()

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    private val _lastImportReport = MutableStateFlow<BatchImportReport?>(null)
    val lastImportReport: StateFlow<BatchImportReport?> = _lastImportReport.asStateFlow()

    // Database screen filters
    private val _dbFilterQuery = MutableStateFlow("")
    val dbFilterQuery: StateFlow<String> = _dbFilterQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedDocumentFilter = MutableStateFlow<String?>(null)
    val selectedDocumentFilter: StateFlow<String?> = _selectedDocumentFilter.asStateFlow()

    val filteredDatabaseQuestions: StateFlow<List<QuestionEntity>> = combine(
        allQuestions,
        dbFilterQuery,
        selectedCategory,
        selectedDocumentFilter
    ) { questions, query, category, docName ->
        val qLower = query.trim().lowercase()
        questions.filter { item ->
            val matchCat = category == null || item.category.equals(category, ignoreCase = true)
            val matchDoc = docName == null || item.sourceDocumentName == docName
            val matchQuery = qLower.isEmpty() ||
                item.questionText.lowercase().contains(qLower) ||
                item.correctAnswer.lowercase().contains(qLower) ||
                item.category.lowercase().contains(qLower) ||
                item.sourceDocumentName.lowercase().contains(qLower)
            matchCat && matchDoc && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Status message for snackbar feedback
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingRestoreUri = MutableStateFlow<Uri?>(null)
    val pendingRestoreUri: StateFlow<Uri?> = _pendingRestoreUri.asStateFlow()

    private var searchDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeSeedIfAvailable()
        }
        viewModelScope.launch {
            allQuestions.collect { questions ->
                val currentQuery = _searchQuery.value
                if (currentQuery.isNotBlank()) {
                    executeSearchInternal(
                        query = currentQuery,
                        snapshot = questions,
                        tier = _selectedTierFilter.value,
                        recordHistory = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(180)
            executeSearchInternal(
                query = query,
                snapshot = allQuestions.value,
                tier = _selectedTierFilter.value,
                recordHistory = false
            )
        }
    }

    fun submitSearch(query: String = _searchQuery.value) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            executeSearchInternal(
                query = query,
                snapshot = allQuestions.value,
                tier = _selectedTierFilter.value,
                recordHistory = true
            )
        }
    }

    fun setTierFilter(tier: SearchTier?) {
        _selectedTierFilter.value = tier
        val q = _searchQuery.value
        if (q.isNotBlank()) {
            viewModelScope.launch {
                executeSearchInternal(
                    query = q,
                    snapshot = allQuestions.value,
                    tier = tier,
                    recordHistory = false
                )
            }
        }
    }

    fun clearSearch() {
        searchDebounceJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    private suspend fun executeSearchInternal(
        query: String,
        snapshot: List<QuestionEntity>,
        tier: SearchTier?,
        recordHistory: Boolean
    ) {
        _isSearching.value = true
        val results = repository.searchOffline(
            query = query,
            questionsSnapshot = snapshot,
            tierFilter = tier,
            recordHistory = recordHistory
        )
        _searchResults.value = results
        _isSearching.value = false
    }

    fun setPreventDuplicates(enabled: Boolean) {
        _preventDuplicates.value = enabled
    }

    fun setDefaultUploadCategory(category: String) {
        _defaultUploadCategory.value = category
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty() || _importProgress.value.isImporting) return
        viewModelScope.launch {
            val report = repository.importDocuments(
                uris = uris,
                preventDuplicates = _preventDuplicates.value,
                defaultCategory = _defaultUploadCategory.value.ifBlank { "Umum" },
                onProgress = { progress -> _importProgress.value = progress }
            )
            _lastImportReport.value = report
            _statusMessage.value = "Impor selesai: ${report.totalImported} berhasil, ${report.totalDuplicates} duplikat, ${report.totalFailed} gagal."
        }
    }

    fun clearImportReport() {
        _lastImportReport.value = null
    }

    fun setDbFilterQuery(query: String) {
        _dbFilterQuery.value = query
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSelectedDocumentFilter(docName: String?) {
        _selectedDocumentFilter.value = docName
    }

    fun toggleFavorite(question: QuestionEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(question)
        }
    }

    fun saveManualQuestion(
        editingQuestion: QuestionEntity?,
        questionText: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        optionE: String,
        correctAnswer: String,
        category: String,
        sourceDocumentName: String,
        sourceLocation: String
    ) {
        if (questionText.isBlank()) {
            _statusMessage.value = "Pertanyaan tidak boleh kosong."
            return
        }
        viewModelScope.launch {
            repository.saveManualQuestion(
                id = editingQuestion?.id ?: 0L,
                questionText = questionText,
                optionA = optionA,
                optionB = optionB,
                optionC = optionC,
                optionD = optionD,
                optionE = optionE,
                correctAnswer = correctAnswer,
                category = category,
                sourceDocumentName = sourceDocumentName,
                sourceLocation = sourceLocation,
                existingCreatedAt = editingQuestion?.createdAt,
                existingIsFavorite = editingQuestion?.isFavorite ?: false,
                existingDocumentId = editingQuestion?.documentId
            )
            _statusMessage.value = if (editingQuestion == null) {
                "Soal baru berhasil disimpan ke database."
            } else {
                "Perubahan soal berhasil disimpan."
            }
        }
    }

    fun deleteQuestion(question: QuestionEntity) {
        viewModelScope.launch {
            repository.deleteQuestion(question.id)
            _statusMessage.value = "Soal dihapus dari database."
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocumentAndQuestions(document.id)
            _statusMessage.value = "Dokumen ${document.fileName} dan soal terkait telah dihapus."
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportBackup(uri)
            }.onSuccess { count ->
                _statusMessage.value = "Backup berhasil disimpan ($count soal)."
            }.onFailure { err ->
                _statusMessage.value = "Gagal membuat backup: ${err.localizedMessage}"
            }
        }
    }

    fun requestRestoreFromUri(uri: Uri) {
        _pendingRestoreUri.value = uri
    }

    fun dismissRestoreDialog() {
        _pendingRestoreUri.value = null
    }

    fun confirmRestore(replaceExisting: Boolean) {
        val uri = _pendingRestoreUri.value ?: return
        _pendingRestoreUri.value = null
        viewModelScope.launch {
            runCatching {
                repository.restoreBackup(uri, replaceExisting)
            }.onSuccess { report: RestoreReport ->
                _statusMessage.value = "Restore selesai: ${report.importedQuestions} soal dipulihkan" +
                    if (report.skippedDuplicates > 0) " (${report.skippedDuplicates} duplikat dilewati)." else "."
            }.onFailure { err ->
                _statusMessage.value = "Gagal memulihkan backup: ${err.localizedMessage}"
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _statusMessage.value = "Seluruh riwayat pencarian telah dihapus."
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
