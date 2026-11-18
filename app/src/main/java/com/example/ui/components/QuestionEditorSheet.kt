package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.QuestionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorSheet(
    editingQuestion: QuestionEntity?,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var questionText by remember(editingQuestion) { mutableStateOf(editingQuestion?.questionText.orEmpty()) }
    var optionA by remember(editingQuestion) { mutableStateOf(editingQuestion?.optionA.orEmpty()) }
    var optionB by remember(editingQuestion) { mutableStateOf(editingQuestion?.optionB.orEmpty()) }
    var optionC by remember(editingQuestion) { mutableStateOf(editingQuestion?.optionC.orEmpty()) }
    var optionD by remember(editingQuestion) { mutableStateOf(editingQuestion?.optionD.orEmpty()) }
    var optionE by remember(editingQuestion) { mutableStateOf(editingQuestion?.optionE.orEmpty()) }
    var correctAnswer by remember(editingQuestion) { mutableStateOf(editingQuestion?.correctAnswer.orEmpty()) }
    var category by remember(editingQuestion) { mutableStateOf(editingQuestion?.category?.ifBlank { "Umum" } ?: "Umum") }
    var sourceDocumentName by remember(editingQuestion) {
        mutableStateOf(editingQuestion?.sourceDocumentName?.ifBlank { "Input Manual" } ?: "Input Manual")
    }
    var sourceLocation by remember(editingQuestion) {
        mutableStateOf(editingQuestion?.sourceLocation?.ifBlank { "Manual" } ?: "Manual")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingQuestion == null) "Tambah Soal Manual" else "Edit Soal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = { Text("Pertanyaan *") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_question_input")
            )

            OutlinedTextField(
                value = optionA,
                onValueChange = { optionA = it },
                label = { Text("Pilihan A (opsional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = optionB,
                onValueChange = { optionB = it },
                label = { Text("Pilihan B (opsional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = optionC,
                onValueChange = { optionC = it },
                label = { Text("Pilihan C (opsional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = optionD,
                onValueChange = { optionD = it },
                label = { Text("Pilihan D (opsional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = optionE,
                onValueChange = { optionE = it },
                label = { Text("Pilihan E (opsional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = correctAnswer,
                onValueChange = { correctAnswer = it },
                label = { Text("Kunci Jawaban (kosongkan jika belum tersedia)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_answer_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = sourceLocation,
                    onValueChange = { sourceLocation = it },
                    label = { Text("Sheet / Halaman") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = sourceDocumentName,
                onValueChange = { sourceDocumentName = it },
                label = { Text("Nama Dokumen Sumber") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        onSave(
                            questionText,
                            optionA,
                            optionB,
                            optionC,
                            optionD,
                            optionE,
                            correctAnswer,
                            category,
                            sourceDocumentName,
                            sourceLocation
                        )
                    },
                    enabled = questionText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_save_button")
                ) {
                    Text("Simpan")
                }
            }
        }
    }
}
