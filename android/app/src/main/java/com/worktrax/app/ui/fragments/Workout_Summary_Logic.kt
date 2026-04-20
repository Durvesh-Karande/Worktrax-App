package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.databinding.WorkoutSummaryDesignBinding
import com.worktrax.app.lib.formatShortDate
import com.worktrax.app.lib.numberWithCommas
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
