package com.example.domain.parser

import com.example.domain.model.ParsedQuestion
import java.util.Locale

data class ExtractionOutcome(
    val questions: List<ParsedQuestion>,
    val failedBlocks: Int = 0
)

data class NumberedParsedQuestion(
    val number: Int?,
    val parsed: ParsedQuestion
)

object QuestionExtractor {

    private val QUESTION_HEADERS = listOf(
        "pertanyaan", "isi soal", "soal", "question", "uraian", "pernyataan", "deskripsi soal", "item"
    )
    private val ANSWER_HEADERS = listOf(
        "kunci jawaban", "kunci_jawaban", "kunci", "jawaban benar", "jawaban_benar",
        "jawaban", "answer key", "correct answer", "answer", "jawab", "key", "benar"
    )
    private val CATEGORY_HEADERS = listOf(
        "kategori", "mata pelajaran", "mapel", "topik", "materi", "bab", "subtest", "subject", "category", "jenis"
    )
    private val OPT_A_HEADERS = listOf("pilihan a", "opsi a", "jawaban a", "opt a", "opt_a", "a")
    private val OPT_B_HEADERS = listOf("pilihan b", "opsi b", "jawaban b", "opt b", "opt_b", "b")
    private val OPT_C_HEADERS = listOf("pilihan c", "opsi c", "jawaban c", "opt c", "opt_c", "c")
    private val OPT_D_HEADERS = listOf("pilihan d", "opsi d", "jawaban d", "opt d", "opt_d", "d")
    private val OPT_E_HEADERS = listOf("pilihan e", "opsi e", "jawaban e", "opt e", "opt_e", "e")

    private val QUESTION_START_REGEX = Regex(
        """^(?:no\.?\s*|soal\s*(?:no\.?\s*)?|q\.?\s*)?(\d{1,5})\s*(?:[.)\]:\-]|\s{2,})\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_OPTION_SPLIT_REGEX = Regex(
        """(?:^|\s)(?:\(([A-Ea-e])\)|([A-Ea-e])[.)])\s+"""
    )
    private val LINE_OPTION_REGEX = Regex(
        """^\s*(?:\(([A-Ea-e])\)|([A-Ea-e])[.)])\s*(.*)$"""
    )
    private val INLINE_ANSWER_REGEX = Regex(
        """^\s*(?:kunci\s*jawaban|kunci|jawaban\s*benar|jawaban|jawab|answer\s*key|answer|key)\s*[:=\-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val ANSWER_KEY_HEADER_REGEX = Regex(
        """^(?:kunci\s*jawaban|answer\s*key|daftar\s*kunci)\b.*$""",
        RegexOption.IGNORE_CASE
    )
    private val ANSWER_KEY_ENTRY_REGEX = Regex(
        """(\d{1,5})\s*[.):\-\s]+\s*([A-Ea-e]|[^\n,;]{1,80})"""
    )

    fun extractFromTableRows(
        rows: List<List<String>>,
        sourceLocation: String,
        defaultCategory: String = "Umum"
    ): ExtractionOutcome {
        val nonEmptyRows = rows.filter { row -> row.any { it.isNotBlank() } }
        if (nonEmptyRows.isEmpty()) return ExtractionOutcome(emptyList(), 0)

        var headerRowIndex = -1
        var colQuestion = -1
        var colOptA = -1
        var colOptB = -1
        var colOptC = -1
        var colOptD = -1
        var colOptE = -1
        var colAnswer = -1
        var colCategory = -1

        val searchLimit = minOf(nonEmptyRows.size, 10)
        for (i in 0 until searchLimit) {
            val row = nonEmptyRows[i]
            val lowerCells = row.map { it.trim().lowercase(Locale("id", "ID")) }
            val qIdx = findHeaderColumn(lowerCells, QUESTION_HEADERS)
            val aIdx = findHeaderColumn(lowerCells, ANSWER_HEADERS)
            val optAIdx = findHeaderColumn(lowerCells, OPT_A_HEADERS, exactShortMatch = true)

            if (qIdx != -1 && (aIdx != -1 || optAIdx != -1 || row.count { it.isNotBlank() } >= 2)) {
                headerRowIndex = i
                colQuestion = qIdx
                colAnswer = aIdx
                colOptA = optAIdx
                colOptB = findHeaderColumn(lowerCells, OPT_B_HEADERS, exactShortMatch = true)
                colOptC = findHeaderColumn(lowerCells, OPT_C_HEADERS, exactShortMatch = true)
                colOptD = findHeaderColumn(lowerCells, OPT_D_HEADERS, exactShortMatch = true)
                colOptE = findHeaderColumn(lowerCells, OPT_E_HEADERS, exactShortMatch = true)
                colCategory = findHeaderColumn(lowerCells, CATEGORY_HEADERS)
                break
            }
        }

        if (headerRowIndex != -1 && colQuestion != -1) {
            val extracted = mutableListOf<ParsedQuestion>()
            var failed = 0
            for (r in (headerRowIndex + 1) until nonEmptyRows.size) {
                val row = nonEmptyRows[r]
                val qText = row.getOrNull(colQuestion)?.trim().orEmpty()
                if (qText.isBlank()) {
                    if (row.any { it.isNotBlank() }) failed++
                    continue
                }
                val optA = if (colOptA != -1) row.getOrNull(colOptA)?.trim().orEmpty() else ""
                val optB = if (colOptB != -1) row.getOrNull(colOptB)?.trim().orEmpty() else ""
                val optC = if (colOptC != -1) row.getOrNull(colOptC)?.trim().orEmpty() else ""
                val optD = if (colOptD != -1) row.getOrNull(colOptD)?.trim().orEmpty() else ""
                val optE = if (colOptE != -1) row.getOrNull(colOptE)?.trim().orEmpty() else ""
                val ans = if (colAnswer != -1) row.getOrNull(colAnswer)?.trim().orEmpty() else ""
                val cat = if (colCategory != -1) {
                    row.getOrNull(colCategory)?.trim().takeUnless { it.isNullOrBlank() } ?: defaultCategory
                } else {
                    defaultCategory
                }

                // If question cell itself embeds options A..D and dedicated option columns are empty, split cleanly
                if (optA.isEmpty() && optB.isEmpty() && optC.isEmpty() && optD.isEmpty()) {
                    val split = splitInlineQuestionAndOptions(qText, ans)
                    extracted.add(
                        split.copy(
                            category = cat,
                            sourceLocation = sourceLocation
                        )
                    )
                } else {
                    extracted.add(
                        ParsedQuestion(
                            questionText = qText,
                            optionA = optA,
                            optionB = optB,
                            optionC = optC,
                            optionD = optD,
                            optionE = optE,
                            correctAnswer = ans,
                            category = cat,
                            sourceLocation = sourceLocation
                        )
                    )
                }
            }
            return ExtractionOutcome(extracted, failed)
        }

        // Fallback: no structured header detected (e.g., 2-column Q|A or 1-column numbered list)
        // Check if most rows have 2+ non-empty columns like [Question, Answer]
        val twoColRows = nonEmptyRows.count { row -> row.count { it.isNotBlank() } == 2 }
        if (twoColRows >= (nonEmptyRows.size * 0.6).toInt() && nonEmptyRows.size >= 2) {
            val extracted = mutableListOf<ParsedQuestion>()
            var failed = 0
            for (row in nonEmptyRows) {
                val filled = row.filter { it.isNotBlank() }.map { it.trim() }
                if (filled.size >= 2) {
                    val first = filled[0]
                    val second = filled[1]
                    // Ignore pure index column if first is just a number and 3+ cols exist
                    val questionRaw = if (first.all { it.isDigit() } && filled.size >= 3) filled[1] else first
                    val answerRaw = if (first.all { it.isDigit() } && filled.size >= 3) filled[2] else second
                    val parsed = splitInlineQuestionAndOptions(questionRaw, answerRaw).copy(
                        category = defaultCategory,
                        sourceLocation = sourceLocation
                    )
                    extracted.add(parsed)
                } else {
                    failed++
                }
            }
            if (extracted.isNotEmpty()) {
                return ExtractionOutcome(extracted, failed)
            }
        }

        // Final fallback: join cells as lines and parse numbered text blocks
        val joinedText = nonEmptyRows.joinToString("\n") { row ->
            row.filter { it.isNotBlank() }.joinToString(" ") { it.trim() }
        }
        return extractFromTextBlocks(joinedText, sourceLocation, defaultCategory)
    }

    fun extractFromTextPages(
        pages: List<Pair<String, String>>,
        defaultCategory: String = "Umum"
    ): ExtractionOutcome {
        val allNumbered = mutableListOf<NumberedParsedQuestion>()
        val trailingAnswerKey = mutableMapOf<Int, String>()
        var totalFailed = 0

        for ((locationLabel, pageText) in pages) {
            val (numbered, pageKeys, failed) = parseTextWithNumberTracking(
                text = pageText,
                sourceLocation = locationLabel,
                defaultCategory = defaultCategory
            )
            allNumbered.addAll(numbered)
            trailingAnswerKey.putAll(pageKeys)
            totalFailed += failed
        }

        val resolved = allNumbered.map { item ->
            val q = item.parsed
            if (q.correctAnswer.isBlank() && item.number != null) {
                val mapped = trailingAnswerKey[item.number]
                if (!mapped.isNullOrBlank()) q.copy(correctAnswer = mapped) else q
            } else {
                q
            }
        }
        return ExtractionOutcome(resolved, totalFailed)
    }

    fun extractFromTextBlocks(
        text: String,
        sourceLocation: String,
        defaultCategory: String = "Umum"
    ): ExtractionOutcome {
        return extractFromTextPages(listOf(sourceLocation to text), defaultCategory)
    }

    private data class InternalPageParse(
        val items: List<NumberedParsedQuestion>,
        val answerKeyMap: Map<Int, String>,
        val failedCount: Int
    )

    private fun parseTextWithNumberTracking(
        text: String,
        sourceLocation: String,
        defaultCategory: String
    ): InternalPageParse {
        val cleaned = TextNormalizer.cleanWhitespace(text)
        if (cleaned.isBlank()) return InternalPageParse(emptyList(), emptyMap(), 0)

        val lines = cleaned.split('\n')
        val items = mutableListOf<NumberedParsedQuestion>()
        val answerKeyMap = mutableMapOf<Int, String>()
        var failedCount = 0

        var inAnswerKeySection = false
        var currentNumber: Int? = null
        val questionBuffer = StringBuilder()
        var optA = StringBuilder()
        var optB = StringBuilder()
        var optC = StringBuilder()
        var optD = StringBuilder()
        var optE = StringBuilder()
        var currentAnswer = ""
        var activeTarget: Char? = null // null = question, 'A'..'E' = option

        fun flushCurrent() {
            val rawQ = questionBuffer.toString().trim()
            if (rawQ.isNotEmpty()) {
                val parsedBase = if (optA.isEmpty() && optB.isEmpty() && optC.isEmpty() && optD.isEmpty()) {
                    splitInlineQuestionAndOptions(rawQ, currentAnswer)
                } else {
                    ParsedQuestion(
                        questionText = rawQ,
                        optionA = optA.toString().trim(),
                        optionB = optB.toString().trim(),
                        optionC = optC.toString().trim(),
                        optionD = optD.toString().trim(),
                        optionE = optE.toString().trim(),
                        correctAnswer = currentAnswer.trim()
                    )
                }
                if (parsedBase.questionText.length >= 4) {
                    items.add(
                        NumberedParsedQuestion(
                            number = currentNumber,
                            parsed = parsedBase.copy(
                                category = defaultCategory,
                                sourceLocation = sourceLocation
                            )
                        )
                    )
                } else {
                    failedCount++
                }
            }
            currentNumber = null
            questionBuffer.clear()
            optA = StringBuilder()
            optB = StringBuilder()
            optC = StringBuilder()
            optD = StringBuilder()
            optE = StringBuilder()
            currentAnswer = ""
            activeTarget = null
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (ANSWER_KEY_HEADER_REGEX.matches(line)) {
                flushCurrent()
                inAnswerKeySection = true
                continue
            }

            if (inAnswerKeySection) {
                val matches = ANSWER_KEY_ENTRY_REGEX.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    for (m in matches) {
                        val num = m.groupValues[1].toIntOrNull()
                        val ans = m.groupValues[2].trim()
                        if (num != null && ans.isNotEmpty()) {
                            answerKeyMap[num] = ans
                        }
                    }
                    continue
                } else if (QUESTION_START_REGEX.matches(line)) {
                    // Left answer key section back into questions
                    inAnswerKeySection = false
                } else {
                    continue
                }
            }

            val ansMatch = INLINE_ANSWER_REGEX.matchEntire(line)
            if (ansMatch != null && questionBuffer.isNotEmpty()) {
                currentAnswer = ansMatch.groupValues[1].trim()
                activeTarget = null
                continue
            }

            val qMatch = QUESTION_START_REGEX.matchEntire(line)
            if (qMatch != null) {
                val num = qMatch.groupValues[1].toIntOrNull()
                val body = qMatch.groupValues[2].trim()
                // Avoid splitting lines like "2024 adalah tahun..." unless body looks like a question start
                if (num != null && num in 1..20000 && body.isNotEmpty()) {
                    flushCurrent()
                    currentNumber = num
                    questionBuffer.append(body)
                    activeTarget = null
                    continue
                }
            }

            val optMatch = LINE_OPTION_REGEX.matchEntire(line)
            if (optMatch != null && questionBuffer.isNotEmpty()) {
                val letter = (optMatch.groupValues[1].ifEmpty { optMatch.groupValues[2] })
                    .uppercase(Locale.ROOT).first()
                val rest = optMatch.groupValues[3].trim()
                // Check if multiple options exist on the same line (e.g., "A. X  B. Y  C. Z  D. W")
                val multiMatches = INLINE_OPTION_SPLIT_REGEX.findAll(line).toList()
                if (multiMatches.size >= 2) {
                    parseMultiOptionsFromLine(line, optA, optB, optC, optD, optE)
                    activeTarget = null
                } else {
                    when (letter) {
                        'A' -> optA.append(rest)
                        'B' -> optB.append(rest)
                        'C' -> optC.append(rest)
                        'D' -> optD.append(rest)
                        'E' -> optE.append(rest)
                    }
                    activeTarget = letter
                }
                continue
            }

            if (questionBuffer.isNotEmpty()) {
                when (activeTarget) {
                    'A' -> optA.append(' ').append(line)
                    'B' -> optB.append(' ').append(line)
                    'C' -> optC.append(' ').append(line)
                    'D' -> optD.append(' ').append(line)
                    'E' -> optE.append(' ').append(line)
                    else -> questionBuffer.append(' ').append(line)
                }
            }
        }
        flushCurrent()

        return InternalPageParse(items, answerKeyMap, failedCount)
    }

    private fun parseMultiOptionsFromLine(
        line: String,
        optA: StringBuilder,
        optB: StringBuilder,
        optC: StringBuilder,
        optD: StringBuilder,
        optE: StringBuilder
    ) {
        val matches = INLINE_OPTION_SPLIT_REGEX.findAll(line).toList()
        for (idx in matches.indices) {
            val match = matches[idx]
            val letter = (match.groupValues[1].ifEmpty { match.groupValues[2] })
                .uppercase(Locale.ROOT).first()
            val start = match.range.last + 1
            val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else line.length
            if (start <= end) {
                val content = line.substring(start, end).trim()
                when (letter) {
                    'A' -> if (optA.isEmpty()) optA.append(content)
                    'B' -> if (optB.isEmpty()) optB.append(content)
                    'C' -> if (optC.isEmpty()) optC.append(content)
                    'D' -> if (optD.isEmpty()) optD.append(content)
                    'E' -> if (optE.isEmpty()) optE.append(content)
                }
            }
        }
    }

    private fun splitInlineQuestionAndOptions(rawText: String, rawAnswer: String): ParsedQuestion {
        var workingText = rawText.trim()
        var answer = rawAnswer.trim()

        // Extract trailing inline answer if present inside question block
        val inlineKeyRegex = Regex(
            """(?:^|\n|\s)(?:kunci\s*jawaban|kunci|jawaban\s*benar|jawaban|answer)\s*[:=\-]\s*([^\n]+)$""",
            RegexOption.IGNORE_CASE
        )
        val keyMatch = inlineKeyRegex.find(workingText)
        if (keyMatch != null) {
            if (answer.isBlank()) {
                answer = keyMatch.groupValues[1].trim()
            }
            workingText = workingText.substring(0, keyMatch.range.first).trim()
        }

        val optMatches = INLINE_OPTION_SPLIT_REGEX.findAll(workingText).toList()
        if (optMatches.size >= 2) {
            val firstLetter = (optMatches[0].groupValues[1].ifEmpty { optMatches[0].groupValues[2] })
                .uppercase(Locale.ROOT).first()
            if (firstLetter == 'A') {
                val qPart = workingText.substring(0, optMatches[0].range.first).trim()
                val optA = StringBuilder()
                val optB = StringBuilder()
                val optC = StringBuilder()
                val optD = StringBuilder()
                val optE = StringBuilder()
                for (i in optMatches.indices) {
                    val letter = (optMatches[i].groupValues[1].ifEmpty { optMatches[i].groupValues[2] })
                        .uppercase(Locale.ROOT).first()
                    val start = optMatches[i].range.last + 1
                    val end = if (i + 1 < optMatches.size) optMatches[i + 1].range.first else workingText.length
                    if (start <= end) {
                        val value = workingText.substring(start, end).trim()
                        when (letter) {
                            'A' -> optA.append(value)
                            'B' -> optB.append(value)
                            'C' -> optC.append(value)
                            'D' -> optD.append(value)
                            'E' -> optE.append(value)
                        }
                    }
                }
                if (qPart.isNotBlank()) {
                    return ParsedQuestion(
                        questionText = qPart,
                        optionA = optA.toString().trim(),
                        optionB = optB.toString().trim(),
                        optionC = optC.toString().trim(),
                        optionD = optD.toString().trim(),
                        optionE = optE.toString().trim(),
                        correctAnswer = answer
                    )
                }
            }
        }

        return ParsedQuestion(
            questionText = workingText,
            correctAnswer = answer
        )
    }

    private fun findHeaderColumn(
        lowerCells: List<String>,
        candidates: List<String>,
        exactShortMatch: Boolean = false
    ): Int {
        for (candidate in candidates) {
            val idx = lowerCells.indexOfFirst { cell ->
                if (exactShortMatch && candidate.length == 1) {
                    cell == candidate || cell == "pilihan $candidate" || cell == "opsi $candidate"
                } else {
                    cell == candidate || cell.contains(candidate)
                }
            }
            if (idx != -1) return idx
        }
        return -1
    }
}
