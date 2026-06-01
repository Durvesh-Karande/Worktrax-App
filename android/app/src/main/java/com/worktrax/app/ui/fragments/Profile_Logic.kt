package com.worktrax.app.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.worktrax.app.R
import com.worktrax.app.data.BodyMeasurement
import com.worktrax.app.data.BodyweightEntry
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.databinding.ProfileDesignBinding
import com.worktrax.app.lib.ReportOptions
import com.worktrax.app.lib.ReportRange
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.bodyweightFromJsonString
import com.worktrax.app.lib.bodyweightToJsonString
import com.worktrax.app.lib.buildAndSaveCsv
import com.worktrax.app.lib.buildAndSaveReport
import com.worktrax.app.lib.formatShortDate
import com.worktrax.app.lib.isoEpochMs
import com.worktrax.app.lib.measurementsFromJsonString
import com.worktrax.app.lib.measurementsToJsonString
import com.worktrax.app.lib.nowIso
import com.worktrax.app.lib.numberWithCommas
import com.worktrax.app.lib.volumeOf
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Suppress("ClassName")
class Profile_Logic : Fragment() {

    private var _binding: ProfileDesignBinding? = null
    private val binding get() = _binding!!

    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })
    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })

    private enum class Range(val labelRes: Int, val days: Int) {
        TODAY(R.string.range_today, 1), W7(R.string.range_7_days, 7), M1(R.string.range_30_days, 30),
        M3(R.string.range_90_days, 90), ALL(R.string.range_all_time, Int.MAX_VALUE)
    }

    private var selectedRange: Range = Range.TODAY

    private inner class WorkoutLogAdapter : RecyclerView.Adapter<WorkoutLogAdapter.VH>() {
        private var items: List<Pair<Workout, WeightUnit>> = emptyList()

        fun submit(list: List<Workout>, unit: WeightUnit) {
            items = list.take(10).map { it to unit }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_recent_workout, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (w, unit) = items[position]
            val firstEx = w.exercises.firstOrNull()
            val title = firstEx?.name ?: w.type.code.uppercase()
            val meta = buildString {
                append(w.type.code.replaceFirstChar { it.uppercase() })
                if (firstEx?.muscle?.isNotBlank() == true) append(" · ").append(firstEx.muscle)
                append(" · ").append(w.exercises.sumOf { it.sets.size }).append(" sets")
            }
            holder.date.text = formatShortDate(w.date)
            holder.desc.text = "$title · $meta · ${volumeOf(w, unit)} ${unit.code}"
            holder.bar.setBackgroundResource(
                when (w.type) {
                    WorkoutType.STRENGTH -> R.color.type_strength_top
                    WorkoutType.CARDIO -> R.color.type_cardio_top
                    WorkoutType.AEROBIC -> R.color.type_aerobic_top
                    WorkoutType.YOGA -> R.color.type_yoga_top
                }
            )
        }

        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val bar: View = view.findViewById(R.id.v_type_bar)
            val date: TextView = view.findViewById(R.id.tv_workout_date)
            val desc: TextView = view.findViewById(R.id.tv_workout_desc)
        }
    }

    private val workoutLogAdapter = WorkoutLogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProfileDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }

        binding.rvFilteredLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFilteredLogs.adapter = workoutLogAdapter

        setupRangeChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(settingsVM.state, historyVM.workouts) { s, h -> s to h }
                    .collectLatest { (settings, workouts) ->
                        binding.tvProfileName.text = if (settings.name.isNotBlank()) renderItalicLast(settings.name) else getString(R.string.your_name_placeholder)
                        binding.tvAvatarInitials.text = if (settings.name.isNotBlank()) settings.name.trim().first().uppercase() else "?"
                        binding.tvProfileSub.text =
                            "${getString(R.string.profile_since)} · ${workouts.size} ${getString(R.string.profile_sessions)}"
                        renderStats(workouts, settings.unit)
                        renderChart(workouts, settings.unit)
                        renderFiltered(workouts, settings.unit)
                    }
            }
        }

        binding.btnDownloadPdf.setOnClickListener { downloadReport() }
        binding.btnDownloadCsv.setOnClickListener { downloadCsv() }
        binding.btnAddBodyweight.setOnClickListener { addBodyweightDialog() }
        binding.btnAddMeasurement.setOnClickListener { addMeasurementDialog() }
        loadBodyweight()
    }

    private fun setupRangeChips() {
        binding.layoutRangeChips.removeAllViews()
        val density = resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (9 * density).toInt()
        val gap = (6 * density).toInt()

        Range.values().forEach { range ->
            val chip = TextView(requireContext()).apply {
                text = getString(range.labelRes)
                textSize = 12f
                setPadding(padH, padV, padH, padV)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_2))
                setBackgroundResource(R.drawable.shape_chip)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, gap, 0) }
                isClickable = true
                setOnClickListener {
                    selectedRange = range
                    updateRangeChipState()
                    refreshFilteredFromVMs()
                }
            }
            binding.layoutRangeChips.addView(chip)
        }
        updateRangeChipState()
    }

    private fun updateRangeChipState() {
        val selectedLabel = getString(selectedRange.labelRes)
        for (i in 0 until binding.layoutRangeChips.childCount) {
            val chip = binding.layoutRangeChips.getChildAt(i) as TextView
            val selected = chip.text.toString() == selectedLabel
            chip.setBackgroundResource(
                if (selected) R.drawable.shape_chip_selected else R.drawable.shape_chip
            )
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.ink_2)
            )
        }
    }

    private fun refreshFilteredFromVMs() {
        val unit = settingsVM.state.value.unit
        val workouts = historyVM.workouts.value
        renderFiltered(workouts, unit)
        renderChart(workouts, unit)
    }

    private fun filterWorkouts(all: List<Workout>): List<Workout> {
        if (selectedRange == Range.ALL) return all
        val cutoff = if (selectedRange == Range.TODAY) midnightToday()
        else System.currentTimeMillis() - selectedRange.days.toLong() * 24 * 3600 * 1000
        return all.filter { isoEpochMs(it.date) >= cutoff }
    }

    private fun midnightToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun renderStats(workouts: List<Workout>, unit: WeightUnit) {
        binding.tvStatSessions.text = workouts.size.toString()
        val vol = workouts.sumOf { volumeOf(it, unit) }
        binding.tvStatLifted.text =
            if (vol >= 1000) "${(vol / 1000.0).format1()}t" else numberWithCommas(vol)

        // day streak — count consecutive days with workouts ending from today
        val workoutDates = workouts
            .map { it.date.substring(0, 10) }
            .toSortedSet()
        var streak = 0
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        while (true) {
            val dateStr = sdf.format(cal.time)
            if (workoutDates.contains(dateStr)) {
                streak++
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        binding.tvStatStreak.text = streak.coerceAtMost(99).toString()
    }

    private fun Double.format1() = "%.1f".format(this)

    private fun renderChart(allWorkouts: List<Workout>, unit: WeightUnit) {
        val filtered = filterWorkouts(allWorkouts)
        // Aggregate into up to 7 buckets (day-of-week if <=7 days, else weekly)
        val bucketCount = 7
        val now = System.currentTimeMillis()
        val windowDays =
            if (selectedRange == Range.ALL) 30 else selectedRange.days.coerceAtLeast(bucketCount)
        val bucketMs = windowDays.toLong() * 24 * 3600 * 1000 / bucketCount

        val values = LongArray(bucketCount)
        filtered.forEach { w ->
            val t = isoEpochMs(w.date)
            val age = now - t
            if (age in 0..(windowDays.toLong() * 24 * 3600 * 1000)) {
                val idx = (bucketCount - 1 - (age / bucketMs)).toInt()
                    .coerceIn(0, bucketCount - 1)
                values[idx] += volumeOf(w, unit).toLong()
            }
        }

        binding.layoutChartBars.removeAllViews()
        binding.layoutChartLabels.removeAllViews()
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val density = resources.displayMetrics.density
        val barGap = (4 * density).toInt()

        values.forEachIndexed { i, v ->
            val frac = v.toFloat() / max.toFloat()
            val bar = View(requireContext()).apply {
                setBackgroundResource(
                    if (v > 0) R.drawable.shape_chart_bar else R.drawable.shape_chart_bar_empty
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    (120 * density * frac.coerceAtLeast(0.04f)).toInt()
                ).apply {
                    weight = 1f
                    setMargins(if (i == 0) 0 else barGap, 0, 0, 0)
                }
            }
            binding.layoutChartBars.addView(bar)

            val label = TextView(requireContext()).apply {
                text = if (windowDays <= 7) dowLabel(i, bucketCount) else "${i + 1}"
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_3))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply {
                        weight = 1f
                        setMargins(if (i == 0) 0 else barGap, 0, 0, 0)
                    }
            }
            binding.layoutChartLabels.addView(label)
        }
    }

    private fun dowLabel(idx: Int, total: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(total - 1 - idx))
        val days = arrayOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
        return days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    private fun renderFiltered(allWorkouts: List<Workout>, unit: WeightUnit) {
        val filtered = filterWorkouts(allWorkouts).sortedByDescending { isoEpochMs(it.date) }
        if (filtered.isEmpty()) {
            binding.tvNoLogs.visibility = View.VISIBLE
            binding.rvFilteredLogs.visibility = View.GONE
            workoutLogAdapter.submit(emptyList(), unit)
            return
        }
        binding.tvNoLogs.visibility = View.GONE
        binding.rvFilteredLogs.visibility = View.VISIBLE
        workoutLogAdapter.submit(filtered, unit)
    }

    private var bodyweightEntries: List<BodyweightEntry> = emptyList()
    private var measurementEntries: List<BodyMeasurement> = emptyList()

    private fun loadBodyweight() {
        val raw = Storage.getString(requireContext(), Storage.KEY_BODYWEIGHT)
        bodyweightEntries = bodyweightFromJsonString(raw)
        renderBodyweight()
        loadMeasurements()
    }

    private fun loadMeasurements() {
        val raw = Storage.getString(requireContext(), Storage.KEY_BODY_MEASUREMENTS)
        measurementEntries = measurementsFromJsonString(raw)
        renderMeasurements()
    }

    private fun renderMeasurements() {
        binding.layoutMeasurementEntries.removeAllViews()
        val recent = measurementEntries.sortedByDescending { it.date }.take(3)
        if (recent.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = getString(R.string.no_entries_yet)
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_3))
            }
            binding.layoutMeasurementEntries.addView(tv)
        } else {
            recent.forEach { m ->
                val parts = mutableListOf<String>()
                if (m.chest > 0) parts.add("Chest ${m.chest} ${m.unit}")
                if (m.waist > 0) parts.add("Waist ${m.waist} ${m.unit}")
                if (m.hips > 0) parts.add("Hips ${m.hips} ${m.unit}")
                if (m.arm > 0) parts.add("Arm ${m.arm} ${m.unit}")
                if (m.thigh > 0) parts.add("Thigh ${m.thigh} ${m.unit}")
                if (m.calf > 0) parts.add("Calf ${m.calf} ${m.unit}")
                val tv = TextView(requireContext()).apply {
                    text = "${parts.joinToString(" · ")}  • ${formatShortDate(m.date)}"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ink))
                }
                binding.layoutMeasurementEntries.addView(tv)
            }
        }
    }

    private fun renderBodyweight() {
        binding.layoutBodyweightEntries.removeAllViews()
        val recent = bodyweightEntries.sortedByDescending { it.date }.take(3)
        if (recent.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = getString(R.string.no_entries_yet)
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_3))
            }
            binding.layoutBodyweightEntries.addView(tv)
        } else {
            recent.forEach { entry ->
                val tv = TextView(requireContext()).apply {
                    text = "${entry.weight} ${entry.unit.code} · ${formatShortDate(entry.date)}"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ink))
                }
                binding.layoutBodyweightEntries.addView(tv)
            }
        }
    }

    private fun addBodyweightDialog() {
        val unit = settingsVM.state.value.unit
        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Weight in ${unit.code}"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_bodyweight)
            .setView(input)
            .setPositiveButton(R.string.save_label) { _, _ ->
                val v = input.text.toString().toDoubleOrNull()
                if (v != null && v > 0) {
                    bodyweightEntries = bodyweightEntries + BodyweightEntry(
                        date = nowIso(),
                        weight = v,
                        unit = unit,
                    )
                    Storage.putString(
                        requireContext(),
                        Storage.KEY_BODYWEIGHT,
                        bodyweightToJsonString(bodyweightEntries),
                    )
                    renderBodyweight()
                }
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun addMeasurementDialog() {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 12) }

        val fields = listOf("chest", "waist", "hips", "arm", "thigh", "calf")
        val inputs = fields.associateWith { EditText(requireContext()).apply {
            hint = "${it.replaceFirstChar { c -> c.uppercase() }} (cm)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = lp
        }}

        val density2 = resources.displayMetrics.density
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((30 * density2).toInt(), (30 * density2).toInt(), (30 * density2).toInt(), (30 * density2).toInt())
            fields.forEach { addView(inputs[it]) }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_measurement)
            .setView(container)
            .setPositiveButton(R.string.save_label) { _, _ ->
                val values = inputs.mapValues { (_, v) -> v.text.toString().toDoubleOrNull() ?: 0.0 }
                if (values.values.all { it <= 0 }) return@setPositiveButton
                val m = BodyMeasurement(
                    date = nowIso(),
                    chest = values["chest"] ?: 0.0,
                    waist = values["waist"] ?: 0.0,
                    arm = values["arm"] ?: 0.0,
                    thigh = values["thigh"] ?: 0.0,
                    calf = values["calf"] ?: 0.0,
                    unit = "cm",
                )
                measurementEntries = measurementEntries + m
                Storage.putString(
                    requireContext(),
                    Storage.KEY_BODY_MEASUREMENTS,
                    measurementsToJsonString(measurementEntries),
                )
                Toast.makeText(requireContext(), R.string.measurement_saved, Toast.LENGTH_SHORT).show()
                renderMeasurements()
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun downloadCsv() {
        Toast.makeText(requireContext(), R.string.generating_csv, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsVM.state.value
            val all = historyVM.workouts.value
            val filtered = filterWorkouts(all)
            val toMs = System.currentTimeMillis()
            val fromMs = when (selectedRange) {
                Range.ALL -> filtered.minOfOrNull { isoEpochMs(it.date) } ?: toMs
                Range.TODAY -> midnightToday()
                else -> toMs - selectedRange.days.toLong() * 24 * 3600 * 1000
            }
            val opts = ReportOptions(
                userName = settings.name,
                unit = settings.unit,
                range = ReportRange(fromMs, toMs, getString(selectedRange.labelRes)),
                workouts = filtered,
                includeSets = true,
                includeSummary = true,
                includePRs = true,
            )
            val uri = withContext(Dispatchers.IO) { buildAndSaveCsv(requireContext(), opts) }
            Toast.makeText(
                requireContext(),
                if (uri != null) R.string.csv_saved else R.string.csv_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun downloadReport() {
        val settings = settingsVM.state.value
        val all = historyVM.workouts.value
        val filtered = filterWorkouts(all)
        val toMs = System.currentTimeMillis()
        val fromMs = when (selectedRange) {
            Range.ALL -> filtered.minOfOrNull { isoEpochMs(it.date) } ?: toMs
            Range.TODAY -> midnightToday()
            else -> toMs - selectedRange.days.toLong() * 24 * 3600 * 1000
        }

        val opts = ReportOptions(
            userName = settings.name,
            unit = settings.unit,
            range = ReportRange(fromMs, toMs, getString(selectedRange.labelRes)),
            workouts = filtered,
            includeSets = true,
            includeSummary = true,
            includePRs = true,
        )
        val uri = buildAndSaveReport(requireContext(), opts)
        Toast.makeText(
            requireContext(),
            if (uri != null) R.string.report_saved else R.string.report_save_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderItalicLast(fullName: String): CharSequence {
        val parts = fullName.trim().split(" ")
        if (parts.size < 2) return fullName
        val first = parts.dropLast(1).joinToString(" ")
        val last = parts.last()
        val builder = SpannableStringBuilder("$first ")
        val start = builder.length
        builder.append(last)
        builder.setSpan(
            StyleSpan(android.graphics.Typeface.ITALIC),
            start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

