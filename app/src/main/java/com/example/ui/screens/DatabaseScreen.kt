package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryCountTuple
import com.example.data.local.DocumentEntity
import com.example.data.local.QuestionEntity
import com.example.ui.components.QuestionCard
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.PureWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DatabaseScreen(
    questions: List<QuestionEntity>,
    totalQuestionCount: Int,
    categories: List<CategoryCountTuple>,
    documents: List<DocumentEntity>,
    filterQuery: String,
    selectedCategory: String?,
    selectedDocumentName: String?,
    pendingRestoreUri: Uri?,
    onFilterQueryChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectDocument: (String?) -> Unit,
    onAddManualQuestion: () -> Unit,
    onEditQuestion: (QuestionEntity) -> Unit,
    onDeleteQuestion: (QuestionEntity) -> Unit,
    onToggleFavorite: (QuestionEntity) -> Unit,
    onBackupToUri: (Uri) -> Unit,
    onRequestRestoreUri: (Uri) -> Unit,
    onConfirmRestore: (Boolean) -> Unit,
    onDismissRestore: () -> Unit
) {
    var questionPendingDelete by remember { mutableStateOf<QuestionEntity?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onBackupToUri(uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRequestRestoreUri(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddManualQuestion,
                containerColor = ForestGreen,
                contentColor = PureWhite,
                modifier = Modifier.testTag("db_add_question_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah soal manual")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Backup/Restore Actions
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ForestGreen
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Database Soal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Text(
                            text = "Menampilkan ${questions.size} dari $totalQuestionCount soal tersimpan",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    backupLauncher.launch("kota_kalong_backup_$stamp.json")
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("db_backup_button")
                            ) {
                                Icon(Icons.Filled.Backup, contentDescription = null, tint = PureWhite)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup", color = PureWhite)
                            }
                            OutlinedButton(
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("db_restore_button")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, tint = PureWhite)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore", color = PureWhite)
                            }
                        }
                    }
                }
            }

            // Filter Search + Category + Document Chips
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = onFilterQueryChange,
                        placeholder = { Text("Saring soal, jawaban, kategori, atau dokumen...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filterQuery.isNotEmpty()) {
                                IconButton(onClick = { onFilterQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Bersihkan filter")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("db_filter_input")
                    )

                    if (categories.isNotEmpty()) {
                        Text(
                            text = "Kelompok Kategori",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { onSelectCategory(null) },
                                label = { Text("Semua ($totalQuestionCount)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = PureWhite
                                )
                            )
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory.equals(cat.category, ignoreCase = true),
                                    onClick = {
                                        onSelectCategory(
                                            if (selectedCategory.equals(cat.category, ignoreCase = true)) null else cat.category
                                        )
                                    },
                                    label = { Text("${cat.category} (${cat.total})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }
                    }

                    if (documents.isNotEmpty()) {
                        Text(
                            text = "Filter Dokumen Sumber",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedDocumentName == null,
                                onClick = { onSelectDocument(null) },
                                label = { Text("Semua Dokumen") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = PureWhite
                                )
                            )
                            documents.forEach { doc ->
                                FilterChip(
                                    selected = selectedDocumentName == doc.fileName,
                                    onClick = {
                                        onSelectDocument(if (selectedDocumentName == doc.fileName) null else doc.fileName)
                                    },
                                    label = { Text(doc.fileName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (questions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Belum ada soal yang sesuai",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tekan tombol + di kanan bawah untuk menambahkan soal manual, atau impor dokumen dari menu Upload.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(questions, key = { it.id }) { question ->
                    QuestionCard(
                        question = question,
                        onToggleFavorite = { onToggleFavorite(question) },
                        onEdit = { onEditQuestion(question) },
                        onDelete = { questionPendingDelete = question },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    questionPendingDelete?.let { q ->
        AlertDialog(
            onDismissRequest = { questionPendingDelete = null },
            title = { Text("Hapus Soal?") },
            text = { Text("Soal ini akan dihapus permanen dari database lokal.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteQuestion(q)
                    questionPendingDelete = null
                }) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { questionPendingDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text("Pilih Mode Restore") },
            text = {
                Text("Apakah Anda ingin menggabungkan data backup dengan database saat ini (duplikat dilewati), atau menimpa seluruh database yang ada?")
            },
            confirmButton = {
                Button(onClick = { onConfirmRestore(false) }) {
                    Text("Gabungkan")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismissRestore) {
                        Text("Batal")
                    }
                    TextButton(onClick = { onConfirmRestore(true) }) {
                        Text("Timpa Semua", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}
