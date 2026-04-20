package com.worktrax.app.lib

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

data class ReportRange(val fromMs: Long, val toMs: Long, val label: String)

data class ReportOptions(
    val userName: String,
    val unit: WeightUnit,
    val range: ReportRange,
    val workouts: List<Workout>,
    val includeSets: Boolean,
    val includeSummary: Boolean,
    val includePRs: Boolean,
)

private fun volumeIn(w: Workout, unit: WeightUnit): Int {
    var t = 0.0
    for (e in w.exercises) for (s in e.sets) {
        val weight = if (s.unit == unit) s.weight else convertWeight(s.weight, s.unit, unit)
        t += weight * s.reps
    }
    return t.roundToInt()
}

private data class PR(val exerciseName: String, val weight: Double, val reps: Int)

private fun personalRecords(workouts: List<Workout>, unit: WeightUnit): List<PR> {
    val byName = HashMap<String, PR>()
    for (w in workouts) for (ex in w.exercises) for (s in ex.sets) {
        val weight = if (s.unit == unit) s.weight else convertWeight(s.weight, s.unit, unit)
        val prev = byName[ex.name]
        if (prev == null || weight > prev.weight) {
            byName[ex.name] = PR(ex.name, (weight * 10).roundToInt() / 10.0, s.reps)
        }
    }
    return byName.values.sortedByDescending { it.weight }
}

/**
 * Writes a PDF report mirroring the original web jsPDF layout at a sensible
 * fidelity for native Android. Returns the resulting file's Uri or null on failure.
 */
fun buildAndSaveReport(ctx: Context, opts: ReportOptions): Uri? {
    val doc = PdfDocument()
    val pageWidth = 595 // A4 @ 72dpi
    val pageHeight = 842
    val margin = 48f

    val ink = android.graphics.Color.parseColor("#0A0A09")
    val muted = android.graphics.Color.parseColor("#8E8B83")
    val accent = android.graphics.Color.parseColor("#C24A1E")
    val hairline = android.graphics.Color.parseColor("#D7D1C4")

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = ink

    var pageIndex = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
    var page = doc.startPage(pageInfo)
    var canvas = page.canvas
    var y = margin

    fun newPage() {
        doc.finishPage(page)
        pageIndex += 1
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
        page = doc.startPage(pageInfo)
        canvas = page.canvas
        y = margin
    }

    fun ensureRoom(needed: Float) {
        if (y + needed > pageHeight - margin) newPage()
    }

    // Header
    paint.color = ink
    paint.textSize = 22f
    paint.isFakeBoldText = true
    canvas.drawText("WORKTRAX", margin, y, paint)

    paint.isFakeBoldText = false
    paint.color = muted
    paint.textSize = 10f
    val wrText = "Workout Report"
    val wrWidth = paint.measureText(wrText)
    canvas.drawText(wrText, pageWidth - margin - wrWidth, y, paint)

    y += 12f
    paint.color = hairline
    paint.strokeWidth = 0.5f
    canvas.drawLine(margin, y, pageWidth - margin, y, paint)
    y += 22f

    paint.color = ink
    paint.isFakeBoldText = true
    paint.textSize = 14f
    canvas.drawText(opts.userName, margin, y, paint)

    paint.isFakeBoldText = false
    paint.color = muted
    paint.textSize = 10f
    val lbl = opts.range.label
    canvas.drawText(lbl, pageWidth - margin - paint.measureText(lbl), y, paint)
    y += 8f
    paint.textSize = 9f
    val dateStr = "${formatShortDate(isoFromEpochMs(opts.range.fromMs))}  –  ${formatShortDate(isoFromEpochMs(opts.range.toMs))}"
    canvas.drawText(dateStr, pageWidth - margin - paint.measureText(dateStr), y + 4, paint)
    y += 24f

    if (opts.includeSummary) {
        val totalSets = opts.workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } }
        val totalReps = opts.workouts.sumOf { w -> w.exercises.sumOf { e -> e.sets.sumOf { it.reps } } }
        val totalVol = opts.workouts.sumOf { volumeIn(it, opts.unit) }

        paint.color = muted; paint.textSize = 8f
        canvas.drawText("SUMMARY", margin, y, paint)
        y += 14f

        val cards = listOf(
            "WORKOUTS" to opts.workouts.size.toString(),
            "SETS" to totalSets.toString(),
            "REPS" to totalReps.toString(),
            "VOLUME" to "${numberWithCommas(totalVol)} ${opts.unit.code}",
        )
        val cardW = (pageWidth - margin * 2 - 12 * 3) / 4f
        cards.forEachIndexed { i, (label, value) ->
            val x = margin + i * (cardW + 12f)
            paint.color = hairline; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
            canvas.drawRoundRect(x, y, x + cardW, y + 48f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL
            paint.color = muted; paint.textSize = 7f
            canvas.drawText(label, x + 8, y + 14, paint)
            paint.color = ink; paint.isFakeBoldText = true; paint.textSize = 14f
            canvas.drawText(value, x + 8, y + 36, paint)
            paint.isFakeBoldText = false
        }
        y += 68f
    }

    paint.color = muted; paint.textSize = 8f
    canvas.drawText("WORKOUTS", margin, y, paint)
    y += 14f

    if (opts.workouts.isEmpty()) {
        paint.color = muted; paint.textSize = 10f
        canvas.drawText("No workouts in this range.", margin, y, paint)
        y += 20f
    }

    for (w in opts.workouts) {
        ensureRoom(60f)
        paint.color = ink; paint.isFakeBoldText = true; paint.textSize = 11f
        canvas.drawText(formatShortDate(w.date), margin, y, paint)
        paint.isFakeBoldText = false; paint.color = muted; paint.textSize = 9f
        canvas.drawText("${w.type.code.uppercase()}  ·  ${w.exercises.size} exercises", margin + 80, y, paint)

        val vol = volumeIn(w, opts.unit)
        paint.color = ink
        val volText = "${numberWithCommas(vol)} ${opts.unit.code}"
        canvas.drawText(volText, pageWidth - margin - paint.measureText(volText), y, paint)
        y += 14f

        for (ex in w.exercises) {
            ensureRoom(if (opts.includeSets) 16f + ex.sets.size * 12f else 16f)
            paint.color = ink; paint.textSize = 10f
            canvas.drawText(ex.name, margin + 14, y, paint)
            paint.color = muted; paint.textSize = 9f
            val tail = "${ex.sets.size} sets  ·  ${ex.muscle}"
            canvas.drawText(tail, pageWidth - margin - paint.measureText(tail), y, paint)
            y += 12f

            if (opts.includeSets) {
                paint.textSize = 8f; paint.color = muted
                for ((i, s) in ex.sets.withIndex()) {
                    ensureRoom(12f)
                    val weight = if (s.unit == opts.unit) s.weight else convertWeight(s.weight, s.unit, opts.unit)
                    val rounded = (weight * 10).roundToInt() / 10.0
                    canvas.drawText(
                        "Set ${i + 1}   ${s.reps} reps × $rounded ${opts.unit.code}",
                        margin + 26, y, paint,
                    )
                    y += 10f
                }
                y += 2f
            }
        }
        y += 8f
        ensureRoom(8f)
        paint.color = hairline; paint.strokeWidth = 0.3f
        canvas.drawLine(margin, y - 4, pageWidth - margin, y - 4, paint)
        y += 8f
    }

    if (opts.includePRs) {
        val prs = personalRecords(opts.workouts, opts.unit)
        if (prs.isNotEmpty()) {
            ensureRoom(30f)
            paint.color = muted; paint.textSize = 8f
            canvas.drawText("PERSONAL RECORDS", margin, y, paint)
            y += 14f
            paint.textSize = 10f
            for (pr in prs.take(20)) {
                ensureRoom(14f)
                paint.color = ink
                canvas.drawText(pr.exerciseName, margin, y, paint)
                paint.color = accent
                val right = "${pr.weight} ${opts.unit.code} × ${pr.reps}"
                canvas.drawText(right, pageWidth - margin - paint.measureText(right), y, paint)
                y += 14f
            }
        }
    }

    doc.finishPage(page)

    // write
    val safe = opts.userName.replace(Regex("[^a-zA-Z0-9]+"), "-").lowercase().ifEmpty { "user" }
    val fromDate = isoFromEpochMs(opts.range.fromMs).substring(0, 10)
    val toDate = isoFromEpochMs(opts.range.toMs).substring(0, 10)
    val filename = "worktrax-$safe-${fromDate}_${toDate}.pdf"

    return try {
        val uri: Uri?
        val os: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            os = uri?.let { ctx.contentResolver.openOutputStream(it) }
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                filename,
            )
            os = file.outputStream()
            uri = Uri.fromFile(file)
        }
        os?.use { doc.writeTo(it) }
        doc.close()
        uri
    } catch (e: Exception) {
        doc.close()
        null
    }
}
