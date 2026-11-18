package com.example.domain.search

import com.example.data.local.QuestionEntity
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SearchTier
import com.example.domain.parser.TextNormalizer
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

class OfflineSearchEngine {

    private class IndexedQuestion(
        val entity: QuestionEntity,
        val simTokens: Set<String>,
        val tfIdfVector: Map<String, Double>,
        val vectorNorm: Double,
        val charGrams: Set<String>,
        val searchableNormalized: String,
        val questionNormalized: String
    )

    @Volatile
    private var cachedSignature: Long = -1L

    @Volatile
    private var cachedIndex: List<IndexedQuestion> = emptyList()

    @Volatile
    private var cachedIdf: Map<String, Double> = emptyMap()

    @Volatile
    private var invertedIndex: Map<String, List<Int>> = emptyMap()

    @Synchronized
    private fun ensureIndex(questions: List<QuestionEntity>) {
        val signature = computeSignature(questions)
        if (signature == cachedSignature && cachedIndex.size == questions.size) {
            return
        }

        val docCount = questions.size
        val docFreq = HashMap<String, Int>()
        val tokenizedDocs = ArrayList<List<String>>(docCount)

        for (q in questions) {
            val tokens = TextNormalizer.tokenizeForSimilarity(q.questionText)
            tokenizedDocs.add(tokens)
            for (term in tokens.distinct()) {
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
        }

        val idfMap = HashMap<String, Double>(docFreq.size)
        for ((term, df) in docFreq) {
            idfMap[term] = ln((docCount + 1.0) / (df + 1.0)) + 1.0
        }

        val indexedList = ArrayList<IndexedQuestion>(docCount)
        val inv = HashMap<String, MutableList<Int>>()

        for (idx in questions.indices) {
            val q = questions[idx]
            val tokens = tokenizedDocs[idx]
            val tfCounts = HashMap<String, Int>()
            for (t in tokens) {
                tfCounts[t] = (tfCounts[t] ?: 0) + 1
            }
            val totalLen = tokens.size.coerceAtLeast(1).toDouble()
            val vec = HashMap<String, Double>(tfCounts.size)
            var sumSq = 0.0
            for ((term, count) in tfCounts) {
                val tf = count / totalLen
                val weight = tf * (idfMap[term] ?: 1.0)
                vec[term] = weight
                sumSq += weight * weight
                inv.getOrPut(term) { ArrayList() }.add(idx)
            }

            val searchable = buildString {
                append(q.questionText).append(' ')
                if (q.optionA.isNotBlank()) append(q.optionA).append(' ')
                if (q.optionB.isNotBlank()) append(q.optionB).append(' ')
                if (q.optionC.isNotBlank()) append(q.optionC).append(' ')
                if (q.optionD.isNotBlank()) append(q.optionD).append(' ')
                if (q.optionE.isNotBlank()) append(q.optionE).append(' ')
                if (q.correctAnswer.isNotBlank()) append(q.correctAnswer).append(' ')
                append(q.category).append(' ')
                append(q.sourceDocumentName)
            }

            indexedList.add(
                IndexedQuestion(
                    entity = q,
                    simTokens = tfCounts.keys,
                    tfIdfVector = vec,
                    vectorNorm = sqrt(sumSq),
                    charGrams = TextNormalizer.charNGrams(q.normalizedQuestion, 3),
                    searchableNormalized = TextNormalizer.normalizeForExact(searchable),
                    questionNormalized = q.normalizedQuestion
                )
            )
        }

        cachedIdf = idfMap
        cachedIndex = indexedList
        invertedIndex = inv
        cachedSignature = signature
    }

    fun invalidate() {
        cachedSignature = -1L
    }

    fun search(
        query: String,
        questions: List<QuestionEntity>,
        tierFilter: SearchTier? = null,
        maxResults: Int = 150
    ): List<SearchResultItem> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || questions.isEmpty()) return emptyList()

        ensureIndex(questions)
        val docs = cachedIndex
        val idf = cachedIdf
        val inv = invertedIndex

        val normQuery = TextNormalizer.normalizeForExact(trimmedQuery)
        if (normQuery.isEmpty()) return emptyList()

        val querySimTokens = TextNormalizer.tokenizeForSimilarity(trimmedQuery)
        val queryKeywords = TextNormalizer.tokenizeForKeyword(trimmedQuery)
        val queryGrams = TextNormalizer.charNGrams(normQuery, 3)

        // Build query TF-IDF vector
        val qTfCounts = HashMap<String, Int>()
        for (t in querySimTokens) {
            qTfCounts[t] = (qTfCounts[t] ?: 0) + 1
        }
        val qLen = querySimTokens.size.coerceAtLeast(1).toDouble()
        val queryVec = HashMap<String, Double>(qTfCounts.size)
        var qSumSq = 0.0
        for ((term, count) in qTfCounts) {
            val w = (count / qLen) * (idf[term] ?: (ln((docs.size + 1.0) / 1.0) + 1.0))
            queryVec[term] = w
            qSumSq += w * w
        }
        val queryNorm = sqrt(qSumSq)

        // Candidate set for similarity via inverted index
        val simCandidates = HashSet<Int>()
        for (term in qTfCounts.keys) {
            inv[term]?.let { simCandidates.addAll(it) }
        }

        val results = ArrayList<SearchResultItem>()

        for (i in docs.indices) {
            val doc = docs[i]
            val q = doc.entity

            // 1. Exact Match Check
            val isLiteralExact = q.questionText.trim().equals(trimmedQuery, ignoreCase = true)
            val isNormalizedExact = doc.questionNormalized == normQuery
            if (isLiteralExact || isNormalizedExact) {
                if (tierFilter == null || tierFilter == SearchTier.EXACT_MATCH) {
                    val exactScore = if (isLiteralExact) 1.0 else 0.999
                    results.add(
                        SearchResultItem(
                            question = q,
                            tier = SearchTier.EXACT_MATCH,
                            matchScore = exactScore,
                            matchPercentage = 100
                        )
                    )
                }
                continue
            }

            // 2. Similarity Search (TF-IDF Cosine + Jaccard Token Overlap + Char 3-Gram Dice)
            var simScore = 0.0
            val shouldEvaluateSim = (i in simCandidates) || (queryGrams.size >= 4 && doc.charGrams.isNotEmpty())
            if (shouldEvaluateSim && querySimTokens.isNotEmpty()) {
                val cosine = if (queryNorm > 0.0 && doc.vectorNorm > 0.0) {
                    var dot = 0.0
                    for ((term, qw) in queryVec) {
                        val dw = doc.tfIdfVector[term]
                        if (dw != null) dot += qw * dw
                    }
                    dot / (queryNorm * doc.vectorNorm)
                } else 0.0

                val intersectionTokens = qTfCounts.keys.count { it in doc.simTokens }
                val unionTokens = qTfCounts.keys.size + doc.simTokens.size - intersectionTokens
                val jaccard = if (unionTokens > 0) intersectionTokens.toDouble() / unionTokens else 0.0

                val gramInter = if (queryGrams.isNotEmpty() && doc.charGrams.isNotEmpty()) {
                    queryGrams.count { it in doc.charGrams }
                } else 0
                val gramUnion = queryGrams.size + doc.charGrams.size - gramInter
                val gramSim = if (gramUnion > 0) gramInter.toDouble() / gramUnion else 0.0

                simScore = (0.60 * cosine) + (0.25 * jaccard) + (0.15 * gramSim)
            }

            val isSimilarityHit = if (querySimTokens.size >= 2) {
                simScore >= 0.18
            } else {
                simScore >= 0.45
            }

            if (isSimilarityHit) {
                if (tierFilter == null || tierFilter == SearchTier.SIMILARITY) {
                    val pct = (simScore * 100.0).roundToInt().coerceIn(25, 99)
                    results.add(
                        SearchResultItem(
                            question = q,
                            tier = SearchTier.SIMILARITY,
                            matchScore = simScore,
                            matchPercentage = pct
                        )
                    )
                }
                continue
            }

            // 3. Keyword Search (across question, options A-E, answer, category, document)
            if (queryKeywords.isNotEmpty()) {
                val matchedInQuestion = queryKeywords.count { kw -> doc.questionNormalized.contains(kw) }
                val matchedInAll = queryKeywords.count { kw -> doc.searchableNormalized.contains(kw) }
                val phraseInSearchable = doc.searchableNormalized.contains(normQuery)

                if (matchedInAll > 0 || phraseInSearchable) {
                    if (tierFilter == null || tierFilter == SearchTier.KEYWORD) {
                        val coverageAll = matchedInAll.toDouble() / queryKeywords.size
                        val coverageQuestion = matchedInQuestion.toDouble() / queryKeywords.size
                        val keywordScore = (0.55 * coverageAll) + (0.35 * coverageQuestion) +
                            (if (phraseInSearchable) 0.10 else 0.0)
                        val pct = (keywordScore * 90.0).roundToInt().coerceIn(15, 95)
                        results.add(
                            SearchResultItem(
                                question = q,
                                tier = SearchTier.KEYWORD,
                                matchScore = keywordScore,
                                matchPercentage = pct
                            )
                        )
                    }
                }
            }
        }

        return results
            .sortedWith(
                compareBy<SearchResultItem> { it.tier.priority }
                    .thenByDescending { it.matchScore }
                    .thenBy { it.question.id }
            )
            .take(maxResults)
    }

    private fun computeSignature(questions: List<QuestionEntity>): Long {
        var hash = questions.size.toLong() * 31L
        val step = (questions.size / 20).coerceAtLeast(1)
        var i = 0
        while (i < questions.size) {
            val q = questions[i]
            hash = hash * 31L + q.id
            hash = hash * 31L + q.updatedAt
            i += step
        }
        return hash
    }
}
