package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.ThemeMode
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.databinding.AppSettingsDesignBinding
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class App_Settings_Logic : Fragment() {

    private var _binding: AppSettingsDesignBinding? = null
    private val binding get() = _binding!!

    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AppSettingsDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupAppearanceLabels()
        setupObservers()
        setupListeners()
    }

    private fun setupAppearanceLabels() {
        binding.segmentedAppearance.option1.text = "Light"
        binding.segmentedAppearance.option2.text = "Dark"
    }

    private fun setupUI() {
        binding.includeTopBar.tvTopBarTitle.text = "Settings"
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsVM.state.collectLatest { state ->
                    if (binding.etName.text.toString() != state.name) {
                        binding.etName.setText(state.name)
                    }
                    updateUnitUI(state.unit)
                    updateThemeUI(state.theme)
                }
            }
        }
    }

    private fun updateUnitUI(unit: WeightUnit) {
        if (unit == WeightUnit.KG) {
            binding.segmentedUnits.option1.setBackgroundResource(R.drawable.shape_chip_selected)
            binding.segmentedUnits.option1.setTextColor(resources.getColor(R.color.paper, null))
            binding.segmentedUnits.option2.setBackgroundResource(R.drawable.shape_chip)
            binding.segmentedUnits.option2.setTextColor(resources.getColor(R.color.ink_2, null))
        } else {
            binding.segmentedUnits.option2.setBackgroundResource(R.drawable.shape_chip_selected)
            binding.segmentedUnits.option2.setTextColor(resources.getColor(R.color.paper, null))
            binding.segmentedUnits.option1.setBackgroundResource(R.drawable.shape_chip)
            binding.segmentedUnits.option1.setTextColor(resources.getColor(R.color.ink_2, null))
        }
    }

    private fun updateThemeUI(theme: ThemeMode) {
        val lightSelected = theme == ThemeMode.LIGHT
        val darkSelected = theme == ThemeMode.DARK
        binding.segmentedAppearance.option1.setBackgroundResource(
            if (lightSelected) R.drawable.shape_chip_selected else R.drawable.shape_chip
        )
        binding.segmentedAppearance.option1.setTextColor(
            resources.getColor(
                if (lightSelected) R.color.paper else R.color.ink_2,
                null
            )
        )
        binding.segmentedAppearance.option2.setBackgroundResource(
            if (darkSelected) R.drawable.shape_chip_selected else R.drawable.shape_chip
        )
        binding.segmentedAppearance.option2.setTextColor(
            resources.getColor(
                if (darkSelected) R.color.paper else R.color.ink_2,
                null
            )
        )
    }

    private fun setupListeners() {
        binding.includeTopBar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s.toString()
                if (name.isNotBlank()) {
                    settingsVM.setName(name)
                }
            }
        })

        binding.segmentedUnits.option1.setOnClickListener { settingsVM.setUnit(WeightUnit.KG) }
        binding.segmentedUnits.option2.setOnClickListener { settingsVM.setUnit(WeightUnit.LB) }

        binding.segmentedAppearance.option1.setOnClickListener {
            settingsVM.setTheme(ThemeMode.LIGHT)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        binding.segmentedAppearance.option2.setOnClickListener {
            settingsVM.setTheme(ThemeMode.DARK)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
