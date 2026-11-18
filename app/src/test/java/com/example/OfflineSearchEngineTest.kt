package com.example

import com.example.data.local.QuestionEntity
import com.example.domain.model.SearchTier
import com.example.domain.parser.TextNormalizer
import com.example.domain.search.OfflineSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSearchEngineTest {

    private fun makeQuestion(
        id: Long,
        text: String,
        answer: String = "A",
        optA: String = "",
        category: String = "Umum",
        doc: String = "bank_soal.xlsx"
    ): QuestionEntity {
        return QuestionEntity(
            id = id,
            sourceDocumentName = doc,
            sourceLocation = "Sheet: Utama",
            questionText = text,
            normalizedQuestion = TextNormalizer.normalizeForExact(text),
            optionA = optA,
            correctAnswer = answer,
            category = category
        )
    }

    @Test
    fun `three-tier search orders Exact Match then Similarity then Keyword`() {
        val engine = OfflineSearchEngine()
        val questions = listOf(
            // Keyword match inside option/question
            makeQuestion(
                id = 1L,
                text = "Sebutkan nama pulau terbesar yang berada di sebelah timur Kalimantan",
                optA = "Ibu kota Nusantara berada di Kalimantan",
                answer = "Papua"
            ),
            // Paraphrased similarity match (different wording/order)
            makeQuestion(
                id = 2L,
                text = "Kota manakah yang menjadi pusat pemerintahan negara Indonesia?",
                answer = "Jakarta"
            ),
            // Exact match
            makeQuestion(
                id = 3L,
                text = "Apa ibu kota negara Indonesia?",
                answer = "Jakarta"
            )
        )

        val results = engine.search("Apa ibu kota negara Indonesia?", questions)
        assertTrue(results.isNotEmpty())
        assertEquals(SearchTier.EXACT_MATCH, results[0].tier)
        assertEquals(3L, results[0].question.id)
        assertEquals(100, results[0].matchPercentage)

        val simIndex = results.indexOfFirst { it.question.id == 2L }
        assertTrue("Expected paraphrased question to match in results", simIndex > 0)
        assertEquals(SearchTier.SIMILARITY, results[simIndex].tier)

        // Verify strict ordering: all EXACT_MATCH <= SIMILARITY <= KEYWORD
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].tier.priority <= results[i + 1].tier.priority)
        }
    }

    @Test
    fun `similarity search finds reordered and paraphrased questions offline`() {
        val engine = OfflineSearchEngine()
        val questions = listOf(
            makeQuestion(
                id = 10L,
                text = "Proses pembuatan makanan pada tumbuhan hijau menggunakan sinar matahari disebut apa?",
                answer = "Fotosintesis"
            ),
            makeQuestion(
                id = 11L,
                text = "Berapa jumlah provinsi di Indonesia?",
                answer = "38"
            )
        )

        val results = engine.search("tumbuhan hijau membuat makanan dengan cahaya matahari", questions)
        assertTrue(results.isNotEmpty())
        assertEquals(10L, results.first().question.id)
        assertEquals(SearchTier.SIMILARITY, results.first().tier)
        assertEquals("Fotosintesis", results.first().question.correctAnswer)
    }

    @Test
    fun `search scales cleanly over 2500 questions offline`() {
        val engine = OfflineSearchEngine()
        val bulk = (1..2500).map { idx ->
            makeQuestion(
                id = idx.toLong(),
                text = "Pertanyaan uji nomor $idx mengenai struktur sel jaringan sistem organ tubuh manusia bagian $idx",
                answer = "Opsi $idx"
            )
        }
        val start = System.currentTimeMillis()
        val results = engine.search("struktur sel jaringan manusia", bulk)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(results.isNotEmpty())
        assertTrue("Search took too long: ${elapsed}ms", elapsed < 2000L)
    }
}
