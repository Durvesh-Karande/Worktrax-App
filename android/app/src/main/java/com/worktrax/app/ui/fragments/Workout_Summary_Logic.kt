package com.worktrax.app.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.Routine
import com.worktrax.app.databinding.WorkoutSummaryDesignBinding
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.formatShortDate
import com.worktrax.app.lib.nowIso
import com.worktrax.app.lib.numberWithCommas
import com.worktrax.app.lib.routinesFromJsonString
import com.worktrax.app.lib.routinesToJsonString
import com.worktrax.app.lib.uid
import com.worktrax.app.lib.volumeOf
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SettingsViewModel

@Suppress("ClassName")
class Workout_Summary_Logic : Fragment() {

    private var _binding: WorkoutSummaryDesignBinding? = null
    private val binding get() = _binding!!

    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })
    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WorkoutSummaryDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val workoutId = arguments?.getString("workoutId")
        val workout = historyVM.workouts.value.firstOrNull { it.id == workoutId }
            ?: historyVM.workouts.value.firstOrNull()
        val unit = settingsVM.state.value.unit

        if (workout != null) {
            val firstEx = workout.exercises.firstOrNull()
            val exName = firstEx?.name ?: "Workout"
            val muscle = firstEx?.muscle ?: ""
            binding.tvSummaryMeta.text = listOf(exName, muscle, formatShortDate(workout.date))
                .filter { it.isNotBlank() }
                .joinToString(" · ")

            val sets = workout.exercises.sumOf { it.sets.size }
            val reps = workout.exercises.sumOf { ex -> ex.sets.sumOf { it.reps } }
            val vol = volumeOf(workout, unit)

            binding.tvSumSets.text = sets.toString()
            binding.tvSumReps.text = reps.toString()
            view.findViewById<TextView?>(R.id.tv_sum_volume)?.text =
                "${numberWithCommas(vol)} ${unit.code}"
        }

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        binding.btnSave.setOnClickListener {
            findNavController().navigate(R.id.exercisePickerFragment)
        }
        binding.btnDiscard.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        binding.btnSaveRoutine.setOnClickListener {
            if (workout == null) return@setOnClickListener
            val input = EditText(requireContext()).apply {
                setText("${workout.type.code.replaceFirstChar { it.uppercase() }} Routine")
                selectAll()
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.save_as_routine)
                .setView(input)
                .setPositiveButton(R.string.save_label) { _, _ ->
                    val name = input.text.toString().trim().ifEmpty { getString(R.string.my_routine_default) }
                    val exerciseIds = workout.exercises.map { it.id }
                    val routine = Routine(
                        id = uid("r"),
                        name = name,
                        type = workout.type,
                        exerciseIds = exerciseIds,
                        createdAt = nowIso(),
                    )
                    val raw = Storage.getString(requireContext(), Storage.KEY_ROUTINES)
                    val routines = routinesFromJsonString(raw) + routine
                    Storage.putString(
                        requireContext(),
                        Storage.KEY_ROUTINES,
                        routinesToJsonString(routines),
                    )
                    Toast.makeText(requireContext(), R.string.routine_saved, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
