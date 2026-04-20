package com.worktrax.app.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
            card.findViewById<android.widget.ImageView>(R.id.iv_muscle_icon)
                .setImageResource(muscleIcons[muscle] ?: R.drawable.ic_m_core)
            card.findViewById<TextView>(R.id.tv_muscle_name).text = muscle
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

                    // Muscle selector: only strength uses muscles
                    binding.layoutMuscleChips.visibility = if (isStrength) View.VISIBLE else View.GONE
                    val chipScroller = binding.layoutMuscleChips.parent as? View
                    chipScroller?.visibility = if (isStrength) View.VISIBLE else View.GONE

                    // Header kicker + search + prompt
                    val muscle = state.muscle
                    val hasMuscle = !muscle.isNullOrBlank()

                    binding.tvExerciseKicker.text = when {
                        !isStrength -> type.code.uppercase() + " · PICK A MOVE"
                        hasMuscle -> muscle!!.uppercase() + " · PICK A MOVE"
                        else -> "TARGET MUSCLE"
                    }

                    // Search bar is always visible. Muscle cards only act as filter shortcuts.
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
            val icon = card.findViewById<android.widget.ImageView>(R.id.iv_muscle_icon)
            val selected = name.text.toString() == selectedMuscle
            card.setBackgroundResource(
                if (selected) R.drawable.shape_muscle_card_selected else R.drawable.shape_field
            )
            val tint = resources.getColor(
                if (selected) R.color.paper else R.color.ink,
                null
            )
            icon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
            name.setTextColor(
                resources.getColor(
                    if (selected) R.color.paper else R.color.ink_2,
                    null
                )
            )
        }
    }

    private fun updateList(query: String, type: WorkoutType, muscleFilter: String?) {
        binding.layoutExercisesList.removeAllViews()

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
            binding.layoutExercisesList.addView(binding.tvNoResults)
            return
        }
        binding.tvNoResults.visibility = View.GONE

        if (type == WorkoutType.STRENGTH) {
            EQUIPMENT_ORDER.forEach { equip ->
                val rows = filtered.filter { it.equipment == equip }
                if (rows.isNotEmpty()) {
                    addKicker(equip.label.uppercase())
                    rows.forEach { addExerciseRow(it) }
                }
            }
        } else {
            // Group non-strength by their 'muscle' field (category)
            val categories = filtered.map { it.muscle }.distinct()
            categories.forEach { cat ->
                val rows = filtered.filter { it.muscle == cat }
                addKicker(cat.uppercase())
                rows.forEach { addExerciseRow(it) }
            }
        }
    }

    private fun addKicker(text: String) {
        val kicker = TextView(requireContext(), null, 0, R.style.TextKicker).apply {
            this.text = text
            setPadding(4, 18, 0, 8)
        }
        binding.layoutExercisesList.addView(kicker)
    }

    private fun addExerciseRow(ex: ExerciseDef) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_exercise_row, binding.layoutExercisesList, false)

        val thumbColors = intArrayOf(
            R.color.thumb_a, R.color.thumb_b, R.color.thumb_c,
            R.color.thumb_d, R.color.thumb_e, R.color.thumb_f,
        )
        val slot = (ex.id.hashCode() and Int.MAX_VALUE) % thumbColors.size
        row.findViewById<View>(R.id.ex_thumb).backgroundTintList =
            ColorStateList.valueOf(resources.getColor(thumbColors[slot], null))
        row.findViewById<TextView>(R.id.tv_ex_name).text = ex.name
        row.findViewById<TextView>(R.id.tv_ex_meta).text =
            "${ex.equipment.label} · ${ex.muscle.lowercase()}"

        row.setOnClickListener {
            sessionVM.pickExercise(ex.id, ex.name, ex.muscle)
            findNavController().navigate(R.id.action_exercise_to_log)
        }

        binding.layoutExercisesList.addView(row)
    }

    private fun setupListeners() {
        binding.includeTopBar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
