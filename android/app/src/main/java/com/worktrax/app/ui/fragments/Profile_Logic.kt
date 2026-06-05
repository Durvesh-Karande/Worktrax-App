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
import com.worktrax.app.R
import com.worktrax.app.data.BodyMeasurement
import com.worktrax.app.data.BodyweightEntry
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.databinding.ProfileDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.isoEpochMs
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.bodyweightFromJsonString
import com.worktrax.app.lib.bodyweightToJsonString
import com.worktrax.app.lib.formatShortDate
import com.worktrax.app.lib.isoEpochMs
import com.worktrax.app.lib.measurementsFromJsonString
import com.worktrax.app.lib.measurementsToJsonString
import com.worktrax.app.lib.nowIso
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("ClassName")
class Profile_Logic : Fragment() {

    private var _binding: ProfileDesignBinding? = null
    private val binding get() = _binding!!

    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })
    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })

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
        AnalyticsHelper.screenView("profile")

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsVM.state.collectLatest { settings ->
                    binding.tvProfileName.text = if (settings.name.isNotBlank()) renderItalicLast(settings.name) else getString(R.string.your_name_placeholder)
                    binding.tvAvatarInitials.text = if (settings.name.isNotBlank()) settings.name.trim().first().uppercase() else "?"
                    val workouts = historyVM.workouts.value
                    val sinceText = if (workouts.isEmpty()) getString(R.string.profile_just_started)
                    else {
                        val first = workouts.minByOrNull { isoEpochMs(it.date) } ?: workouts.first()
                        val cal = java.util.Calendar.getInstance().apply { time = java.util.Date(isoEpochMs(first.date)) }
                        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                        "${getString(R.string.profile_since)} ${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
                    }
                    binding.tvProfileSub.text = "$sinceText · ${workouts.size} ${getString(R.string.profile_sessions)}"
                }
            }
        }

        binding.btnAddBodyweight.setOnClickListener { addBodyweightDialog() }
        binding.btnAddMeasurement.setOnClickListener { addMeasurementDialog() }
        loadBodyweight()
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
                    AnalyticsHelper.bodyweightLogged(unit.code)
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
                AnalyticsHelper.measurementLogged()
                Toast.makeText(requireContext(), R.string.measurement_saved, Toast.LENGTH_SHORT).show()
                renderMeasurements()
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
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

