package com.worktrax.app.ui.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.Routine
import com.worktrax.app.databinding.WorkoutSummaryDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.buildShareImage
import com.worktrax.app.lib.formatShortDate
import com.worktrax.app.lib.nowIso
import com.worktrax.app.lib.numberWithCommas
import com.worktrax.app.lib.routinesFromJsonString
import com.worktrax.app.lib.routinesToJsonString
import com.worktrax.app.lib.totalDistanceOf
import com.worktrax.app.lib.totalDurationOf
import com.worktrax.app.lib.totalRepsOf
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
        AnalyticsHelper.screenView("workout_summary")

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
            val reps = totalRepsOf(workout)
            val vol = volumeOf(workout, unit)
            val totalDurSec = totalDurationOf(workout)
            val totalDurMin = totalDurSec / 60
            val totalDist = totalDistanceOf(workout)

            if (workout.type == com.worktrax.app.data.WorkoutType.CARDIO) {
                val unit = settingsVM.state.value.unit
                val isImperial = unit == com.worktrax.app.data.WeightUnit.LB
                binding.tvSumSets.text = totalDurMin.toString()
                binding.tvSumSetsLabel.text = "Duration (min)"
                binding.tvSumReps.text = if (isImperial) "%.1f".format(totalDist * 0.621371) else "%.1f".format(totalDist)
                binding.tvSumRepsLabel.text = if (isImperial) "Distance (mi)" else "Distance (km)"
                binding.tvSumVolume.text = if (totalDurMin > 0) "${(totalDist / totalDurMin).toInt()}" else "0"
                binding.tvSumVolumeLabel.text = if (isImperial) "Pace (min/mi)" else "Pace (min/km)"
            } else if (workout.type == com.worktrax.app.data.WorkoutType.AEROBIC) {
                binding.tvSumSets.text = totalDurMin.toString()
                binding.tvSumSetsLabel.text = "Duration (min)"
                binding.tvSumReps.text = totalDurSec.toString()
                binding.tvSumRepsLabel.text = "Total (sec)"
                binding.tvSumVolume.text = sets.toString()
                binding.tvSumVolumeLabel.text = "Sets"
            } else {
                binding.tvSumSets.text = sets.toString()
                binding.tvSumReps.text = reps.toString()
                binding.tvSumVolume.text = "${numberWithCommas(vol)} ${unit.code}"
            }
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
        binding.btnShare.setOnClickListener {
            if (workout == null) return@setOnClickListener
            AnalyticsHelper.workoutShared()
            val file = buildShareImage(
                ctx = requireContext(),
                workout = workout,
                unit = unit,
                userName = settingsVM.state.value.name,
            )
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_workout)))
            } else {
                Toast.makeText(requireContext(), R.string.report_save_failed, Toast.LENGTH_SHORT).show()
            }
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
                    AnalyticsHelper.routineSaved()
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
