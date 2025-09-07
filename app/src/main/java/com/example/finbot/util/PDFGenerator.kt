package com.example.finbot.util

import android.content.Context
import android.os.Environment
import com.example.finbot.data.ExpenseReportData
import com.itextpdf.text.*
import com.itextpdf.text.pdf.*
// Add these imports for Kotlin extension properties
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Element
import com.itextpdf.text.Chunk
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PDFGenerator {
    companion object {
        private fun getCategoryName(categoryId: Int): String {
            return when (categoryId) {
                1 -> "Food"
                2 -> "Shopping"
                3 -> "Transport"
                4 -> "Health"
                5 -> "Utilities"
                6 -> "Others"
                else -> "Unknown"
            }
        }

        fun generateExpenseReport(context: Context, reportData: ExpenseReportData): File? {
            return try {
                // Create file in Downloads directory
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ExpenseReport_$timestamp.pdf"
                val file = File(downloadsDir, fileName)

                // Create PDF document
                val document = Document(PageSize.A4)
                val writer = PdfWriter.getInstance(document, FileOutputStream(file))

                document.open()

                // Define fonts and colors
                val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f, BaseColor.DARK_GRAY)
                val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, BaseColor.BLACK)
                val normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12f, BaseColor.BLACK)
                val smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor.GRAY)

                // Title
                val title = Paragraph("Expense Report", titleFont)
                title.alignment = Element.ALIGN_CENTER
                title.setSpacingAfter(20f)  // Use method instead of property
                document.add(title)

                // Date and user info
                val dateStr = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())
                val userInfo = Paragraph("Generated on: $dateStr\nUser: ${reportData.username}\nEmail: ${reportData.email}", smallFont)
                userInfo.setSpacingAfter(20f)  // Use method instead of property
                document.add(userInfo)

                // Summary section
                val summaryTitle = Paragraph("Summary", headerFont)
                summaryTitle.setSpacingAfter(10f)  // Use method instead of property
                document.add(summaryTitle)

                val summaryTable = PdfPTable(2)
                summaryTable.widthPercentage = 100f
                summaryTable.setWidths(floatArrayOf(60f, 40f))

                addTableRow(summaryTable, "Total Expenses:", "${reportData.currencyType} ${String.format("%.2f", reportData.totalExpenses)}")
                addTableRow(summaryTable, "Budget Limit:", "${reportData.currencyType} ${String.format("%.2f", reportData.budgetLimit)}")
                addTableRow(summaryTable, "Highest Used Category:", reportData.highestUsedCategory)
                addTableRow(summaryTable, "Currency Type:", reportData.currencyType)
                addTableRow(summaryTable, "Most Expenses Per Day:", "${reportData.currencyType} ${String.format("%.2f", reportData.mostExpensesValuePerDay)}")
                addTableRow(summaryTable, "Daily Average:", "${reportData.currencyType} ${String.format("%.2f", reportData.dailyAverageValue)}")

                summaryTable.setSpacingAfter(20f)  // Use method instead of property
                document.add(summaryTable)

                // Expenses detail section
                val expensesTitle = Paragraph("Expense Details", headerFont)
                expensesTitle.setSpacingAfter(10f)  // Use method instead of property
                document.add(expensesTitle)

                // Create expenses table
                val expensesTable = PdfPTable(5)
                expensesTable.widthPercentage = 100f
                expensesTable.setWidths(floatArrayOf(25f, 20f, 15f, 15f, 25f))

                // Add headers
                val headers = arrayOf("Description", "Category", "Date", "Amount", "Time")
                for (header in headers) {
                    val cell = PdfPCell(Phrase(header, headerFont))
                    cell.backgroundColor = BaseColor.LIGHT_GRAY
                    cell.setPadding(8f)  // Use method instead of property
                    expensesTable.addCell(cell)
                }

                // Add expense data
                for (expense in reportData.expenses) {
                    expensesTable.addCell(createCell(expense.name, normalFont))
                    expensesTable.addCell(createCell(getCategoryName(expense.categoryId), normalFont))
                    expensesTable.addCell(createCell(expense.date, normalFont))
                    expensesTable.addCell(createCell("${reportData.currencyType} ${String.format("%.2f", expense.amount)}", normalFont))
                    expensesTable.addCell(createCell(expense.time, normalFont))
                }

                document.add(expensesTable)

                // Footer
                document.add(Chunk.NEWLINE)
                val footer = Paragraph("Report generated by FinBot", smallFont)
                footer.alignment = Element.ALIGN_CENTER
                footer.setSpacingBefore(20f)  // Use method instead of property
                document.add(footer)

                document.close()
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun addTableRow(table: PdfPTable, label: String, value: String) {
            val labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, BaseColor.BLACK)
            val valueFont = FontFactory.getFont(FontFactory.HELVETICA, 12f, BaseColor.BLACK)

            table.addCell(createCell(label, labelFont))
            table.addCell(createCell(value, valueFont))
        }

        private fun createCell(text: String, font: Font): PdfPCell {
            val cell = PdfPCell(Phrase(text, font))
            cell.setPadding(8f)  // Use method instead of property
            cell.borderWidth = 1f
            cell.borderColor = BaseColor.GRAY
            return cell
        }
    }
}