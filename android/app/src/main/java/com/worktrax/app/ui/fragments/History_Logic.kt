package com.worktrax.app.ui.fragments

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.worktrax.app.R
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.databinding.HistoryDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.formatDate
import com.worktrax.app.store.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class History_Logic : Fragment() {

    private var _binding: HistoryDesignBinding? = null
    private val binding get() = _binding!!

    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HistoryDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AnalyticsHelper.screenView("history")

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        setupSwipeToDelete()
        setupObservers()
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = binding.rvHistory.adapter as? HistoryAdapter ?: return
                val workout = adapter.getWorkoutAt(position) ?: return
                AnalyticsHelper.workoutDeleted(workout.type.code)
                historyVM.remove(workout.id)
                Snackbar.make(binding.root, R.string.workout_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo_label) { historyVM.add(workout) }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (dX < 0) {
                    val paint = Paint().apply { color = ContextCompat.getColor(requireContext(), R.color.accent) }
                    val corner = 16f * resources.displayMetrics.density
                    val rect = RectF(
                        vh.itemView.right + dX - corner,
                        vh.itemView.top.toFloat(),
                        vh.itemView.right.toFloat(),
                        vh.itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(rect, corner, corner, paint)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvHistory)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyVM.workouts.collectLatest { workouts ->
                    if (workouts.isEmpty()) {
                        binding.tvEmptyHistory.visibility = View.VISIBLE
                        binding.rvHistory.visibility = View.GONE
                    } else {
                        binding.tvEmptyHistory.visibility = View.GONE
                        binding.rvHistory.visibility = View.VISIBLE
                        binding.rvHistory.adapter = HistoryAdapter(workouts) { workout ->
                            AnalyticsHelper.historyItemViewed(workout.type.code)
                            val bundle = Bundle().apply {
                                putString("workoutId", workout.id)
                            }
                            findNavController().navigate(R.id.action_history_to_detail, bundle)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class HistoryAdapter(
        private val workouts: List<Workout>,
        private val onClick: (Workout) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        fun getWorkoutAt(position: Int): Workout? = workouts.getOrNull(position)

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val date: TextView = view.findViewById(R.id.tv_workout_date)
            val desc: TextView = view.findViewById(R.id.tv_workout_desc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_workout, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = workouts[position]
            holder.date.text = formatDate(workout.date)
            holder.desc.text = "${workout.type.code.uppercase()} • ${workout.exercises.size} exercises"
            holder.itemView.findViewById<View>(R.id.v_type_bar)?.setBackgroundResource(
                when (workout.type) {
                    WorkoutType.STRENGTH -> R.color.type_strength_top
                    WorkoutType.CARDIO -> R.color.type_cardio_top
                    WorkoutType.AEROBIC -> R.color.type_aerobic_top
                    WorkoutType.YOGA -> R.color.type_yoga_top
                }
            )
            holder.itemView.setOnClickListener { onClick(workout) }
        }

        override fun getItemCount() = workouts.size
    }
}
