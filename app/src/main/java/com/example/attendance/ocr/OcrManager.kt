package com.example.attendance.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class RecognizedText(val text: String, val boundingBox: Rect?)

class OcrManager(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractStructuredTextFromBitmap(bitmap: Bitmap): List<RecognizedText> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        return@withContext try {
            val result = recognizer.process(image).await()
            result.textBlocks.flatMap { block ->
                block.lines.map { line ->
                    RecognizedText(line.text, line.boundingBox)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun extractStructuredTextFromUri(uri: Uri): List<RecognizedText> = withContext(Dispatchers.Default) {
        return@withContext try {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            result.textBlocks.flatMap { block ->
                block.lines.map { line ->
                    RecognizedText(line.text, line.boundingBox)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun pdfToBitmaps(uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        val parcelFileDescriptor: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
        parcelFileDescriptor?.let {
            val renderer = PdfRenderer(it)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                // Use smaller ARGB_4444 or RGB_565 if memory is tight, but 8888 is best for OCR
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
        }
        return@withContext bitmaps
    }
}
