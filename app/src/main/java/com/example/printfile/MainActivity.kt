package com.example.printfile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private var filePath: Uri? = null
    private var fileName: String = ""
    private lateinit var fileSelectionButton : Button
    private lateinit var printButton : Button
    private lateinit var uriFileTextView : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        fileSelectionButton = findViewById(R.id.fileSelectionButton)
        uriFileTextView = findViewById(R.id.uriFileTextView)
        printButton = findViewById(R.id.printButton)
        fileSelectionButton.setOnClickListener(View.OnClickListener {
            try {
                val intent = Intent()
                    .setType("application/pdf")
                    .setAction(Intent.ACTION_GET_CONTENT)

                startActivityForResult(Intent.createChooser(intent, "Select a file"), 777)
            } catch (e: Exception) {
                startActivity(Intent(this, MainActivity::class.java))
                Toast.makeText(this, "Ошибка при выборе файла: " + e.message.toString(), Toast.LENGTH_LONG).show()
            }
        })
        printButton.setOnClickListener(View.OnClickListener {
            if (filePath == null) {
                Toast.makeText(this, "Выберите файл *.pdf", Toast.LENGTH_LONG).show()
            }
            else {
                try{
                    printPDF(baseContext!!, filePath!!)
                } catch (e: Exception) {
                    startActivity(Intent(this, MainActivity::class.java))
                    Toast.makeText(this, "Ошибка печати: " + e.message.toString(), Toast.LENGTH_LONG).show()
                }

            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 777) {
            if (data != null) {
                filePath = data.data
                uriFileTextView.text = getFileNameFromUri(this, filePath!!)
            }
        }
    }

    fun printPDF(context: Context, pdfUri: Uri) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "PDF Document"

        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                private var document: PrintedPdfDocument? = null
                private var totalPages = 0

                override fun onLayout(
                    oldAttributes: PrintAttributes,
                    newAttributes: PrintAttributes,
                    cancellationSignal: android.os.CancellationSignal,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    document = PrintedPdfDocument(context, newAttributes)
                    totalPages = getTotalPages(pdfUri)
                    if (cancellationSignal.isCanceled) {
                        callback.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo
                        .Builder("print.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(totalPages)
                        .build()

                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: android.os.CancellationSignal,
                    callback: WriteResultCallback
                ) {
                    // Чтение и печать PDF файла из Uri
                    try {
                        val inputStream = context.contentResolver.openInputStream(pdfUri)
                        val outputStream = FileOutputStream(destination.fileDescriptor)

                        inputStream?.copyTo(outputStream)
                        outputStream.close()
                        inputStream?.close()
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: IOException) {
                        e.printStackTrace()
                        callback.onWriteFailed(e.message)
                    }
                }

                override fun onFinish() {
                    document?.close()
                    document = null
                }

                private fun getTotalPages(uri: Uri): Int {
                    var pageCount = 0
                    var parcelFileDescriptor: ParcelFileDescriptor? = null
                    var pdfRenderer: PdfRenderer? = null

                    try {
                        // Получаем дескриптор файла
                        parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                        pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
                        pageCount = pdfRenderer.pageCount
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pdfRenderer?.close()
                        parcelFileDescriptor?.close()
                    }

                    return pageCount
                }
            },
            null
        )
    }

    @SuppressLint("Range")
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val fileName: String?
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        fileName = cursor?.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        cursor?.close()
        return fileName
    }
}