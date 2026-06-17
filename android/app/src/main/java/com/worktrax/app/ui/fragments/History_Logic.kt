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
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.worktrax.app.R
import com.worktrax.app.data.ExerciseEntry
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.databinding.HistoryDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.loadBannerAd
import com.worktrax.app.lib.loadNativeAd
import com.worktrax.app.lib.populateNativeAdView
import com.worktrax.app.lib.formatDate
import com.worktrax.app.store.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class History_Logic : Fragment() {

    sealed class HistoryItem {
        data class DaySummary(val dateIso: String, val workouts: List<Workout>) : HistoryItem()
        object NativeAdPlaceholder : HistoryItem()
    }

    private var _binding: HistoryDesignBinding? = null
    private val binding get() = _binding!!

    private val historyVM: HistoryViewModel by viewModels({ requireActivity() })
    private var loadedNativeAd: NativeAd? = null
    private var nativeAdInserted = false

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
        binding.includeBanner.adView.loadBannerAd()
        loadNativeAd(requireActivity(), "ca-app-pub-2162470152606094/9929808986", { ad ->
            loadedNativeAd = ad
            nativeAdInserted = false
            rebuildAdapter()
        })
    }

    private fun rebuildAdapter() {
        val adapter = binding.rvHistory.adapter as? HistoryAdapter
        adapter?.notifyDataSetChanged()
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = binding.rvHistory.adapter as? HistoryAdapter ?: return
                val item = adapter.getItemAt(position)
                if (item !is HistoryItem.DaySummary) return
                val workouts = item.workouts

                workouts.forEach { historyVM.remove(it.id) }
                AnalyticsHelper.workoutDeleted("multiple_day")

                Snackbar.make(binding.root, R.string.workout_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo_label) {
                        workouts.forEach { historyVM.add(it) }
                    }
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

                        val grouped = workouts.groupBy { it.date.substring(0, 10) }
                        val sortedDates = grouped.keys.sortedDescending()
                        val items = mutableListOf<HistoryItem>()
                        sortedDates.forEachIndexed { index, dateKey ->
                            items.add(HistoryItem.DaySummary(dateKey, grouped[dateKey]!!))
                            if (index == 2 && loadedNativeAd != null && !nativeAdInserted) {
                                items.add(HistoryItem.NativeAdPlaceholder)
                                nativeAdInserted = true
                            }
                        }

                        binding.rvHistory.adapter = HistoryAdapter(items, loadedNativeAd) { dateIso ->
                            AnalyticsHelper.historyItemViewed("day_view")
                            val bundle = Bundle().apply {
                                putString("dateIso", dateIso)
                            }
                            findNavController().navigate(R.id.action_history_to_detail, bundle)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        loadedNativeAd?.destroy()
        loadedNativeAd = null
        super.onDestroyView()
        _binding = null
    }

    inner class HistoryAdapter(
        private val items: List<HistoryItem>,
        private val nativeAd: NativeAd?,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_DAY = 0
        private val TYPE_AD = 1

        fun getItemAt(position: Int): HistoryItem = items[position]

        inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tv_workout_date)
            val desc: TextView = view.findViewById(R.id.tv_workout_desc)
            val typeBar: View? = view.findViewById(R.id.v_type_bar)
        }

        inner class AdViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is HistoryItem.DaySummary -> TYPE_DAY
                is HistoryItem.NativeAdPlaceholder -> TYPE_AD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_AD -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.view_native_ad_history, parent, false)
                    AdViewHolder(view)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_recent_workout, parent, false)
                    DayViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is HistoryItem.DaySummary -> {
                    val h = holder as DayViewHolder
                    val firstWorkout = item.workouts.first()
                    h.name.text = formatDate(firstWorkout.date)

                    val totalExercises = item.workouts.sumOf { it.exercises.size }
                    h.desc.text = h.itemView.context.resources.getQuantityString(
                        R.plurals.exercise_count,
                        totalExercises,
                        totalExercises
                    )

                    h.typeBar?.setBackgroundResource(
                        when (firstWorkout.type) {
                            WorkoutType.STRENGTH -> R.color.type_strength_top
                            WorkoutType.CARDIO -> R.color.type_cardio_top
                            WorkoutType.AEROBIC -> R.color.type_aerobic_top
                            WorkoutType.YOGA -> R.color.type_yoga_top
                        }
                    )
                    h.itemView.setOnClickListener { onClick(item.dateIso) }
                }
                is HistoryItem.NativeAdPlaceholder -> {
                    val ad = nativeAd ?: return
                    val adView = holder.itemView as NativeAdView
                    populateNativeAdView(ad, adView)
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
