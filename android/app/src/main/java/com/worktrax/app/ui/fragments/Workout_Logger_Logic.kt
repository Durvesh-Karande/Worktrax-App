package com.worktrax.app.ui.fragments

import android.app.AlertDialog
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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
import com.worktrax.app.data.SetEntry
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.databinding.WorkoutLoggerDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.volumeOf
import com.worktrax.app.lib.weightStep
import com.worktrax.app.store.lastSetForExercise
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SessionViewModel
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Suppress("ClassName")
class Workout_Logger_Logic : Fragment() {

    private var _binding: WorkoutLoggerDesignBinding? = null
    private val binding get() = _binding!!

    private val sessionVM: SessionViewModel by viewModels({ requireActivity() })
    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })
    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })

    private var currentMetricType = "strength"
    private var currentReps = 8
    private var currentWeight = 80.0
    private var totalSets = 4
    private var currentWarmup = false
    private var currentRpe: Int? = null
    private var currentDuration = 30
    private var currentDurationSec = 0
    private var currentDistance = 1.0
    private var currentRounds = 4
    private var lastInitializedExerciseId: String? = null

    private var restTimer: CountDownTimer? = null
    private var restSeconds = 60

    private var workoutTimer: CountDownTimer? = null
    private var isWorkoutTimerRunning = false
    private var workoutTimerElapsedSec: Int = 0
    private var timerEndSound: MediaPlayer? = null

    private fun haptic() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WorkoutLoggerDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AnalyticsHelper.screenView("workout_logger")
        setupSteppers()
        setupWarmupRpe()
        setupObservers()
        setupListeners()
    }

    private fun showFieldsForType(metricType: String) {
        binding.layoutStrengthFields.visibility = if (metricType == "strength") View.VISIBLE else View.GONE
        binding.layoutBodyweightFields.visibility = if (metricType == "bodyweight") View.VISIBLE else View.GONE
        binding.layoutTimedFields.visibility = if (metricType == "timed") View.VISIBLE else View.GONE
        binding.layoutCardioFields.visibility = if (metricType == "cardio") View.VISIBLE else View.GONE
        binding.layoutHiitFields.visibility = if (metricType == "hiit") View.VISIBLE else View.GONE
        binding.layoutYogaFields.visibility = if (metricType == "yoga") View.VISIBLE else View.GONE

        binding.tvUnit.visibility = if (metricType in listOf("strength", "bodyweight")) View.VISIBLE else View.GONE
        binding.btnPlateCalc.visibility = if (metricType == "strength") View.VISIBLE else View.GONE

        val isTimedType = metricType in listOf("timed", "cardio", "yoga", "hiit")
        binding.containerWorkoutTimer.visibility = if (isTimedType) View.VISIBLE else View.GONE
        binding.btnLogSet.visibility = if (isTimedType) View.GONE else View.VISIBLE
        if (isTimedType) stopWorkoutTimer()
    }

    private fun setupWarmupRpe() {
        binding.btnWarmupToggle.setOnClickListener {
            currentWarmup = !currentWarmup
            binding.btnWarmupToggle.text = if (currentWarmup) "ON" else "OFF"
            binding.btnWarmupToggle.setBackgroundResource(
                if (currentWarmup) R.drawable.shape_chip_selected else R.drawable.shape_chip
            )
            binding.btnWarmupToggle.setTextColor(
                ContextCompat.getColor(requireContext(), if (currentWarmup) R.color.white else R.color.ink_3)
            )
            haptic()
        }
        binding.layoutRpeButtons.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (6 * density).toInt()
        for (rpe in 1..10) {
            val btn = TextView(requireContext()).apply {
                text = rpe.toString()
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_2))
                setBackgroundResource(R.drawable.shape_chip)
                setPadding(12, 6, 12, 6)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, gap, 0) }
                setOnClickListener {
                    currentRpe = if (currentRpe == rpe) null else rpe
                    updateRpeChips()
                }
            }
            binding.layoutRpeButtons.addView(btn)
        }
    }

    private fun updateRpeChips() {
        for (i in 0 until binding.layoutRpeButtons.childCount) {
            val chip = binding.layoutRpeButtons.getChildAt(i) as TextView
            val rpe = i + 1
            val selected = currentRpe == rpe
            chip.setBackgroundResource(
                if (selected) R.drawable.shape_chip_selected else R.drawable.shape_chip
            )
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.ink_2)
            )
        }
    }

    private fun setupStepper(
        label: String, value: Int, min: Int, max: Int,
        tvLabel: TextView, tvValue: TextView, btnMinus: View, btnPlus: View,
        onChanged: (Int) -> Unit
    ) {
        tvLabel.text = label
        tvValue.text = value.toString()
        btnMinus.setOnClickListener {
            val v = onChanged(0) // just to read current
            val cur = tvValue.text.toString().toIntOrNull() ?: value
            if (cur > min) {
                val next = cur - 1
                tvValue.text = next.toString()
                onChanged(next)
                haptic()
            }
        }
        btnPlus.setOnClickListener {
            val cur = tvValue.text.toString().toIntOrNull() ?: value
            if (cur < max) {
                val next = cur + 1
                tvValue.text = next.toString()
                onChanged(next)
                haptic()
            }
        }
        tvValue.setOnClickListener {
            showNumberInput(label, tvValue.text.toString().toIntOrNull() ?: value, min, max, 1) { v ->
                tvValue.text = v.toString()
                onChanged(v)
            }
        }
    }

    private val unitCode: String get() = settingsVM.state.value.unit.code.uppercase()

    private fun setupSteppers() {
        // Strength steppers
        setupStepper(
            getString(R.string.reps_stepper_label), currentReps, 0, 999,
            binding.stepperReps.tvStepperLabel, binding.stepperReps.tvValue,
            binding.stepperReps.btnMinus, binding.stepperReps.btnPlus
        ) { v -> currentReps = v }

        // Weight stepper (special: decimal steps)
        binding.stepperWeight.tvStepperLabel.text = "Weight ($unitCode)"
        binding.stepperWeight.tvValue.text = currentWeight.toInt().toString()
        binding.stepperWeight.btnMinus.setOnClickListener {
            if (currentWeight > 0) {
                currentWeight = (currentWeight - weightStep(settingsVM.state.value.unit)).coerceAtLeast(0.0)
                binding.stepperWeight.tvValue.text = currentWeight.toInt().toString()
                haptic()
            }
        }
        binding.stepperWeight.btnPlus.setOnClickListener {
            currentWeight += weightStep(settingsVM.state.value.unit)
            binding.stepperWeight.tvValue.text = currentWeight.toInt().toString()
            haptic()
        }
        binding.stepperWeight.tvValue.setOnClickListener {
            showDecimalInput("Weight ($unitCode)", currentWeight, 0.0, 999.0) { v ->
                currentWeight = v
                binding.stepperWeight.tvValue.text = currentWeight.toInt().toString()
            }
        }

        setupStepper(
            getString(R.string.total_sets_stepper_label), totalSets, 1, 12,
            binding.stepperTotalSets.tvStepperLabel, binding.stepperTotalSets.tvValue,
            binding.stepperTotalSets.btnMinus, binding.stepperTotalSets.btnPlus
        ) { v -> totalSets = v; refreshTable() }

        // Bodyweight steppers
        setupStepper(
            getString(R.string.reps_stepper_label), currentReps, 0, 999,
            binding.sbwReps.tvStepperLabel, binding.sbwReps.tvValue,
            binding.sbwReps.btnMinus, binding.sbwReps.btnPlus
        ) { v -> currentReps = v }
        setupStepper(
            getString(R.string.total_sets_stepper_label), totalSets, 1, 12,
            binding.sbwTotalSets.tvStepperLabel, binding.sbwTotalSets.tvValue,
            binding.sbwTotalSets.btnMinus, binding.sbwTotalSets.btnPlus
        ) { v -> totalSets = v; refreshTable() }

        // Cardio steppers
        setupStepper("Min", currentDuration, 0, 999,
            binding.scDurationMin.tvStepperLabel, binding.scDurationMin.tvValue,
            binding.scDurationMin.btnMinus, binding.scDurationMin.btnPlus
        ) { v -> currentDuration = v }
        setupStepper("Sec", currentDurationSec, 0, 59,
            binding.scDurationSec.tvStepperLabel, binding.scDurationSec.tvValue,
            binding.scDurationSec.btnMinus, binding.scDurationSec.btnPlus
        ) { v -> currentDurationSec = v }
        setupStepper("Distance (km)", (currentDistance * 10).toInt(), 0, 999,
            binding.scDistance.tvStepperLabel, binding.scDistance.tvValue,
            binding.scDistance.btnMinus, binding.scDistance.btnPlus
        ) { v -> currentDistance = v / 10.0 }
        // Override value click for min/sec to show quick picker
        binding.scDurationMin.tvValue.setOnClickListener { showDurationPicker("Min", binding.scDurationMin.tvValue, 0, 999, 1) { n -> currentDuration = n; binding.scDurationMin.tvValue.text = n.toString() } }
        binding.scDurationSec.tvValue.setOnClickListener { showDurationPicker("Sec", binding.scDurationSec.tvValue, 0, 59, 5) { n -> currentDurationSec = n; binding.scDurationSec.tvValue.text = n.toString() } }

        // HIIT steppers
        setupStepper("Min", currentDuration, 0, 999,
            binding.shiitMin.tvStepperLabel, binding.shiitMin.tvValue,
            binding.shiitMin.btnMinus, binding.shiitMin.btnPlus
        ) { v -> currentDuration = v }
        setupStepper("Sec", currentDurationSec, 0, 59,
            binding.shiitSec.tvStepperLabel, binding.shiitSec.tvValue,
            binding.shiitSec.btnMinus, binding.shiitSec.btnPlus
        ) { v -> currentDurationSec = v }
        binding.shiitMin.tvValue.setOnClickListener { showDurationPicker("Min", binding.shiitMin.tvValue, 0, 999, 1) { n -> currentDuration = n; binding.shiitMin.tvValue.text = n.toString() } }
        binding.shiitSec.tvValue.setOnClickListener { showDurationPicker("Sec", binding.shiitSec.tvValue, 0, 59, 5) { n -> currentDurationSec = n; binding.shiitSec.tvValue.text = n.toString() } }

        // Timed steppers
        setupStepper("Hold (sec)", currentDuration, 1, 999,
            binding.stDuration.tvStepperLabel, binding.stDuration.tvValue,
            binding.stDuration.btnMinus, binding.stDuration.btnPlus
        ) { v -> currentDuration = v }
        setupStepper("Sets", totalSets, 1, 12,
            binding.stTotalSets.tvStepperLabel, binding.stTotalSets.tvValue,
            binding.stTotalSets.btnMinus, binding.stTotalSets.btnPlus
        ) { v -> totalSets = v; refreshTable() }

        // Yoga steppers
        setupStepper("Duration (min)", currentDuration, 1, 999,
            binding.syDuration.tvStepperLabel, binding.syDuration.tvValue,
            binding.syDuration.btnMinus, binding.syDuration.btnPlus
        ) { v -> currentDuration = v }
    }

    private fun showNumberInput(
        label: String,
        current: Int,
        min: Int,
        max: Int,
        step: Int,
        onSet: (Int) -> Unit
    ) {
        val input = EditText(requireContext()).apply {
            setText(current.toString())
            selectAll()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.enter_label, label))
            .setView(input)
            .setPositiveButton(R.string.set_label_action) { _, _ ->
                val v = input.text.toString().toIntOrNull()
                if (v != null) onSet(v.coerceIn(min, max))
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun showDecimalInput(
        label: String,
        current: Double,
        min: Double,
        max: Double,
        onSet: (Double) -> Unit
    ) {
        val input = EditText(requireContext()).apply {
            setText(current.toInt().toString())
            selectAll()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.enter_label, label))
            .setView(input)
            .setPositiveButton(R.string.set_label_action) { _, _ ->
                val v = input.text.toString().toDoubleOrNull()
                if (v != null) onSet(v.coerceIn(min, max))
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sessionVM.state, settingsVM.state) { session, settings ->
                    session to settings.unit
                }.collectLatest { (session, unit) ->
                    if (!isAdded || _binding == null) return@collectLatest
                    binding.tvTitle.text = getString(R.string.workout_logger_title)
                    binding.tvMuscleKicker.text = "${session.muscle?.uppercase() ?: getString(R.string.muscle_logging_placeholder)} ${getString(R.string.type_logging_suffix)}"

                    val currentEx = session.exercises.find { it.id == session.currentExerciseId }
                    binding.tvExerciseName.text = currentEx?.name ?: getString(R.string.no_exercise_selected)

                    // Update metric type from current exercise
                    if (currentEx != null) {
                        if (currentEx.metricType != currentMetricType) {
                            currentMetricType = currentEx.metricType
                            showFieldsForType(currentMetricType)
                            resetDefaults()
                        }
                        showPreviousValues(currentEx, unit)
                    }

                    binding.tvUnit.text = unit.code.uppercase()

                    val loggedCount = currentEx?.sets?.size ?: 0

                    val hasLimit = currentMetricType in listOf("strength", "bodyweight", "timed")
                    val displayTotal = if (hasLimit) totalSets else 1
                    val currentIndex = loggedCount + 1
                    binding.tvSetCounter.text =
                        if (hasLimit && currentIndex > displayTotal) getString(R.string.all_sets_done)
                        else if (hasLimit) getString(R.string.set_counter_format, currentIndex, displayTotal)
                        else ""

                    binding.stepperTotalSets.root.visibility = if (hasLimit) View.VISIBLE else View.GONE
                    binding.sbwTotalSets.root.visibility = if (hasLimit) View.VISIBLE else View.GONE

                    binding.layoutWarmup.visibility = if (currentMetricType == "strength") View.VISIBLE else View.GONE
                    binding.layoutRpe.visibility = if (currentMetricType == "strength") View.VISIBLE else View.GONE

                    binding.btnUndo.visibility = if (loggedCount > 0) View.VISIBLE else View.GONE

                    updateTableHeaders(currentMetricType)
                    updateLoggedSets(currentEx?.sets ?: emptyList(), unit, currentMetricType)
                }
            }
        }
    }

    private fun showPreviousValues(currentEx: com.worktrax.app.data.ExerciseEntry, unit: WeightUnit) {
        if (currentEx.sets.isNotEmpty()) {
            binding.tvPreviousValues.visibility = View.GONE
            return
        }
        val lastSet = lastSetForExercise(historyVM.workouts.value, currentEx.id)

        // Initialize state variables for this exercise once per session
        if (lastInitializedExerciseId != currentEx.id) {
            lastInitializedExerciseId = currentEx.id
            if (lastSet != null) {
                when (currentMetricType) {
                    "strength" -> {
                        currentWeight = if (lastSet.unit == unit) lastSet.weight
                        else com.worktrax.app.lib.convertWeight(lastSet.weight, lastSet.unit, unit)
                        currentReps = lastSet.reps
                        totalSets = 4
                    }
                    "bodyweight" -> {
                        currentReps = lastSet.reps
                        totalSets = 3
                    }
                    "timed" -> {
                        currentDuration = lastSet.durationSec
                        totalSets = 3
                    }
                    "cardio" -> {
                        currentDuration = lastSet.durationSec / 60
                        currentDurationSec = lastSet.durationSec % 60
                        currentDistance = lastSet.distanceKm
                    }
                    "hiit" -> {
                        currentDuration = lastSet.durationSec / 60
                        currentDurationSec = lastSet.durationSec % 60
                    }
                    "yoga" -> {
                        currentDuration = lastSet.durationSec / 60
                        currentDurationSec = lastSet.durationSec % 60
                    }
                }
            } else {
                resetDefaults()
            }
            syncStepperValues()
        }

        if (lastSet == null) {
            binding.tvPreviousValues.visibility = View.GONE
            return
        }

        val lastSummary = when (currentMetricType) {
            "strength" -> {
                val w = if (lastSet.unit == unit) lastSet.weight
                else com.worktrax.app.lib.convertWeight(lastSet.weight, lastSet.unit, unit)
                "${w.toInt()} ${unit.code} x ${lastSet.reps} reps"
            }
            "bodyweight" -> "${lastSet.reps} reps"
            "timed" -> "${lastSet.durationSec}s hold"
            "cardio" -> "${formatDuration(lastSet.durationSec)}, ${lastSet.distanceKm} km"
            "hiit" -> formatDuration(lastSet.durationSec)
            "yoga" -> formatDuration(lastSet.durationSec)
            else -> ""
        }

        if (lastSummary.isNotBlank()) {
            binding.tvPreviousValues.text = getString(R.string.last_time_suggestion, lastSummary)
            binding.tvPreviousValues.visibility = View.VISIBLE
        } else {
            binding.tvPreviousValues.visibility = View.GONE
        }
    }

    private fun updateTableHeaders(metricType: String) {
        val (left, right) = when (metricType) {
            "strength" -> Pair("Reps", "Weight")
            "bodyweight" -> Pair("Reps", "")
            "timed" -> Pair("Hold", "")
            "cardio" -> Pair("Duration", "Distance")
            "hiit" -> Pair("Duration", "")
            "yoga" -> Pair("Duration", "")
            else -> Pair("Reps", "Weight")
        }
        binding.tblColLeft.text = left
        binding.tblColRight.text = right
    }

    private fun resetDefaults() {
        when (currentMetricType) {
            "strength" -> { currentReps = 8; currentWeight = 80.0; totalSets = 4; currentWarmup = false; currentRpe = null }
            "bodyweight" -> { currentReps = 10; totalSets = 3 }
            "timed" -> { currentDuration = 30; totalSets = 3 }
            "cardio" -> { currentDuration = 30; currentDurationSec = 0; currentDistance = 5.0 }
            "hiit" -> { currentDuration = 5; currentDurationSec = 0 }
            "yoga" -> { currentDuration = 5; currentDurationSec = 0 }
        }
        syncStepperValues()
    }

    private fun syncStepperValues() {
        binding.stepperReps.tvValue.text = currentReps.toString()
        binding.stepperWeight.tvValue.text = currentWeight.toInt().toString()
        binding.stepperTotalSets.tvValue.text = totalSets.toString()
        binding.sbwReps.tvValue.text = currentReps.toString()
        binding.sbwTotalSets.tvValue.text = totalSets.toString()
        binding.scDurationMin.tvValue.text = currentDuration.toString()
        binding.scDurationSec.tvValue.text = currentDurationSec.toString()
        binding.scDistance.tvValue.text = (currentDistance * 10).toInt().toString()
        binding.shiitMin.tvValue.text = currentDuration.toString()
        binding.shiitSec.tvValue.text = currentDurationSec.toString()
        binding.stDuration.tvValue.text = currentDuration.toString()
        binding.stTotalSets.tvValue.text = totalSets.toString()
        binding.syDuration.tvValue.text = currentDuration.toString()
    }

    private fun refreshTable() {
        val session = sessionVM.state.value
        val currentEx = session.exercises.find { it.id == session.currentExerciseId }
        val sets = currentEx?.sets ?: emptyList()
        updateLoggedSets(sets, settingsVM.state.value.unit, currentMetricType)
        val hasLimit = currentMetricType in listOf("strength", "bodyweight", "timed")
        val displayTotal = if (hasLimit) totalSets else 1
        val currentIndex = sets.size + 1
        binding.tvSetCounter.text =
            if (hasLimit && currentIndex > displayTotal) getString(R.string.all_sets_done)
            else if (hasLimit) getString(R.string.set_counter_format, currentIndex, displayTotal)
            else ""
    }

    private fun updateLoggedSets(sets: List<SetEntry>, unit: WeightUnit, metricType: String) {
        binding.listLoggedSets.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        val hasLimit = metricType in listOf("strength", "bodyweight", "timed")
        val displayTotal = if (hasLimit) totalSets else 1

        for (i in 1..displayTotal) {
            val row = inflater.inflate(R.layout.item_set_row, binding.listLoggedSets, false)
            val logged = sets.getOrNull(i - 1)
            val isCurrent = logged == null && i == sets.size + 1

            row.findViewById<TextView>(R.id.tv_set_number).text = i.toString()
            val tvReps = row.findViewById<TextView>(R.id.tv_reps)
            val tvWeight = row.findViewById<TextView>(R.id.tv_weight)
            val tvUnit = row.findViewById<TextView>(R.id.tv_unit)

            if (logged != null) {
                val (leftText, rightText, unitText) = formatSetRow(logged, unit)
                tvReps.text = leftText
                tvWeight.text = rightText
                tvUnit.text = unitText
                if (logged.warmup) {
                    row.alpha = 0.6f
                    row.findViewById<TextView>(R.id.tv_set_number).setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.ink_3)
                    )
                }
            } else if (isCurrent) {
                val (leftText, rightText, unitText) = formatPendingRow(metricType, unit)
                tvReps.text = leftText
                tvWeight.text = rightText
                tvUnit.text = unitText
                row.setBackgroundResource(R.drawable.shape_set_current)
                row.findViewById<TextView>(R.id.tv_set_number)
                    .setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                row.scaleX = 0.95f
                row.scaleY = 0.95f
                row.animate().scaleX(1f).scaleY(1f).setDuration(250)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else {
                tvReps.text = "—"
                tvWeight.text = "—"
                tvUnit.text = ""
            }
            if (i == displayTotal) {
                row.findViewById<View>(R.id.divider).visibility = View.GONE
            }
            binding.listLoggedSets.addView(row)
        }
    }

    private fun formatSetRow(set: SetEntry, unit: WeightUnit): Triple<String, String, String> {
        return when (set.metricType) {
            "strength" -> {
                val rpe = if (set.rpe != null) " @${set.rpe}" else ""
                Triple(set.reps.toString(), "${set.weight}$rpe", set.unit.code)
            }
            "bodyweight" -> Triple("${set.reps} reps", "", "")
            "timed" -> Triple("${set.durationSec}s", "", "")
            "cardio" -> Triple(formatDuration(set.durationSec), "${set.distanceKm} km", "")
            "hiit" -> Triple(formatDuration(set.durationSec), "", "")
            "yoga" -> Triple(formatDuration(set.durationSec), "", "")
            else -> Triple(set.reps.toString(), set.weight.toString(), set.unit.code)
        }
    }

    private fun formatPendingRow(metricType: String, unit: WeightUnit): Triple<String, String, String> {
        return when (metricType) {
            "strength" -> {
                val rpe = if (currentRpe != null) " @${currentRpe}" else ""
                Triple(currentReps.toString(), "${currentWeight.toInt()}$rpe", unit.code)
            }
            "bodyweight" -> Triple("${currentReps} reps", "", "")
            "timed" -> Triple("${currentDuration}s", "", "")
            "cardio" -> Triple("${currentDuration} min ${currentDurationSec} sec", "${currentDistance} km", "")
            "hiit" -> Triple("${currentDuration} min ${currentDurationSec} sec", "", "")
            "yoga" -> Triple("${currentDuration} min ${currentDurationSec} sec", "", "")
            else -> Triple(currentReps.toString(), currentWeight.toInt().toString(), unit.code)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            AnalyticsHelper.workoutCancelled(sessionVM.state.value.type?.code ?: "unknown")
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.exit_workout_title)
                .setMessage(R.string.exit_workout_message)
                .setPositiveButton(R.string.exit_button) { _, _ ->
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.keep_logging_button, null)
                .show()
        }

        binding.btnLogSet.setOnClickListener {
            val session = sessionVM.state.value
            val currentEx = session.exercises.find { it.id == session.currentExerciseId }
            if (currentEx == null) {
                Toast.makeText(requireContext(), R.string.no_exercise_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val loggedCount = currentEx.sets.size
            val hasLimit = currentMetricType in listOf("strength", "bodyweight", "timed")
            if (!hasLimit || loggedCount < totalSets) {
                val unit = settingsVM.state.value.unit
                when (currentMetricType) {
                    "strength" -> {
                        sessionVM.addSet(reps = currentReps, weight = currentWeight, unit = unit, warmup = currentWarmup, rpe = currentRpe)
                        AnalyticsHelper.setLogged(currentEx.name, currentReps, currentWeight, currentWarmup, currentRpe)
                    }
                    "bodyweight" -> {
                        sessionVM.addSet(reps = currentReps)
                        AnalyticsHelper.setLogged(currentEx.name, currentReps, 0.0, false, null)
                    }
                    "timed" -> {
                        sessionVM.addSet(durationSec = currentDuration)
                    }
                    "cardio" -> {
                        sessionVM.addSet(durationSec = currentDuration, distanceKm = currentDistance)
                    }
                    "hiit" -> {
                        sessionVM.addSet(durationSec = currentDuration)
                    }
                    "yoga" -> {
                        sessionVM.addSet(durationSec = currentDuration)
                    }
                }
                startRestTimer()
            }
        }

        binding.btnSkipRest.setOnClickListener { stopRestTimer() }

        binding.btnFinishWorkout.setOnClickListener {
            if (_binding == null || !isAdded) return@setOnClickListener
            stopRestTimer()
            val workout = try { sessionVM.finish() } catch (e: Exception) { null }
            if (workout != null) {
                try { historyVM.add(workout) } catch (_: Exception) {}
                try {
                    val vol = volumeOf(workout, settingsVM.state.value.unit)
                    AnalyticsHelper.workoutCompleted(workout.type.code, workout.exercises.size, vol.toDouble(), workout.durationSec)
                } catch (_: Exception) {}
                try {
                    val bundle = Bundle().apply { putString("workoutId", workout.id) }
                    findNavController().navigate(R.id.action_log_to_summary, bundle)
                } catch (e: Exception) {
                    findNavController().popBackStack()
                }
            } else {
                try {
                    AnalyticsHelper.workoutCancelled(sessionVM.state.value.type?.code ?: "unknown")
                    findNavController().popBackStack()
                } catch (_: Exception) {}
            }
        }

        binding.btnAddExercise.setOnClickListener {
            findNavController().navigate(R.id.action_log_to_exercise)
        }

        binding.btnUndo.setOnClickListener {
            val session = sessionVM.state.value
            val currentEx = session.exercises.find { it.id == session.currentExerciseId }
            if (currentEx != null && currentEx.sets.isNotEmpty()) {
                sessionVM.removeLastSet()
                Toast.makeText(requireContext(), R.string.set_undone, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.nothing_to_undo, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPlateCalc.setOnClickListener { showPlateCalculator() }

        binding.btnTimerStart.setOnClickListener {
            if (isWorkoutTimerRunning) return@setOnClickListener
            val session = sessionVM.state.value
            val currentEx = session.exercises.find { it.id == session.currentExerciseId }
            if (currentEx == null) {
                Toast.makeText(requireContext(), R.string.no_exercise_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startWorkoutTimer()
        }
        binding.btnTimerStop.setOnClickListener {
            if (!isWorkoutTimerRunning) return@setOnClickListener
            stopWorkoutTimer()
            logTimedSet()
        }
    }

    private fun showPlateCalculator() {
        val weight = currentWeight
        val unit = settingsVM.state.value.unit
        val barWeight = if (unit == WeightUnit.KG) 20.0 else 45.0
        val availablePlates = if (unit == WeightUnit.KG)
            listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        else
            listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

        val perSide = (weight - barWeight) / 2.0
        if (perSide <= 0) {
            Toast.makeText(requireContext(), "Use just the bar (${barWeight.toInt()} ${unit.code})", Toast.LENGTH_SHORT).show()
            return
        }

        var remaining = perSide
        val plates = mutableListOf<Double>()
        for (p in availablePlates) {
            while (remaining >= p - 0.01) {
                plates.add(p)
                remaining -= p
            }
        }

        val perSideText = plates.joinToString(" + ") { "${it.toInt()} ${unit.code}" }
        val totalText = buildString {
            appendLine("Bar: ${barWeight.toInt()} ${unit.code}")
            appendLine("Each side: $perSideText")
            if (remaining > 0.01) appendLine("Remaining: ${"%.1f".format(remaining)} ${unit.code}")
            appendLine()
            append("Total: ${weight.toInt()} ${unit.code}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.plate_calculator)
            .setMessage(totalText)
            .setPositiveButton(R.string.done_label, null)
            .show()
    }

    private fun startRestTimer() {
        stopRestTimer()
        restSeconds = 60
        binding.containerRestTimer.visibility = View.VISIBLE
        binding.tvRestTimer.text = restSeconds.toString()
        restTimer = object : CountDownTimer(restSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                restSeconds = (millisUntilFinished / 1000).toInt()
                binding.tvRestTimer.text = restSeconds.toString()
            }
            override fun onFinish() {
                binding.containerRestTimer.visibility = View.GONE
                binding.tvRestTimer.text = "0"
            }
        }.start()
    }

    private fun stopRestTimer() {
        restTimer?.cancel()
        restTimer = null
        binding.containerRestTimer.visibility = View.GONE
    }

    private fun formatDuration(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m} min ${s} sec" else "${s} sec"
    }

    private fun formatTimerDisplay(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun showDurationPicker(
        label: String, tvValue: TextView, min: Int, max: Int, step: Int,
        onSet: (Int) -> Unit
    ) {
        val commonValues = when {
            label == "Min" -> listOf(0, 1, 2, 3, 5, 10, 15, 20, 30, 45, 60)
            label == "Sec" -> listOf(0, 5, 10, 15, 20, 30, 45)
            else -> (min..max step step).take(20).toList()
        }
        val current = tvValue.text.toString().toIntOrNull() ?: min
        val items = commonValues.map { it.toString() }.toTypedArray()
        val checked = commonValues.indexOfFirst { it >= current }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(label)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                val v = commonValues[which]
                tvValue.text = v.toString()
                onSet(v)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun startWorkoutTimer() {
        stopWorkoutTimer()
        val target = when (currentMetricType) {
            "cardio" -> (currentDuration * 60 + currentDurationSec).coerceAtLeast(1)
            "hiit" -> (currentDuration * 60 + currentDurationSec).coerceAtLeast(1)
            "yoga" -> (currentDuration * 60 + currentDurationSec).coerceAtLeast(1)
            else -> currentDuration.coerceAtLeast(1)
        }
        workoutTimerElapsedSec = 0
        isWorkoutTimerRunning = true
        binding.btnTimerStart.isEnabled = false
        binding.btnTimerStop.isEnabled = true
        binding.tvWorkoutTimer.text = formatTimerDisplay(target)
        workoutTimer = object : CountDownTimer((target * 1000L) + 500, 1000L) {
            var remaining = target
            override fun onTick(millisUntilFinished: Long) {
                remaining = ((millisUntilFinished + 500) / 1000).toInt()
                workoutTimerElapsedSec = target - remaining
                binding.tvWorkoutTimer.text = formatTimerDisplay(remaining)
            }
            override fun onFinish() {
                workoutTimerElapsedSec = target
                binding.tvWorkoutTimer.text = "00:00"
                isWorkoutTimerRunning = false
                binding.btnTimerStart.isEnabled = true
                binding.btnTimerStop.isEnabled = false
                logTimedSet()
                showTimerEndDialog()
            }
        }.start()
    }

    private fun showTimerEndDialog() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            timerEndSound = MediaPlayer.create(requireContext(), uri).apply {
                isLooping = true
                setVolume(0.5f, 0.5f)
                start()
            }
        } catch (_: Exception) {}

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.timer_ends))
            .setPositiveButton(getString(R.string.cancel_label)) { _, _ -> stopTimerSound() }
            .setOnDismissListener { stopTimerSound() }
            .setCancelable(true)
            .show()
    }

    private fun stopTimerSound() {
        timerEndSound?.apply {
            if (isPlaying) stop()
            release()
        }
        timerEndSound = null
    }

    private fun stopWorkoutTimer() {
        workoutTimer?.cancel()
        workoutTimer = null
        isWorkoutTimerRunning = false
        binding.btnTimerStart.isEnabled = true
        binding.btnTimerStop.isEnabled = false
    }

    private fun logTimedSet() {
        when (currentMetricType) {
            "timed" -> {
                sessionVM.addSet(durationSec = currentDuration)
            }
            "cardio" -> {
                val totalSec = if (workoutTimerElapsedSec > 0) workoutTimerElapsedSec else currentDuration * 60 + currentDurationSec
                sessionVM.addSet(durationSec = totalSec, distanceKm = currentDistance)
            }
            "hiit" -> {
                val totalSec = if (workoutTimerElapsedSec > 0) workoutTimerElapsedSec else currentDuration * 60 + currentDurationSec
                sessionVM.addSet(durationSec = totalSec)
            }
            "yoga" -> {
                val totalSec = if (workoutTimerElapsedSec > 0) workoutTimerElapsedSec else currentDuration * 60 + currentDurationSec
                sessionVM.addSet(durationSec = totalSec)
            }
        }
        stopRestTimer()
        startRestTimer()
    }

    override fun onDestroyView() {
        stopWorkoutTimer()
        stopRestTimer()
        stopTimerSound()
        super.onDestroyView()
        _binding = null
    }
}
