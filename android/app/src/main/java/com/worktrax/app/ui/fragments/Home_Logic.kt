package com.worktrax.app.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.databinding.HomeDesignBinding
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.formatTopDate
import com.worktrax.app.lib.greeting
import com.worktrax.app.lib.routinesFromJsonString
import com.worktrax.app.store.SessionViewModel
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("ClassName")
class Home_Logic : Fragment() {

    private var _binding: HomeDesignBinding? = null
    private val binding get() = _binding!!

    private val sessionVM: SessionViewModel by viewModels({ requireActivity() })
    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HomeDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDateKicker.text = formatTopDate()
        setupGrid()
        setupObservers()
        binding.btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_profile)
        }
        showOnboardingIfNeeded()
    }

    private fun showOnboardingIfNeeded() {
        val done = Storage.getString(requireContext(), Storage.KEY_ONBOARDING_DONE)
        if (done != null) return
        val overlay = LayoutInflater.from(requireContext())
            .inflate(R.layout.overlay_onboarding, binding.root as? ViewGroup, false)
        val etName = overlay.findViewById<EditText>(R.id.et_name)
        overlay.findViewById<View>(R.id.btn_get_started).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isBlank()) {
                etName.error = getString(R.string.enter_name_hint)
                etName.requestFocus()
                return@setOnClickListener
            }
            settingsVM.setName(name)
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            Storage.putString(requireContext(), Storage.KEY_ONBOARDING_DONE, "1")
        }
        overlay.findViewById<View>(R.id.btn_skip).setOnClickListener {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            Storage.putString(requireContext(), Storage.KEY_ONBOARDING_DONE, "1")
        }
        (binding.root as? ViewGroup)?.addView(overlay)
    }

    private fun setupGrid() {
        val types = WorkoutType.values()
        val density = resources.displayMetrics.density
        val gap = (10 * density).toInt()
        val topRow = binding.root.findViewById<LinearLayout>(R.id.row_types_top)
        val bottomRow = binding.root.findViewById<LinearLayout>(R.id.row_types_bottom)
        topRow.removeAllViews()
        bottomRow.removeAllViews()

        types.forEachIndexed { index, type ->
            val row = if (index < 2) topRow else bottomRow
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_type_card, row, false)

            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val leftGap = if (index % 2 == 1) gap else 0
            params.setMargins(leftGap, 0, 0, 0)
            card.layoutParams = params

            val nameTv = card.findViewById<TextView>(R.id.tv_name)
            val iconTv = card.findViewById<TextView>(R.id.tv_icon)
            val descTv = card.findViewById<TextView>(R.id.tv_desc)
            val layout = card.findViewById<View>(R.id.layout_bg)

            layout.setBackgroundResource(
                when (type) {
                    WorkoutType.STRENGTH -> R.drawable.type_strength
                    WorkoutType.CARDIO -> R.drawable.type_cardio
                    WorkoutType.AEROBIC -> R.drawable.type_aerobic
                    WorkoutType.YOGA -> R.drawable.type_yoga
                }
            )

            nameTv.text = type.code.replaceFirstChar { it.uppercase() }
            iconTv.text = when (type) {
                WorkoutType.STRENGTH -> "🏋️"
                WorkoutType.CARDIO -> "🏃"
                WorkoutType.AEROBIC -> "⚡"
                WorkoutType.YOGA -> "🧘"
            }
            descTv.text = when (type) {
                WorkoutType.STRENGTH -> "Lift · sets & reps"
                WorkoutType.CARDIO -> "Run · bike · row"
                WorkoutType.AEROBIC -> "HIIT · intervals"
                WorkoutType.YOGA -> "Flow · poses"
            }

            card.setOnClickListener {
                sessionVM.start(type)
                findNavController().navigate(R.id.action_home_to_exercise)
            }
            card.setOnLongClickListener {
                val raw = Storage.getString(requireContext(), Storage.KEY_ROUTINES)
                val routines = routinesFromJsonString(raw).filter { it.type == type }
                if (routines.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.no_saved_routines, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                val names = routines.map { it.name }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.start_routine_title)
                    .setItems(names) { _, i ->
                        val routine = routines[i]
                        sessionVM.start(type)
                        routine.exerciseIds.forEach { id ->
                            val def = com.worktrax.app.data.EXERCISES.find { it.id == id }
                            if (def != null) {
                                sessionVM.pickExercise(def.id, def.name, def.muscle)
                            }
                        }
                        findNavController().navigate(R.id.action_home_to_exercise)
                    }
                    .show()
                true
            }

            row.addView(card)
        }

        // Staggered fade-in-up animation
        val allCards = mutableListOf<View>()
        for (i in 0 until topRow.childCount) allCards.add(topRow.getChildAt(i))
        for (i in 0 until bottomRow.childCount) allCards.add(bottomRow.getChildAt(i))
        allCards.forEachIndexed { idx, card ->
            card.alpha = 0f
            card.translationY = 20f * resources.displayMetrics.density
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((idx * 80).toLong())
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsVM.state.collectLatest { state ->
                    binding.tvGreeting.text = if (state.name.isNotBlank()) {
                        getString(R.string.hi_name_format, state.name)
                    } else {
                        getString(R.string.good_greeting_format, greeting())
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
