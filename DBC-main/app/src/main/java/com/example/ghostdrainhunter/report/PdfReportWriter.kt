package com.example.ghostdrainhunter.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.ghostdrainhunter.domain.GhostScoreResult
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportWriter(private val context: Context) {

    companion object {
        const val PAGE_WIDTH = 595  // A4 points
        const val PAGE_HEIGHT = 842 // A4 points
        const val MARGIN_LEFT = 40f
        const val MARGIN_RIGHT = 555f
        const val MARGIN_TOP = 40f
        const val MARGIN_BOTTOM = 800f
    }

    fun writeReport(reportData: ReportData, outputStream: OutputStream) {
        val pdfDocument = PdfDocument()

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = MARGIN_TOP

        // Helper for pagination
        fun checkPageBreak(requiredHeight: Float, onNewPage: (() -> Unit)? = null) {
            if (y + requiredHeight > MARGIN_BOTTOM) {
                // Draw footer for current page
                paint.textSize = 9f
                paint.typeface = Typeface.DEFAULT
                paint.color = Color.DKGRAY
                canvas.drawText("Ghost Drain Hunter • Page $pageNumber", MARGIN_LEFT, 820f, paint)

                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN_TOP

                onNewPage?.invoke()
            }
        }

        // Title Block
        paint.color = Color.rgb(20, 30, 45)
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Ghost Drain Hunter", MARGIN_LEFT, y + 20f, paint)
        y += 26f

        paint.textSize = 14f
        paint.color = Color.rgb(40, 60, 80)
        canvas.drawText("${reportData.periodDays}-Day Battery Drain Report", MARGIN_LEFT, y + 14f, paint)
        y += 20f

        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        paint.textSize = 9f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.GRAY
        val periodStartStr = dateFormat.format(Date(reportData.periodStartMillis))
        val periodEndStr = dateFormat.format(Date(reportData.periodEndMillis))
        canvas.drawText("Period: $periodStartStr - $periodEndStr | Generated: ${dateFormat.format(Date(reportData.generatedAtMillis))}", MARGIN_LEFT, y + 10f, paint)
        y += 20f

        // Separator line
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, paint)
        y += 12f

        // Disclaimer
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.color = Color.rgb(100, 100, 100)
        canvas.drawText(reportData.disclaimer, MARGIN_LEFT, y + 9f, paint)
        y += 18f

        // Summary Box
        paint.color = Color.rgb(245, 247, 250)
        canvas.drawRoundRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 110f, 6f, 6f, paint)

        val scoreText = when (val score = reportData.scoreResult) {
            is GhostScoreResult.NotEnoughData -> "Score: Not enough data yet"
            is GhostScoreResult.Score -> "Ghost Drain Score: ${score.score}/100 (${score.label})"
        }

        paint.color = Color.rgb(30, 40, 55)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(scoreText, MARGIN_LEFT + 14f, y + 22f, paint)

        paint.textSize = 9.5f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(50, 60, 75)

        val col1X = MARGIN_LEFT + 14f
        val col2X = MARGIN_LEFT + 260f

        canvas.drawText(String.format(Locale.US, "Total Idle Time: %.1f h", reportData.totalValidIdleHours), col1X, y + 42f, paint)
        canvas.drawText("Total Battery Drained: ${reportData.totalPercentLost}%", col1X, y + 58f, paint)
        canvas.drawText(String.format(Locale.US, "Average Idle Rate: %.2f %%/hr", reportData.avgDrainRatePerHour), col1X, y + 74f, paint)
        canvas.drawText("Valid Sessions: ${reportData.validSessionsCount} (Excluded: ${reportData.excludedSessionsCount})", col1X, y + 90f, paint)

        canvas.drawText(String.format(Locale.US, "User Threshold: %.1f %%/hr (%d above)", reportData.thresholdPerHour, reportData.sessionsAboveThresholdCount), col2X, y + 42f, paint)
        canvas.drawText("Pattern: ${reportData.patternLine}", col2X, y + 58f, paint)
        canvas.drawText(reportData.weekVsWeekSummary, col2X, y + 74f, paint)

        y += 124f

        // Top 5 Highest Drain Sessions Table
        if (reportData.topHighestDrainSessions.isNotEmpty()) {
            checkPageBreak(120f)
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.rgb(25, 35, 50)
            canvas.drawText("Top Highest-Drain Sessions", MARGIN_LEFT, y + 11f, paint)
            y += 18f

            fun drawTopSessionsHeader() {
                paint.color = Color.rgb(230, 235, 242)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 18f, paint)
                paint.color = Color.rgb(40, 50, 65)
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                canvas.drawText("Date", MARGIN_LEFT + 8f, y + 12f, paint)
                canvas.drawText("Time Window", MARGIN_LEFT + 90f, y + 12f, paint)
                canvas.drawText("Duration", MARGIN_LEFT + 220f, y + 12f, paint)
                canvas.drawText("Battery Lost", MARGIN_LEFT + 320f, y + 12f, paint)
                canvas.drawText("Rate (%/hr)", MARGIN_LEFT + 420f, y + 12f, paint)
                y += 18f
            }

            drawTopSessionsHeader()

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8.5f
            paint.color = Color.rgb(30, 40, 50)

            for (session in reportData.topHighestDrainSessions) {
                checkPageBreak(18f) { drawTopSessionsHeader() }

                canvas.drawText(session.dateFormatted, MARGIN_LEFT + 8f, y + 12f, paint)
                canvas.drawText(session.timeRangeFormatted, MARGIN_LEFT + 90f, y + 12f, paint)
                canvas.drawText(session.durationFormatted, MARGIN_LEFT + 220f, y + 12f, paint)
                canvas.drawText("${session.percentLost}%", MARGIN_LEFT + 320f, y + 12f, paint)
                canvas.drawText(String.format(Locale.US, "%.1f %%/hr", session.ratePerHour), MARGIN_LEFT + 420f, y + 12f, paint)

                paint.color = Color.rgb(240, 240, 240)
                canvas.drawLine(MARGIN_LEFT, y + 16f, MARGIN_RIGHT, y + 16f, paint)
                paint.color = Color.rgb(30, 40, 50)
                y += 18f
            }
            y += 14f
        }

        // Daily Breakdown Table
        checkPageBreak(120f)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(25, 35, 50)
        canvas.drawText("Daily Drain Breakdown", MARGIN_LEFT, y + 11f, paint)
        y += 18f

        fun drawDailyTableHeader() {
            paint.color = Color.rgb(230, 235, 242)
            canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 18f, paint)
            paint.color = Color.rgb(40, 50, 65)
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            canvas.drawText("Day", MARGIN_LEFT + 8f, y + 12f, paint)
            canvas.drawText("Idle Hours", MARGIN_LEFT + 160f, y + 12f, paint)
            canvas.drawText("Drain (%)", MARGIN_LEFT + 280f, y + 12f, paint)
            canvas.drawText("Rate (%/hr)", MARGIN_LEFT + 400f, y + 12f, paint)
            y += 18f
        }

        drawDailyTableHeader()

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 8.5f
        paint.color = Color.rgb(30, 40, 50)

        for (row in reportData.dailyTable) {
            checkPageBreak(18f) { drawDailyTableHeader() }

            canvas.drawText(row.dayLabel, MARGIN_LEFT + 8f, y + 12f, paint)
            canvas.drawText(String.format(Locale.US, "%.1f h", row.idleHours), MARGIN_LEFT + 160f, y + 12f, paint)
            canvas.drawText(String.format(Locale.US, "%.1f%%", row.percentLost), MARGIN_LEFT + 280f, y + 12f, paint)
            canvas.drawText(String.format(Locale.US, "%.1f %%/hr", row.ratePerHour), MARGIN_LEFT + 400f, y + 12f, paint)

            paint.color = Color.rgb(240, 240, 240)
            canvas.drawLine(MARGIN_LEFT, y + 16f, MARGIN_RIGHT, y + 16f, paint)
            paint.color = Color.rgb(30, 40, 50)
            y += 18f
        }

        // Draw footer on last page
        paint.textSize = 9f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.DKGRAY
        canvas.drawText("Ghost Drain Hunter • Page $pageNumber", MARGIN_LEFT, 820f, paint)

        pdfDocument.finishPage(page)

        // Write to stream
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}
