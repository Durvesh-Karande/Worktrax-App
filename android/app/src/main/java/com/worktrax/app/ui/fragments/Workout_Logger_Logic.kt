package com.worktrax.app.ui.fragments

import android.app.AlertDialog
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

    private var currentReps = 8
    private var currentWeight = 80.0
    private var totalSets = 4
    private var currentWarmup = false
    private var currentRpe: Int? = null

    private var restTimer: CountDownTimer? = null
    private var restSeconds = 60

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
        binding.layoutRpe.visibility = View.VISIBLE
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

    private fun setupSteppers() {
        binding.stepperReps.tvStepperLabel.text = getString(R.string.reps_stepper_label)
        binding.stepperReps.tvValue.text = currentReps.toString()
        binding.stepperReps.btnMinus.setOnClickListener {
            if (currentReps > 0) {
                currentReps--
                binding.stepperReps.tvValue.text = currentReps.toString()
                haptic()
            }
        }
        binding.stepperReps.btnPlus.setOnClickListener {
            currentReps++
            binding.stepperReps.tvValue.text = currentReps.toString()
            haptic()
        }
        binding.stepperReps.tvValue.setOnClickListener {
            showNumberInput("Reps", currentReps, 0, 999, 1) { v ->
                currentReps = v
                binding.stepperReps.tvValue.text = currentReps.toString()
            }
        }

        binding.stepperWeight.tvStepperLabel.text = getString(R.string.weight_stepper_label)
        binding.stepperWeight.tvValue.text = currentWeight.toString()
        binding.stepperWeight.btnMinus.setOnClickListener {
            if (currentWeight > 0) {
                currentWeight = (currentWeight - weightStep(settingsVM.state.value.unit)).coerceAtLeast(0.0)
                binding.stepperWeight.tvValue.text = currentWeight.toString()
                haptic()
            }
        }
        binding.stepperWeight.btnPlus.setOnClickListener {
            currentWeight += weightStep(settingsVM.state.value.unit)
            binding.stepperWeight.tvValue.text = currentWeight.toString()
            haptic()
        }
        binding.stepperWeight.tvValue.setOnClickListener {
            showDecimalInput("Weight (${
                settingsVM.state.value.unit.code.uppercase()
            })", currentWeight, 0.0, 999.0) { v ->
                currentWeight = v
                binding.stepperWeight.tvValue.text = currentWeight.toString()
            }
        }

        binding.stepperTotalSets.tvStepperLabel.text = getString(R.string.total_sets_stepper_label)
        binding.stepperTotalSets.tvValue.text = totalSets.toString()
        binding.stepperTotalSets.btnMinus.setOnClickListener {
            if (totalSets > 1) {
                totalSets--
                binding.stepperTotalSets.tvValue.text = totalSets.toString()
                haptic()
                refreshTable()
            }
        }
        binding.stepperTotalSets.btnPlus.setOnClickListener {
            if (totalSets < 12) {
                totalSets++
                binding.stepperTotalSets.tvValue.text = totalSets.toString()
                haptic()
                refreshTable()
            }
        }
        binding.stepperTotalSets.tvValue.setOnClickListener {
            showNumberInput("Total sets", totalSets, 1, 12, 1) { v ->
                totalSets = v
                binding.stepperTotalSets.tvValue.text = totalSets.toString()
                refreshTable()
            }
        }
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
            setText(current.toString())
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
                    binding.tvTitle.text = getString(R.string.workout_logger_title)
                    binding.tvMuscleKicker.text = "${session.muscle?.uppercase() ?: getString(R.string.muscle_logging_placeholder)} ${getString(R.string.type_logging_suffix)}"

                    val currentEx = session.exercises.find { it.id == session.currentExerciseId }
                    binding.tvExerciseName.text = currentEx?.name ?: getString(R.string.no_exercise_selected)

                    // Show previous workout values
                    if (currentEx != null) {
                        val lastSet = lastSetForExercise(historyVM.workouts.value, currentEx.id)
                        if (lastSet != null && currentEx.sets.isEmpty()) {
                            val prevWeight = if (lastSet.unit == unit) lastSet.weight
                            else com.worktrax.app.lib.convertWeight(lastSet.weight, lastSet.unit, unit)
                            binding.tvPreviousValues.text = "Last: ${prevWeight} ${unit.code} × ${lastSet.reps} reps"
                            binding.tvPreviousValues.visibility = View.VISIBLE
                        } else {
                            binding.tvPreviousValues.visibility = View.GONE
                        }
                    } else {
                        binding.tvPreviousValues.visibility = View.GONE
                    }

                    binding.tvUnit.text = unit.code.uppercase()

                    val loggedCount = currentEx?.sets?.size ?: 0
                    val currentIndex = loggedCount + 1
                    binding.tvSetCounter.text =
                        if (currentIndex > totalSets) getString(R.string.all_sets_done)
                        else getString(R.string.set_counter_format, currentIndex, totalSets)

                    updateLoggedSets(currentEx?.sets ?: emptyList(), unit)
                }
            }
        }
    }

    private fun refreshTable() {
        val session = sessionVM.state.value
        val currentEx = session.exercises.find { it.id == session.currentExerciseId }
        val sets = currentEx?.sets ?: emptyList()
        updateLoggedSets(sets, settingsVM.state.value.unit)
        val currentIndex = sets.size + 1
        binding.tvSetCounter.text =
            if (currentIndex > totalSets) getString(R.string.all_sets_done)
            else getString(R.string.set_counter_format, currentIndex, totalSets)
    }

    private fun updateLoggedSets(sets: List<com.worktrax.app.data.SetEntry>, unit: WeightUnit) {
        val newCount = sets.size
        binding.listLoggedSets.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (i in 1..totalSets) {
            val row = inflater.inflate(R.layout.item_set_row, binding.listLoggedSets, false)
            val logged = sets.getOrNull(i - 1)
            val isCurrent = logged == null && i == sets.size + 1

            row.findViewById<TextView>(R.id.tv_set_number).text = i.toString()
            if (logged != null) {
                val warmup = logged.warmup
                row.findViewById<TextView>(R.id.tv_reps).text = logged.reps.toString()
                val weightText = logged.weight.toString()
                val rpeSuffix = if (logged.rpe != null) " @${logged.rpe}" else ""
                row.findViewById<TextView>(R.id.tv_weight).text = "$weightText$rpeSuffix"
                row.findViewById<TextView>(R.id.tv_unit).text = logged.unit.code
                if (warmup) {
                    row.alpha = 0.6f
                    row.findViewById<TextView>(R.id.tv_set_number).setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.ink_3)
                    )
                }
            } else if (isCurrent) {
                row.findViewById<TextView>(R.id.tv_reps).text = currentReps.toString()
                val rpeSuffix = if (currentRpe != null) " @${currentRpe}" else ""
                row.findViewById<TextView>(R.id.tv_weight).text = "${currentWeight}$rpeSuffix"
                row.findViewById<TextView>(R.id.tv_unit).text = unit.code
                row.setBackgroundResource(R.drawable.shape_set_current)
                row.findViewById<TextView>(R.id.tv_set_number)
                    .setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                // Spring animation on the newly pending row
                row.scaleX = 0.95f
                row.scaleY = 0.95f
                row.animate().scaleX(1f).scaleY(1f).setDuration(250)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else {
                row.findViewById<TextView>(R.id.tv_reps).text = "—"
                row.findViewById<TextView>(R.id.tv_weight).text = "—"
                row.findViewById<TextView>(R.id.tv_unit).text = ""
            }
            if (i == totalSets) {
                row.findViewById<View>(R.id.divider).visibility = View.GONE
            }
            binding.listLoggedSets.addView(row)
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
            if (loggedCount < totalSets) {
                val unit = settingsVM.state.value.unit
                sessionVM.addSet(currentReps, currentWeight, unit, currentWarmup, currentRpe)
                AnalyticsHelper.setLogged(currentEx.name, currentReps, currentWeight, currentWarmup, currentRpe)
                startRestTimer()
            }
        }

        binding.btnSkipRest.setOnClickListener { stopRestTimer() }

        binding.btnFinishWorkout.setOnClickListener {
            val workout = sessionVM.finish()
            if (workout != null) {
                historyVM.add(workout)
                val vol = volumeOf(workout, settingsVM.state.value.unit)
                AnalyticsHelper.workoutCompleted(workout.type.code, workout.exercises.size, vol.toDouble(), workout.durationSec)
                val bundle = Bundle().apply { putString("workoutId", workout.id) }
                findNavController().navigate(R.id.action_log_to_summary, bundle)
            } else {
                AnalyticsHelper.workoutCancelled(sessionVM.state.value.type?.code ?: "unknown")
                findNavController().popBackStack()
            }
        }

        binding.btnAddExercise.setOnClickListener {
            findNavController().navigate(R.id.action_log_to_exercise)
        }

        binding.btnPlateCalc.setOnClickListener { showPlateCalculator() }
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

    override fun onDestroyView() {
        stopRestTimer()
        super.onDestroyView()
        _binding = null
    }
}
