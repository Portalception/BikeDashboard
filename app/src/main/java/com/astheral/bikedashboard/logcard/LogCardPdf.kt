package com.astheral.bikedashboard.logcard

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Reads the text out of a PDF the user picked. Call it off the main thread. */
object LogCardPdf {
    fun text(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        return input.use { stream -> PDDocument.load(stream).use { PDFTextStripper().getText(it) } }
    }
}
