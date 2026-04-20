package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
        
        binding.layoutExercisesContainer.removeAllViews()
        workout.exercises.forEach { exercise ->
            val exerciseView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_exercise_detail, binding.layoutExercisesContainer, false)
            
            exerciseView.findViewById<TextView>(R.id.tv_exercise_name).text = exercise.name
            exerciseView.findViewById<TextView>(R.id.tv_muscle_name).text = exercise.muscle
            
            val setsContainer = exerciseView.findViewById<LinearLayout>(R.id.layout_sets_container)
            exercise.sets.forEachIndexed { index, set ->
                val setView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_set_row, setsContainer, false)
                
                setView.findViewById<TextView>(R.id.tv_set_number).text = (index + 1).toString()
                setView.findViewById<TextView>(R.id.tv_reps).text = set.reps.toString()
                setView.findViewById<TextView>(R.id.tv_weight).text = set.weight.toString()
                setView.findViewById<TextView>(R.id.tv_unit).text = set.unit.code
                
                if (index == exercise.sets.size - 1) {
                    setView.findViewById<View>(R.id.divider).visibility = View.GONE
                }
                
                setsContainer.addView(setView)
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
            historyVM.remove(workoutId)
            findNavController().popBackStack()
        }
        
        binding.btnRepeat.setOnClickListener {
            val workoutId = arguments?.getString("workoutId") ?: return@setOnClickListener
            val workout = historyVM.workouts.value.find { it.id == workoutId } ?: return@setOnClickListener
            sessionVM.seedFromWorkout(workout)
            findNavController().navigate(R.id.logFragment) // Fixed ID to direct navigate
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
