package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.databinding.WorkoutLoggerDesignBinding
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

        setupSteppers()
        setupObservers()
        setupListeners()
    }

    private fun setupSteppers() {
        binding.stepperReps.tvStepperLabel.text = "REPS"
        binding.stepperReps.tvValue.text = currentReps.toString()
        binding.stepperReps.btnMinus.setOnClickListener {
            if (currentReps > 0) {
                currentReps--
                binding.stepperReps.tvValue.text = currentReps.toString()
            }
        }
        binding.stepperReps.btnPlus.setOnClickListener {
            currentReps++
            binding.stepperReps.tvValue.text = currentReps.toString()
        }

        binding.stepperWeight.tvStepperLabel.text = "WEIGHT"
        binding.stepperWeight.tvValue.text = currentWeight.toString()
        binding.stepperWeight.btnMinus.setOnClickListener {
            if (currentWeight > 0) {
                currentWeight -= 2.5
                binding.stepperWeight.tvValue.text = currentWeight.toString()
            }
        }
        binding.stepperWeight.btnPlus.setOnClickListener {
            currentWeight += 2.5
            binding.stepperWeight.tvValue.text = currentWeight.toString()
        }

        binding.stepperTotalSets.tvStepperLabel.text = "TOTAL SETS"
        binding.stepperTotalSets.tvValue.text = totalSets.toString()
        binding.stepperTotalSets.btnMinus.setOnClickListener {
            if (totalSets > 1) {
                totalSets--
                binding.stepperTotalSets.tvValue.text = totalSets.toString()
                refreshTable()
            }
        }
        binding.stepperTotalSets.btnPlus.setOnClickListener {
            if (totalSets < 12) {
                totalSets++
                binding.stepperTotalSets.tvValue.text = totalSets.toString()
                refreshTable()
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sessionVM.state, settingsVM.state) { session, settings ->
                    session to settings.unit
                }.collectLatest { (session, unit) ->
                    binding.tvTitle.text = getString(R.string.workout_logger_title)
                    binding.tvMuscleKicker.text = "${session.muscle?.uppercase() ?: "TYPE"} · LOGGING"

                    val currentEx = session.exercises.find { it.id == session.currentExerciseId }
                    binding.tvExerciseName.text = currentEx?.name ?: "No Exercise Selected"

                    binding.tvUnit.text = unit.code.uppercase()

                    val loggedCount = currentEx?.sets?.size ?: 0
                    val currentIndex = loggedCount + 1
                    binding.tvSetCounter.text =
                        if (currentIndex > totalSets) "All sets done. Tap Done to save."
                        else "Set $currentIndex of $totalSets · enter values, then Next."

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
            if (currentIndex > totalSets) "All sets done. Tap Done to save."
            else "Set $currentIndex of $totalSets · enter values, then Next."
    }

    private fun updateLoggedSets(sets: List<com.worktrax.app.data.SetEntry>, unit: WeightUnit) {
        binding.listLoggedSets.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (i in 1..totalSets) {
            val row = inflater.inflate(R.layout.item_set_row, binding.listLoggedSets, false)
            val logged = sets.getOrNull(i - 1)
            val isCurrent = logged == null && i == sets.size + 1

            row.findViewById<TextView>(R.id.tv_set_number).text = i.toString()
            if (logged != null) {
                row.findViewById<TextView>(R.id.tv_reps).text = logged.reps.toString()
                row.findViewById<TextView>(R.id.tv_weight).text = logged.weight.toString()
                row.findViewById<TextView>(R.id.tv_unit).text = logged.unit.code
            } else if (isCurrent) {
                row.findViewById<TextView>(R.id.tv_reps).text = currentReps.toString()
                row.findViewById<TextView>(R.id.tv_weight).text = currentWeight.toString()
                row.findViewById<TextView>(R.id.tv_unit).text = unit.code
                row.setBackgroundResource(R.drawable.shape_set_current)
                row.findViewById<TextView>(R.id.tv_set_number)
                    .setTextColor(resources.getColor(R.color.accent, null))
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
            findNavController().popBackStack()
        }

        binding.btnLogSet.setOnClickListener {
            val session = sessionVM.state.value
            val currentEx = session.exercises.find { it.id == session.currentExerciseId }
            val loggedCount = currentEx?.sets?.size ?: 0
            if (loggedCount < totalSets) {
                val unit = settingsVM.state.value.unit
                sessionVM.addSet(currentReps, currentWeight, unit)
            }
        }

        binding.btnFinishWorkout.setOnClickListener {
            val workout = sessionVM.finish()
            if (workout != null) {
                historyVM.add(workout)
                val bundle = Bundle().apply { putString("workoutId", workout.id) }
                findNavController().navigate(R.id.action_log_to_summary, bundle)
            } else {
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
