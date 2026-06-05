package com.worktrax.app.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.SetEntry
import com.worktrax.app.data.Workout
import com.worktrax.app.databinding.WorkoutDetailDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.formatDate
import com.worktrax.app.lib.formatDuration
import com.worktrax.app.store.bestSetForExercise
import com.worktrax.app.store.estimated1rm
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SessionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class Workout_Detail_Logic : Fragment() {

    private var _binding: WorkoutDetailDesignBinding? = null
    private val binding get() = _binding!!

    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })
    private val sessionVM: SessionViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WorkoutDetailDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AnalyticsHelper.screenView("workout_detail")
        
        val dateIsoOrId = arguments?.getString("dateIso") ?: arguments?.getString("workoutId") ?: return
        
        setupObservers(dateIsoOrId)
        setupListeners()
    }

    private fun setupObservers(dateIsoOrId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyVM.workouts.collectLatest { workouts ->
                    // dateIso is yyyy-MM-dd, workoutId is uid
                    val relevantWorkouts = if (dateIsoOrId.length == 10 && dateIsoOrId.contains("-")) {
                        workouts.filter { it.date.startsWith(dateIsoOrId) }.sortedBy { it.date }
                    } else {
                        workouts.filter { it.id == dateIsoOrId }
                    }

                    if (relevantWorkouts.isNotEmpty()) {
                        bindWorkouts(relevantWorkouts)
                    } else {
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun bindWorkouts(workouts: List<Workout>) {
        val firstWorkout = workouts.first()
        binding.includeTopBar.tvTopBarTitle.text = formatDate(firstWorkout.date)
        binding.includeTopBar.btnDelete.visibility = View.VISIBLE
        
        binding.tvWorkoutType.visibility = View.GONE
        binding.tvDuration.visibility = View.GONE
        
        val allWorkouts = historyVM.workouts.value
        
        binding.layoutExercisesContainer.removeAllViews()
        
        // Group all exercises from all workouts of the day by exercise ID
        val groupedExercises = workouts.flatMap { it.exercises }
            .groupBy { it.id }
            .values
            .map { exercises ->
                // Merge sets for the same exercise performed multiple times in a day
                exercises.reduce { acc, ex -> 
                    acc.copy(sets = acc.sets + ex.sets)
                }
            }
        
        groupedExercises.forEach { exercise ->
            val exerciseView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_exercise_detail, binding.layoutExercisesContainer, false)
            
            exerciseView.findViewById<TextView>(R.id.tv_exercise_name).text = exercise.name
            exerciseView.findViewById<TextView>(R.id.tv_muscle_name).text = exercise.muscle

            // Set column headers based on metric type
            val (leftHeader, rightHeader) = when (exercise.metricType) {
                "strength" -> "Reps" to "Weight"
                "bodyweight" -> "Reps" to ""
                "timed" -> "Hold" to ""
                "cardio" -> "Duration" to "Distance"
                "hiit" -> "Rounds" to "Reps"
                "yoga" -> "Duration" to ""
                else -> "Reps" to "Weight"
            }
            exerciseView.findViewById<TextView>(R.id.tbl_col_left).text = leftHeader
            exerciseView.findViewById<TextView>(R.id.tbl_col_right).text = rightHeader
            exerciseView.findViewById<TextView>(R.id.tbl_col_right).visibility =
                if (rightHeader.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            
            // PR badge (only for strength exercises)
            val prBadge = exerciseView.findViewById<TextView>(R.id.tv_pr_badge)
            if (exercise.metricType == "strength") {
                val best = bestSetForExercise(allWorkouts, exercise.id)
                if (best != null) {
                    val sessionBest = exercise.sets.maxByOrNull { it.weight * (1 + it.reps / 30.0) }
                    if (sessionBest != null &&
                        (sessionBest.weight * (1 + sessionBest.reps / 30.0)) >=
                        (best.weight * (1 + best.reps / 30.0))
                    ) {
                        val e1rm = estimated1rm(sessionBest.weight, sessionBest.reps)
                        prBadge.text = "PR ${e1rm.toInt()} ${sessionBest.unit.code}"
                        prBadge.visibility = View.VISIBLE
                    } else {
                        val e1rm = estimated1rm(best.weight, best.reps)
                        prBadge.text = "BEST ${e1rm.toInt()} ${best.unit.code}"
                        prBadge.visibility = View.VISIBLE
                    }
                }
            }
            
            val setsContainer = exerciseView.findViewById<LinearLayout>(R.id.layout_sets_container)
            exercise.sets.forEachIndexed { index, set ->
                val setView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_set_row, setsContainer, false)

                setView.findViewById<TextView>(R.id.tv_set_number).text = (index + 1).toString()
                val (leftText, rightText, unitText) = formatDetailSetRow(set)
                setView.findViewById<TextView>(R.id.tv_reps).text = leftText
                setView.findViewById<TextView>(R.id.tv_weight).text = rightText
                setView.findViewById<TextView>(R.id.tv_unit).text = unitText
                if (set.warmup) {
                    setView.alpha = 0.6f
                    setView.findViewById<TextView>(R.id.tv_set_number).setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.ink_3)
                    )
                }

                if (index == exercise.sets.size - 1) {
                    setView.findViewById<View>(R.id.divider).visibility = View.GONE
                }

                setsContainer.addView(setView)
            }
            
            // Mini progression chart
            val chart = exerciseView.findViewById<LinearLayout>(R.id.layout_exercise_chart)
            val historySets = allWorkouts
                .filter { w -> w.exercises.any { it.id == exercise.id } }
                .sortedBy { it.date }
                .mapNotNull { w ->
                    val ex = w.exercises.find { it.id == exercise.id } ?: return@mapNotNull null
                    when (exercise.metricType) {
                        "strength" -> ex.sets.maxByOrNull { it.weight * (1 + it.reps / 30.0) }
                        "cardio" -> ex.sets.maxByOrNull { it.distanceKm }
                        "timed", "yoga" -> ex.sets.maxByOrNull { it.durationSec.toDouble() }
                        "hiit" -> ex.sets.maxByOrNull { it.rounds.toDouble() * 1000 + it.reps }
                        else -> ex.sets.firstOrNull()
                    }
                }

            if (historySets.size >= 2) {
                val maxScore = historySets.maxOf { s ->
                    when (exercise.metricType) {
                        "strength" -> s.weight * (1 + s.reps / 30.0)
                        "cardio" -> s.distanceKm
                        "timed", "yoga" -> s.durationSec.toDouble()
                        "hiit" -> s.rounds.toDouble() * 1000 + s.reps
                        else -> 1.0
                    }
                }.coerceAtLeast(1.0)

                val chartH = 40f
                val density = resources.displayMetrics.density
                chart.removeAllViews()
                historySets.forEach { s ->
                    val score = when (exercise.metricType) {
                        "strength" -> s.weight * (1 + s.reps / 30.0)
                        "cardio" -> s.distanceKm
                        "timed", "yoga" -> s.durationSec.toDouble()
                        "hiit" -> s.rounds.toDouble() * 1000 + s.reps
                        else -> 0.0
                    }
                    val frac = (score / maxScore).coerceIn(0.05, 1.0)
                    val bar = View(requireContext()).apply {
                        val h = (chartH * frac).toInt()
                        layoutParams = LinearLayout.LayoutParams(0, h).apply {
                            weight = 1f
                            setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
                        }
                        backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accent)
                        setBackgroundResource(R.drawable.shape_chart_bar)
                    }
                    chart.addView(bar)
                }
                chart.visibility = View.VISIBLE
            } else {
                chart.visibility = View.GONE
            }
            
            binding.layoutExercisesContainer.addView(exerciseView)
        }
    }

    private fun setupListeners() {
        binding.includeTopBar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        
        binding.includeTopBar.btnDelete.setOnClickListener {
            val dateIso = arguments?.getString("dateIso")
            val workoutId = arguments?.getString("workoutId")
            
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_workout_title)
                .setMessage(R.string.delete_workout_message)
                .setPositiveButton(R.string.delete_label) { _, _ ->
                    if (dateIso != null) {
                        historyVM.workouts.value
                            .filter { it.date.startsWith(dateIso) }
                            .forEach { historyVM.remove(it.id) }
                    } else if (workoutId != null) {
                        historyVM.remove(workoutId)
                    }
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }

        binding.btnRepeat.setOnClickListener {
            val dateIso = arguments?.getString("dateIso")
            val workoutId = arguments?.getString("workoutId")
            val workouts = historyVM.workouts.value
            val relevantWorkouts = if (dateIso != null) {
                workouts.filter { it.date.startsWith(dateIso) }
            } else {
                workouts.filter { it.id == workoutId }
            }
            if (relevantWorkouts.isNotEmpty()) {
                // To repeat a "day", we can just take the first workout's type or seed all.
                // Seed from the first for now.
                sessionVM.seedFromWorkout(relevantWorkouts.first())
                findNavController().navigate(R.id.exercisePickerFragment)
            }
        }
    }

    private fun formatDurationDetail(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m} min ${s} sec" else "${s} sec"
    }

    private fun formatDetailSetRow(set: SetEntry): Triple<String, String, String> {
        return when (set.metricType) {
            "strength" -> {
                val rpe = if (set.rpe != null) " @${set.rpe}" else ""
                Triple(set.reps.toString(), "${set.weight}$rpe", set.unit.code)
            }
            "bodyweight" -> Triple("${set.reps} reps", "", "")
            "timed" -> Triple("${set.durationSec}s hold", "", "")
            "cardio" -> Triple(formatDurationDetail(set.durationSec), "${set.distanceKm} km", "")
            "hiit" -> Triple("${set.rounds} rounds", "${set.reps} reps", "")
            "yoga" -> Triple(formatDurationDetail(set.durationSec), "", "")
            else -> Triple(set.reps.toString(), set.weight.toString(), set.unit.code)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
