package com.worktrax.app.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.worktrax.app.R
import com.worktrax.app.data.EQUIPMENT_ORDER
import com.worktrax.app.data.EXERCISES
import com.worktrax.app.data.ExerciseDef
import com.worktrax.app.data.Muscles
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.databinding.PickerExerciseDesignBinding
import com.worktrax.app.store.SessionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("ClassName")
class Picker_Exercise_Logic : Fragment() {

    private var _binding: PickerExerciseDesignBinding? = null
    private val binding get() = _binding!!

    private val sessionVM: SessionViewModel by viewModels({ requireActivity() })

    private val muscleIcons = mapOf(
        Muscles.CHEST to R.drawable.ic_m_chest,
        Muscles.BACK to R.drawable.ic_m_back,
        Muscles.SHOULDERS to R.drawable.ic_m_shoulders,
        Muscles.BICEPS to R.drawable.ic_m_biceps,
        Muscles.TRICEPS to R.drawable.ic_m_triceps,
        Muscles.CORE to R.drawable.ic_m_core,
        Muscles.QUADS to R.drawable.ic_m_quads,
        Muscles.HAMSTRINGS to R.drawable.ic_m_hamstrings,
        Muscles.GLUTES to R.drawable.ic_m_glutes,
        Muscles.CALVES to R.drawable.ic_m_calves,
    )

    private sealed class ExerciseListItem {
        data class Header(val text: String) : ExerciseListItem()
        data class Exercise(val def: ExerciseDef) : ExerciseListItem()
    }

    private inner class ExerciseAdapter : ListAdapter<ExerciseListItem, RecyclerView.ViewHolder>(
        object : DiffUtil.ItemCallback<ExerciseListItem>() {
            override fun areItemsTheSame(a: ExerciseListItem, b: ExerciseListItem) = when {
                a is ExerciseListItem.Header && b is ExerciseListItem.Header -> a.text == b.text
                a is ExerciseListItem.Exercise && b is ExerciseListItem.Exercise -> a.def.id == b.def.id
                else -> false
            }
            override fun areContentsTheSame(a: ExerciseListItem, b: ExerciseListItem) = a == b
        }
    ) {
        private val TYPE_HEADER = 0
        private val TYPE_EXERCISE = 1

        override fun getItemViewType(position: Int) = when (getItem(position)) {
            is ExerciseListItem.Header -> TYPE_HEADER
            is ExerciseListItem.Exercise -> TYPE_EXERCISE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val tv = TextView(requireContext(), null, 0, R.style.TextKicker).apply {
                    setPadding(4, 18, 0, 8)
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    )
                }
                HeaderVH(tv)
            } else {
                val view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_exercise_row, parent, false)
                ExerciseVH(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is ExerciseListItem.Header -> (holder as HeaderVH).bind(item)
                is ExerciseListItem.Exercise -> (holder as ExerciseVH).bind(item.def)
            }
        }

        inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(item: ExerciseListItem.Header) {
                (itemView as TextView).text = item.text
            }
        }

        inner class ExerciseVH(view: View) : RecyclerView.ViewHolder(view) {
            private val thumb: View = view.findViewById(R.id.ex_thumb)
            private val name: TextView = view.findViewById(R.id.tv_ex_name)
            private val meta: TextView = view.findViewById(R.id.tv_ex_meta)

            fun bind(ex: ExerciseDef) {
                val thumbColors = intArrayOf(
                    R.color.thumb_a, R.color.thumb_b, R.color.thumb_c,
                    R.color.thumb_d, R.color.thumb_e, R.color.thumb_f,
                )
                val exIndex = EXERCISES.indexOfFirst { it.id == ex.id }.coerceAtLeast(0)
                thumb.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), thumbColors[exIndex % thumbColors.size]))
                name.text = ex.name
                meta.text = "${ex.equipment.label} · ${ex.muscle.lowercase()}"
                itemView.setOnClickListener {
                    sessionVM.pickExercise(ex.id, ex.name, ex.muscle)
                    findNavController().navigate(R.id.action_exercise_to_log)
                }
            }
        }
    }

    private val exerciseAdapter = ExerciseAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PickerExerciseDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.includeTopBar.tvTopBarTitle.text = getString(R.string.picker_exercise_title)
        binding.rvExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExercises.adapter = exerciseAdapter
        setupMuscleChips()
        setupObservers()
        setupListeners()
    }

    private fun setupMuscleChips() {
        binding.layoutMuscleChips.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (8 * density).toInt()

        Muscles.ALL.forEach { muscle ->
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_muscle_card, binding.layoutMuscleChips, false)
            card.findViewById<ImageView>(R.id.iv_muscle_icon)
                .setImageResource(muscleIcons[muscle] ?: R.drawable.ic_m_core)
            card.findViewById<TextView>(R.id.tv_muscle_name).text = muscle
            card.contentDescription = getString(R.string.cd_muscle) + ": " + muscle
            (card.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, gap, 0)
            card.setOnClickListener { sessionVM.setMuscle(muscle) }
            binding.layoutMuscleChips.addView(card)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionVM.state.collectLatest { state ->
                    val type = state.type ?: WorkoutType.STRENGTH
                    val isStrength = type == WorkoutType.STRENGTH

                    binding.layoutMuscleChips.visibility = if (isStrength) View.VISIBLE else View.GONE
                    val chipScroller = binding.layoutMuscleChips.parent as? View
                    chipScroller?.visibility = if (isStrength) View.VISIBLE else View.GONE

                    val muscle = state.muscle
                    val hasMuscle = !muscle.isNullOrBlank()

                    binding.tvExerciseKicker.text = when {
                        !isStrength -> type.code.uppercase() + " · PICK A MOVE"
                        hasMuscle -> muscle!!.uppercase() + " · PICK A MOVE"
                        else -> "TARGET MUSCLE"
                    }

                    binding.layoutSearchWrap.visibility = View.VISIBLE
                    binding.tvPrompt.visibility = View.GONE

                    if (isStrength) updateChipsSelection(muscle)
                    updateList(
                        binding.etSearch.text?.toString() ?: "",
                        type,
                        if (isStrength) muscle else null
                    )
                }
            }
        }
    }

    private fun updateChipsSelection(selectedMuscle: String?) {
        for (i in 0 until binding.layoutMuscleChips.childCount) {
            val card = binding.layoutMuscleChips.getChildAt(i)
            val name = card.findViewById<TextView>(R.id.tv_muscle_name)
            val icon = card.findViewById<ImageView>(R.id.iv_muscle_icon)
            val selected = name.text.toString() == selectedMuscle
            card.setBackgroundResource(
                if (selected) R.drawable.shape_muscle_card_selected else R.drawable.shape_field
            )
            val tint = ContextCompat.getColor(requireContext(),
                if (selected) R.color.white else R.color.ink
            )
            icon.imageTintList = ColorStateList.valueOf(tint)
            name.setTextColor(
                ContextCompat.getColor(requireContext(),
                    if (selected) R.color.white else R.color.ink_2
                )
            )
        }
    }

    private fun updateList(query: String, type: WorkoutType, muscleFilter: String?) {
        val pool = EXERCISES.filter { it.type == type }
        val byMuscle = if (muscleFilter != null) pool.filter { it.muscle == muscleFilter } else pool
        val filtered = if (query.isBlank()) byMuscle
        else byMuscle.filter {
            it.name.lowercase().contains(query.lowercase()) ||
                it.muscle.lowercase().contains(query.lowercase()) ||
                it.equipment.label.lowercase().contains(query.lowercase())
        }

        if (filtered.isEmpty()) {
            binding.tvNoResults.visibility = View.VISIBLE
            binding.rvExercises.visibility = View.GONE
            exerciseAdapter.submitList(emptyList())
            return
        }
        binding.tvNoResults.visibility = View.GONE
        binding.rvExercises.visibility = View.VISIBLE

        val items = mutableListOf<ExerciseListItem>()
        if (type == WorkoutType.STRENGTH) {
            EQUIPMENT_ORDER.forEach { equip ->
                val rows = filtered.filter { it.equipment == equip }
                if (rows.isNotEmpty()) {
                    items.add(ExerciseListItem.Header(equip.label.uppercase()))
                    rows.forEach { items.add(ExerciseListItem.Exercise(it)) }
                }
            }
        } else {
            val categories = filtered.map { it.muscle }.distinct()
            categories.forEach { cat ->
                val rows = filtered.filter { it.muscle == cat }
                items.add(ExerciseListItem.Header(cat.uppercase()))
                rows.forEach { items.add(ExerciseListItem.Exercise(it)) }
            }
        }
        exerciseAdapter.submitList(items)
    }

    private fun setupListeners() {
        binding.includeTopBar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {
                val state = sessionVM.state.value
                val type = state.type ?: WorkoutType.STRENGTH
                val isStrength = type == WorkoutType.STRENGTH
                updateList(
                    s?.toString() ?: "",
                    type,
                    if (isStrength) state.muscle else null
                )
            }
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
