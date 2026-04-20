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
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.databinding.HomeDesignBinding
import com.worktrax.app.lib.formatTopDate
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

            row.addView(card)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsVM.state.collectLatest { state ->
                    binding.tvGreeting.text = "Hi, ${state.name}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
