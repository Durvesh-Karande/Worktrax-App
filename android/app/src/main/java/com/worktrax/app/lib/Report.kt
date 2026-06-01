package com.worktrax.app.lib

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
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

private fun workoutTypeColor(type: WorkoutType): Int {
    return when (type) {
        WorkoutType.STRENGTH -> android.graphics.Color.parseColor("#7A5D2E")
        WorkoutType.CARDIO -> android.graphics.Color.parseColor("#2E5A6A")
        WorkoutType.AEROBIC -> android.graphics.Color.parseColor("#C24A1E")
        WorkoutType.YOGA -> android.graphics.Color.parseColor("#5A3E7A")
    }
}

private val BITMAP_W = 1080
private val BITMAP_H = 1350 // 4:5 portrait — good for stories/shares
private val PAD = 64f

/**
 * Renders a shareable workout-summary card bitmap and saves it to the app's
 * cache directory. Returns the File or null on failure.
 */
fun buildShareImage(
    ctx: Context,
    workout: Workout,
    unit: WeightUnit,
    userName: String,
): File? {
    val typeColor = workoutTypeColor(workout.type)
    val paper = android.graphics.Color.parseColor("#F2EFE9")
    val surface = android.graphics.Color.parseColor("#FFFFFF")
    val ink = android.graphics.Color.parseColor("#1B1B1A")
    val muted = android.graphics.Color.parseColor("#8E8B83")
    val accent = android.graphics.Color.parseColor("#E85D2A")
    val line = android.graphics.Color.parseColor("#E6E1D7")

    val bmp = Bitmap.createBitmap(BITMAP_W, BITMAP_H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val normal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    // Background
    c.drawColor(paper)

    // Top banner (workout type stripe)
    val bannerH = 200f
    val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = typeColor }
    c.drawRoundRect(0f, 0f, BITMAP_W.toFloat(), bannerH, 0f, 0f, bannerPaint)

    // Banner shading — subtle gradient overlay (darker bottom)
    val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#33000000")
    }
    c.drawRect(0f, bannerH * 0.6f, BITMAP_W.toFloat(), bannerH, shadePaint)

    // "WORKTRAX" branding on banner
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = bold
        textSize = 32f
        alpha = 180
    }
    c.drawText("WORKTRAX", PAD, PAD + 34f, brandPaint)

    // Workout type label on banner
    val typeLabel = workout.type.code.uppercase()
    val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = bold
        textSize = 48f
    }
    c.drawText(typeLabel, PAD, bannerH - PAD, typePaint)

    // Card (white rounded rect)
    val cardTop = bannerH - 40f
    val cardBottom = BITMAP_H - PAD
    val cardLeft = PAD
    val cardRight = BITMAP_W - PAD
    val cardRadius = 24f
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface }
    val cardP = android.graphics.RectF(cardLeft, cardTop, cardRight, cardBottom)
    c.drawRoundRect(cardP, cardRadius, cardRadius, cardPaint)

    // Card shadow outline
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0C000000")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    c.drawRoundRect(cardP, cardRadius, cardRadius, shadowPaint)

    // Inner content area
    val innerLeft = cardLeft + 48f
    val innerW = cardRight - cardLeft - 96f

    // Greeting
    val greetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        typeface = bold
        textSize = 28f
    }
    c.drawText("Nice work!", innerLeft, cardTop + 70f, greetPaint)

    // User name
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        typeface = normal
        textSize = 22f
    }
    c.drawText(userName.ifEmpty { "Athlete" }, innerLeft, cardTop + 102f, namePaint)

    // First exercise
    val ex = workout.exercises.firstOrNull()
    val exName = ex?.name ?: "Workout"
    val exPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = serif
        textSize = 42f
    }
    // Truncate if too long
    val displayName = if (exName.length > 28) exName.take(25) + "…" else exName
    c.drawText(displayName, innerLeft, cardTop + 170f, exPaint)

    // Muscle group
    val muscleName = ex?.muscle ?: ""
    if (muscleName.isNotBlank()) {
        val musPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            typeface = normal
            textSize = 20f
        }
        c.drawText(muscleName.uppercase(), innerLeft, cardTop + 200f, musPaint)
    }

    // Divider
    val divY = cardTop + 240f
    val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = line
        strokeWidth = 2f
    }
    c.drawLine(innerLeft, divY, cardRight - 48f, divY, divPaint)

    // Stats row
    val stats = workout.exercises.sumOf { it.sets.size }
    val reps = workout.exercises.sumOf { ex -> ex.sets.sumOf { it.reps } }
    val vol = volumeOf(workout, unit)
    val volText = "${numberWithCommas(vol)} ${unit.code}"

    val statColW = innerW / 3f
    val statData = listOf(
        stats.toString() to "SETS",
        reps.toString() to "REPS",
        volText to "VOLUME",
    )

    val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }
    val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        typeface = bold
        textSize = 16f
        textAlign = Paint.Align.CENTER
    }
    val statDivPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = line
        strokeWidth = 1.5f
    }

    statData.forEachIndexed { i, (value, label) ->
        val cx = innerLeft + statColW * i + statColW / 2f
        c.drawText(value, cx, divY + 70f, statValPaint)
        c.drawText(label, cx, divY + 100f, statLabelPaint)
        if (i < statData.size - 1) {
            val lx = innerLeft + statColW * (i + 1)
            c.drawLine(lx, divY + 30f, lx, divY + 100f, statDivPaint)
        }
    }

    // Date
    val dateText = formatShortDate(workout.date)
    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        typeface = normal
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    c.drawText(dateText, BITMAP_W / 2f, cardBottom - 52f, datePaint)

    // Duration
    val durText = formatDuration(workout.durationSec)
    val durPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        typeface = normal
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    c.drawText(durText, BITMAP_W / 2f, cardBottom - 28f, durPaint)

    // Bottom app name mini
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#BDB9AE")
        typeface = bold
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    c.drawText("Worktrax", BITMAP_W / 2f, BITMAP_H - 28f, footerPaint)

    // Save
    return try {
        val dir = File(ctx.cacheDir, "images").also { it.mkdirs() }
        val file = File(dir, "worktrax_share_${workout.id.takeLast(12)}.png")
        file.outputStream().use { fos -> bmp.compress(Bitmap.CompressFormat.PNG, 90, fos) }
        bmp.recycle()
        file
    } catch (_: Exception) {
        bmp.recycle()
        null
    }
}

fun buildAndSaveCsv(ctx: Context, opts: ReportOptions): Uri? {
    val sb = StringBuilder()
    sb.appendLine("Date,Type,Duration,Exercise,Muscle,Set,Reps,Weight,Unit,Warmup,RPE")
    for (w in opts.workouts) {
        for (ex in w.exercises) {
            ex.sets.forEachIndexed { i, s ->
                val weight = if (s.unit == opts.unit) s.weight else convertWeight(s.weight, s.unit, opts.unit)
                sb.appendLine(
                    listOf(
                        w.date.substringBefore("T"),
                        w.type.code,
                        formatMinutes(w.durationSec),
                        ex.name,
                        ex.muscle,
                        (i + 1).toString(),
                        s.reps.toString(),
                        "%.1f".format(weight),
                        opts.unit.code,
                        if (s.warmup) "yes" else "no",
                        s.rpe?.toString() ?: "",
                    ).joinToString(",")
                )
            }
        }
    }
    val csv = sb.toString()
    val safe = opts.userName.replace(Regex("[^a-zA-Z0-9]+"), "-").lowercase().ifEmpty { "user" }
    val fromDate = isoFromEpochMs(opts.range.fromMs).substring(0, 10)
    val toDate = isoFromEpochMs(opts.range.toMs).substring(0, 10)
    val filename = "worktrax-$safe-${fromDate}_${toDate}.csv"
    return try {
        val uri: Uri?
        val os: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
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
        os?.use { it.write(csv.toByteArray()) }
        uri
    } catch (e: Exception) {
        null
    }
}
