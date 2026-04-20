package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.databinding.ProfileDesignBinding
import com.worktrax.app.lib.ReportOptions
import com.worktrax.app.lib.ReportRange
import com.worktrax.app.lib.buildAndSaveReport
import com.worktrax.app.lib.formatShortDate
import com.worktrax.app.lib.isoEpochMs
import com.worktrax.app.lib.numberWithCommas
import com.worktrax.app.lib.volumeOf
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

@Suppress("ClassName")
class Profile_Logic : Fragment() {

    private var _binding: ProfileDesignBinding? = null
    private val binding get() = _binding!!

    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })
    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })

    private enum class Range(val label: String, val days: Int) {
        TODAY("Today", 1), W7("7 days", 7), M1("30 days", 30), M3("90 days", 90), ALL("All time", Int.MAX_VALUE)
    }

    private var selectedRange: Range = Range.TODAY

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

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        setupRangeChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(settingsVM.state, historyVM.workouts) { s, h -> s to h }
                    .collectLatest { (settings, workouts) ->
                        binding.tvProfileName.text = renderItalicLast(settings.name)
                        binding.tvProfileSub.text =
                            "Since April 2024 · ${workouts.size} sessions"
                        renderStats(workouts, settings.unit)
                        renderChart(workouts, settings.unit)
                        renderFiltered(workouts, settings.unit)
                    }
            }
        }

        binding.btnDownloadPdf.setOnClickListener { downloadReport() }
    }

    private fun setupRangeChips() {
        binding.layoutRangeChips.removeAllViews()
        val density = resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (9 * density).toInt()
        val gap = (6 * density).toInt()

        Range.values().forEach { range ->
            val chip = TextView(requireContext()).apply {
                text = range.label
                textSize = 12f
                setPadding(padH, padV, padH, padV)
                setTextColor(resources.getColor(R.color.ink_2, null))
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
        for (i in 0 until binding.layoutRangeChips.childCount) {
            val chip = binding.layoutRangeChips.getChildAt(i) as TextView
            val selected = chip.text.toString() == selectedRange.label
            chip.setBackgroundResource(
                if (selected) R.drawable.shape_chip_selected else R.drawable.shape_chip
            )
            chip.setTextColor(
                resources.getColor(if (selected) R.color.paper else R.color.ink_2, null)
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

        // crude "day streak" — distinct dated days with a workout in the last 30 days
        val days = workouts.map { it.date.substring(0, 10) }.toSet()
        binding.tvStatStreak.text = days.size.coerceAtMost(99).toString()
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
                setTextColor(resources.getColor(R.color.ink_3, null))
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
        val days = arrayOf("S", "M", "T", "W", "T", "F", "S")
        return days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    private fun renderFiltered(allWorkouts: List<Workout>, unit: WeightUnit) {
        val filtered = filterWorkouts(allWorkouts).sortedByDescending { isoEpochMs(it.date) }
        binding.layoutFilteredLogs.removeAllViews()
        if (filtered.isEmpty()) {
            binding.tvNoLogs.visibility = View.VISIBLE
            return
        }
        binding.tvNoLogs.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        filtered.take(10).forEach { w ->
            val row = inflater.inflate(R.layout.item_recent_workout, binding.layoutFilteredLogs, false)
            val firstEx = w.exercises.firstOrNull()
            val title = firstEx?.name ?: w.type.code.uppercase()
            val meta = buildString {
                append(w.type.code.replaceFirstChar { it.uppercase() })
                if (firstEx?.muscle?.isNotBlank() == true) append(" · ").append(firstEx.muscle)
                append(" · ").append(w.exercises.sumOf { it.sets.size }).append(" sets")
            }
            row.findViewById<TextView>(R.id.tv_workout_date).text = formatShortDate(w.date)
            row.findViewById<TextView>(R.id.tv_workout_desc).text = "$title · $meta · ${volumeOf(w, unit)} ${unit.code}"
            binding.layoutFilteredLogs.addView(row)
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
            range = ReportRange(fromMs, toMs, selectedRange.label),
            workouts = filtered,
            includeSets = true,
            includeSummary = true,
            includePRs = true,
        )
        val uri = buildAndSaveReport(requireContext(), opts)
        Toast.makeText(
            requireContext(),
            if (uri != null) "Report saved to Downloads" else "Could not save report",
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
