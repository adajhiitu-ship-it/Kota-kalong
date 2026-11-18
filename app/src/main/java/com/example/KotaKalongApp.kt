package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.QuestionBankRepository
import com.example.domain.parser.BackupManager
import com.example.domain.parser.DocumentImportService
import com.example.domain.parser.PdfParser

class KotaKalongApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: QuestionBankRepository by lazy {
        QuestionBankRepository(
            database = database,
            importService = DocumentImportService(this, database),
            backupManager = BackupManager(this, database)
        )
    }

    override fun onCreate() {
        super.onCreate()
        PdfParser.ensureInitialized(this)
    }
}
