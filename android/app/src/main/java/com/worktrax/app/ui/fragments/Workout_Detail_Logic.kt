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
import com.worktrax.app.data.Workout
import com.worktrax.app.databinding.WorkoutDetailDesignBinding
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
        
        val workoutId = arguments?.getString("workoutId") ?: return
        
        setupObservers(workoutId)
        setupListeners()
    }

    private fun setupObservers(workoutId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyVM.workouts.collectLatest { workouts ->
                    val workout = workouts.find { it.id == workoutId }
                    if (workout != null) {
                        bindWorkout(workout)
                    } else {
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun bindWorkout(workout: Workout) {
        binding.includeTopBar.tvTopBarTitle.text = formatDate(workout.date)
        binding.includeTopBar.btnDelete.visibility = View.VISIBLE
        
        binding.tvWorkoutType.text = workout.type.code.uppercase()
        binding.tvDuration.text = formatDuration(workout.durationSec)
        
        val allWorkouts = historyVM.workouts.value
        
        binding.layoutExercisesContainer.removeAllViews()
        workout.exercises.forEach { exercise ->
            val exerciseView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_exercise_detail, binding.layoutExercisesContainer, false)
            
            exerciseView.findViewById<TextView>(R.id.tv_exercise_name).text = exercise.name
            exerciseView.findViewById<TextView>(R.id.tv_muscle_name).text = exercise.muscle
            
            // PR badge
            val prBadge = exerciseView.findViewById<TextView>(R.id.tv_pr_badge)
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
            
            val setsContainer = exerciseView.findViewById<LinearLayout>(R.id.layout_sets_container)
            exercise.sets.forEachIndexed { index, set ->
                val setView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_set_row, setsContainer, false)
                
                setView.findViewById<TextView>(R.id.tv_set_number).text = (index + 1).toString()
                setView.findViewById<TextView>(R.id.tv_reps).text = set.reps.toString()
                val rpeSuffix = if (set.rpe != null) " @${set.rpe}" else ""
                setView.findViewById<TextView>(R.id.tv_weight).text = "${set.weight}$rpeSuffix"
                setView.findViewById<TextView>(R.id.tv_unit).text = set.unit.code
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
                    ex.sets.maxByOrNull { it.weight * (1 + it.reps / 30.0) }
                }
            if (historySets.size >= 2) {
                val maxScore = historySets.maxOf { it.weight * (1 + it.reps / 30.0) }.coerceAtLeast(1.0)
                val chartH = 40f
                val density = resources.displayMetrics.density
                historySets.forEachIndexed { i, s ->
                    val score = s.weight * (1 + s.reps / 30.0)
                    val frac = (score / maxScore).coerceIn(0.05f, 1.0f)
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
            }
            
            binding.layoutExercisesContainer.addView(exerciseView)
        }
    }

    private fun setupListeners() {
        binding.includeTopBar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        
        binding.includeTopBar.btnDelete.setOnClickListener {
            val workoutId = arguments?.getString("workoutId") ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_workout_title)
                .setMessage(R.string.delete_workout_message)
                .setPositiveButton(R.string.delete_label) { _, _ ->
                    historyVM.remove(workoutId)
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }

        binding.btnRepeat.setOnClickListener {
            val workoutId = arguments?.getString("workoutId") ?: return@setOnClickListener
            val workout = historyVM.workouts.value.find { it.id == workoutId } ?: return@setOnClickListener
            sessionVM.seedFromWorkout(workout)
            findNavController().navigate(R.id.exercisePickerFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
