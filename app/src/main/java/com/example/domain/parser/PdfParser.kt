package com.example.domain.parser

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

object PdfParser {

    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            runCatching {
                PDFBoxResourceLoader.init(context.applicationContext)
            }
        }
    }

    fun parsePages(context: Context, inputStream: InputStream): List<Pair<String, String>> {
        ensureInitialized(context)
        val pages = mutableListOf<Pair<String, String>>()
        PDDocument.load(inputStream).use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            val totalPages = document.numberOfPages
            for (pageNum in 1..totalPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val pageText = stripper.getText(document).orEmpty()
                if (pageText.isNotBlank()) {
                    pages.add("Halaman $pageNum" to pageText)
                }
            }
        }
        return pages
    }
}
